package dev.lezli.hotrulez

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.diagnostics.FirebaseRulesSymbolInspection

/**
 * Covers [FirebaseRulesSymbolInspection]: undefined references and unused declarations, plus
 * the negatives that must never be flagged (forward references, built-ins, members, path
 * variables) and graceful behaviour on a malformed file. Assertions match on the highlight
 * description so they are independent of how the `LIKE_UNKNOWN_SYMBOL` / `LIKE_UNUSED_SYMBOL`
 * highlight types map to severities.
 */
class FirebaseRulesSymbolInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(FirebaseRulesSymbolInspection())
    }

    fun testUndefinedReferenceIsFlagged() {
        assertReported(inDocuments("match /c/{id} { allow read: if foo == true; }"), "Cannot resolve symbol 'foo'")
    }

    fun testUndefinedCalleeIsFlagged() {
        assertReported(inDocuments("match /c/{id} { allow read: if isOwner(); }"), "Cannot resolve symbol 'isOwner'")
    }

    fun testForwardFunctionReferenceIsNotFlagged() {
        // The call precedes the declaration textually; scope-based resolution still links them.
        assertNotReported(
            inDocuments(
                """
                match /c/{id} { allow read: if isOk(); }
                function isOk() { return true; }
                """.trimIndent(),
            ),
            "Cannot resolve symbol 'isOk'",
        )
    }

    fun testBuiltinsAndMembersAreNotFlagged() {
        assertNoUnresolved(
            inDocuments("match /c/{id} { allow read: if request.auth != null && resource.data.customField > 0; }"),
        )
    }

    fun testPathVariableUseIsNotFlagged() {
        assertNoUnresolved(inDocuments("match /users/{userId} { allow read: if userId == request.auth.uid; }"))
    }

    fun testUnusedPathVariableIsNotFlagged() {
        // A match binding is meaningful even when its condition never reads it.
        assertNotReported(inDocuments("match /users/{userId} { allow read: if true; }"), "never used")
    }

    fun testUnusedFunctionIsFlagged() {
        assertReported(
            inDocuments(
                """
                function unused() { return true; }
                match /c/{id} { allow read: if true; }
                """.trimIndent(),
            ),
            "Function 'unused' is never used",
        )
    }

    fun testUnusedLetIsFlagged() {
        assertReported(
            inDocuments(
                """
                function f() { let x = 1; return true; }
                match /c/{id} { allow read: if f(); }
                """.trimIndent(),
            ),
            "Variable 'x' is never used",
        )
    }

    fun testUnusedParameterIsFlagged() {
        assertReported(
            inDocuments(
                """
                function f(a) { return true; }
                match /c/{id} { allow read: if f(1); }
                """.trimIndent(),
            ),
            "Parameter 'a' is never used",
        )
    }

    fun testUsedParameterIsNotFlagged() {
        assertNotReported(
            inDocuments(
                """
                function f(a) { return a; }
                match /c/{id} { allow read: if f(true); }
                """.trimIndent(),
            ),
            "Parameter 'a' is never used",
        )
    }

    fun testUnusedFunctionDoesNotAlsoFlagItsParameter() {
        val descriptions = descriptions(inDocuments("function unused(a) { return true; }\nmatch /c/{id} { allow read: if true; }"))
        assertTrue("expected the function to be reported unused", descriptions.any { it.contains("Function 'unused' is never used") })
        assertFalse("must not also flag the unused function's parameter", descriptions.any { it.contains("Parameter 'a' is never used") })
    }

    fun testMalformedFileDegradesGracefully() {
        // A parse error in one block must not throw and must not stop an unrelated block's
        // symbols from resolving.
        assertNotReported(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /broken/{id} { allow read: if ; }
                function ok() { return true; }
                match /c/{id} { allow read: if ok(); }
              }
            }
            """.trimIndent(),
            "Cannot resolve symbol 'ok'",
        )
    }

    fun testGlobalNamespacesAndTypeFunctionsAreNotFlagged() {
        // math/timestamp/duration/latlng/hashing namespaces, the debug() helper, and
        // type-conversion functions (int/float/...) are global built-ins, not undefined symbols.
        assertNoUnresolved(
            inDocuments(
                """
                match /logs/{id} {
                  allow create: if request.time < timestamp.date(2050, 1, 1)
                    && math.abs(request.resource.data.delta) < 100
                    && hashing.sha256(request.resource.data.tok).size() == 32
                    && int(request.resource.data.n) > 0
                    && debug(request.auth) != null;
                }
                """.trimIndent(),
            ),
        )
    }

    fun testPathHelpersAreNotFlagged() {
        assertNoUnresolved(inDocuments("match /c/{id} { allow read: if exists(/databases/x); }"))
    }

    fun testSelfRecursiveFunctionIsFlaggedUnused() {
        // A function referenced only from its own body is still dead code (Firebase rejects
        // recursion anyway), so the self-call must not mark it used.
        assertReported(
            inDocuments(
                """
                function loops() { return loops(); }
                match /c/{id} { allow read: if true; }
                """.trimIndent(),
            ),
            "Function 'loops' is never used",
        )
    }

    /** Wraps [inner] inside a well-formed v2 Cloud Firestore documents root match. */
    private fun inDocuments(inner: String): String =
        """
        rules_version = '2';
        service cloud.firestore {
          match /databases/{database}/documents {
            $inner
          }
        }
        """.trimIndent()

    private fun descriptions(text: String): List<String> {
        myFixture.configureByText(FirebaseRulesFileType, text)
        return myFixture.doHighlighting().mapNotNull(HighlightInfo::getDescription)
    }

    private fun assertReported(text: String, fragment: String) {
        val descriptions = descriptions(text)
        assertTrue("expected a highlight containing \"$fragment\" but got: $descriptions", descriptions.any { it.contains(fragment) })
    }

    private fun assertNotReported(text: String, fragment: String) {
        val descriptions = descriptions(text)
        assertFalse("did not expect a highlight containing \"$fragment\" but got: $descriptions", descriptions.any { it.contains(fragment) })
    }

    private fun assertNoUnresolved(text: String) = assertNotReported(text, "Cannot resolve symbol")
}
