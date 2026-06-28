package dev.lezli.hotrulez

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Scope-aware, doc-sourced completion: operations, structural keywords,
 * `cloud.firestore`, in-scope symbols, built-ins/helpers/literals, and shallow
 * `request.`/`resource.` members. No type inference; degrades gracefully in a
 * malformed file.
 */
class FirestoreRulesCompletionTest : BasePlatformTestCase() {

    // --- Operations and structural keywords ------------------------------

    fun testOperationsAfterAllow() {
        val items = complete(inCity("allow <caret>"))
        assertContainsAll(items, "get", "list", "read", "create", "update", "delete", "write")
    }

    fun testOperationsAfterComma() {
        val items = complete(inCity("allow read, <caret>"))
        assertContainsAll(items, "create", "update", "delete", "write")
    }

    fun testServiceCompletion() {
        val items = complete("rules_version = '2';\nservice <caret>")
        assertContainsAll(items, "cloud.firestore")
    }

    fun testTopLevelKeywords() {
        val items = complete("<caret>")
        assertContainsAll(items, "rules_version", "service")
    }

    fun testBlockKeywords() {
        val items = complete(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                <caret>
              }
            }
            """.trimIndent(),
        )
        assertContainsAll(items, "match", "allow", "function")
    }

    fun testFunctionBodyKeywords() {
        val items = complete(inDocuments("function f() { <caret> }"))
        assertContainsAll(items, "let", "return")
    }

    fun testIfKeywordAfterAllowColon() {
        val items = complete(inCity("allow read: <caret>"))
        assertContainsAll(items, "if")
    }

    // --- Expression position ---------------------------------------------

    fun testExpressionPositionOffersSymbolsBuiltinsHelpersLiterals() {
        val items = complete(
            inDocuments(
                """
                function isSignedIn() { return request.auth != null; }
                match /cities/{city} {
                  allow read: if <caret>;
                }
                """,
            ),
        )
        assertContainsAll(items, "isSignedIn", "city", "request", "resource", "get", "exists", "true", "false", "null")
    }

    fun testParameterOfferedInsideItsFunction() {
        // Empty prefix so the lookup is shown rather than auto-inserted on a single match.
        val items = complete(inDocuments("function f(secret) { return <caret> ; }"))
        assertContainsAll(items, "secret")
    }

    fun testParameterNotOfferedOutsideItsFunction() {
        val items = complete(
            inDocuments(
                """
                function f(secret) { return true; }
                match /cities/{city} { allow read: if <caret>; }
                """,
            ),
        )
        assertDoesNotContain(items, "secret")
    }

    fun testLetOfferedAfterDeclaration() {
        val items = complete(inDocuments("function f() { let known = 1; return <caret>; }"))
        assertContainsAll(items, "known")
    }

    fun testLetNotOfferedBeforeDeclaration() {
        val items = complete(inDocuments("function f() { let a = <caret>; let later = 1; return a; }"))
        assertDoesNotContain(items, "later")
    }

    // --- Shallow member completion ---------------------------------------

    fun testRequestMembers() {
        val items = complete(inCity("allow read: if request.<caret>"))
        assertContainsAll(items, "auth", "resource", "method", "time", "params", "path")
    }

    fun testResourceMembers() {
        val items = complete(inCity("allow read: if resource.<caret>"))
        assertContainsAll(items, "data", "id", "__name__")
    }

    fun testRequestAuthMembers() {
        val items = complete(inCity("allow read: if request.auth.<caret>"))
        assertContainsAll(items, "uid", "token")
    }

    fun testRequestResourceMembers() {
        val items = complete(inCity("allow create: if request.resource.<caret>"))
        assertContainsAll(items, "data", "id", "__name__")
    }

    fun testMemberCompletionToleratesCommentInReceiver() {
        // A comment inside the receiver chain must not corrupt the member lookup key.
        val items = complete(inCity("allow read: if request /* note */ .auth.<caret>"))
        assertContainsAll(items, "uid", "token")
    }

    // --- Position edge cases ---------------------------------------------

    fun testMapEntryColonOffersExpressionNotIfKeyword() {
        // A ':' inside a map literal in the condition is not the allow rule's own ':',
        // so the value position offers expression symbols, never the 'if' keyword.
        val items = complete(inCity("allow read: if x in { role: <caret> };"))
        assertContainsAll(items, "request", "resource")
        assertDoesNotContain(items, "if")
    }

    fun testNoExpressionSymbolsInParameterDeclaration() {
        val first = complete(inDocuments("function f(<caret>) { return true; }"))
        assertDoesNotContain(first, "request")
        val second = complete(inDocuments("function f(a, <caret>) { return true; }"))
        assertDoesNotContain(second, "request")
    }

    // --- Recovery --------------------------------------------------------

    fun testCompletionDegradesGracefullyInMalformedFile() {
        // Must not throw, and an unrelated condition still offers expression symbols.
        val items = complete(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /broken/ { allow read: if ;;; }
                match /cities/{city} { allow read: if <caret>; }
              }
            }
            """.trimIndent(),
        )
        assertContainsAll(items, "request", "resource")
    }

    // --- Helpers ---------------------------------------------------------

    private fun complete(text: String): List<String> {
        myFixture.configureByText(FirestoreRulesFileType, text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    private fun assertContainsAll(items: List<String>, vararg expected: String) {
        for (value in expected) {
            assertTrue("expected completion '$value' in $items", value in items)
        }
    }

    private fun assertDoesNotContain(items: List<String>, value: String) {
        assertFalse("did not expect completion '$value' in $items", value in items)
    }

    private fun inCity(statement: String): String =
        inDocuments("match /cities/{city} {\n  $statement\n}")

    private fun inDocuments(body: String): String =
        "rules_version = '2';\n" +
            "service cloud.firestore {\n" +
            "  match /databases/{database}/documents {\n" +
            body.trimIndent() + "\n" +
            "  }\n" +
            "}\n"
}
