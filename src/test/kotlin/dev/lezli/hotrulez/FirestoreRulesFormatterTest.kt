package dev.lezli.hotrulez

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path
import kotlin.io.path.readText

class FirestoreRulesFormatterTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    fun testFormatsCompactInput() {
        assertFormats("formatter/compact.before.rules", "formatter/compact.after.rules")
    }

    fun testPreservesMultilineConditions() {
        assertFormats("formatter/multiline-condition.before.rules", "formatter/multiline-condition.after.rules")
    }

    fun testFormatsComments() {
        assertFormats("formatter/comments.before.rules", "formatter/comments.after.rules")
    }

    fun testPreservesIntentionalBlankLines() {
        assertFormats("formatter/blank-lines.before.rules", "formatter/blank-lines.after.rules")
    }

    fun testFormatsRecursiveWildcardPaths() {
        assertFormats("formatter/recursive-wildcard.before.rules", "formatter/recursive-wildcard.after.rules")
    }

    fun testFormatsMultiParameterFunction() {
        // Guards comma spacing inside a parameter list now that parameters are wrapped
        // in PARAMETER nodes rather than bare IDENTIFIER leaves.
        assertFormats("formatter/parameter-list.before.rules", "formatter/parameter-list.after.rules")
    }

    fun testKeepsUnaryOperatorAttached() {
        assertFormats("formatter/unary.before.rules", "formatter/unary.after.rules")
    }

    fun testFormatsAroundMalformedInput() {
        assertFormats("formatter/malformed.before.rules", "formatter/malformed.after.rules")
    }

    fun testSeparatesBlockMembersWithBlankLines() {
        assertFormats("formatter/block-separation.before.rules", "formatter/block-separation.after.rules")
    }

    fun testSeparatesAllowFromNestedMatchBlock() {
        assertFormats("formatter/nested-match.before.rules", "formatter/nested-match.after.rules")
    }

    fun testKeepsBlankLineBeforeCommentedFunction() {
        assertFormats("formatter/comment-before-function.before.rules", "formatter/comment-before-function.after.rules")
    }

    fun testIndentsMultilineParenthesizedExpression() {
        assertFormats("formatter/multiline-expression.before.rules", "formatter/multiline-expression.after.rules")
    }

    fun testFormatsPathExpressionsInCalls() {
        assertFormats("formatter/path-call.before.rules", "formatter/path-call.after.rules")
    }

    fun testKeepsMultiTokenPathSegmentsTight() {
        assertFormats("formatter/path-segment-literal.before.rules", "formatter/path-segment-literal.after.rules")
    }

    fun testIndentsMultilineCallArguments() {
        assertFormats("formatter/call-arguments.before.rules", "formatter/call-arguments.after.rules")
    }

    fun testHangingIndentsChainedCallContinuation() {
        assertFormats("formatter/chained-call.before.rules", "formatter/chained-call.after.rules")
    }

    fun testIndentsMultilineMapLiteral() {
        assertFormats("formatter/map-literal.before.rules", "formatter/map-literal.after.rules")
    }

    fun testFormatsIsTypeOperator() {
        assertFormats("formatter/type-operators.before.rules", "formatter/type-operators.after.rules")
    }

    fun testNewLineInArgumentListGetsContinuationIndent() {
        // getChildAttributes must hang a freshly typed line inside an argument list the
        // same way childIndent/reformat does, so pressing Enter mid-call indents past
        // the call line rather than aligning with it (regression guard against
        // getChildAttributes and childIndent drifting apart).
        myFixture.configureByText(
            FirestoreRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if hasAny(a,<caret>b);
              }
            }
            """.trimIndent(),
        )

        myFixture.type('\n')

        val lines = myFixture.editor.document.text.lines()
        val callLine = lines.first { it.contains("hasAny(") }
        val continuation = lines[lines.indexOf(callLine) + 1]
        assertTrue(
            "expected continuation \"$continuation\" to hang past call line \"$callLine\"",
            continuation.indentWidth() > callLine.indentWidth(),
        )
    }

    private fun String.indentWidth(): Int = takeWhile { it == ' ' }.length

    private fun assertFormats(beforePath: String, afterPath: String) {
        val file = myFixture.configureByFile(beforePath)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, listOf(file.textRange))
        }
        val expected = Path.of(testDataPath, afterPath).readText()
        assertEquals(expected, file.text)
    }
}
