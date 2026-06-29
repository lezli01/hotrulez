package dev.lezli.hotrulez.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object FirebaseRulesHighlightingColors {
    val KEYWORD: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_KEYWORD",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
    val CONSTANT: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_CONSTANT",
        DefaultLanguageHighlighterColors.CONSTANT,
    )
    val OPERATION: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_OPERATION",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
    val BUILTIN: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_BUILTIN",
        DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL,
    )
    val TYPE: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_TYPE",
        DefaultLanguageHighlighterColors.CLASS_NAME,
    )
    val SERVICE_NAME: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_SERVICE_NAME",
        DefaultLanguageHighlighterColors.CLASS_NAME,
    )
    val FUNCTION_DECLARATION: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_FUNCTION_DECLARATION",
        DefaultLanguageHighlighterColors.FUNCTION_DECLARATION,
    )
    val FUNCTION_CALL: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_FUNCTION_CALL",
        DefaultLanguageHighlighterColors.FUNCTION_CALL,
    )
    val PATH_VARIABLE: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_PATH_VARIABLE",
        DefaultLanguageHighlighterColors.LOCAL_VARIABLE,
    )
    val RECURSIVE_WILDCARD: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_RECURSIVE_WILDCARD",
        DefaultLanguageHighlighterColors.METADATA,
    )
    val IDENTIFIER: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_IDENTIFIER",
        DefaultLanguageHighlighterColors.IDENTIFIER,
    )
    val STRING: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_STRING",
        DefaultLanguageHighlighterColors.STRING,
    )
    val NUMBER: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_NUMBER",
        DefaultLanguageHighlighterColors.NUMBER,
    )
    val LINE_COMMENT: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_LINE_COMMENT",
        DefaultLanguageHighlighterColors.LINE_COMMENT,
    )
    val BLOCK_COMMENT: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_BLOCK_COMMENT",
        DefaultLanguageHighlighterColors.BLOCK_COMMENT,
    )
    val PATH_SEPARATOR: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_PATH_SEPARATOR",
        DefaultLanguageHighlighterColors.OPERATION_SIGN,
    )
    val BRACES: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_BRACES",
        DefaultLanguageHighlighterColors.BRACES,
    )
    val PARENTHESES: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_PARENTHESES",
        DefaultLanguageHighlighterColors.PARENTHESES,
    )
    val BRACKETS: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_BRACKETS",
        DefaultLanguageHighlighterColors.BRACKETS,
    )
    val COMMA: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_COMMA",
        DefaultLanguageHighlighterColors.COMMA,
    )
    val DOT: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_DOT",
        DefaultLanguageHighlighterColors.DOT,
    )
    val SEMICOLON: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_SEMICOLON",
        DefaultLanguageHighlighterColors.SEMICOLON,
    )
    val OPERATOR: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_OPERATOR",
        DefaultLanguageHighlighterColors.OPERATION_SIGN,
    )
    val BAD_CHARACTER: TextAttributesKey = createTextAttributesKey(
        "FIREBASE_RULES_BAD_CHARACTER",
        HighlighterColors.BAD_CHARACTER,
    )
}
