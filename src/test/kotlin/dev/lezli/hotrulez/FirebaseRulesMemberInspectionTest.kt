package dev.lezli.hotrulez

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.diagnostics.FirebaseRulesMemberInspection

/**
 * Covers [FirebaseRulesMemberInspection]: the true positives it must flag (unknown members
 * on closed, service-scoped built-in receivers, including cross-dialect mistakes), the full
 * guaranteed-negative set it must never flag (open namespaces, valid members, shadowing,
 * non-chain receivers, neutral/malformed files), and the did-you-mean quick-fix.
 *
 * Assertions match on the highlight description, filtered to the inspection's own messages
 * (all contain the word "member"), so they are independent of how `WEAK WARNING` /
 * `GENERIC_ERROR_OR_WARNING` map to severities.
 */
class FirebaseRulesMemberInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(FirebaseRulesMemberInspection())
    }

    // --- True positives --------------------------------------------------

    fun testUnknownMemberOnRequestIsFlagged() {
        assertFlagged(firestore("allow read: if request.foo == true;"), "'foo' is not a member of `request`")
    }

    fun testFirestoreResourceSizeIsFlaggedAsCrossDialect() {
        // `size` is a Cloud Storage member; Firestore `resource` is {data, id, __name__}.
        assertFlagged(firestore("allow read: if resource.size > 0;"), "'size' is a Cloud Storage member")
    }

    fun testStorageRequestMethodIsFlaggedAsCrossDialect() {
        // `method` is a Cloud Firestore member; Storage `request` has no `method`.
        assertFlagged(storage("allow read: if request.method == 'get';"), "'method' is a Cloud Firestore member")
    }

    fun testMemberTypoOffersDidYouMean() {
        assertFlagged(firestore("allow read: if request.resourse != null;"), "'resourse' is not a member of `request`")
    }

    fun testUnknownQueryMemberIsFlagged() {
        assertFlagged(firestore("allow list: if request.query.zzz == 1;"), "'zzz' is not a member of `request.query`")
    }

    // --- Guaranteed negatives: open namespaces ---------------------------

    fun testUserDocumentFieldsAreNotFlagged() {
        assertNothingFlagged(firestore("allow read: if resource.data.anything > request.resource.data.other;"))
    }

    fun testCustomAuthClaimsAreNotFlagged() {
        assertNothingFlagged(firestore("allow read: if request.auth.token.admin == true;"))
    }

    fun testFirebaseReservedClaimIsNotFlagged() {
        // MFA/SAML members + the open index signature: never flag under `.firebase`.
        assertNothingFlagged(
            firestore("allow read: if request.auth.token.firebase.sign_in_second_factor == 'phone';"),
        )
        assertNothingFlagged(firestore("allow read: if request.auth.token.firebase.tenant == 't';"))
    }

    fun testStorageMetadataIsNotFlagged() {
        assertNothingFlagged(storage("allow read: if resource.metadata.owner == request.auth.uid;"))
    }

    fun testStorageRequestParamsAreNotFlagged() {
        assertNothingFlagged(storage("allow read: if request.params.anything == 1;"))
    }

    // --- Guaranteed negatives: valid members on every closed receiver ----

    fun testValidFirestoreMembersAreNotFlagged() {
        assertNothingFlagged(
            firestore(
                """
                allow read: if request.auth != null
                  && request.method == 'get'
                  && request.time != null
                  && request.auth.uid == resource.data.owner
                  && request.auth.token != null
                  && resource.id == request.resource.id
                  && resource.__name__ == request.resource.__name__
                  && request.query.limit <= 10
                  && request.query.offset >= 0
                  && request.query.orderBy != null;
                """.trimIndent(),
            ),
        )
    }

    fun testValidStorageMembersAreNotFlagged() {
        assertNothingFlagged(
            storage(
                """
                allow write: if request.auth != null
                  && request.params.x == 1
                  && request.time != null
                  && resource.size < 5
                  && resource.contentType.matches('image/.*')
                  && resource.metadata.k == 'v'
                  && request.resource.size < 5
                  && request.resource.md5Hash != null;
                """.trimIndent(),
            ),
        )
    }

    // --- Guaranteed negatives: shadowing & non-chain receivers -----------

    fun testParameterShadowingResourceIsNotFlagged() {
        // `resource` here is the function parameter, not the built-in.
        assertNothingFlagged(
            firestore(
                """
                function check(resource) { return resource.foo == 1; }
                match /c/{id} { allow read: if check(1); }
                """.trimIndent(),
            ),
        )
    }

    fun testPathVariableShadowingRequestIsNotFlagged() {
        // `{request}` binds a path variable named `request`; its members are the user's.
        assertNothingFlagged(firestore("match /{request} { allow read: if request.zzz == 1; }"))
    }

    fun testLetBindingShadowingResourceIsNotFlagged() {
        // `resource` here is the function-local `let` binding, not the built-in.
        assertNothingFlagged(
            firestore(
                """
                function f() { let resource = request.resource; return resource.foo == 1; }
                match /c/{id} { allow read: if f(); }
                """.trimIndent(),
            ),
        )
    }

    fun testCallResultReceiverIsNotFlagged() {
        assertNothingFlagged(firestore("allow read: if resource().foo == 1;"))
    }

    fun testIndexResultReceiverIsNotFlagged() {
        assertNothingFlagged(firestore("allow read: if resource[0].foo == 1;"))
    }

    fun testMapMethodOnClosedReceiverIsNotFlagged() {
        // request.auth is a rules.Map; get/keys/size are documented map methods, valid to call
        // directly on it. Members in call position (method calls) are never flagged — the closed
        // sets are documented property interfaces, and method existence would need type inference.
        assertNothingFlagged(firestore("allow read: if request.auth.get('role', 'guest') == 'admin';"))
        assertNothingFlagged(firestore("allow read: if request.auth.keys().hasAll(['uid']);"))
    }

    // --- Guaranteed negatives: neutral & malformed files -----------------

    fun testNeutralFileFlagsNothing() {
        // No recognised `service` declaration: the dialect is unknown, so flag nothing.
        assertNothingFlagged("match /c/{id} { allow read: if request.foo == true; }")
    }

    fun testMalformedFileThrowsNothing() {
        // A parse error must not throw and must not stop an unrelated valid member from being
        // left alone; the genuinely-unknown member in the intact block is still flagged.
        assertFlagged(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /broken/{id} { allow read: if ; }
                match /c/{id} { allow read: if request.bogus == true; }
              }
            }
            """.trimIndent(),
            "'bogus' is not a member of `request`",
        )
    }

    // --- Quick-fix -------------------------------------------------------

    fun testDidYouMeanRenameApplies() {
        myFixture.configureByText(FirebaseRulesFileType, firestore("allow read: if request.resourse != null;"))
        launchFix("Change to 'resource'")
        myFixture.checkResult(firestore("allow read: if request.resource != null;"))
    }

    fun testCrossDialectMistakeOffersNoRename() {
        myFixture.configureByText(FirebaseRulesFileType, firestore("allow read: if resource.size > 0;"))
        val fixes = myFixture.getAllQuickFixes()
        fixes.forEach { runCatching { it.isAvailable(myFixture.project, myFixture.editor, myFixture.file) } }
        assertFalse(
            "a cross-dialect true positive must offer no rename; got: ${fixes.map { it.text }}",
            fixes.any { it.text.startsWith("Change to") },
        )
        assertTrue(
            "the cross-dialect message should still be shown",
            flags(myFixture.file.text).any { it.contains("'size' is a Cloud Storage member") },
        )
    }

    // --- Registration ----------------------------------------------------

    fun testRegisteredAsWeakWarningOnByDefault() {
        // The behavioral tests force-enable the tool and match on text, so they cannot catch a
        // plugin.xml regression (level flipped to WARNING, or enabledByDefault to false). Pin the
        // registered defaults directly against the profile.
        val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
        val key = HighlightDisplayKey.find("FirebaseRulesMember")
        assertNotNull("inspection must be registered under shortName 'FirebaseRulesMember'", key)
        assertTrue("inspection must be enabled by default", profile.isToolEnabled(key))
        assertEquals(
            "inspection default severity must be WEAK WARNING",
            HighlightDisplayLevel.WEAK_WARNING,
            profile.getErrorLevel(key!!, null),
        )
    }

    // --- Harness ---------------------------------------------------------

    /** Wraps [inner] in a well-formed v2 Cloud Firestore documents root match. */
    private fun firestore(inner: String): String =
        """
        rules_version = '2';
        service cloud.firestore {
          match /databases/{database}/documents {
            $inner
          }
        }
        """.trimIndent()

    /** Wraps [inner] in a well-formed v2 Cloud Storage bucket root match. */
    private fun storage(inner: String): String =
        """
        rules_version = '2';
        service firebase.storage {
          match /b/{bucket}/o {
            $inner
          }
        }
        """.trimIndent()

    /** The inspection's own highlight messages (all contain the word "member"). */
    private fun flags(text: String): List<String> {
        myFixture.configureByText(FirebaseRulesFileType, text)
        return myFixture.doHighlighting().mapNotNull(HighlightInfo::getDescription).filter { it.contains("member") }
    }

    private fun assertFlagged(text: String, fragment: String) {
        val flags = flags(text)
        assertTrue("expected a member warning containing \"$fragment\" but got: $flags", flags.any { it.contains(fragment) })
    }

    private fun assertNothingFlagged(text: String) {
        val flags = flags(text)
        assertTrue("expected no member warnings but got: $flags", flags.isEmpty())
    }

    private fun launchFix(hint: String) {
        val fixes = myFixture.getAllQuickFixes()
        fixes.forEach { runCatching { it.isAvailable(myFixture.project, myFixture.editor, myFixture.file) } }
        val matches = fixes.filter { it.text == hint }
        assertTrue("expected a quick-fix \"$hint\"; available: ${fixes.map { it.text }}", matches.isNotEmpty())
        myFixture.launchAction(matches.first())
    }
}
