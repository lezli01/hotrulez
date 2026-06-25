package dev.lezli.hotrulez.parser

import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import dev.lezli.hotrulez.psi.FirestoreRulesTypes

/**
 * Token sets over the generated [FirestoreRulesTypes] tokens, used by the
 * parser definition and the formatter.
 */
object FirestoreRulesTokenSets {
    val WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    val COMMENTS: TokenSet = TokenSet.create(
        FirestoreRulesTypes.LINE_COMMENT,
        FirestoreRulesTypes.BLOCK_COMMENT,
    )

    val STRINGS: TokenSet = TokenSet.create(FirestoreRulesTypes.STRING)

    val BRACES: TokenSet = TokenSet.create(
        FirestoreRulesTypes.LBRACE,
        FirestoreRulesTypes.RBRACE,
    )

    /** Binary/relational/logical operator tokens that take a space on each side. */
    val BINARY_OPERATORS: TokenSet = TokenSet.create(
        FirestoreRulesTypes.OR_OR,
        FirestoreRulesTypes.AND_AND,
        FirestoreRulesTypes.EQEQ,
        FirestoreRulesTypes.NE,
        FirestoreRulesTypes.LT,
        FirestoreRulesTypes.LE,
        FirestoreRulesTypes.GT,
        FirestoreRulesTypes.GE,
        FirestoreRulesTypes.PLUS,
        FirestoreRulesTypes.MINUS,
        FirestoreRulesTypes.STAR,
        FirestoreRulesTypes.SLASH,
        FirestoreRulesTypes.PERCENT,
        FirestoreRulesTypes.IN_KEYWORD,
        FirestoreRulesTypes.IS_KEYWORD,
    )
}
