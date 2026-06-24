package dev.lezli.hotrulez.formatting

import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.lexer.FirestoreRulesTokenTypes
import dev.lezli.hotrulez.parser.FirestoreRulesElementTypes
import dev.lezli.hotrulez.parser.FirestoreRulesTokenSets

class FirestoreRulesBlock(
    val astNode: ASTNode,
    private val blockIndent: Indent? = Indent.getNoneIndent(),
) : AbstractBlock(astNode, Wrap.createWrap(WrapType.NONE, false), null) {
    override fun buildChildren(): List<Block> =
        astNode
            .getChildren(null)
            .filterNot { it.elementType == TokenType.WHITE_SPACE }
            .map { child -> FirestoreRulesBlock(child, childIndent(child)) }

    override fun getIndent(): Indent? = blockIndent

    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        spacingBetween(
            (child1 as? FirestoreRulesBlock)?.astNode,
            (child2 as? FirestoreRulesBlock)?.astNode,
        )

    override fun isLeaf(): Boolean = astNode.firstChildNode == null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
        if (astNode.elementType == FirestoreRulesElementTypes.BLOCK) {
            ChildAttributes(NESTED_INDENT, null)
        } else {
            ChildAttributes(Indent.getNoneIndent(), null)
        }

    private fun childIndent(child: ASTNode): Indent? =
        if (astNode.elementType == FirestoreRulesElementTypes.BLOCK && !child.isBrace()) {
            NESTED_INDENT
        } else {
            Indent.getNoneIndent()
        }

    private fun spacingBetween(left: ASTNode?, right: ASTNode?): Spacing? {
        if (left == null || right == null) {
            return null
        }

        if (astNode.elementType == FirestoreRulesElementTypes.BLOCK) {
            if (left.elementType == FirestoreRulesTokenTypes.L_BRACE && right.elementType == FirestoreRulesTokenTypes.R_BRACE) {
                return noSpace()
            }
            if (left.elementType == FirestoreRulesTokenTypes.L_BRACE || right.elementType == FirestoreRulesTokenTypes.R_BRACE) {
                return lineBreak()
            }
            // Follow the Firebase docs' layout: separate block-level members
            // (function declarations and match blocks) from their siblings with a
            // blank line, while keeping simple statements (allow, return) tight.
            if (separatedByBlankLine(left, right)) {
                return blankLine()
            }
            return lineBreak()
        }

        if (astNode.elementType == FirestoreRulesElementTypes.FILE) {
            return lineBreak()
        }

        if (left.elementType == FirestoreRulesTokenTypes.SEMICOLON) {
            return lineBreak()
        }

        if (right.elementType == FirestoreRulesElementTypes.MATCH_PATH) {
            return oneSpace()
        }

        if (right.elementType == FirestoreRulesElementTypes.BLOCK) {
            return oneSpace()
        }

        if (left.elementType == FirestoreRulesElementTypes.BLOCK) {
            return lineBreak()
        }

        if (left.isComment() || right.isComment()) {
            return lineBreak()
        }

        if (left.inMatchPath() || right.inMatchPath()) {
            return matchPathSpacing(left, right)
        }

        if (left.elementType == FirestoreRulesTokenTypes.OPERATOR && left.text == "!") {
            return noSpace()
        }

        return tokenSpacing(left.elementType, right.elementType)
    }

    private fun matchPathSpacing(left: ASTNode, right: ASTNode): Spacing {
        val leftType = left.elementType
        val rightType = right.elementType

        if (leftType == FirestoreRulesTokenTypes.COMMA) {
            return oneSpace()
        }
        if (rightType == FirestoreRulesTokenTypes.COMMA) {
            return noSpace()
        }
        return noSpace()
    }

    private fun tokenSpacing(leftType: IElementType, rightType: IElementType): Spacing {
        if (leftType == FirestoreRulesTokenTypes.COMMA) {
            return oneSpace()
        }
        if (rightType == FirestoreRulesTokenTypes.COMMA ||
            rightType == FirestoreRulesTokenTypes.SEMICOLON ||
            rightType == FirestoreRulesTokenTypes.COLON ||
            rightType == FirestoreRulesElementTypes.PARAMETER_LIST
        ) {
            return noSpace()
        }
        if (leftType == FirestoreRulesTokenTypes.COLON) {
            return oneSpace()
        }
        if (leftType == FirestoreRulesTokenTypes.DOT ||
            rightType == FirestoreRulesTokenTypes.DOT ||
            rightType == FirestoreRulesTokenTypes.L_PAREN ||
            leftType == FirestoreRulesTokenTypes.L_PAREN ||
            rightType == FirestoreRulesTokenTypes.R_PAREN ||
            leftType == FirestoreRulesTokenTypes.L_BRACKET ||
            rightType == FirestoreRulesTokenTypes.R_BRACKET
        ) {
            return noSpace()
        }
        if (leftType in FirestoreRulesTokenSets.EXPRESSION_OPERATORS ||
            rightType in FirestoreRulesTokenSets.EXPRESSION_OPERATORS
        ) {
            return oneSpace()
        }
        if (rightType == FirestoreRulesElementTypes.BLOCK) {
            return oneSpace()
        }
        return oneSpace()
    }

    private fun separatedByBlankLine(left: ASTNode, right: ASTNode): Boolean {
        // A leading comment stays attached to the member it documents, so any
        // blank line belongs before the comment, not between it and that member.
        if (left.isComment()) {
            return false
        }
        // Look past a leading comment so a documented function/match still gets
        // separated from the previous member (the blank lands before the comment).
        val rightTarget = if (right.isComment()) firstNonCommentSibling(right) else right
        return isBlockMember(left) || (rightTarget != null && isBlockMember(rightTarget))
    }

    private fun isBlockMember(node: ASTNode): Boolean =
        node.elementType == FirestoreRulesElementTypes.FUNCTION_DECLARATION ||
            node.elementType == FirestoreRulesElementTypes.MATCH_BLOCK

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
        elementType == FirestoreRulesTokenTypes.L_BRACE || elementType == FirestoreRulesTokenTypes.R_BRACE

    private fun ASTNode.isComment(): Boolean = elementType in FirestoreRulesTokenSets.COMMENTS

    private fun ASTNode.inMatchPath(): Boolean =
        elementType == FirestoreRulesElementTypes.MATCH_PATH ||
            treeParent?.elementType == FirestoreRulesElementTypes.MATCH_PATH ||
            treeParent?.treeParent?.elementType == FirestoreRulesElementTypes.MATCH_PATH

    private fun noSpace(): Spacing = Spacing.createSpacing(0, 0, 0, true, 1)

    private fun oneSpace(): Spacing = Spacing.createSpacing(1, 1, 0, true, 1)

    private fun lineBreak(): Spacing = Spacing.createSpacing(0, 0, 1, true, 1)

    private fun blankLine(): Spacing = Spacing.createSpacing(0, 0, 2, true, 1)

    private companion object {
        val NESTED_INDENT: Indent = Indent.getSpaceIndent(2)
    }
}
