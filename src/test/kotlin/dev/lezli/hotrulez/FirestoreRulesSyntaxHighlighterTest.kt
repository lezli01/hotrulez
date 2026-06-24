package dev.lezli.hotrulez

import com.intellij.openapi.editor.colors.TextAttributesKey
import dev.lezli.hotrulez.highlighting.FirestoreRulesHighlightingColors
import dev.lezli.hotrulez.highlighting.FirestoreRulesSyntaxHighlighter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreRulesSyntaxHighlighterTest {
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

        assertTrue(keys.contains(FirestoreRulesHighlightingColors.KEYWORD))
        assertTrue(keys.contains(FirestoreRulesHighlightingColors.OPERATION))
        assertTrue(keys.contains(FirestoreRulesHighlightingColors.STRING))
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

        assertTrue(keys.contains(FirestoreRulesHighlightingColors.PATH_SEPARATOR))
        assertTrue(keys.contains(FirestoreRulesHighlightingColors.BRACES))
        assertTrue(keys.contains(FirestoreRulesHighlightingColors.BUILTIN))
        // `$(...)` path interpolation must not be flagged as an invalid token.
        assertFalse(keys.contains(FirestoreRulesHighlightingColors.BAD_CHARACTER))
    }

    @Test
    fun highlightsInvalidTokens() {
        assertTrue(highlightedKeys("@").contains(FirestoreRulesHighlightingColors.BAD_CHARACTER))
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

        assertTrue(keys.contains(FirestoreRulesHighlightingColors.CONSTANT))
        assertTrue(keys.contains(FirestoreRulesHighlightingColors.FUNCTION_CALL))
        assertTrue(keys.contains(FirestoreRulesHighlightingColors.RECURSIVE_WILDCARD))
    }

    @Test
    fun highlightsTypeTestTernaryAndTypeNames() {
        val keys = highlightedKeys("allow read: if resource.data.value is int ? true : false;")

        assertTrue(keys.contains(FirestoreRulesHighlightingColors.TYPE))
        assertTrue(keys.contains(FirestoreRulesHighlightingColors.OPERATOR))
        // The ternary `?` and the `is` operator must not be flagged as invalid.
        assertFalse(keys.contains(FirestoreRulesHighlightingColors.BAD_CHARACTER))
    }

    private fun highlightedKeys(text: String): Set<TextAttributesKey> {
        val highlighter = FirestoreRulesSyntaxHighlighter()
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
