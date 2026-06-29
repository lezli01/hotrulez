package dev.lezli.hotrulez

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.lexer.FirebaseRulesLexer
import dev.lezli.hotrulez.lexer.FirebaseRulesTokenTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseRulesLexerTest {
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

        assertTrue(tokens.contains(FirebaseRulesTokenTypes.KEYWORD))
        assertTrue(tokens.contains(FirebaseRulesTokenTypes.OPERATION))
        assertTrue(tokens.contains(FirebaseRulesTokenTypes.STRING))
        assertTrue(tokens.contains(FirebaseRulesTokenTypes.PATH_SEPARATOR))
    }

    @Test
    fun tokenizesInvalidCharacters() {
        assertEquals(listOf(TokenType.BAD_CHARACTER), tokenTypes("@"))
    }

    @Test
    fun tokenizesBacktickAsInvalidCharacter() {
        // Backtick is not a valid Firestore/CEL string delimiter; the highlighting
        // lexer must treat it as a bad character, matching the JFlex parsing lexer.
        assertEquals(listOf(TokenType.BAD_CHARACTER), tokenTypes("`"))
    }

    @Test
    fun tokenizesGetAsOperationAndHelper() {
        assertEquals(FirebaseRulesTokenTypes.OPERATION, tokenTypes("allow get: if true;")[1])
        assertEquals(FirebaseRulesTokenTypes.BUILTIN, tokenTypes("get(/databases/$(database)/documents/users/user)")[0])
    }

    @Test
    fun tokenizesBooleansAndNullAsConstants() {
        assertEquals(listOf(FirebaseRulesTokenTypes.CONSTANT), tokenTypes("true"))
        assertEquals(listOf(FirebaseRulesTokenTypes.CONSTANT), tokenTypes("false"))
        assertEquals(listOf(FirebaseRulesTokenTypes.CONSTANT), tokenTypes("null"))
    }

    @Test
    fun tokenizesUserFunctionCallsDistinctlyFromIdentifiers() {
        assertEquals(FirebaseRulesTokenTypes.FUNCTION_CALL, tokenTypes("isOwner(uid)")[0])
        assertEquals(FirebaseRulesTokenTypes.IDENTIFIER, tokenTypes("owner.field")[0])
    }

    @Test
    fun tokenizesInAsOperator() {
        assertEquals(FirebaseRulesTokenTypes.OPERATOR, tokenTypes("role in roles")[1])
    }

    @Test
    fun keepsInAsMemberNameAfterDot() {
        // `in` is a membership operator, but as a field access (e.g. resource.data.in)
        // it must stay a member reference, not be miscolored as an operator.
        assertEquals(FirebaseRulesTokenTypes.IDENTIFIER, tokenTypes("resource.data.in == true")[4])
    }

    @Test
    fun keepsInAsPathSegmentName() {
        // A path segment named `in` (between path separators) is not an operator.
        assertEquals(FirebaseRulesTokenTypes.IDENTIFIER, tokenTypes("match /in/{doc}")[2])
    }

    @Test
    fun tokenizesLetAsKeyword() {
        // `let` introduces a local binding and must highlight as a keyword.
        assertEquals(FirebaseRulesTokenTypes.KEYWORD, tokenTypes("let nowMs = request.time;")[0])
    }

    @Test
    fun tokenizesDollarPathInterpolation() {
        // `$(var)` path interpolation: the `$` is a real token, not a bad character.
        val tokens = tokenTypes("exists(/databases/$(database)/documents)")
        assertTrue(tokens.contains(FirebaseRulesTokenTypes.DOLLAR))
        assertFalse(tokens.contains(TokenType.BAD_CHARACTER))
    }

    @Test
    fun tokenizesTernaryQuestionMarkAsOperator() {
        // `a ? b : c` ternary: `?` is an operator, not a bad character.
        val tokens = tokenTypes("a ? b : c")
        assertEquals(FirebaseRulesTokenTypes.OPERATOR, tokens[1])
        assertFalse(tokens.contains(TokenType.BAD_CHARACTER))
    }

    @Test
    fun tokenizesIsAsTypeOperatorFollowedByType() {
        // `score is int`: `is` is an operator and `int` is a type name.
        val tokens = tokenTypes("score is int")
        assertEquals(FirebaseRulesTokenTypes.OPERATOR, tokens[1])
        assertEquals(FirebaseRulesTokenTypes.TYPE, tokens[2])
    }

    @Test
    fun keepsIsAsMemberNameAfterDot() {
        assertEquals(FirebaseRulesTokenTypes.IDENTIFIER, tokenTypes("resource.data.is == true")[4])
    }

    @Test
    fun tokenizesGlobalNamespaceAsType() {
        // `math.abs(x)`: the namespace `math` is a type/built-in, `abs` is a call.
        val tokens = tokenTypes("math.abs(x)")
        assertEquals(FirebaseRulesTokenTypes.TYPE, tokens[0])
        assertEquals(FirebaseRulesTokenTypes.FUNCTION_CALL, tokens[2])
    }

    @Test
    fun keepsTypeNameAsMemberNameAfterDot() {
        // A field named like a type (e.g. resource.data.map) stays an identifier.
        assertEquals(FirebaseRulesTokenTypes.IDENTIFIER, tokenTypes("resource.data.map")[4])
    }

    @Test
    fun tokenizesPathAsCallNotTypeWhenFollowedByParen() {
        // `path('/x')` is a function call; `is path` (no paren) would be a type.
        assertEquals(FirebaseRulesTokenTypes.FUNCTION_CALL, tokenTypes("path('/x')")[0])
    }

    @Test
    fun tokenizesHexLiteralAsSingleNumber() {
        // `0xFF` is one NUMBER token, matching the JFlex parsing lexer rather than
        // splitting into `0` (NUMBER) followed by `xFF` (IDENTIFIER).
        assertEquals(listOf(FirebaseRulesTokenTypes.NUMBER), tokenTypes("0xFF"))
    }

    @Test
    fun tokenizesScientificLiteralAsSingleNumber() {
        // Exponent notation is a single NUMBER token, matching the JFlex parsing lexer.
        assertEquals(listOf(FirebaseRulesTokenTypes.NUMBER), tokenTypes("1e9"))
        assertEquals(listOf(FirebaseRulesTokenTypes.NUMBER), tokenTypes("1.5e-3"))
    }

    @Test
    fun tokenizesNonAsciiLetterAsBadCharacter() {
        // Identifiers are ASCII only (matching the JFlex parsing lexer and the Firestore
        // grammar), so a trailing non-ASCII letter is a bad character, not part of the
        // identifier.
        assertEquals(
            listOf(FirebaseRulesTokenTypes.IDENTIFIER, TokenType.BAD_CHARACTER),
            tokenTypes("café"),
        )
    }

    @Test
    fun unterminatedStringHighlightsToEndOfLineOnly() {
        // An unterminated quote highlights as a string up to the end of its line (so the
        // quote handler can auto-close it) but must not swallow the following lines.
        val tokens = tokenTypes("'oops\nallow read")
        assertEquals(FirebaseRulesTokenTypes.STRING, tokens.first())
        assertTrue(tokens.contains(FirebaseRulesTokenTypes.OPERATION))
    }

    @Test
    fun keepsTypeNameAsPathSegmentAfterHyphen() {
        // A built-in type name embedded after a hyphen in a path segment (`/a-map/`)
        // stays an identifier rather than being miscolored as a type.
        assertEquals(FirebaseRulesTokenTypes.IDENTIFIER, tokenTypes("match /a-map/{id}")[4])
    }

    private fun tokenTypes(text: String): List<IElementType> {
        val lexer = FirebaseRulesLexer()
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
