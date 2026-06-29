package dev.lezli.hotrulez.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import dev.lezli.hotrulez.psi.FirebaseRulesMatchDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesPathWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression
import dev.lezli.hotrulez.references.FirebaseRulesNamedElement

/**
 * Scopes a path-variable rename to the binding's own `match` subtree. Combined
 * with shadowing-aware resolution, this guarantees that renaming an outer
 * `{city}` updates only the uses that resolve to it and leaves a nested,
 * same-named `{city}` (and its uses) untouched.
 *
 * Functions, parameters, and `let` bindings are left to the platform's default
 * processor — their references already resolve correctly.
 */
class FirebaseRulesPathVariableRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean =
        element is FirebaseRulesPathWildcard || element is FirebaseRulesRecursiveWildcard

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> {
        val name = (element as? FirebaseRulesNamedElement)?.name ?: return emptyList()
        val match = PsiTreeUtil.getParentOfType(element, FirebaseRulesMatchDeclaration::class.java)
            ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(match, FirebaseRulesReferenceExpression::class.java)
            .filter { it.identifier.text == name }
            .mapNotNull { it.reference }
            // A use inside a nested match that re-binds the name resolves to that inner
            // binding, so isReferenceTo(outer) is false and the shadow boundary holds.
            .filter { it.isReferenceTo(element) }
    }
}
