package dev.lezli.hotrulez.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import dev.lezli.hotrulez.psi.FirestoreRulesMatchDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesPathWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesReferenceExpression
import dev.lezli.hotrulez.references.FirestoreRulesNamedElement

/**
 * Scopes a path-variable rename to the binding's own `match` subtree. Combined
 * with shadowing-aware resolution, this guarantees that renaming an outer
 * `{city}` updates only the uses that resolve to it and leaves a nested,
 * same-named `{city}` (and its uses) untouched.
 *
 * Functions, parameters, and `let` bindings are left to the platform's default
 * processor — their references already resolve correctly.
 */
class FirestoreRulesPathVariableRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean =
        element is FirestoreRulesPathWildcard || element is FirestoreRulesRecursiveWildcard

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> {
        val name = (element as? FirestoreRulesNamedElement)?.name ?: return emptyList()
        val match = PsiTreeUtil.getParentOfType(element, FirestoreRulesMatchDeclaration::class.java)
            ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(match, FirestoreRulesReferenceExpression::class.java)
            .filter { it.identifier.text == name }
            .mapNotNull { it.reference }
            // A use inside a nested match that re-binds the name resolves to that inner
            // binding, so isReferenceTo(outer) is false and the shadow boundary holds.
            .filter { it.isReferenceTo(element) }
    }
}
