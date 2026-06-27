package dev.lezli.hotrulez.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.lexer.FirestoreRulesTokenTypes

/**
 * Matches the three bracket pairs Firestore Rules uses: `{}` for `service`,
 * `match`, and `function` blocks (and path wildcards), `()` for calls and
 * grouping, and `[]` for list/index literals.
 *
 * The brace matcher runs against the editor highlighter, so it keys off the
 * highlighting lexer's [FirestoreRulesTokenTypes] rather than the generated PSI
 * tokens.
 */
class FirestoreRulesBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    // Allow the IDE to auto-insert a closing brace regardless of what follows the
    // caret; Firestore Rules has no context where that is undesirable.
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    private companion object {
        val PAIRS = arrayOf(
            // Curly braces are structural: they drive block matching and the
            // "show matched brace" gutter for service/match/function blocks.
            BracePair(FirestoreRulesTokenTypes.L_BRACE, FirestoreRulesTokenTypes.R_BRACE, true),
            BracePair(FirestoreRulesTokenTypes.L_PAREN, FirestoreRulesTokenTypes.R_PAREN, false),
            BracePair(FirestoreRulesTokenTypes.L_BRACKET, FirestoreRulesTokenTypes.R_BRACKET, false),
        )
    }
}
