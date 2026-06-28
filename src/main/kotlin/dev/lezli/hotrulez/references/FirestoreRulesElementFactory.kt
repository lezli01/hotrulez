package dev.lezli.hotrulez.references

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import dev.lezli.hotrulez.FirestoreRulesFileType
import dev.lezli.hotrulez.psi.FirestoreRulesFunctionDeclaration

/** Synthesises throwaway PSI fragments, used to produce replacement leaves for rename. */
object FirestoreRulesElementFactory {
    /**
     * A standalone `IDENTIFIER` leaf carrying [name], extracted from a throwaway
     * `function <name>() { ... }` file. In the IDE the names validator rejects an
     * illegal [name] before a rename reaches here; if a caller bypasses that (e.g. a
     * programmatic rename to a keyword), the synthesised function has no name
     * identifier, and a platform-friendly [IncorrectOperationException] is thrown
     * rather than a bare error.
     */
    fun identifier(project: Project, name: String): PsiElement {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("_hotrulez_dummy.rules", FirestoreRulesFileType, "function $name() { return true; }")
        val identifier = PsiTreeUtil.findChildOfType(file, FirestoreRulesFunctionDeclaration::class.java)?.identifier
        return identifier ?: throw IncorrectOperationException("'$name' is not a valid Firestore Rules identifier")
    }
}
