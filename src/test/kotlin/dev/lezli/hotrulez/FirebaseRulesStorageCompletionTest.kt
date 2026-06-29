package dev.lezli.hotrulez

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Service-aware completion for Cloud Storage (`service firebase.storage`) rules.
 * The `request`/`resource` member tables, the expression-position globals and
 * helpers, and the service literal all differ from Cloud Firestore. Detection is
 * content-based, so the same caret positions that yield Firestore members in
 * [FirebaseRulesCompletionTest] yield Storage members here.
 */
class FirebaseRulesStorageCompletionTest : BasePlatformTestCase() {

    fun testServiceCompletionOffersBothDialects() {
        val items = complete("rules_version = '2';\nservice <caret>")
        assertContainsAll(items, "cloud.firestore", "firebase.storage")
    }

    fun testStorageResourceMembers() {
        val items = complete(inImage("allow read: if resource.<caret>"))
        assertContainsAll(items, "size", "contentType", "metadata", "name", "timeCreated", "md5Hash", "bucket")
        // Firestore-only members must not leak into a storage file.
        assertDoesNotContain(items, "data")
        assertDoesNotContain(items, "__name__")
    }

    fun testStorageRequestResourceMembers() {
        val items = complete(inImage("allow write: if request.resource.<caret>"))
        assertContainsAll(items, "size", "contentType", "metadata", "name")
        // Server-assigned fields are not part of the incoming upload.
        assertDoesNotContain(items, "generation")
        assertDoesNotContain(items, "data")
    }

    fun testStorageRequestMembers() {
        val items = complete(inImage("allow read: if request.<caret>"))
        assertContainsAll(items, "auth", "resource", "time", "params", "path")
        // Firestore-only request members must not leak into storage.
        assertDoesNotContain(items, "method")
        assertDoesNotContain(items, "query")
    }

    fun testStorageRequestAuthMembersAreShared() {
        val items = complete(inImage("allow read: if request.auth.<caret>"))
        assertContainsAll(items, "uid", "token")
    }

    fun testStorageExpressionOffersFirestoreNamespaceNotBareHelpers() {
        val items = complete(inImage("allow read: if <caret>;"))
        assertContainsAll(items, "request", "resource", "firestore", "true", "false", "null")
        // Storage has no bare path helpers; cross-service access is firestore.get/exists.
        assertDoesNotContain(items, "getAfter")
        assertDoesNotContain(items, "existsAfter")
    }

    fun testStorageCrossServiceFirestoreMembers() {
        val items = complete(inImage("allow read: if firestore.<caret>"))
        assertContainsAll(items, "get", "exists")
    }

    // --- Helpers ---------------------------------------------------------

    private fun complete(text: String): List<String> {
        myFixture.configureByText(FirebaseRulesFileType, text)
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

    /** Wraps a statement in a minimal, well-formed v2 Cloud Storage rules file. */
    private fun inImage(statement: String): String =
        "rules_version = '2';\n" +
            "service firebase.storage {\n" +
            "  match /b/{bucket}/o {\n" +
            "    match /images/{imageId} {\n" +
            "      $statement\n" +
            "    }\n" +
            "  }\n" +
            "}\n"
}
