package dev.lezli.hotrulez

import com.intellij.openapi.editor.colors.TextAttributesKey
import dev.lezli.hotrulez.highlighting.FirebaseRulesHighlightingColors
import dev.lezli.hotrulez.highlighting.FirebaseRulesSyntaxHighlighter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseRulesSyntaxHighlighterTest {
    @Test
    fun highlightsMinimalRulesFile() {
        val keys = highlightedKeys(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )

        assertTrue(keys.contains(FirebaseRulesHighlightingColors.KEYWORD))
        assertTrue(keys.contains(FirebaseRulesHighlightingColors.OPERATION))
        assertTrue(keys.contains(FirebaseRulesHighlightingColors.STRING))
    }

    @Test
    fun highlightsNestedMatchPaths() {
        val keys = highlightedKeys(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city}/{document=**} {
                  allow read: if exists(/databases/$(database)/documents/cities/$(city));
                }
              }
            }
            """.trimIndent(),
        )

        assertTrue(keys.contains(FirebaseRulesHighlightingColors.PATH_SEPARATOR))
        assertTrue(keys.contains(FirebaseRulesHighlightingColors.BRACES))
        assertTrue(keys.contains(FirebaseRulesHighlightingColors.BUILTIN))
        // `$(...)` path interpolation must not be flagged as an invalid token.
        assertFalse(keys.contains(FirebaseRulesHighlightingColors.BAD_CHARACTER))
    }

    @Test
    fun highlightsInvalidTokens() {
        assertTrue(highlightedKeys("@").contains(FirebaseRulesHighlightingColors.BAD_CHARACTER))
    }

    @Test
    fun highlightsConstantsFunctionCallsAndRecursiveWildcards() {
        val keys = highlightedKeys(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /logs/{document=**} {
                  allow read: if isOwner(request.auth.uid) && resource.data.active == true;
                }
              }
            }
            """.trimIndent(),
        )

        assertTrue(keys.contains(FirebaseRulesHighlightingColors.CONSTANT))
        assertTrue(keys.contains(FirebaseRulesHighlightingColors.FUNCTION_CALL))
        assertTrue(keys.contains(FirebaseRulesHighlightingColors.RECURSIVE_WILDCARD))
    }

    @Test
    fun highlightsTypeTestTernaryAndTypeNames() {
        val keys = highlightedKeys("allow read: if resource.data.value is int ? true : false;")

        assertTrue(keys.contains(FirebaseRulesHighlightingColors.TYPE))
        assertTrue(keys.contains(FirebaseRulesHighlightingColors.OPERATOR))
        // The ternary `?` and the `is` operator must not be flagged as invalid.
        assertFalse(keys.contains(FirebaseRulesHighlightingColors.BAD_CHARACTER))
    }

    private fun highlightedKeys(text: String): Set<TextAttributesKey> {
        val highlighter = FirebaseRulesSyntaxHighlighter()
        val lexer = highlighter.highlightingLexer
        val keys = mutableSetOf<TextAttributesKey>()

        lexer.start(text)
        while (lexer.tokenType != null) {
            lexer.tokenType
                ?.let(highlighter::getTokenHighlights)
                ?.forEach(keys::add)
            lexer.advance()
        }

        return keys
    }
}
