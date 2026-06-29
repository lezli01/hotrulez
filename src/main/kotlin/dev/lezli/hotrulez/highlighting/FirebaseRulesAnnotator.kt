package dev.lezli.hotrulez.highlighting

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.lezli.hotrulez.psi.FirebaseRulesTypes as T

/**
 * Adds context-dependent highlighting that the context-free lexer cannot produce:
 * service names (`cloud.firestore`), function declaration names, and path variables
 * bound inside `{...}` wildcards. These rely on parser structure, so they are applied
 * here rather than in the syntax highlighter.
 */
class FirebaseRulesAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val node = element.node ?: return
        when (node.elementType) {
            T.SERVICE_NAME ->
                highlightServiceName(node, holder)
            T.FUNCTION_DECLARATION ->
                highlightFunctionName(node, holder)
            T.PATH_WILDCARD,
            T.RECURSIVE_WILDCARD ->
                highlightPathVariables(node, holder)
        }
    }

    private fun highlightServiceName(node: ASTNode, holder: AnnotationHolder) {
        // Color the identifier segments of the service name (e.g. cloud, firestore);
        // `node` is the `service_name` rule, whose children are the dotted identifiers.
        for (child in node.getChildren(null)) {
            if (child.elementType == T.IDENTIFIER) {
                apply(holder, child.textRange, FirebaseRulesHighlightingColors.SERVICE_NAME)
            }
        }
    }

    private fun highlightFunctionName(node: ASTNode, holder: AnnotationHolder) {
        // The first identifier before the parameter list / body is the function name.
        for (child in node.getChildren(null)) {
            val type = child.elementType
            if (type == T.PARAMETER_LIST ||
                type == T.FUNCTION_BODY
            ) {
                break
            }
            if (type == T.IDENTIFIER) {
                apply(holder, child.textRange, FirebaseRulesHighlightingColors.FUNCTION_DECLARATION)
                break
            }
        }
    }

    private fun highlightPathVariables(node: ASTNode, holder: AnnotationHolder) {
        // The bound variable name(s) inside `{name}` or `{name=**}`.
        for (child in node.getChildren(null)) {
            if (child.elementType == T.IDENTIFIER) {
                apply(holder, child.textRange, FirebaseRulesHighlightingColors.PATH_VARIABLE)
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
