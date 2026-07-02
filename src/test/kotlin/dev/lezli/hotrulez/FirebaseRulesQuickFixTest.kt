package dev.lezli.hotrulez

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.diagnostics.FirebaseRulesStructureInspection
import dev.lezli.hotrulez.diagnostics.FirebaseRulesSymbolInspection
import dev.lezli.hotrulez.diagnostics.FirebaseRulesUsageInspection

/**
 * Applies each Milestone 0.7 quick-fix to a before-fixture and asserts the resulting text
 * (and, for placeholder fixes, the caret selection). Fixes come from three surfaces: the
 * structure/usage/symbol inspections (enabled below) and the always-on annotator.
 */
class FirebaseRulesQuickFixTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(
            FirebaseRulesStructureInspection(),
            FirebaseRulesUsageInspection(),
            FirebaseRulesSymbolInspection(),
        )
    }

    // --- Structure warnings ----------------------------------------------

    fun testInsertMissingRulesVersion() {
        applyFix(
            "Set rules_version = '2';",
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
    }

    fun testChangeNonV2RulesVersion() {
        applyFix(
            "Set rules_version = '2';",
            """
            rules_version = '1';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
    }

    fun testMoveRulesVersionToTop() {
        applyFix(
            "Move 'rules_version' before the 'service' block",
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            rules_version = '2';
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
    }

    fun testChangeUnknownServiceToFirestore() {
        applyFix(
            "Change service to 'cloud.firestore'",
            """
            rules_version = '2';
            service firebase.database {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
    }

    fun testChangeUnknownServiceToStorage() {
        applyFix(
            "Change service to 'firebase.storage'",
            """
            rules_version = '2';
            service firebase.database {
              match /b/{bucket}/o {
                allow read: if true;
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service firebase.storage {
              match /b/{bucket}/o {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
    }

    fun testInsertRootMatch() {
        applyFix(
            "Insert root 'match /databases/{database}/documents' block",
            """
            rules_version = '2';
            service cloud.firestore {
              match /stuff/{id} {
                allow read: if true;
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
              }

              match /stuff/{id} {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
    }

    fun testAddServiceBlock() {
        applyFix(
            "Add rule block",
            """
            rules_version = '2';
            service cloud.firestore
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
              }
            }
            """.trimIndent(),
        )
    }

    fun testInsertRootMatchStorage() {
        applyFix(
            "Insert root 'match /b/{bucket}/o' block",
            """
            rules_version = '2';
            service firebase.storage {
              match /images/{id} {
                allow read: if true;
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service firebase.storage {
              match /b/{bucket}/o {
              }

              match /images/{id} {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
    }

    // --- Annotator errors ------------------------------------------------

    fun testChangeUnknownOperation() {
        applyFix(
            "Change operation to 'read'",
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  allow raed: if true;
                }
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testUnknownOperationFarFromAnyKnownOffersNoFix() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  allow xyzzy: if true;
                }
              }
            }
            """.trimIndent(),
        )
        val fixes = myFixture.getAllQuickFixes()
        fixes.forEach { runCatching { it.isAvailable(myFixture.project, myFixture.editor, myFixture.file) } }
        assertFalse(
            "a token far from every operation must offer no 'Change operation' fix; got: ${fixes.map { it.text }}",
            fixes.any { it.text.startsWith("Change operation to") },
        )
    }

    fun testRemoveDuplicateParameter() {
        applyFix(
            "Remove duplicate parameter",
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function f(a, a) {
                  return a;
                }
                match /cities/{city} {
                  allow read: if f(true);
                }
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function f(a) {
                  return a;
                }
                match /cities/{city} {
                  allow read: if f(true);
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testAddReturnFalse() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function f() {
                }
                match /cities/{city} {
                  allow read: if f();
                }
              }
            }
            """.trimIndent(),
        )
        launchFix("Add 'return false;'")
        myFixture.checkResult(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function f() {
                  return false;
                }

                match /cities/{city} {
                  allow read: if f();
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals("false", myFixture.editor.selectionModel.selectedText)
    }

    fun testMoveRecursiveWildcardToLast() {
        applyFix(
            "Move recursive wildcard to the last segment",
            """
            rules_version = '1';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /{rest=**}/songs {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
            """
            rules_version = '1';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /songs/{rest=**} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
    }

    // --- Usage warnings --------------------------------------------------

    fun testAddIfConditionSelectsPlaceholder() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  allow read;
                }
              }
            }
            """.trimIndent(),
        )
        launchFix("Add ': if <condition>'")
        myFixture.checkResult(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  allow read: if false;
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals("false", myFixture.editor.selectionModel.selectedText)
    }

    fun testRecursiveWildcardOffersRulesVersionFix() {
        applyFix(
            "Set rules_version = '2';",
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{document=**} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{document=**} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
    }

    // --- Symbol inspection -----------------------------------------------

    fun testCreateFunctionForUnresolvedCall() {
        applyFix(
            "Create function 'isOwner'",
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  allow read: if isOwner(resource);
                }
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  function isOwner(arg1) {
                    return false;
                  }

                  allow read: if isOwner(resource);
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testRemoveUnusedFunction() {
        applyFix(
            "Remove function 'unused'",
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function unused() {
                  return true;
                }
                match /cities/{city} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testRemoveUnusedLet() {
        applyFix(
            "Remove 'let' binding",
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function f() {
                  let unused = 1;
                  return true;
                }
                match /cities/{city} {
                  allow read: if f();
                }
              }
            }
            """.trimIndent(),
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function f() {
                  return true;
                }
                match /cities/{city} {
                  allow read: if f();
                }
              }
            }
            """.trimIndent(),
        )
    }

    private fun applyFix(hint: String, before: String, after: String) {
        myFixture.configureByText(FirebaseRulesFileType, before)
        launchFix(hint)
        myFixture.checkResult(after)
    }

    /**
     * Applies the quick-fix whose text equals [hint]. Uses getAllQuickFixes (fixes from every
     * highlight in the file) rather than findSingleIntention (only fixes at the caret), since
     * these diagnostics are not at offset 0. ModCommand-backed intentions (the annotator's
     * `withFix(asIntention())`) compute their presentation lazily, so availability is queried
     * first to initialise getText(). When a shared fix is offered from two diagnostics (e.g.
     * the rules_version fix from both the structure and usage inspections) the duplicates are
     * identical, so the first is applied.
     */
    private fun launchFix(hint: String) {
        val fixes = myFixture.getAllQuickFixes()
        fixes.forEach { runCatching { it.isAvailable(myFixture.project, myFixture.editor, myFixture.file) } }
        val matches = fixes.filter { it.text == hint }
        assertTrue("expected a quick-fix \"$hint\"; available: ${fixes.map { it.text }}", matches.isNotEmpty())
        myFixture.launchAction(matches.first())
    }
}
