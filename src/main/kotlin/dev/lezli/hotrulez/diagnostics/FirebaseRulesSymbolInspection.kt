package dev.lezli.hotrulez.diagnostics

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.diagnostics.fixes.CreateFunctionFix
import dev.lezli.hotrulez.diagnostics.fixes.RemoveDeclarationFix
import dev.lezli.hotrulez.diagnostics.fixes.asQuickFix
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesLetStatement
import dev.lezli.hotrulez.psi.FirebaseRulesParameter
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression
import dev.lezli.hotrulez.references.FirebaseRulesBuiltins
import dev.lezli.hotrulez.references.FirebaseRulesScopes

/**
 * Flags user symbols that don't resolve and user declarations that nothing references,
 * reusing the same scope-based resolver as go-to-definition / rename / find-usages
 * ([FirebaseRulesScopes]) so the two directions stay consistent:
 *
 *  - a `reference_expression` that resolves to nothing and is not a built-in is an
 *    **undefined reference** (`Cannot resolve symbol 'x'`); an undefined *callee* additionally
 *    offers a create-function fix;
 *  - a `function` / `let` / parameter that no reference resolves to is **unused**.
 *
 * Members (`request.auth`) are excluded structurally — they are `member_expression`s, not
 * `reference_expression`s — as are path / wildcard variables (never reported unused, since a
 * match binding is meaningful even when its condition never reads it) and built-ins/helpers.
 * A single pass resolves every reference once, both to surface undefined uses and to
 * accumulate the set of declarations that are used; forward references and post-declaration
 * `let` visibility fall out of the resolver for free.
 */
class FirebaseRulesSymbolInspection : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is FirebaseRulesFile) return null

        val problems = mutableListOf<ProblemDescriptor>()
        val usedDeclarations = HashSet<PsiElement>()

        for (reference in PsiTreeUtil.findChildrenOfType(file, FirebaseRulesReferenceExpression::class.java)) {
            // Skip anything inside a parse error so we don't pile onto the parser's own
            // diagnostics while the file is being typed / is malformed.
            if (PsiTreeUtil.getParentOfType(reference, PsiErrorElement::class.java) != null) continue

            val targets = FirebaseRulesScopes.resolve(reference)
            if (targets.isNotEmpty()) {
                // A reference inside a function's own body is not a real use of that function,
                // so a purely self-recursive helper is still reported unused (Firebase rejects
                // recursion anyway); a genuine external call keeps the function used.
                val enclosingFunction = reference.enclosingFunction()
                targets.filterTo(usedDeclarations) { it !== enclosingFunction }
                continue
            }

            val name = reference.identifier.text
            if (FirebaseRulesBuiltins.isBuiltinName(name)) continue

            val fixes = if (FirebaseRulesScopes.isFunctionCall(reference)) {
                arrayOf(CreateFunctionFix(reference).asQuickFix())
            } else {
                LocalQuickFix.EMPTY_ARRAY
            }
            problems += manager.createProblemDescriptor(
                reference,
                "Cannot resolve symbol '$name'.",
                isOnTheFly,
                fixes,
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            )
        }

        val unusedFunctions = PsiTreeUtil.findChildrenOfType(file, FirebaseRulesFunctionDeclaration::class.java)
            .filter { it !in usedDeclarations }
            .toSet()
        for (function in unusedFunctions) {
            val nameIdentifier = function.nameIdentifier ?: continue
            problems += unused(
                manager, nameIdentifier, isOnTheFly,
                "Function '${function.name}' is never used.",
                RemoveDeclarationFix(function, "function '${function.name}'").asQuickFix(),
            )
        }

        for (binding in PsiTreeUtil.findChildrenOfType(file, FirebaseRulesLetStatement::class.java)) {
            if (binding in usedDeclarations || binding.enclosingFunction() in unusedFunctions) continue
            val nameIdentifier = binding.nameIdentifier ?: continue
            problems += unused(
                manager, nameIdentifier, isOnTheFly,
                "Variable '${binding.name}' is never used.",
                RemoveDeclarationFix(binding, "'let' binding").asQuickFix(),
            )
        }

        // Parameters are report-only: removing one would change the function's arity and every
        // call site, which is deferred beyond 0.7. Skip params of an already-unused function so
        // the same declaration isn't flagged twice.
        for (parameter in PsiTreeUtil.findChildrenOfType(file, FirebaseRulesParameter::class.java)) {
            if (parameter in usedDeclarations || parameter.enclosingFunction() in unusedFunctions) continue
            val nameIdentifier = parameter.nameIdentifier ?: continue
            problems += unused(manager, nameIdentifier, isOnTheFly, "Parameter '${parameter.name}' is never used.")
        }

        return problems.toTypedArray()
    }

    private fun PsiElement.enclosingFunction(): FirebaseRulesFunctionDeclaration? =
        PsiTreeUtil.getParentOfType(this, FirebaseRulesFunctionDeclaration::class.java)

    private fun unused(
        manager: InspectionManager,
        element: PsiElement,
        isOnTheFly: Boolean,
        message: String,
        vararg fixes: LocalQuickFix,
    ): ProblemDescriptor =
        manager.createProblemDescriptor(element, message, isOnTheFly, fixes, ProblemHighlightType.LIKE_UNUSED_SYMBOL)
}
