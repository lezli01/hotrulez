package dev.lezli.hotrulez

import dev.lezli.hotrulez.editor.FirebaseRulesBraceMatcher
import dev.lezli.hotrulez.lexer.FirebaseRulesTokenTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseRulesBraceMatcherTest {
    private val matcher = FirebaseRulesBraceMatcher()

    @Test
    fun matchesTheThreeBracketPairs() {
        val pairs = matcher.pairs.associate { it.leftBraceType to it.rightBraceType }

        assertEquals(3, matcher.pairs.size)
        assertEquals(FirebaseRulesTokenTypes.R_BRACE, pairs[FirebaseRulesTokenTypes.L_BRACE])
        assertEquals(FirebaseRulesTokenTypes.R_PAREN, pairs[FirebaseRulesTokenTypes.L_PAREN])
        assertEquals(FirebaseRulesTokenTypes.R_BRACKET, pairs[FirebaseRulesTokenTypes.L_BRACKET])
    }

    @Test
    fun onlyCurlyBracesAreStructural() {
        val structural = matcher.pairs.associate { it.leftBraceType to it.isStructural }

        assertTrue(structural[FirebaseRulesTokenTypes.L_BRACE]!!)
        assertEquals(false, structural[FirebaseRulesTokenTypes.L_PAREN])
        assertEquals(false, structural[FirebaseRulesTokenTypes.L_BRACKET])
    }

    @Test
    fun allowsPairedBracesBeforeAnyToken() {
        assertTrue(
            matcher.isPairedBracesAllowedBeforeType(FirebaseRulesTokenTypes.L_BRACE, FirebaseRulesTokenTypes.R_BRACE),
        )
        assertTrue(matcher.isPairedBracesAllowedBeforeType(FirebaseRulesTokenTypes.L_PAREN, null))
    }
}
