package dev.lezli.hotrulez

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Exercises the brace matcher, quote handler, and commenter through the real
 * editor pipeline (typed-character handling and the comment actions) so the
 * extension-point registration in plugin.xml is covered end to end.
 */
class FirebaseRulesEditorBehaviorTest : BasePlatformTestCase() {
    fun testTypingParenInsertsClosingParen() {
        myFixture.configureByText(FirebaseRulesFileType, "allow read: if isOwner<caret>")
        myFixture.type("(")
        assertEquals("allow read: if isOwner()", myFixture.editor.document.text)
    }

    fun testTypingBracketInsertsClosingBracket() {
        myFixture.configureByText(FirebaseRulesFileType, "allow read: if x in roles<caret>")
        myFixture.type("[")
        assertEquals("allow read: if x in roles[]", myFixture.editor.document.text)
    }

    fun testTypingParenBeforeIdentifierDoesNotAutoClose() {
        // The closer must NOT be auto-inserted when the caret sits directly before an
        // identifier, otherwise typing '(' to wrap an existing call strands a spurious ')'.
        myFixture.configureByText(FirebaseRulesFileType, "allow read: if <caret>isOwner(uid);")
        myFixture.type("(")
        assertEquals("allow read: if (isOwner(uid);", myFixture.editor.document.text)
    }

    fun testTypingParenBeforeSeparatorStillAutoCloses() {
        // When a separator/closer (not word-like content) follows the caret, auto-close
        // is still desirable and must keep working.
        myFixture.configureByText(FirebaseRulesFileType, "allow read: if x == <caret>;")
        myFixture.type("(")
        assertEquals("allow read: if x == ();", myFixture.editor.document.text)
    }

    fun testTypingSingleQuoteInsertsPair() {
        myFixture.configureByText(FirebaseRulesFileType, "rules_version = <caret>")
        myFixture.type("'")
        assertEquals("rules_version = ''", myFixture.editor.document.text)
    }

    fun testTypingDoubleQuoteInsertsPair() {
        myFixture.configureByText(FirebaseRulesFileType, "allow read: if name == <caret>")
        myFixture.type("\"")
        assertEquals("allow read: if name == \"\"", myFixture.editor.document.text)
    }

    fun testTypingClosingQuoteTypesOver() {
        myFixture.configureByText(FirebaseRulesFileType, "rules_version = '<caret>';")
        myFixture.type("'")
        // The typed quote types over the existing closing quote rather than stacking up.
        assertEquals("rules_version = '';", myFixture.editor.document.text)
    }

    fun testMatchingBraceNavigation() {
        // Exercises actual brace matching (the matcher's core purpose) through the real
        // editor pipeline: with the caret on the structural `service {`, the
        // matching-brace action must jump to its closing `}` across the nested block.
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            service cloud.firestore <caret>{
              match /databases/{database}/documents {
              }
            }
            """.trimIndent(),
        )
        val openOffset = myFixture.caretOffset
        val closeOffset = myFixture.editor.document.text.lastIndexOf('}')

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MATCH_BRACE)

        val caret = myFixture.editor.caretModel.offset
        assertTrue("caret should leave the opening brace", caret != openOffset)
        assertTrue(
            "expected caret at the matching '}' ($closeOffset), was $caret",
            caret == closeOffset || caret == closeOffset + 1,
        )
    }

    fun testCommentLineRoundTrips() {
        myFixture.configureByText(FirebaseRulesFileType, "<caret>allow read: if true;")

        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        val commented = myFixture.editor.document.text
        assertTrue("expected a line comment, got: $commented", commented.trimStart().startsWith("//"))
        assertTrue(commented.contains("allow read: if true;"))

        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        assertEquals("allow read: if true;", myFixture.editor.document.text)
    }

    fun testBlockCommentWrapsSelection() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            "allow read: if <selection>request.auth != null</selection>;",
        )
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)

        val text = myFixture.editor.document.text
        assertTrue("expected a block comment, got: $text", text.contains("/*") && text.contains("*/"))
        assertTrue(text.contains("request.auth != null"))
    }

    fun testFileTypeExposesIcon() {
        assertNotNull(FirebaseRulesFileType.icon)
    }
}
