package dev.lezli.hotrulez.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.lezli.hotrulez.lexer.FirebaseRulesTokenTypes

/**
 * Matches the three bracket pairs Firestore Rules uses: `{}` for `service`,
 * `match`, and `function` blocks (and path wildcards), `()` for calls and
 * grouping, and `[]` for list/index literals.
 *
 * The brace matcher runs against the editor highlighter, so it keys off the
 * highlighting lexer's [FirebaseRulesTokenTypes] rather than the generated PSI
 * tokens.
 */
class FirebaseRulesBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    // Auto-insert the matching closer unless the caret sits directly before word-like
    // content (an identifier, keyword, operation, builtin, type, constant, string,
    // number, or path-interpolation '$'). Closing before such a token would strand a
    // spurious closer when the user types an opener to wrap existing code. Before
    // whitespace, comments, closers, separators, operators, or end of file the closer
    // is still inserted.
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean =
        contextType == null || !WORD_LIKE.contains(contextType)

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    private companion object {
        // Tokens that begin a value or a name; auto-inserting a closer before one of
        // these would wrap the wrong text.
        val WORD_LIKE = TokenSet.create(
            FirebaseRulesTokenTypes.IDENTIFIER,
            FirebaseRulesTokenTypes.KEYWORD,
            FirebaseRulesTokenTypes.OPERATION,
            FirebaseRulesTokenTypes.BUILTIN,
            FirebaseRulesTokenTypes.TYPE,
            FirebaseRulesTokenTypes.FUNCTION_CALL,
            FirebaseRulesTokenTypes.CONSTANT,
            FirebaseRulesTokenTypes.STRING,
            FirebaseRulesTokenTypes.NUMBER,
            FirebaseRulesTokenTypes.DOLLAR,
        )

        val PAIRS = arrayOf(
            // Curly braces are structural: they drive block matching and the
            // "show matched brace" gutter for service/match/function blocks.
            BracePair(FirebaseRulesTokenTypes.L_BRACE, FirebaseRulesTokenTypes.R_BRACE, true),
            BracePair(FirebaseRulesTokenTypes.L_PAREN, FirebaseRulesTokenTypes.R_PAREN, false),
            BracePair(FirebaseRulesTokenTypes.L_BRACKET, FirebaseRulesTokenTypes.R_BRACKET, false),
        )
    }
}
