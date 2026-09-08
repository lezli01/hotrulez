package dev.lezli.hotrulez

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.documentation.FirebaseRulesDocumentationProvider

/**
 * Quick documentation: doc-grounded prose for built-in vocabulary (dialect-aware,
 * via [FirebaseRulesDocumentationProvider.getCustomDocumentationElement]) and
 * reconstructed signatures/comments for resolved user symbols. Asserts the generated
 * HTML contains the expected phrasing per target category, that dialect-exclusive
 * members never leak across services, that unknown/unresolved targets yield `null`,
 * and that `getUrlFor` surfaces the Firebase reference page.
 */
class FirebaseRulesDocumentationTest : BasePlatformTestCase() {

    private val provider = FirebaseRulesDocumentationProvider()

    // --- allow operations ------------------------------------------------

    fun testOperationReadShowsExpansionNote() {
        val doc = provider.generateDoc(builtinTarget(inCity("allow re${CARET}ad: if true;")), null)
        assertDocContains(doc, "read")
        assertDocContains(doc, "expands")
    }

    fun testOperationListMentionsRulesVersion() {
        val doc = provider.generateDoc(builtinTarget(inCity("allow li${CARET}st: if true;")), null)
        assertDocContains(doc, "rules_version")
    }

    fun testOperationInMultiMethodListDocuments() {
        // The label is rebuilt per-identifier: the caret sits on the second method.
        val doc = provider.generateDoc(builtinTarget(inCity("allow read, cre${CARET}ate: if true;")), null)
        assertDocContains(doc, "create")
    }

    // --- built-in globals ------------------------------------------------

    fun testGlobalRequestDocumentsAndHasUrl() {
        val target = builtinTarget(inCity("allow read: if req${CARET}uest.auth != null;"))
        val doc = provider.generateDoc(target, null)
        assertDocContains(doc, "request")

        val urls = provider.getUrlFor(target, null)
        assertNotNull("expected a docs URL for the 'request' global", urls)
        assertTrue("expected a non-empty URL list", urls!!.isNotEmpty())
        assertTrue("expected a firebase.google.com URL, got $urls", urls.first().contains("firebase.google.com"))
    }

    // --- Firestore members -----------------------------------------------

    fun testFirestoreRequestAuthMember() {
        val doc = provider.generateDoc(builtinTarget(inCity("allow read: if request.au${CARET}th != null;")), null)
        assertDocContains(doc, "authentication")
    }

    fun testFirestoreResourceDataMember() {
        val doc = provider.generateDoc(builtinTarget(inCity("allow read: if resource.da${CARET}ta.x == 1;")), null)
        assertDocContains(doc, "existing")
    }

    // --- Storage members + dialect gate ----------------------------------

    fun testStorageResourceSizeMember() {
        val doc = provider.generateDoc(builtinTarget(inObject("allow read: if resource.si${CARET}ze < 100;")), null)
        assertDocContains(doc, "size")
        assertDocContains(doc, "bytes")
    }

    fun testStorageMemberUrlPointsAtLiveStoragePage() {
        // Firebase retired `docs/reference/rules/rules.storage` (404). The Storage
        // object-metadata table now lives in the Cloud Storage rules-conditions guide,
        // and that is the page "open in browser" must reach from a Storage member.
        val target = builtinTarget(inObject("allow read: if resource.content${CARET}Type == 'image/png';"))
        val urls = provider.getUrlFor(target, null)
        assertNotNull("expected a docs URL for a Storage metadata member", urls)
        assertEquals(
            listOf("https://firebase.google.com/docs/storage/security/rules-conditions"),
            urls,
        )
    }

    fun testStorageOnlyMemberDoesNotLeakIntoFirestoreFile() {
        // `resource.size` is Storage-only; inside a Firestore file it must document nothing.
        val target = builtinTarget(inCity("allow read: if resource.si${CARET}ze < 100;"))
        assertNull("a Storage-only member must not document inside a Firestore file", provider.generateDoc(target, null))
    }

    fun testFirestoreOnlyMemberDoesNotLeakIntoStorageFile() {
        // `request.method` is Firestore-only; inside a Storage file it must document nothing.
        val target = builtinTarget(inObject("allow read: if request.met${CARET}hod == 'get';"))
        assertNull("a Firestore-only member must not document inside a Storage file", provider.generateDoc(target, null))
    }

    // --- path / cross-service helpers ------------------------------------

    fun testBareGetHelperShowsSignature() {
        val doc = provider.generateDoc(
            builtinTarget(inCity("allow read: if g${CARET}et(/databases/x/documents/cities/paris) != null;")),
            null,
        )
        assertDocContains(doc, "get(path)")
    }

    fun testCrossServiceFirestoreGetInStorageFileShowsSignature() {
        val doc = provider.generateDoc(
            builtinTarget(inObject("allow read: if firestore.g${CARET}et(/databases/x/documents/cities/paris) != null;")),
            null,
        )
        assertDocContains(doc, "get(path)")
    }

    fun testCrossServiceFirestoreGetDoesNotLeakIntoFirestoreFile() {
        // `firestore.get` is a Cloud Storage cross-service call; `firestore` is not a
        // namespace in Cloud Firestore, so inside a Firestore file it must document nothing.
        val target = builtinTarget(inCity("allow read: if firestore.g${CARET}et(/x) != null;"))
        assertNull(
            "firestore.get must not document inside a Cloud Firestore file",
            provider.generateDoc(target, null),
        )
    }

    // --- user symbols ----------------------------------------------------

