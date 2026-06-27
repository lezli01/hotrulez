package dev.lezli.hotrulez

import dev.lezli.hotrulez.editor.FirestoreRulesBraceMatcher
import dev.lezli.hotrulez.lexer.FirestoreRulesTokenTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreRulesBraceMatcherTest {
    private val matcher = FirestoreRulesBraceMatcher()

    @Test
    fun matchesTheThreeBracketPairs() {
        val pairs = matcher.pairs.associate { it.leftBraceType to it.rightBraceType }

        assertEquals(3, matcher.pairs.size)
        assertEquals(FirestoreRulesTokenTypes.R_BRACE, pairs[FirestoreRulesTokenTypes.L_BRACE])
        assertEquals(FirestoreRulesTokenTypes.R_PAREN, pairs[FirestoreRulesTokenTypes.L_PAREN])
        assertEquals(FirestoreRulesTokenTypes.R_BRACKET, pairs[FirestoreRulesTokenTypes.L_BRACKET])
    }

    @Test
    fun onlyCurlyBracesAreStructural() {
        val structural = matcher.pairs.associate { it.leftBraceType to it.isStructural }

        assertTrue(structural[FirestoreRulesTokenTypes.L_BRACE]!!)
        assertEquals(false, structural[FirestoreRulesTokenTypes.L_PAREN])
        assertEquals(false, structural[FirestoreRulesTokenTypes.L_BRACKET])
    }

    @Test
    fun allowsPairedBracesBeforeAnyToken() {
        assertTrue(
            matcher.isPairedBracesAllowedBeforeType(FirestoreRulesTokenTypes.L_BRACE, FirestoreRulesTokenTypes.R_BRACE),
        )
        assertTrue(matcher.isPairedBracesAllowedBeforeType(FirestoreRulesTokenTypes.L_PAREN, null))
    }
}
