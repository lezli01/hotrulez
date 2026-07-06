package dev.lezli.hotrulez.parameterinfo

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.documentation.FirebaseRulesDocs
import dev.lezli.hotrulez.psi.FirebaseRulesArgumentList
import dev.lezli.hotrulez.psi.FirebaseRulesCallExpression
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesMemberExpression
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression
import dev.lezli.hotrulez.psi.FirebaseRulesTypes as T
import dev.lezli.hotrulez.references.FirebaseRulesMemberPath
import dev.lezli.hotrulez.references.FirebaseRulesScopes
import dev.lezli.hotrulez.references.RulesService

/**
 * Parameter info (the `fn(|)` hint popup, View | Parameter Info) for Firebase
 * Rules calls. It presents the *declared* signature of the call under the caret
 * and highlights the argument the caret currently sits on — a read-only authoring
 * aid, not an arity or type check.
 *
 * The owner element is the [FirebaseRulesArgumentList] surrounding the caret; the
 * presentation ([SignaturePresentation]) is one line per candidate signature.
 * The callee (`call.expression`) is dispatched purely structurally, mirroring the
 * completion contributor and resolver so forward references and dialect switches
 * behave identically:
 *
 *  - a [FirebaseRulesReferenceExpression] callee resolves through
 *    [FirebaseRulesScopes] to the visible user [FirebaseRulesFunctionDeclaration](s)
 *    — one signature per declaration (poly-variant), parameters read from the
 *    declaration's parameter list;
 *  - if no user function of that name is visible and the name is a bare path
 *    helper for the file's dialect ([RulesService.bareHelpersFor] — Firestore's
 *    `get`/`exists`/`getAfter`/`existsAfter`, union in a neutral file), a single
 *    `path` signature is shown, annotated with the helper's one-line doc note. A
 *    same-named user function therefore *shadows* the helper, matching scoping;
 *  - a [FirebaseRulesMemberExpression] callee of the shape `firestore.<get|exists>`
 *    ([RulesService.CROSS_SERVICE_HELPERS], the Storage cross-service lookups)
 *    likewise shows a single `path` signature;
 *  - any other member call (e.g. `math.abs`) offers nothing — namespace-function
 *    signatures are deferred (their arity is `UNCONFIRMED`).
 *
 * The current-parameter index is the number of top-level `,` children of the
 * argument list before the caret; commas nested inside argument sub-expressions
 * (maps, lists, ternaries) do not count and a trailing comma is tolerated. Arity
 * is never validated — an over-count simply clamps the highlight onto the last
 * parameter. Every lookup is null-safe so a partially-malformed call never throws.
 */
