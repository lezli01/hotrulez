package dev.lezli.hotrulez.lexer

import com.intellij.lexer.FlexAdapter

/**
 * Parsing lexer for the Grammar-Kit parser. Wraps the JFlex-generated
 * [_FirestoreRulesLexer] (see src/main/grammar/FirestoreRules.flex).
 *
 * This is intentionally separate from [FirestoreRulesLexer], which is the
 * coarse, context-sensitive lexer used only for syntax highlighting.
 */
class FirestoreRulesParsingLexer : FlexAdapter(_FirestoreRulesLexer(null))
