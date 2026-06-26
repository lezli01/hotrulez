package dev.lezli.hotrulez.parser

import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import dev.lezli.hotrulez.psi.FirestoreRulesTypes

/**
 * Token sets over the generated [FirestoreRulesTypes] tokens, used by the
 * parser definition and the formatter.
 */
object FirestoreRulesTokenSets {
    val WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    val COMMENTS: TokenSet = TokenSet.create(
        FirestoreRulesTypes.LINE_COMMENT,
        FirestoreRulesTypes.BLOCK_COMMENT,
    )

    val STRINGS: TokenSet = TokenSet.create(FirestoreRulesTypes.STRING)
}
