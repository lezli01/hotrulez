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

    private fun assertFormats(beforePath: String, afterPath: String) {
        val file = myFixture.configureByFile(beforePath)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(file, listOf(file.textRange))
        }
        val expected = Path.of(testDataPath, afterPath).readText()
        assertEquals(expected, file.text)
    }
}
