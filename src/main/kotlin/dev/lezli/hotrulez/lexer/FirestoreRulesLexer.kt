package dev.lezli.hotrulez.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class FirestoreRulesLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        tokenStart = startOffset
        tokenEnd = startOffset
        locateToken()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        tokenStart = tokenEnd
        locateToken()
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    private fun locateToken() {
        if (tokenStart >= endOffset) {
            tokenType = null
            tokenEnd = tokenStart
            return
        }

        val current = buffer[tokenStart]
        when {
            current.isWhitespace() -> scanWhitespace()
            current == '/' && nextIs('/') -> scanLineComment()
            current == '/' && nextIs('*') -> scanBlockComment()
            current == '\'' || current == '"' || current == '`' -> scanString(current)
            current.isDigit() -> scanNumber()
            isIdentifierStart(current) -> scanIdentifier()
            current == '*' && nextIs('*') -> finish(FirestoreRulesTokenTypes.STAR_STAR, tokenStart + 2)
            else -> scanSingleCharacter(current)
        }
    }

    private fun scanWhitespace() {
        var index = tokenStart + 1
        while (index < endOffset && buffer[index].isWhitespace()) {
            index++
        }
        finish(TokenType.WHITE_SPACE, index)
    }

    private fun scanLineComment() {
        var index = tokenStart + 2
        while (index < endOffset && buffer[index] != '\n' && buffer[index] != '\r') {
            index++
        }
        finish(FirestoreRulesTokenTypes.LINE_COMMENT, index)
    }

    private fun scanBlockComment() {
        var index = tokenStart + 2
        while (index < endOffset - 1) {
            if (buffer[index] == '*' && buffer[index + 1] == '/') {
                finish(FirestoreRulesTokenTypes.BLOCK_COMMENT, index + 2)
                return
            }
            index++
        }
        finish(FirestoreRulesTokenTypes.BLOCK_COMMENT, endOffset)
    }

    private fun scanString(quote: Char) {
        var index = tokenStart + 1
        var escaped = false
        while (index < endOffset) {
            val current = buffer[index]
            if (escaped) {
                escaped = false
            } else if (current == '\\') {
                escaped = true
            } else if (current == quote) {
                finish(FirestoreRulesTokenTypes.STRING, index + 1)
                return
            }
            index++
        }
        finish(FirestoreRulesTokenTypes.STRING, endOffset)
    }

    private fun scanNumber() {
        var index = tokenStart + 1
        while (index < endOffset && buffer[index].isDigit()) {
            index++
        }
        if (index < endOffset && buffer[index] == '.' && index + 1 < endOffset && buffer[index + 1].isDigit()) {
            index++
            while (index < endOffset && buffer[index].isDigit()) {
                index++
            }
        }
        finish(FirestoreRulesTokenTypes.NUMBER, index)
    }

    private fun scanIdentifier() {
        var index = tokenStart + 1
        while (index < endOffset && isIdentifierPart(buffer[index])) {
            index++
        }

        val text = buffer.subSequence(tokenStart, index).toString()
        val type = when {
            text in KEYWORDS -> FirestoreRulesTokenTypes.KEYWORD
            text in CONSTANTS -> FirestoreRulesTokenTypes.CONSTANT
            text in BUILTINS && nextNonWhitespace(index) == '(' -> FirestoreRulesTokenTypes.BUILTIN
            text in OPERATIONS -> FirestoreRulesTokenTypes.OPERATION
            text in BUILTINS -> FirestoreRulesTokenTypes.BUILTIN
            text in WORD_OPERATORS && isOperatorPosition(tokenStart) ->
                FirestoreRulesTokenTypes.OPERATOR
            nextNonWhitespace(index) == '(' -> FirestoreRulesTokenTypes.FUNCTION_CALL
            else -> FirestoreRulesTokenTypes.IDENTIFIER
        }
        finish(type, index)
    }

    private fun scanSingleCharacter(current: Char) {
        when (current) {
            '/' -> finish(FirestoreRulesTokenTypes.PATH_SEPARATOR, tokenStart + 1)
            '{' -> finish(FirestoreRulesTokenTypes.L_BRACE, tokenStart + 1)
            '}' -> finish(FirestoreRulesTokenTypes.R_BRACE, tokenStart + 1)
            '(' -> finish(FirestoreRulesTokenTypes.L_PAREN, tokenStart + 1)
            ')' -> finish(FirestoreRulesTokenTypes.R_PAREN, tokenStart + 1)
            '[' -> finish(FirestoreRulesTokenTypes.L_BRACKET, tokenStart + 1)
            ']' -> finish(FirestoreRulesTokenTypes.R_BRACKET, tokenStart + 1)
            ',' -> finish(FirestoreRulesTokenTypes.COMMA, tokenStart + 1)
            '.' -> finish(FirestoreRulesTokenTypes.DOT, tokenStart + 1)
            ':' -> finish(FirestoreRulesTokenTypes.COLON, tokenStart + 1)
            ';' -> finish(FirestoreRulesTokenTypes.SEMICOLON, tokenStart + 1)
            '$' -> finish(FirestoreRulesTokenTypes.DOLLAR, tokenStart + 1)
            '=' -> {
                val hasEquals = nextIs('=')
                finish(
                    if (hasEquals) FirestoreRulesTokenTypes.OPERATOR else FirestoreRulesTokenTypes.EQUALS,
                    tokenStart + if (hasEquals) 2 else 1,
                )
            }
            '!', '<', '>' -> {
                val hasEquals = nextIs('=')
                finish(FirestoreRulesTokenTypes.OPERATOR, tokenStart + if (hasEquals) 2 else 1)
            }
            '&' -> {
                val hasAmpersand = nextIs('&')
                finish(
                    if (hasAmpersand) FirestoreRulesTokenTypes.OPERATOR else TokenType.BAD_CHARACTER,
                    tokenStart + if (hasAmpersand) 2 else 1,
                )
            }
            '|' -> {
                val hasPipe = nextIs('|')
                finish(
                    if (hasPipe) FirestoreRulesTokenTypes.OPERATOR else TokenType.BAD_CHARACTER,
                    tokenStart + if (hasPipe) 2 else 1,
                )
            }
            '+', '-', '*', '%' -> finish(FirestoreRulesTokenTypes.OPERATOR, tokenStart + 1)
            else -> finish(TokenType.BAD_CHARACTER, tokenStart + 1)
        }
    }

    private fun nextIs(expected: Char): Boolean = tokenStart + 1 < endOffset && buffer[tokenStart + 1] == expected

    private fun nextNonWhitespace(index: Int): Char? {
        var current = index
        while (current < endOffset && buffer[current].isWhitespace()) {
            current++
        }
        return buffer.getOrNull(current)
    }

    private fun previousNonWhitespace(index: Int): Char? {
        var current = index - 1
        while (current >= 0 && buffer[current].isWhitespace()) {
            current--
        }
        return if (current >= 0) buffer[current] else null
    }

    // A word operator like `in` is only an operator between values. When it
    // directly follows a member access `.`, a path separator `/`, or an opening
    // wildcard/brace `{`, it is a field, path segment, or path variable name.
    private fun isOperatorPosition(index: Int): Boolean {
        val previous = previousNonWhitespace(index)
        return previous != '.' && previous != '/' && previous != '{'
    }

    private fun finish(type: IElementType, end: Int) {
        tokenType = type
        tokenEnd = end
    }

    private fun isIdentifierStart(char: Char): Boolean = char == '_' || char.isLetter()

    private fun isIdentifierPart(char: Char): Boolean = char == '_' || char.isLetterOrDigit()

    private companion object {
        val KEYWORDS = setOf(
            "rules_version",
            "service",
            "match",
            "allow",
            "if",
            "function",
            "return",
            "let",
        )

        val CONSTANTS = setOf("true", "false", "null")

        // Word-form membership operator used in Firestore Rules conditions, e.g. `'admin' in roles`.
        val WORD_OPERATORS = setOf("in")

        val OPERATIONS = setOf("get", "list", "read", "create", "update", "delete", "write")

        val BUILTINS = setOf("request", "resource", "exists", "existsAfter", "get", "getAfter")
    }
}
