package dev.lezli.hotrulez.parser

import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import dev.lezli.hotrulez.lexer.FirestoreRulesTokenTypes

object FirestoreRulesTokenSets {
    val WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
    val COMMENTS: TokenSet = TokenSet.create(
        FirestoreRulesTokenTypes.LINE_COMMENT,
        FirestoreRulesTokenTypes.BLOCK_COMMENT,
    )
    val STRINGS: TokenSet = TokenSet.create(FirestoreRulesTokenTypes.STRING)
    val BRACES: TokenSet = TokenSet.create(
        FirestoreRulesTokenTypes.L_BRACE,
        FirestoreRulesTokenTypes.R_BRACE,
    )
    val SEMICOLON_TERMINATED_STATEMENTS: TokenSet = TokenSet.create(
        FirestoreRulesElementTypes.RULES_VERSION_DECLARATION,
        FirestoreRulesElementTypes.ALLOW_STATEMENT,
        FirestoreRulesElementTypes.RETURN_STATEMENT,
    )
    val EXPRESSION_OPERATORS: TokenSet = TokenSet.create(
        FirestoreRulesTokenTypes.EQUALS,
        FirestoreRulesTokenTypes.OPERATOR,
    )
}
