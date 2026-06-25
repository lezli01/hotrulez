package dev.lezli.hotrulez.formatting

import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.parser.FirestoreRulesTokenSets
import dev.lezli.hotrulez.psi.FirestoreRulesTypes as T

/**
 * Formatter block backed by the Grammar-Kit PSI. Children of a braced block
 * (`block` / `function_body`) are indented one level; everything else aligns
 * with its statement. All spacings keep user line breaks so multiline
 * conditions and intentional blank lines survive.
 */
class FirestoreRulesBlock(
    node: ASTNode,
    private val blockIndent: Indent? = Indent.getNoneIndent(),
) : AbstractBlock(node, Wrap.createWrap(WrapType.NONE, false), null) {

    override fun buildChildren(): List<Block> =
        node
            .getChildren(null)
            .filterNot { it.elementType == TokenType.WHITE_SPACE || it.textLength == 0 }
            .map { child -> FirestoreRulesBlock(child, childIndent(child)) }

    override fun getIndent(): Indent? = blockIndent

    override fun isLeaf(): Boolean = node.firstChildNode == null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
        if (node.elementType in BRACED_BLOCKS) {
            ChildAttributes(NESTED_INDENT, null)
        } else {
            ChildAttributes(Indent.getNoneIndent(), null)
        }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        spacingBetween(
            (child1 as? FirestoreRulesBlock)?.node,
            (child2 as? FirestoreRulesBlock)?.node,
        )

    private fun childIndent(child: ASTNode): Indent? =
        if (node.elementType in BRACED_BLOCKS && !child.isBrace()) {
            NESTED_INDENT
        } else {
            Indent.getNoneIndent()
        }

    private fun spacingBetween(left: ASTNode?, right: ASTNode?): Spacing? {
        if (left == null || right == null) {
            return null
        }
        val parent = node.elementType

        // Braced blocks: opener/body/closer each on their own line.
        if (parent in BRACED_BLOCKS) {
            if (left.elementType == T.LBRACE && right.elementType == T.RBRACE) {
                return noSpace()
            }
            // Strip a blank line immediately after '{' or before '}'.
            if (left.elementType == T.LBRACE || right.elementType == T.RBRACE) {
                return lineBreakNoBlank()
            }
            // Separate structural block members while keeping simple statements tight.
            if (separatedByBlankLine(left, right)) {
                return blankLine()
            }
            return lineBreak()
        }

        if (parent == FILE) {
            return lineBreak()
        }

        // Comments always sit on their own line within their statement.
        if (left.isComment() || right.isComment()) {
            return lineBreak()
        }

        return tokenSpacing(parent, left.elementType, right.elementType)
    }

    private fun tokenSpacing(parent: IElementType, left: IElementType, right: IElementType): Spacing {
        // One space before an opening braced block: `service ... {`, `match ... {`, `function(...) {`.
        if (right in BRACED_BLOCKS) {
            return oneSpace()
        }

        // No space before a call/parameter argument list or a subscript: `f(x)`, `a[0]`.
        if (right == T.PARAMETER_LIST || right == T.ARGUMENT_LIST || right == T.LBRACKET) {
            return noSpace()
        }

        // Paths and path arguments never carry internal spaces.
        if (parent in PATH_ELEMENTS) {
            return noSpace()
        }

        // No space immediately inside (), [], or around map/wildcard braces.
        if (left == T.LPAREN || right == T.RPAREN ||
            left == T.LBRACKET || right == T.RBRACKET ||
            left == T.LBRACE || right == T.RBRACE
        ) {
            return noSpace()
        }

        // No space around member-access dots.
        if (left == T.DOT || right == T.DOT) {
            return noSpace()
        }

        // Comma and statement-terminator spacing.
        if (right == T.COMMA || right == T.SEMICOLON) {
            return noSpace()
        }
        if (left == T.COMMA) {
            return oneSpace()
        }

        // Colon depends on the surrounding construct.
        if (left == T.COLON || right == T.COLON) {
            return colonSpacing(parent, right == T.COLON)
        }

        // Ternary '?' takes a space on both sides.
        if (left == T.QUESTION || right == T.QUESTION) {
            return oneSpace()
        }

        // Unary operators stay attached to their operand: `!x`, `-1`.
        if (parent == T.NOT_EXPRESSION || parent == T.NEG_EXPRESSION) {
            return noSpace()
        }

        // Recursive wildcard `{name=**}` has no internal spaces.
        if (parent == T.RECURSIVE_WILDCARD) {
            return noSpace()
        }

        // Everything else — binary/relational/logical operators, assignment in
        // `rules_version`/`let`, and keyword-separated tokens — takes one space on
        // each side.
        return oneSpace()
    }

    private fun colonSpacing(parent: IElementType, beforeColon: Boolean): Spacing =
        when (parent) {
            T.TERNARY_EXPRESSION -> oneSpace()
            T.SLICE -> noSpace()
            else -> if (beforeColon) noSpace() else oneSpace() // allow `: if`, map `key: value`
        }

    private fun separatedByBlankLine(left: ASTNode, right: ASTNode): Boolean {
        // A leading comment stays attached to the member it documents, so any
        // blank line belongs before the comment, not between it and that member.
        if (left.isComment()) {
            return false
        }
        val rightTarget = if (right.isComment()) firstNonCommentSibling(right) else right
        return left.isBlockMember() || rightTarget?.isBlockMember() == true
    }

    private fun firstNonCommentSibling(node: ASTNode): ASTNode? {
        var sibling = node.treeNext
        while (sibling != null &&
            (sibling.elementType == TokenType.WHITE_SPACE || sibling.isComment())
        ) {
            sibling = sibling.treeNext
        }
        return sibling
    }

    private fun ASTNode.isBrace(): Boolean =
        elementType == T.LBRACE || elementType == T.RBRACE

    private fun ASTNode.isComment(): Boolean = elementType in FirestoreRulesTokenSets.COMMENTS

    private fun ASTNode.isBlockMember(): Boolean =
        elementType == T.FUNCTION_DECLARATION || elementType == T.MATCH_DECLARATION

    private fun noSpace(): Spacing = Spacing.createSpacing(0, 0, 0, true, 1)

    private fun oneSpace(): Spacing = Spacing.createSpacing(1, 1, 0, true, 1)

    private fun lineBreak(): Spacing = Spacing.createSpacing(0, 0, 1, true, 1)

    private fun blankLine(): Spacing = Spacing.createSpacing(0, 0, 2, true, 1)

    private fun lineBreakNoBlank(): Spacing = Spacing.createSpacing(0, 0, 1, true, 0)

    private companion object {
        val NESTED_INDENT: Indent = Indent.getSpaceIndent(2)
        val FILE: IElementType = dev.lezli.hotrulez.parser.FirestoreRulesParserDefinition.FILE

        val BRACED_BLOCKS = setOf(T.BLOCK, T.FUNCTION_BODY)

        val PATH_ELEMENTS = setOf(
            T.MATCH_PATH,
            T.PATH_ARGUMENT,
            T.PATH_WILDCARD,
            T.PATH_INTERPOLATION,
            T.PAREN_PATH_SEGMENT,
            // RECURSIVE_WILDCARD handled separately so the rule reads explicitly,
            // but it is also space-free:
        )
    }
}