    fun testUserFunctionShowsSignatureAndPrecedingComment() {
        val doc = userDoc(
            inDocuments(
                """
                // Returns true for signed-in users.
                function isSignedIn() { return request.auth != null; }
                match /cities/{city} { allow read: if isSign${CARET}edIn(); }
                """,
            ),
        )
        assertDocContains(doc, "function isSignedIn(")
        assertDocContains(doc, "Returns true for signed-in users.")
    }

    fun testTrailingCommentOfPreviousFunctionNotAttributed() {
        // The `// trailing note` belongs to `first`, on its own line; it must not become
        // the doc of the next declaration just because it sits directly above it.
        val doc = userDoc(
            inDocuments(
                """
                function first() { return true; } // trailing note for first
                function isSignedIn() { return request.auth != null; }
                match /cities/{city} { allow read: if isSign${CARET}edIn(); }
                """,
            ),
        )
        assertDocContains(doc, "function isSignedIn(")
        assertFalse(
            "a previous statement's trailing comment must not document the next declaration:\n$doc",
            doc!!.contains("trailing note"),
        )
    }

    fun testCommentDetachedByBlankLineNotAttributed() {
        // A blank line detaches the comment: it is no longer immediately preceding.
        val doc = userDoc(
            inDocuments(
                """
                // Detached note.

                function isSignedIn() { return request.auth != null; }
                match /cities/{city} { allow read: if isSign${CARET}edIn(); }
                """,
            ),
        )
        assertDocContains(doc, "function isSignedIn(")
        assertFalse(
            "a comment separated from the declaration by a blank line must not document it:\n$doc",
            doc!!.contains("Detached note"),
        )
    }

    fun testParameterShowsOwningFunction() {
        val doc = userDoc(
            inDocuments(
                """
                function isOwner(uid) { return resource.data.owner == u${CARET}id; }
                match /cities/{city} { allow read: if isOwner(request.auth.uid); }
                """,
            ),
        )
        assertDocContains(doc, "parameter 'uid' of function")
    }

    fun testLetBindingShowsExpression() {
        val doc = userDoc(
            inDocuments(
                """
                function check() {
                  let signedIn = request.auth != null;
                  return signed${CARET}In;
                }
                match /cities/{city} { allow read: if check(); }
                """,
            ),
        )
        assertDocContains(doc, "let signedIn =")
    }

    fun testPathVariableShowsCapture() {
        val doc = userDoc(
            inDocuments(
                """
                match /cities/{city} {
                  allow read: if ci${CARET}ty == 'paris';
                }
                """,
            ),
        )
        assertDocContains(doc, "path variable 'city'")
    }

    fun testUserFunctionHasNoExternalUrl() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            inDocuments(
                """
                function isSignedIn() { return request.auth != null; }
                match /cities/{city} { allow read: if isSign${CARET}edIn(); }
                """,
            ),
        )
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
        assertNotNull("expected the function use to resolve", target)
        assertNull("user symbols carry no external Firebase URL", provider.getUrlFor(target, null))
    }

    // --- no fabrication --------------------------------------------------

    fun testUnknownMemberYieldsNull() {
        val target = builtinTarget(inCity("allow read: if request.fo${CARET}o == 1;"))
        assertNull("an unknown member on a known built-in must not fabricate prose", provider.generateDoc(target, null))
    }

    fun testUnresolvedBareNameYieldsNull() {
        val target = builtinTarget(inCity("allow read: if foob${CARET}ar == 1;"))
        // No built-in target is selected, and generateDoc over whatever survives fabricates nothing.
        assertNull("an unresolved bare name has no built-in documentation", provider.generateDoc(target, null))
    }

    // --- recovery --------------------------------------------------------

    fun testDegradesGracefullyInMalformedFile() {
        // A broken block must neither throw nor stop an unrelated built-in from documenting.
        val target = builtinTarget(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /broken/ { allow read: if ;;; }
                match /cities/{city} { allow read: if req${CARET}uest.auth != null; }
              }
            }
            """.trimIndent(),
        )
        assertDocContains(provider.generateDoc(target, null), "request")
    }

    // --- Helpers ---------------------------------------------------------

    /** Configure [text] and return the built-in documentation target under the caret (built-in path). */
    private fun builtinTarget(text: String): PsiElement? {
        myFixture.configureByText(FirebaseRulesFileType, text)
        val offset = myFixture.caretOffset
        val context = myFixture.file.findElementAt(offset)
        return provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, context, offset)
    }

    /** Configure [text], resolve the reference under the caret, and generate its doc (user-symbol path). */
    private fun userDoc(text: String): String? {
        myFixture.configureByText(FirebaseRulesFileType, text)
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
        assertNotNull("expected the use under the caret to resolve to a declaration", target)
        return provider.generateDoc(target, null)
    }

    private fun assertDocContains(doc: String?, needle: String) {
        assertNotNull("expected generated documentation, got null", doc)
        assertTrue("expected '$needle' in generated doc:\n$doc", doc!!.contains(needle))
    }

    // --- File wrappers ---------------------------------------------------

    private fun inCity(statement: String): String =
        inDocuments("match /cities/{city} {\n  $statement\n}")

    private fun inDocuments(body: String): String =
        "rules_version = '2';\n" +
            "service cloud.firestore {\n" +
            "  match /databases/{database}/documents {\n" +
            body.trimIndent() + "\n" +
            "  }\n" +
            "}\n"

    private fun inObject(statement: String): String =
        inStorage("match /images/{imageId} {\n  $statement\n}")

    private fun inStorage(body: String): String =
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
