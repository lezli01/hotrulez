package dev.lezli.hotrulez.diagnostics

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.diagnostics.FirestoreRulesDiagnostics.SERVICE_FIRESTORE
import dev.lezli.hotrulez.psi.FirestoreRulesFile
import dev.lezli.hotrulez.psi.FirestoreRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesMatchDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesRulesVersionStatement
import dev.lezli.hotrulez.psi.FirestoreRulesServiceDeclaration

/**
 * File-level structure warnings for Firestore Rules: the checks that need the
 * whole file in view rather than a single element. They are warnings (not
 * errors) and configurable because a partial or snippet `.rules` file is a
 * legitimate intermediate state while editing.
 *
 * Wording is purely structural — these checks never assert that a rule is
 * secure or that it authorizes a request correctly.
 */
class FirestoreRulesStructureInspection : LocalInspectionTool() {
    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): Array<ProblemDescriptor>? {
        if (file !is FirestoreRulesFile) return null

        val versions = PsiTreeUtil.getChildrenOfType(file, FirestoreRulesRulesVersionStatement::class.java)
            ?.toList().orEmpty()
        val services = PsiTreeUtil.getChildrenOfType(file, FirestoreRulesServiceDeclaration::class.java)
            ?.toList().orEmpty()

        // Nothing declared yet (empty or comment-only file): no expectations to enforce.
        val anchor = firstDeclaration(file) ?: return null

        val problems = mutableListOf<ProblemDescriptor>()

        if (versions.isEmpty()) {
            problems += problem(manager, anchor, isOnTheFly, "Missing 'rules_version = '2';' declaration at the top of the file.")
        } else if (services.isNotEmpty()) {
            val firstServiceOffset = services.minOf { it.textRange.startOffset }
            for (version in versions) {
                if (version.textRange.startOffset > firstServiceOffset) {
                    problems += problem(manager, version, isOnTheFly, "'rules_version' must be declared before the 'service' block.")
                }
            }
        }

        if (services.isEmpty()) {
            problems += problem(manager, anchor, isOnTheFly, "Missing 'service cloud.firestore { ... }' block.")
        } else {
            for (service in services) {
                checkService(service, manager, isOnTheFly, problems)
            }
        }

        return if (problems.isEmpty()) null else problems.toTypedArray()
    }

    private fun checkService(
        service: FirestoreRulesServiceDeclaration,
        manager: InspectionManager,
        isOnTheFly: Boolean,
        problems: MutableList<ProblemDescriptor>,
    ) {
        val serviceName = service.serviceName ?: return
        val name = serviceName.text.filterNot { it.isWhitespace() }

        if (name != SERVICE_FIRESTORE) {
            problems += problem(
                manager,
                serviceName,
                isOnTheFly,
                "Firestore Rules files target 'service cloud.firestore'; found 'service $name'.",
            )
            return
        }

        val block = service.block ?: return
        val hasRootMatch = PsiTreeUtil.findChildrenOfType(block, FirestoreRulesMatchDeclaration::class.java)
            .any { FirestoreRulesDiagnostics.isRootDocumentsPath(it.matchPath) }
        if (!hasRootMatch) {
            problems += problem(
                manager,
                serviceName,
                isOnTheFly,
                "Missing root 'match /databases/{database}/documents' block inside 'service cloud.firestore'.",
            )
        }
    }

    /** The first top-level declaration, used as the anchor for file-wide problems. */
    private fun firstDeclaration(file: FirestoreRulesFile): PsiElement? =
        file.children.firstOrNull {
            it is FirestoreRulesRulesVersionStatement ||
                it is FirestoreRulesServiceDeclaration ||
                it is FirestoreRulesMatchDeclaration ||
                it is FirestoreRulesFunctionDeclaration
        }

    private fun problem(
        manager: InspectionManager,
        element: PsiElement,
        isOnTheFly: Boolean,
        message: String,
    ): ProblemDescriptor =
        manager.createProblemDescriptor(
            element,
            message,
            isOnTheFly,
            LocalQuickFix.EMPTY_ARRAY,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        )
}
