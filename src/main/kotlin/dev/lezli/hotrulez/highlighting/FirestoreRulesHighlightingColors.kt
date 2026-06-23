package dev.lezli.hotrulez.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object FirestoreRulesHighlightingColors {
    val KEYWORD: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_KEYWORD",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
    val OPERATION: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_OPERATION",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
    val BUILTIN: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_BUILTIN",
        DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL,
    )
    val IDENTIFIER: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_IDENTIFIER",
        DefaultLanguageHighlighterColors.IDENTIFIER,
    )
    val STRING: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_STRING",
        DefaultLanguageHighlighterColors.STRING,
    )
    val NUMBER: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_NUMBER",
        DefaultLanguageHighlighterColors.NUMBER,
    )
    val LINE_COMMENT: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_LINE_COMMENT",
        DefaultLanguageHighlighterColors.LINE_COMMENT,
    )
    val BLOCK_COMMENT: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_BLOCK_COMMENT",
        DefaultLanguageHighlighterColors.BLOCK_COMMENT,
    )
    val PATH_SEPARATOR: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_PATH_SEPARATOR",
        DefaultLanguageHighlighterColors.OPERATION_SIGN,
    )
    val BRACES: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_BRACES",
        DefaultLanguageHighlighterColors.BRACES,
    )
    val PARENTHESES: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_PARENTHESES",
        DefaultLanguageHighlighterColors.PARENTHESES,
    )
    val BRACKETS: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_BRACKETS",
        DefaultLanguageHighlighterColors.BRACKETS,
    )
    val COMMA: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_COMMA",
        DefaultLanguageHighlighterColors.COMMA,
    )
    val DOT: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_DOT",
        DefaultLanguageHighlighterColors.DOT,
    )
    val SEMICOLON: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_SEMICOLON",
        DefaultLanguageHighlighterColors.SEMICOLON,
    )
    val OPERATOR: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_OPERATOR",
        DefaultLanguageHighlighterColors.OPERATION_SIGN,
    )
    val BAD_CHARACTER: TextAttributesKey = createTextAttributesKey(
        "FIRESTORE_RULES_BAD_CHARACTER",
        HighlighterColors.BAD_CHARACTER,
    )
}
