package dev.lezli.hotrulez.references

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.psi.FirebaseRulesBlock
import dev.lezli.hotrulez.psi.FirebaseRulesCallExpression
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionBody
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesMatchDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression

/**
 * Scope-based resolution for Firestore Rules, implementing the language's actual
 * (not textual) visibility rules:
 *
 *  - **Functions** resolve to a declaration of that name visible in the current
 *    or an enclosing service/match scope, regardless of declaration order
 *    (forward references are valid; resolution is scope-based, not
 *    top-to-bottom).
 *  - **Parameters** are function-local.
 *  - **`let` bindings** are function-local and visible only *after* their
 *    declaration.
 *  - **Path / wildcard variables** are visible within the binding `match`
 *    subtree and any nested scope (including functions declared inside the
 *    match), with a nested redeclaration shadowing the outer name.
 *
 * Built-ins (`request`, `resource`) and helpers (`get`/`exists`/...) are not
 * declarations and resolve to nothing — recognised, but non-navigable.
 */
object FirebaseRulesScopes {

    /** Declaration(s) a use denotes: function(s) in callee position, else a single variable binding. */
    fun resolve(reference: FirebaseRulesReferenceExpression): List<FirebaseRulesNamedElement> {
        val name = reference.identifier.text
        return if (isFunctionCall(reference)) {
            resolveFunctionCall(reference, name)
        } else {
            listOfNotNull(resolveVariable(reference, name))
        }
    }

    /** True when [reference] is the callee of a call expression — i.e. a function name. */
    fun isFunctionCall(reference: FirebaseRulesReferenceExpression): Boolean {
        val parent = reference.parent
        return parent is FirebaseRulesCallExpression && parent.expression === reference
    }

    /**
     * The function(s) named [name] in the nearest enclosing scope that declares one. A nested-scope
     * declaration shadows an outer same-named one (mirroring path-variable/`let` shadowing); forward
     * references within that scope still resolve because the whole scope is scanned, not just the
     * preceding siblings. Stays poly-variant within a single scope to cover genuine duplicates.
     */
    private fun resolveFunctionCall(place: PsiElement, name: String): List<FirebaseRulesFunctionDeclaration> {
        var scope: PsiElement? = place
        while (scope != null) {
            val matches = functionsDeclaredIn(scope).filter { it.name == name }
            if (matches.isNotEmpty()) return matches
            scope = scope.parent
        }
        return emptyList()
    }

    /** Resolve a value use to the nearest enclosing parameter, `let`, or path variable. */
    fun resolveVariable(use: PsiElement, name: String): FirebaseRulesNamedElement? {
        var context: PsiElement? = use.parent
        while (context != null) {
            when (context) {
                is FirebaseRulesFunctionBody -> {
                    // A `let` is visible only after its full declaration; the nearest such wins.
                    val binding = context.letStatementList
                        .filter { it.name == name && it.textRange.endOffset <= use.textRange.startOffset }
                        .maxByOrNull { it.textRange.startOffset }
                    if (binding != null) return binding
                }
                is FirebaseRulesFunctionDeclaration -> {
                    val parameter = context.parameterList?.parameterList?.firstOrNull { it.name == name }
                    if (parameter != null) return parameter
                }
                is FirebaseRulesMatchDeclaration -> {
                    val wildcard = pathVariableBindings(context).firstOrNull { it.name == name }
                    if (wildcard != null) return wildcard
                }
            }
            context = context.parent
        }
        return null
    }

    /**
     * Every in-scope user-defined named symbol at [place], for completion. Nearest
     * binding wins on a name collision (so an inner path variable / local shadows an
     * outer one), and `let` bindings appear only once their declaration precedes [place].
     */
    fun visibleSymbols(place: PsiElement): List<FirebaseRulesNamedElement> {
        val byName = LinkedHashMap<String, FirebaseRulesNamedElement>()
        fun offer(element: FirebaseRulesNamedElement) {
            val name = element.name ?: return
            byName.putIfAbsent(name, element)
        }

        var context: PsiElement? = place
        while (context != null) {
            when (context) {
                is FirebaseRulesFunctionBody ->
                    context.letStatementList
                        .filter { it.textRange.endOffset <= place.textRange.startOffset }
                        .forEach(::offer)
                is FirebaseRulesFunctionDeclaration ->
                    context.parameterList?.parameterList?.forEach(::offer)
                is FirebaseRulesMatchDeclaration ->
                    pathVariableBindings(context).forEach(::offer)
            }
            functionsDeclaredIn(context).forEach(::offer)
            context = context.parent
        }
        return byName.values.toList()
    }

    private fun functionsDeclaredIn(scope: PsiElement): List<FirebaseRulesFunctionDeclaration> =
        when (scope) {
            is FirebaseRulesBlock -> scope.functionDeclarationList
            is FirebaseRulesFile ->
                PsiTreeUtil.getChildrenOfTypeAsList(scope, FirebaseRulesFunctionDeclaration::class.java)
            else -> emptyList()
        }

    private fun pathVariableBindings(match: FirebaseRulesMatchDeclaration): List<FirebaseRulesNamedElement> {
        val path = match.matchPath ?: return emptyList()
        val bindings = mutableListOf<FirebaseRulesNamedElement>()
        bindings += path.pathWildcardList
        bindings += path.recursiveWildcardList
        return bindings
    }
}
