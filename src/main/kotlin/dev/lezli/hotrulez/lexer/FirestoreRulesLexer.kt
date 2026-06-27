package dev.lezli.hotrulez.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.diagnostics.FirestoreRulesDiagnostics

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
            current == '\'' || current == '"' -> scanString(current)
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
            // A string never spans a line break: it ends at the newline (or end of
            // file) so an unterminated quote does not swallow the rest of the file.
            if (current == '\n' || current == '\r') {
                break
            }
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
        // No closing quote before the end of the line: highlight the open string up to
        // that point (matching how IDEs colour an in-progress literal and letting the
        // quote handler auto-close it) instead of flagging the lone quote as a bad
        // character. The precise "unterminated string" error stays the parser's job.
        finish(FirestoreRulesTokenTypes.STRING, index)
    }

    private fun scanNumber() {
        // Hex literal `0x[0-9a-fA-F]+`, matching the JFlex parsing lexer's NUMBER rule.
        if (buffer[tokenStart] == '0' && (charAt(tokenStart + 1) == 'x' || charAt(tokenStart + 1) == 'X')) {
            var hex = tokenStart + 2
            while (hex < endOffset && buffer[hex].isHexDigit()) {
                hex++
            }
            // Only `0x` followed by at least one hex digit is a hex number; otherwise
            // fall through and lex the leading `0` as a plain decimal.
            if (hex > tokenStart + 2) {
                finish(FirestoreRulesTokenTypes.NUMBER, hex)
                return
            }
        }

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
        // Optional CEL exponent `[eE][+-]?[0-9]+`; leave a bare `e` to the identifier
        // scanner when no well-formed exponent follows.
        if (index < endOffset && (buffer[index] == 'e' || buffer[index] == 'E')) {
            var probe = index + 1
            if (probe < endOffset && (buffer[probe] == '+' || buffer[probe] == '-')) {
                probe++
            }
            if (probe < endOffset && buffer[probe].isDigit()) {
                probe++
                while (probe < endOffset && buffer[probe].isDigit()) {
                    probe++
                }
                index = probe
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
            text in FirestoreRulesDiagnostics.ALLOW_OPERATIONS -> FirestoreRulesTokenTypes.OPERATION
            text in BUILTINS -> FirestoreRulesTokenTypes.BUILTIN
            text in WORD_OPERATORS && inValuePosition(tokenStart) ->
                FirestoreRulesTokenTypes.OPERATOR
            nextNonWhitespace(index) == '(' -> FirestoreRulesTokenTypes.FUNCTION_CALL
            text in TYPE_NAMES && inValuePosition(tokenStart) -> FirestoreRulesTokenTypes.TYPE
            else -> FirestoreRulesTokenTypes.IDENTIFIER
        }
        finish(type, index)
    }

    private fun scanSingleCharacter(current: Char) {
        when (current) {
            // `/` is always lexed as a path separator. Firestore Rules also allow `/`
            // as arithmetic division, but a context-free lexer cannot tell the two
            // apart; paths dominate real rules and both share the operator color.
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
            '+', '-', '*', '%', '?' -> finish(FirestoreRulesTokenTypes.OPERATOR, tokenStart + 1)
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

    // A word operator (`in`, `is`) or built-in type/namespace name is only itself
    // when it stands as a value. When it directly follows a member access `.`, a
    // path separator `/`, an opening wildcard/brace `{`, or a path-segment hyphen
    // `-` (e.g. `/user-map/`), it is instead a field, path segment, or path variable
    // name and must stay an identifier.
    private fun inValuePosition(index: Int): Boolean {
        val previous = previousNonWhitespace(index)
        return previous != '.' && previous != '/' && previous != '{' && previous != '-'
    }

    private fun finish(type: IElementType, end: Int) {
        tokenType = type
        tokenEnd = end
    }

    private fun charAt(index: Int): Char? = if (index in 0 until endOffset) buffer[index] else null

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    // Identifiers are ASCII only, matching the Firestore Rules grammar and the JFlex
    // parsing lexer (`[a-zA-Z_][a-zA-Z0-9_]*`): a non-ASCII letter is a bad character
    // in both lexers rather than a silently-accepted identifier in one and a parse
    // error in the other.
    private fun isIdentifierStart(char: Char): Boolean = char == '_' || char in 'a'..'z' || char in 'A'..'Z'

    private fun isIdentifierPart(char: Char): Boolean = isIdentifierStart(char) || char in '0'..'9'

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

        // Word-form operators in Firestore Rules conditions: membership (`x in list`)
        // and type test (`x is string`).
        val WORD_OPERATORS = setOf("in", "is")

        // Built-in type names (used with the `is` operator) and global function
        // namespaces (`math.*`, `timestamp.*`, `duration.*`, `latlng.*`, `hashing.*`).
        val TYPE_NAMES = setOf(
            "bool", "bytes", "float", "int", "number", "string",
            "list", "map", "set", "path", "latlng", "timestamp",
            "duration", "constraint", "map_diff", "math", "hashing",
        )

        // The set of valid `allow` operations is owned by FirestoreRulesDiagnostics
        // (the single source of truth shared with the annotator); see its use in
        // scanIdentifier above.

        val BUILTINS = setOf("request", "resource", "exists", "existsAfter", "get", "getAfter")
    }
}
