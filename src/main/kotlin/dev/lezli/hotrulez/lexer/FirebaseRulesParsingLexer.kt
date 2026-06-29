package dev.lezli.hotrulez.lexer

import com.intellij.lexer.FlexAdapter

/**
 * Parsing lexer for the Grammar-Kit parser. Wraps the JFlex-generated
 * [_FirebaseRulesLexer] (see src/main/grammar/FirebaseRules.flex).
 *
 * This is intentionally separate from [FirebaseRulesLexer], which is the
 * coarse, context-sensitive lexer used only for syntax highlighting.
 */
class FirebaseRulesParsingLexer : FlexAdapter(_FirebaseRulesLexer(null))
