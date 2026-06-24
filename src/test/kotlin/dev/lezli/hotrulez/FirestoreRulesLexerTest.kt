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

    @Test
    fun tokenizesBooleansAndNullAsConstants() {
        assertEquals(listOf(FirestoreRulesTokenTypes.CONSTANT), tokenTypes("true"))
        assertEquals(listOf(FirestoreRulesTokenTypes.CONSTANT), tokenTypes("false"))
        assertEquals(listOf(FirestoreRulesTokenTypes.CONSTANT), tokenTypes("null"))
    }

    @Test
    fun tokenizesUserFunctionCallsDistinctlyFromIdentifiers() {
        assertEquals(FirestoreRulesTokenTypes.FUNCTION_CALL, tokenTypes("isOwner(uid)")[0])
        assertEquals(FirestoreRulesTokenTypes.IDENTIFIER, tokenTypes("owner.field")[0])
    }

    @Test
    fun tokenizesInAsOperator() {
        assertEquals(FirestoreRulesTokenTypes.OPERATOR, tokenTypes("role in roles")[1])
    }

    @Test
    fun keepsInAsMemberNameAfterDot() {
        // `in` is a membership operator, but as a field access (e.g. resource.data.in)
        // it must stay a member reference, not be miscolored as an operator.
        assertEquals(FirestoreRulesTokenTypes.IDENTIFIER, tokenTypes("resource.data.in == true")[4])
    }

    @Test
    fun keepsInAsPathSegmentName() {
        // A path segment named `in` (between path separators) is not an operator.
        assertEquals(FirestoreRulesTokenTypes.IDENTIFIER, tokenTypes("match /in/{doc}")[2])
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
