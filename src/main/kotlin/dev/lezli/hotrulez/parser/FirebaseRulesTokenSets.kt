package dev.lezli.hotrulez.parser

import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import dev.lezli.hotrulez.psi.FirebaseRulesTypes

/**
 * Token sets over the generated [FirebaseRulesTypes] tokens, used by the
 * parser definition and the formatter.
 */
object FirebaseRulesTokenSets {
    val WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    val COMMENTS: TokenSet = TokenSet.create(
        FirebaseRulesTypes.LINE_COMMENT,
        FirebaseRulesTypes.BLOCK_COMMENT,
    )

    val STRINGS: TokenSet = TokenSet.create(FirebaseRulesTypes.STRING)
}
