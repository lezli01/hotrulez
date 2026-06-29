package dev.lezli.hotrulez

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration

/**
 * Parity coverage: the service-agnostic machinery (parsing, symbol resolution,
 * rename, and formatting) works identically on a Cloud Storage (`firebase.storage`,
 * `/b/{bucket}/o`) file. These features do not depend on the dialect — the same
 * grammar and PSI back both — and this proves a realistic storage file flows
 * through them without special-casing.
 */
class FirebaseRulesStorageParityTest : BasePlatformTestCase() {

    fun testFunctionCallResolvesInStorageFile() {
        val target = resolveAtCaret(
            inBucket(
                """
                function isImageOwner() { return request.auth != null; }
                match /images/{imageId} {
                  allow read: if isImage${CARET}Owner();
                }
                """,
            ),
        )
        assertTrue("expected a function declaration, got $target", target is FirebaseRulesFunctionDeclaration)
        assertEquals("isImageOwner", (target as FirebaseRulesFunctionDeclaration).name)
    }

    fun testPathVariableResolvesInStorageFile() {
        val target = resolveAtCaret(
            inBucket(
                """
                match /images/{imageId} {
                  allow read: if image${CARET}Id != null;
                }
                """,
            ),
        )
        assertNotNull("a path-variable use should resolve to its binding wildcard", target)
        assertTrue("expected to resolve to the {imageId} wildcard, got ${target?.text}", target!!.text.contains("imageId"))
    }

    fun testRenameFunctionInStorageFileUpdatesDeclarationAndCall() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service firebase.storage {
              match /b/{bucket}/o {
                function isImageOwner() { return request.auth != null; }
                match /images/{imageId} {
                  allow read: if isImage${CARET}Owner();
                }
              }
            }
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("isOwner")
        myFixture.checkResult(
            """
            rules_version = '2';
            service firebase.storage {
              match /b/{bucket}/o {
                function isOwner() { return request.auth != null; }
                match /images/{imageId} {
                  allow read: if isOwner();
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testReformatExpandsCompactStorageFile() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            "rules_version='2';service firebase.storage{match /b/{bucket}/o{allow read: if true;}}",
        )
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        val result = myFixture.file.text
        assertTrue("reformat should expand the service block, got:\n$result", result.contains("service firebase.storage {"))
        assertTrue("reformat should indent the bucket root match, got:\n$result", result.contains("  match /b/{bucket}/o {"))
    }

    // --- Helpers ---------------------------------------------------------

    private fun resolveAtCaret(text: String): PsiElement? {
        myFixture.configureByText(FirebaseRulesFileType, text)
        return myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
    }

    /** Wraps body inside the conventional v2 Cloud Storage service + bucket root match. */
    private fun inBucket(body: String): String =
        "rules_version = '2';\n" +
            "service firebase.storage {\n" +
            "  match /b/{bucket}/o {\n" +
            body.trimIndent() + "\n" +
            "  }\n" +
            "}\n"

    private companion object {
        const val CARET = "<caret>"
    }
}
