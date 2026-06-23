package dev.lezli.hotrulez

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.lexer.FirestoreRulesLexer
import dev.lezli.hotrulez.lexer.FirestoreRulesTokenTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreRulesLexerTest {
    @Test
    fun tokenizesMinimalRulesFile() {
        val tokens = tokenTypes(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )

        assertTrue(tokens.contains(FirestoreRulesTokenTypes.KEYWORD))
        assertTrue(tokens.contains(FirestoreRulesTokenTypes.OPERATION))
        assertTrue(tokens.contains(FirestoreRulesTokenTypes.STRING))
        assertTrue(tokens.contains(FirestoreRulesTokenTypes.PATH_SEPARATOR))
    }

    @Test
    fun tokenizesInvalidCharacters() {
        assertEquals(listOf(TokenType.BAD_CHARACTER), tokenTypes("@"))
    }

    @Test
    fun tokenizesGetAsOperationAndHelper() {
        assertEquals(FirestoreRulesTokenTypes.OPERATION, tokenTypes("allow get: if true;")[1])
        assertEquals(FirestoreRulesTokenTypes.BUILTIN, tokenTypes("get(/databases/$(database)/documents/users/user)")[0])
    }

    private fun tokenTypes(text: String): List<IElementType> {
        val lexer = FirestoreRulesLexer()
        val tokens = mutableListOf<IElementType>()

        lexer.start(text)
        while (lexer.tokenType != null) {
            lexer.tokenType
                ?.takeUnless { it == TokenType.WHITE_SPACE }
                ?.let(tokens::add)
            lexer.advance()
        }

        return tokens
    }
}
