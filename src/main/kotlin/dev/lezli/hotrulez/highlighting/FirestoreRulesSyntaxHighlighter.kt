package dev.lezli.hotrulez.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.lexer.FirestoreRulesLexer
import dev.lezli.hotrulez.lexer.FirestoreRulesTokenTypes

class FirestoreRulesSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = FirestoreRulesLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            FirestoreRulesTokenTypes.KEYWORD -> pack(FirestoreRulesHighlightingColors.KEYWORD)
            FirestoreRulesTokenTypes.OPERATION -> pack(FirestoreRulesHighlightingColors.OPERATION)
            FirestoreRulesTokenTypes.BUILTIN -> pack(FirestoreRulesHighlightingColors.BUILTIN)
            FirestoreRulesTokenTypes.IDENTIFIER -> pack(FirestoreRulesHighlightingColors.IDENTIFIER)
            FirestoreRulesTokenTypes.STRING -> pack(FirestoreRulesHighlightingColors.STRING)
            FirestoreRulesTokenTypes.NUMBER -> pack(FirestoreRulesHighlightingColors.NUMBER)
            FirestoreRulesTokenTypes.LINE_COMMENT -> pack(FirestoreRulesHighlightingColors.LINE_COMMENT)
            FirestoreRulesTokenTypes.BLOCK_COMMENT -> pack(FirestoreRulesHighlightingColors.BLOCK_COMMENT)
            FirestoreRulesTokenTypes.PATH_SEPARATOR -> pack(FirestoreRulesHighlightingColors.PATH_SEPARATOR)
            FirestoreRulesTokenTypes.L_BRACE,
            FirestoreRulesTokenTypes.R_BRACE -> pack(FirestoreRulesHighlightingColors.BRACES)
            FirestoreRulesTokenTypes.L_PAREN,
            FirestoreRulesTokenTypes.R_PAREN -> pack(FirestoreRulesHighlightingColors.PARENTHESES)
            FirestoreRulesTokenTypes.L_BRACKET,
            FirestoreRulesTokenTypes.R_BRACKET -> pack(FirestoreRulesHighlightingColors.BRACKETS)
            FirestoreRulesTokenTypes.COMMA -> pack(FirestoreRulesHighlightingColors.COMMA)
            FirestoreRulesTokenTypes.DOT -> pack(FirestoreRulesHighlightingColors.DOT)
            FirestoreRulesTokenTypes.COLON,
            FirestoreRulesTokenTypes.EQUALS,
            FirestoreRulesTokenTypes.STAR_STAR,
            FirestoreRulesTokenTypes.OPERATOR -> pack(FirestoreRulesHighlightingColors.OPERATOR)
            FirestoreRulesTokenTypes.SEMICOLON -> pack(FirestoreRulesHighlightingColors.SEMICOLON)
            TokenType.BAD_CHARACTER -> pack(FirestoreRulesHighlightingColors.BAD_CHARACTER)
            else -> TextAttributesKey.EMPTY_ARRAY
        }
}
