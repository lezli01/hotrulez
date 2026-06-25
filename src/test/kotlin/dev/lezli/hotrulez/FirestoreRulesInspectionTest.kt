package dev.lezli.hotrulez

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.diagnostics.FirestoreRulesStructureInspection
import dev.lezli.hotrulez.diagnostics.FirestoreRulesUsageInspection

/**
 * Covers the configurable WARNING diagnostics from the file-structure inspection
 * ([FirestoreRulesStructureInspection]) and the suspicious-usage inspection
 * ([FirestoreRulesUsageInspection]).
 */
class FirestoreRulesInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(FirestoreRulesStructureInspection(), FirestoreRulesUsageInspection())
    }

    // --- File structure --------------------------------------------------

    fun testMissingRulesVersionIsWarning() {
        val warnings = warningsFor(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "Missing 'rules_version = '2';'")
    }

    fun testRulesVersionAfterServiceIsWarning() {
        val warnings = warningsFor(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            rules_version = '2';
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "must be declared before the 'service' block")
    }

    fun testMissingServiceIsWarning() {
        val warnings = warningsFor("rules_version = '2';")
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "Missing 'service cloud.firestore")
    }

    fun testUnsupportedServiceNameIsWarning() {
        val warnings = warningsFor(
            """
            rules_version = '2';
            service firebase.storage {
              match /b/{id} {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "found 'service firebase.storage'")
    }

    fun testMissingRootMatchIsWarning() {
        val warnings = warningsFor(
            """
            rules_version = '2';
            service cloud.firestore {
              match /stuff/{id} {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "Missing root 'match /databases/{database}/documents'")
    }

    fun testWellFormedFileHasNoWarnings() {
        assertEmpty(
            warningsFor(
                """
                rules_version = '2';
                service cloud.firestore {
                  match /databases/{database}/documents {
                    match /cities/{city} {
                      allow read, write: if request.auth != null;
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    fun testEmptyFileHasNoWarnings() {
        assertEmpty(warningsFor("// just a comment\n"))
    }

    // --- Suspicious usage ------------------------------------------------

    fun testRecursiveWildcardWithoutV2IsWarning() {
        val warnings = warningsFor(
            """
            rules_version = '1';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{document=**} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "Recursive wildcard '{document=**}' should be used with rules_version = '2'")
    }

    fun testHelperCallWrongArityIsWarning() {
        val warnings = warningsFor(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} {
                  allow read: if exists();
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "'exists()' takes exactly one path argument but found 0")
    }

    fun testHelperCallCorrectArityHasNoWarning() {
        assertEmpty(
            warningsFor(
                """
                rules_version = '2';
                service cloud.firestore {
                  match /databases/{database}/documents {
                    match /cities/{city} {
                      allow read: if exists(/databases/$(database)/documents/cities/$(city));
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    fun testFirestoreNamespaceHelperWrongArityIsWarning() {
        val warnings = warningsFor(
            inCity("allow read: if firestore.exists();"),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "'exists()' takes exactly one path argument but found 0")
    }

    fun testMapGetWithDefaultHasNoWarning() {
        // resource.data.get('key', default) is the two-argument Firestore Map.get,
        // not the path helper get(), so it must not be flagged for arity.
        assertEmpty(warningsFor(inCity("allow read: if resource.data.get('owner', '') == request.auth.uid;")))
    }

    fun testRecursiveWildcardInPathArgumentHasNoWarning() {
        // A recursive wildcard inside a get()/exists() path argument is not a match
        // path, so the rules_version match-semantics warning must not fire there.
        assertEmpty(
            warningsFor(
                """
                rules_version = '1';
                service cloud.firestore {
                  match /databases/{database}/documents {
                    match /cities/{city} {
                      allow read: if exists(/databases/$(database)/documents/logs/{rest=**});
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    fun testUnconditionalAllowIsWarning() {
        val warnings = warningsFor(inCity("allow read;"))
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "has no 'if' condition")
    }

    fun testUnconditionalAllowWhileTypingIsNotWarned() {
        // An incomplete `allow read` (no terminator) is mid-edit: the parser already
        // reports it, so the usage inspection must stay quiet rather than pile on.
        assertEmpty(
            warningsFor(
                """
                rules_version = '2';
                service cloud.firestore {
                  match /databases/{database}/documents {
                    match /cities/{city} {
                      allow read
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    private fun warningsFor(text: String): List<HighlightInfo> {
        myFixture.configureByText(FirestoreRulesFileType, text)
        return myFixture.doHighlighting().filter { it.severity == HighlightSeverity.WARNING }
    }

    /** Wraps a statement in a minimal, otherwise-well-formed v2 rules file. */
    private fun inCity(statement: String): String =
        """
        rules_version = '2';
        service cloud.firestore {
          match /databases/{database}/documents {
            match /cities/{city} {
              $statement
            }
          }
        }
        """.trimIndent()

    private fun assertContainsDescription(infos: List<HighlightInfo>, fragment: String) {
        assertTrue(
            "expected a warning containing \"$fragment\" but got: ${infos.map { it.description }}",
            infos.any { it.description?.contains(fragment) == true },
        )
    }
}