class FirebaseRulesParameterInfoHandler :
    ParameterInfoHandler<FirebaseRulesArgumentList, FirebaseRulesParameterInfoHandler.SignaturePresentation> {

    /**
     * One rendered signature line: the ordered [params] names and an optional
     * [note] appended (un-highlighted) after them — used to carry a path helper's
     * `get(path)` one-liner from [FirebaseRulesDocs]. User functions have no note.
     */
    data class SignaturePresentation(val params: List<String>, val note: String?)

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): FirebaseRulesArgumentList? {
        val argumentList = argumentListAt(context.file, context.offset) ?: return null
        val items = signaturesFor(argumentList)
        if (items.isEmpty()) return null
        context.itemsToShow = items.toTypedArray()
        return argumentList
    }

    override fun showParameterInfo(element: FirebaseRulesArgumentList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): FirebaseRulesArgumentList? =
        argumentListAt(context.file, context.offset)

    override fun updateParameterInfo(argumentList: FirebaseRulesArgumentList, context: UpdateParameterInfoContext) {
        context.setCurrentParameter(currentParameterIndex(argumentList, context.offset))
    }

    override fun updateUI(p: SignaturePresentation, context: ParameterInfoUIContext) {
        val signature = if (p.params.isEmpty()) NO_PARAMETERS else p.params.joinToString(PARAM_SEPARATOR)

        // Compute the highlight span for the current parameter within the signature
        // text. Clamp into range (never validate arity); no highlight when empty.
        var highlightStart = -1
        var highlightEnd = -1
        if (p.params.isNotEmpty()) {
            val index = context.currentParameterIndex.coerceIn(0, p.params.size - 1)
            var start = 0
            for (i in 0 until index) {
                start += p.params[i].length + PARAM_SEPARATOR.length
            }
            highlightStart = start
            highlightEnd = start + p.params[index].length
        }

        // The note trails the signature and is deliberately outside the highlight span.
        val text = if (p.note != null) signature + NOTE_SEPARATOR + p.note else signature
        context.setupUIComponentPresentation(
            text,
            highlightStart,
            highlightEnd,
            false,
            false,
            false,
            context.defaultParameterColor,
        )
    }

    // couldShowInLookup / getParametersForLookup / getParameterCloseChars /
    // tracksParameterIndex are intentionally NOT overridden: the platform marks them
    // @Deprecated(forRemoval) and "not used", so their defaults already apply and
    // overriding them has no runtime effect — it only trips the plugin verifier's
    // scheduled-for-removal check. The current-parameter highlight is driven entirely by
    // updateParameterInfo -> setCurrentParameter below, not by tracksParameterIndex().

    /** The innermost [FirebaseRulesArgumentList] enclosing [offset] in [file], or null. */
    private fun argumentListAt(file: PsiFile, offset: Int): FirebaseRulesArgumentList? {
        // Fall back one char left so the hint still resolves with the caret parked
        // just inside the closing paren or at the file's end.
        val leaf = file.findElementAt(offset) ?: file.findElementAt((offset - 1).coerceAtLeast(0)) ?: return null
        return PsiTreeUtil.getParentOfType(leaf, FirebaseRulesArgumentList::class.java, false)
    }

    /** The signatures to show for [argumentList]'s enclosing call, dispatched on the callee. */
    private fun signaturesFor(argumentList: FirebaseRulesArgumentList): List<SignaturePresentation> {
        val call = argumentList.parent as? FirebaseRulesCallExpression ?: return emptyList()
        return when (val callee = call.expression) {
            is FirebaseRulesReferenceExpression -> referenceSignatures(callee)
            is FirebaseRulesMemberExpression -> memberSignatures(callee)
            else -> emptyList()
        }
    }

    /**
     * User function(s) named by [callee], resolved scope-based (forward references
     * included); one signature per declaration. When none resolve, a bare path
     * helper of the same name in the file's dialect (union when neutral).
     */
    private fun referenceSignatures(callee: FirebaseRulesReferenceExpression): List<SignaturePresentation> {
        val functions = FirebaseRulesScopes.resolve(callee).filterIsInstance<FirebaseRulesFunctionDeclaration>()
        if (functions.isNotEmpty()) {
            return functions.map { function ->
                val params = function.parameterList?.parameterList?.mapNotNull { it.name }.orEmpty()
                SignaturePresentation(params, null)
            }
        }

        val name = callee.identifier?.text ?: return emptyList()
        if (name in RulesService.bareHelpersFor(RulesService.forElement(callee))) {
            return listOf(SignaturePresentation(listOf(PATH_PARAM), FirebaseRulesDocs.forHelper(name)?.title))
        }
        return emptyList()
    }

    /**
     * The cross-service `firestore.get`/`firestore.exists` helpers (a single `path`
     * argument); any other member call (e.g. `math.abs`) offers nothing. The receiver key
     * is built from tokens via the shared [FirebaseRulesMemberPath.receiverKey] (matching
     * the documentation provider and completion), and the pair is validated against the
     * file's dialect member table — so it never fires inside a Cloud Firestore file, where
     * `firestore` is not a namespace.
     */
    private fun memberSignatures(callee: FirebaseRulesMemberExpression): List<SignaturePresentation> {
        val receiver = FirebaseRulesMemberPath.receiverKey(callee.expression)
        val member = callee.identifier.text
        val members = RulesService.membersFor(RulesService.forElement(callee))
        if (receiver == FIRESTORE_NAMESPACE &&
            member in RulesService.CROSS_SERVICE_HELPERS &&
            member in members[receiver].orEmpty()
        ) {
            return listOf(SignaturePresentation(listOf(PATH_PARAM), FirebaseRulesDocs.forHelper(member)?.title))
        }
        return emptyList()
    }

    /**
     * The count of top-level `,` children of [argumentList] whose start precedes
     * [offset]. Only the argument-list's own comma leaves are counted, so commas
     * nested inside an argument sub-expression are ignored and a trailing comma is
     * harmless; the result is never bounded by the signature's arity.
     */
    private fun currentParameterIndex(argumentList: FirebaseRulesArgumentList, offset: Int): Int {
        var index = 0
        var child = argumentList.node.firstChildNode
        while (child != null) {
            if (child.elementType == T.COMMA && child.textRange.startOffset < offset) index++
            child = child.treeNext
        }
        return index
    }

    private companion object {
        const val PARAM_SEPARATOR = ", "
        const val NOTE_SEPARATOR = "  "
        const val NO_PARAMETERS = "<no parameters>"
        const val PATH_PARAM = "path"
        const val FIRESTORE_NAMESPACE = "firestore"
    }
}
