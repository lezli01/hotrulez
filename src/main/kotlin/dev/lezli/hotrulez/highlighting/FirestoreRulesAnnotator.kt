package dev.lezli.hotrulez.highlighting

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.lezli.hotrulez.lexer.FirestoreRulesTokenTypes
import dev.lezli.hotrulez.parser.FirestoreRulesElementTypes

/**
 * Adds context-dependent highlighting that the context-free lexer cannot produce:
 * service names (`cloud.firestore`), function declaration names, and path variables
 * bound inside `{...}` wildcards. These rely on parser structure, so they are applied
 * here rather than in the syntax highlighter.
 */
class FirestoreRulesAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val node = element.node ?: return
        when (node.elementType) {
            FirestoreRulesElementTypes.SERVICE_DECLARATION ->
                highlightServiceName(node, holder)
            FirestoreRulesElementTypes.FUNCTION_DECLARATION ->
                highlightFunctionName(node, holder)
            FirestoreRulesElementTypes.PATH_WILDCARD,
            FirestoreRulesElementTypes.RECURSIVE_WILDCARD ->
                highlightPathVariables(node, holder)
        }
    }

    private fun highlightServiceName(node: ASTNode, holder: AnnotationHolder) {
        // Color the identifier segments of the service name (e.g. cloud, firestore),
        // stopping before the service body block.
        for (child in node.getChildren(null)) {
            if (child.elementType == FirestoreRulesElementTypes.BLOCK) {
                break
            }
            if (child.elementType == FirestoreRulesTokenTypes.IDENTIFIER) {
                apply(holder, child.textRange, FirestoreRulesHighlightingColors.SERVICE_NAME)
            }
        }
    }

    private fun highlightFunctionName(node: ASTNode, holder: AnnotationHolder) {
        // The first name token before the parameter list / body is the function name.
        // The lexer usually reports it as a FUNCTION_CALL (identifier followed by `(`)
        // or a plain IDENTIFIER, but a function whose name shadows a built-in or an
        // operation word (e.g. `function exists(...)`) is lexed as BUILTIN/OPERATION;
        // accept those here so the declaration name is always highlighted as one.
        for (child in node.getChildren(null)) {
            val type = child.elementType
            if (type == FirestoreRulesElementTypes.PARAMETER_LIST ||
                type == FirestoreRulesElementTypes.BLOCK
            ) {
                break
            }
            if (type == FirestoreRulesTokenTypes.FUNCTION_CALL ||
                type == FirestoreRulesTokenTypes.IDENTIFIER ||
                type == FirestoreRulesTokenTypes.BUILTIN ||
                type == FirestoreRulesTokenTypes.OPERATION
            ) {
                apply(holder, child.textRange, FirestoreRulesHighlightingColors.FUNCTION_DECLARATION)
                break
            }
        }
    }

    private fun highlightPathVariables(node: ASTNode, holder: AnnotationHolder) {
        // The bound variable name(s) inside `{name}` or `{name=**}`.
        for (child in node.getChildren(null)) {
            if (child.elementType == FirestoreRulesTokenTypes.IDENTIFIER) {
                apply(holder, child.textRange, FirestoreRulesHighlightingColors.PATH_VARIABLE)
            }
        }
    }

    private fun apply(holder: AnnotationHolder, range: TextRange, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range)
            .textAttributes(key)
            .create()
    }
}
