package dev.lezli.hotrulez.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.lexer.FirebaseRulesLexer
import dev.lezli.hotrulez.lexer.FirebaseRulesTokenTypes

class FirebaseRulesSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = FirebaseRulesLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            FirebaseRulesTokenTypes.KEYWORD -> pack(FirebaseRulesHighlightingColors.KEYWORD)
            FirebaseRulesTokenTypes.CONSTANT -> pack(FirebaseRulesHighlightingColors.CONSTANT)
            FirebaseRulesTokenTypes.OPERATION -> pack(FirebaseRulesHighlightingColors.OPERATION)
            FirebaseRulesTokenTypes.BUILTIN -> pack(FirebaseRulesHighlightingColors.BUILTIN)
            FirebaseRulesTokenTypes.TYPE -> pack(FirebaseRulesHighlightingColors.TYPE)
            FirebaseRulesTokenTypes.FUNCTION_CALL -> pack(FirebaseRulesHighlightingColors.FUNCTION_CALL)
            FirebaseRulesTokenTypes.IDENTIFIER -> pack(FirebaseRulesHighlightingColors.IDENTIFIER)
            FirebaseRulesTokenTypes.STRING -> pack(FirebaseRulesHighlightingColors.STRING)
            FirebaseRulesTokenTypes.NUMBER -> pack(FirebaseRulesHighlightingColors.NUMBER)
            FirebaseRulesTokenTypes.LINE_COMMENT -> pack(FirebaseRulesHighlightingColors.LINE_COMMENT)
            FirebaseRulesTokenTypes.BLOCK_COMMENT -> pack(FirebaseRulesHighlightingColors.BLOCK_COMMENT)
            FirebaseRulesTokenTypes.PATH_SEPARATOR -> pack(FirebaseRulesHighlightingColors.PATH_SEPARATOR)
            FirebaseRulesTokenTypes.L_BRACE,
            FirebaseRulesTokenTypes.R_BRACE -> pack(FirebaseRulesHighlightingColors.BRACES)
            FirebaseRulesTokenTypes.L_PAREN,
            FirebaseRulesTokenTypes.R_PAREN -> pack(FirebaseRulesHighlightingColors.PARENTHESES)
            FirebaseRulesTokenTypes.L_BRACKET,
            FirebaseRulesTokenTypes.R_BRACKET -> pack(FirebaseRulesHighlightingColors.BRACKETS)
            FirebaseRulesTokenTypes.COMMA -> pack(FirebaseRulesHighlightingColors.COMMA)
            FirebaseRulesTokenTypes.DOT -> pack(FirebaseRulesHighlightingColors.DOT)
            FirebaseRulesTokenTypes.STAR_STAR -> pack(FirebaseRulesHighlightingColors.RECURSIVE_WILDCARD)
            FirebaseRulesTokenTypes.COLON,
            FirebaseRulesTokenTypes.EQUALS,
            FirebaseRulesTokenTypes.DOLLAR,
            FirebaseRulesTokenTypes.OPERATOR -> pack(FirebaseRulesHighlightingColors.OPERATOR)
            FirebaseRulesTokenTypes.SEMICOLON -> pack(FirebaseRulesHighlightingColors.SEMICOLON)
            TokenType.BAD_CHARACTER -> pack(FirebaseRulesHighlightingColors.BAD_CHARACTER)
            else -> TextAttributesKey.EMPTY_ARRAY
        }
}
