package dev.lezli.hotrulez.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.lexer.FirestoreRulesTokenTypes

class FirestoreRulesParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val file = builder.mark()
        while (!builder.eof()) {
            val offset = builder.currentOffset
            parseTopLevel(builder)
            // Guarantee forward progress: a stray token (e.g. an unbalanced `}`)
            // that no production consumes would otherwise loop forever.
            if (builder.currentOffset == offset && !builder.eof()) {
                builder.advanceLexer()
            }
        }
        file.done(root)
        return builder.treeBuilt
    }

    private fun parseTopLevel(builder: PsiBuilder) {
        when {
            builder.atKeyword("rules_version") -> parseRulesVersionDeclaration(builder)
            builder.atKeyword("service") -> parseServiceDeclaration(builder)
            builder.tokenType in FirestoreRulesTokenSets.COMMENTS -> builder.advanceLexer()
            else -> parseUnknownStatement(builder)
        }
    }

    private fun parseBlockMember(builder: PsiBuilder) {
        when {
            builder.atKeyword("rules_version") -> parseRulesVersionDeclaration(builder)
            builder.atKeyword("service") -> parseServiceDeclaration(builder)
            builder.atKeyword("match") -> parseMatchBlock(builder)
            builder.atKeyword("allow") -> parseAllowStatement(builder)
            builder.atKeyword("function") -> parseFunctionDeclaration(builder)
            builder.atKeyword("return") -> parseReturnStatement(builder)
            builder.tokenType == FirestoreRulesTokenTypes.L_BRACE -> parseBlock(builder)
            builder.tokenType in FirestoreRulesTokenSets.COMMENTS -> builder.advanceLexer()
            else -> parseUnknownStatement(builder)
        }
    }

    private fun parseRulesVersionDeclaration(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer()
        consumeUntil(builder, stopAtSemicolon = true, stopAtBlockEnd = true)
        consumeIf(builder, FirestoreRulesTokenTypes.SEMICOLON)
        marker.done(FirestoreRulesElementTypes.RULES_VERSION_DECLARATION)
    }

    private fun parseServiceDeclaration(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer()
        while (!builder.eof() && builder.tokenType != FirestoreRulesTokenTypes.L_BRACE) {
            if (builder.tokenType == FirestoreRulesTokenTypes.SEMICOLON || builder.tokenType == FirestoreRulesTokenTypes.R_BRACE) {
                marker.done(FirestoreRulesElementTypes.SERVICE_DECLARATION)
                return
            }
            builder.advanceLexer()
        }
        if (builder.tokenType == FirestoreRulesTokenTypes.L_BRACE) {
            parseBlock(builder)
        }
        marker.done(FirestoreRulesElementTypes.SERVICE_DECLARATION)
    }

    private fun parseMatchBlock(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer()
        parseMatchPath(builder)
        if (builder.tokenType == FirestoreRulesTokenTypes.L_BRACE) {
            parseBlock(builder)
        }
        marker.done(FirestoreRulesElementTypes.MATCH_BLOCK)
    }

    private fun parseMatchPath(builder: PsiBuilder) {
        val marker = builder.mark()
        var previousTokenType: IElementType? = null
        while (!builder.eof()) {
            val currentTokenType = builder.tokenType
            when {
                currentTokenType == FirestoreRulesTokenTypes.L_BRACE &&
                    previousTokenType != FirestoreRulesTokenTypes.PATH_SEPARATOR -> break
                currentTokenType == FirestoreRulesTokenTypes.L_BRACE -> {
                    parsePathWildcard(builder)
                    previousTokenType = FirestoreRulesTokenTypes.R_BRACE
                }
                currentTokenType == FirestoreRulesTokenTypes.SEMICOLON ||
                    currentTokenType == FirestoreRulesTokenTypes.R_BRACE -> break
                else -> {
                    previousTokenType = currentTokenType
                    builder.advanceLexer()
                }
            }
        }
        marker.done(FirestoreRulesElementTypes.MATCH_PATH)
    }

    private fun parsePathWildcard(builder: PsiBuilder) {
        val marker = builder.mark()
        var recursive = false
        consumeIf(builder, FirestoreRulesTokenTypes.L_BRACE)
        while (!builder.eof() && builder.tokenType != FirestoreRulesTokenTypes.R_BRACE) {
            recursive = recursive || builder.tokenType == FirestoreRulesTokenTypes.STAR_STAR
            builder.advanceLexer()
        }
        consumeIf(builder, FirestoreRulesTokenTypes.R_BRACE)
        marker.done(
            if (recursive) {
                FirestoreRulesElementTypes.RECURSIVE_WILDCARD
            } else {
                FirestoreRulesElementTypes.PATH_WILDCARD
            },
        )
    }

    private fun parseBlock(builder: PsiBuilder) {
        val marker = builder.mark()
        consumeIf(builder, FirestoreRulesTokenTypes.L_BRACE)
        while (!builder.eof() && builder.tokenType != FirestoreRulesTokenTypes.R_BRACE) {
            val offset = builder.currentOffset
            parseBlockMember(builder)
            if (builder.currentOffset == offset) {
                builder.advanceLexer()
            }
        }
        consumeIf(builder, FirestoreRulesTokenTypes.R_BRACE)
        marker.done(FirestoreRulesElementTypes.BLOCK)
    }

    private fun parseAllowStatement(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer()
        parseOperationList(builder)
        if (builder.tokenType == FirestoreRulesTokenTypes.COLON) {
            builder.advanceLexer()
            if (builder.atKeyword("if")) {
                builder.advanceLexer()
            }
            parseExpression(builder)
        }
        consumeIf(builder, FirestoreRulesTokenTypes.SEMICOLON)
        marker.done(FirestoreRulesElementTypes.ALLOW_STATEMENT)
    }

    private fun parseOperationList(builder: PsiBuilder) {
        val marker = builder.mark()
        while (!builder.eof()) {
            val tokenType = builder.tokenType
            if (tokenType == FirestoreRulesTokenTypes.COLON ||
                tokenType == FirestoreRulesTokenTypes.SEMICOLON ||
                tokenType == FirestoreRulesTokenTypes.L_BRACE ||
                tokenType == FirestoreRulesTokenTypes.R_BRACE
            ) {
                break
            }
            builder.advanceLexer()
        }
        marker.done(FirestoreRulesElementTypes.OPERATION_LIST)
    }

    private fun parseFunctionDeclaration(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer()
        while (!builder.eof() &&
            builder.tokenType != FirestoreRulesTokenTypes.L_PAREN &&
            builder.tokenType != FirestoreRulesTokenTypes.L_BRACE &&
            builder.tokenType != FirestoreRulesTokenTypes.SEMICOLON
        ) {
            builder.advanceLexer()
        }
        if (builder.tokenType == FirestoreRulesTokenTypes.L_PAREN) {
            parseParameterList(builder)
        }
        if (builder.tokenType == FirestoreRulesTokenTypes.L_BRACE) {
            parseBlock(builder)
        }
        marker.done(FirestoreRulesElementTypes.FUNCTION_DECLARATION)
    }

    private fun parseParameterList(builder: PsiBuilder) {
        val marker = builder.mark()
        consumeBalanced(builder, FirestoreRulesTokenTypes.L_PAREN, FirestoreRulesTokenTypes.R_PAREN)
        marker.done(FirestoreRulesElementTypes.PARAMETER_LIST)
    }

    private fun parseReturnStatement(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer()
        parseExpression(builder)
        consumeIf(builder, FirestoreRulesTokenTypes.SEMICOLON)
        marker.done(FirestoreRulesElementTypes.RETURN_STATEMENT)
    }

    private fun parseExpression(builder: PsiBuilder) {
        val marker = builder.mark()
        consumeUntil(builder, stopAtSemicolon = true, stopAtBlockEnd = true)
        marker.done(FirestoreRulesElementTypes.EXPRESSION)
    }

    private fun parseUnknownStatement(builder: PsiBuilder) {
        val marker = builder.mark()
        if (builder.tokenType == FirestoreRulesTokenTypes.L_BRACE) {
            parseBlock(builder)
        } else {
            consumeUntil(builder, stopAtSemicolon = true, stopAtBlockEnd = true)
            consumeIf(builder, FirestoreRulesTokenTypes.SEMICOLON)
        }
        marker.done(FirestoreRulesElementTypes.UNKNOWN_STATEMENT)
    }

    private fun consumeUntil(
        builder: PsiBuilder,
        stopAtSemicolon: Boolean,
        stopAtBlockEnd: Boolean,
    ) {
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        while (!builder.eof()) {
            val tokenType = builder.tokenType
            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                if (stopAtSemicolon && tokenType == FirestoreRulesTokenTypes.SEMICOLON) {
                    break
                }
                if (stopAtBlockEnd && tokenType == FirestoreRulesTokenTypes.R_BRACE) {
                    break
                }
            }
            when (tokenType) {
                FirestoreRulesTokenTypes.L_PAREN -> parenDepth++
                FirestoreRulesTokenTypes.R_PAREN -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                FirestoreRulesTokenTypes.L_BRACKET -> bracketDepth++
                FirestoreRulesTokenTypes.R_BRACKET -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                FirestoreRulesTokenTypes.L_BRACE -> braceDepth++
                FirestoreRulesTokenTypes.R_BRACE -> braceDepth = (braceDepth - 1).coerceAtLeast(0)
            }
            builder.advanceLexer()
        }
    }

    private fun consumeBalanced(builder: PsiBuilder, open: IElementType, close: IElementType) {
        var depth = 0
        while (!builder.eof()) {
            val tokenType = builder.tokenType
            if (tokenType == open) {
                depth++
            } else if (tokenType == close) {
                depth--
            }
            builder.advanceLexer()
            if (depth <= 0) {
                break
            }
        }
    }

    private fun consumeIf(builder: PsiBuilder, tokenType: IElementType): Boolean {
        if (builder.tokenType != tokenType) {
            return false
        }
        builder.advanceLexer()
        return true
    }

    private fun PsiBuilder.atKeyword(keyword: String): Boolean =
        tokenType == FirestoreRulesTokenTypes.KEYWORD && tokenText == keyword
}
