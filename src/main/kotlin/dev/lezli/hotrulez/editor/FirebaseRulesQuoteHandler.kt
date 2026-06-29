package dev.lezli.hotrulez.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import dev.lezli.hotrulez.lexer.FirebaseRulesTokenTypes

/**
 * Auto-closes and types over string quotes. Firestore Rules strings may use
 * single or double quotes, and the highlighting lexer emits both as a single
 * [FirebaseRulesTokenTypes.STRING] token, so one token in the set covers both.
 */
class FirebaseRulesQuoteHandler : SimpleTokenSetQuoteHandler(FirebaseRulesTokenTypes.STRING)
