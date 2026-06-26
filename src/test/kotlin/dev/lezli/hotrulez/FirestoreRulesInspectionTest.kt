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

    fun testNonV2RulesVersionIsWarning() {
        val warnings = warningsFor(
            """
            rules_version = '1';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "Expected 'rules_version = '2';'")
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
        assertEquals(2, warnings.size)
        assertContainsDescription(warnings, "Expected 'rules_version = '2';'")
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
        assertContainsDescription(warnings, "'exists()' takes exactly one argument but found 0")
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
        assertContainsDescription(warnings, "'exists()' takes exactly one argument but found 0")
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
                rules_version = '2';
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

    fun testRecursiveWildcardWithoutAnyVersionIsUsageWarned() {
        // No rules_version declaration uses v1 behavior, so the recursive-wildcard
        // usage warning should accompany the structure inspection's missing-version warning.
        val warnings = warningsFor(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{document=**} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(2, warnings.size)
        assertContainsDescription(warnings, "should be used with rules_version")
        assertContainsDescription(warnings, "Missing 'rules_version")
    }

    fun testBareCallToShadowingUserFunctionIsNotArityWarned() {
        // A user-defined function named `get` shadows the builtin path helper, so a
        // two-argument call to it must not be flagged as a path-helper arity error.
        assertEmpty(
            warningsFor(
                """
                rules_version = '2';
                service cloud.firestore {
                  match /databases/{database}/documents {
                    function get(a, b) { return a == b; }
                    match /cities/{city} {
                      allow read: if get(resource.data.x, resource.data.y);
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    fun testDefaultDatabaseRootMatchHasNoMissingRootWarning() {
        // `/databases/(default)/documents` is a valid root documents path, so the
        // structure inspection must not report a missing root match for it.
        assertEmpty(
            warningsFor(
                """
                rules_version = '2';
                service cloud.firestore {
                  match /databases/(default)/documents {
                    allow read: if true;
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    fun testNonDefaultParenDatabaseRootWarnsMissingRoot() {
        val warnings = warningsFor(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/(prod)/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "Missing root 'match /databases/{database}/documents'")
    }

    fun testNestedRootMatchStillWarnsMissingRoot() {
        // A `/databases/{database}/documents` match buried inside another match is not
        // the service block's top-level root, so the missing-root warning must fire.
        val warnings = warningsFor(
            """
            rules_version = '2';
            service cloud.firestore {
              match /stuff/{id} {
                match /databases/{database}/documents {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
        assertContainsDescription(warnings, "Missing root 'match /databases/{database}/documents'")
    }

    fun testMultipleRulesVersionAfterServiceWarnsOnce() {
        // Multiple stray rules_version lines after the service block collapse to a
        // single ordering warning rather than one warning per line.
        val warnings = warningsFor(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            rules_version = '2';
            rules_version = '2';
            """.trimIndent(),
        )
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "must be declared before the 'service' block")
    }

    fun testBareHelperMisuseNotSuppressedByUnrelatedSiblingFunction() {
        // A `function get` declared in a sibling match block must NOT suppress a genuine
        // bare-get arity warning in another block: shadow detection is lexically scoped,
        // not file-wide.
        val warnings = warningsFor(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /a/{id} {
                  function get(x, y) { return x == y; }
                  allow read: if true;
                }
                match /b/{id} {
                  allow read: if get(/databases/x, /databases/y);
                }
              }
            }
            """.trimIndent(),
        )
        assertContainsDescription(warnings, "'get()' takes exactly one argument but found 2")
    }

    fun testMissingServiceBlockIsWarning() {
        // The grammar accepts a blockless service (so it does not flash a parse error
        // while typing); the missing block surfaces as a configurable warning instead.
        val warnings = warningsFor("rules_version = '2';\nservice cloud.firestore")
        assertEquals(1, warnings.size)
        assertContainsDescription(warnings, "missing its rule block")
    }

    fun testNonV2RulesVersionAfterServiceWarnsValueAndPlacement() {
        // A non-'2' rules_version declared after the service has two independent,
        // actionable issues — wrong value and wrong placement — and is intentionally
        // flagged for both.
        val warnings = warningsFor(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            rules_version = '1';
            """.trimIndent(),
        )
        assertEquals(2, warnings.size)
        assertContainsDescription(warnings, "found version '1'")
        assertContainsDescription(warnings, "must be declared before the 'service' block")
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
