package dev.lezli.hotrulez

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.highlighting.FirebaseRulesHighlightingColors

/**
 * Covers always-on ERROR diagnostics produced by
 * [dev.lezli.hotrulez.diagnostics.FirebaseRulesAnnotator], plus annotator-driven
 * semantic highlighting that is not covered by the lexer highlighter tests.
 */
class FirebaseRulesAnnotatorTest : BasePlatformTestCase() {
    fun testHighlightsServiceFunctionAndPathVariables() {
        myFixture.configureByText(
            "rules.rules",
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function isOwner(uid) {
                  return resource.data.owner == uid;
                }

                match /cities/{city} {
                  allow read: if isOwner(request.auth.uid);
                }
              }
            }
            """.trimIndent(),
        )

        val forcedKeys: Set<TextAttributesKey> = myFixture.doHighlighting()
            .mapNotNull { it.forcedTextAttributesKey }
            .toSet()

        assertTrue(forcedKeys.contains(FirebaseRulesHighlightingColors.SERVICE_NAME))
        assertTrue(forcedKeys.contains(FirebaseRulesHighlightingColors.FUNCTION_DECLARATION))
        assertTrue(forcedKeys.contains(FirebaseRulesHighlightingColors.PATH_VARIABLE))
    }

    fun testHighlightsFunctionDeclarationNamedAfterBuiltin() {
        // The only function here is named `exists`, which the lexer classifies as a
        // BUILTIN; the declaration name must still be highlighted as a declaration.
        myFixture.configureByText(
            "rules.rules",
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                function exists(path) {
                  return true;
                }
              }
            }
            """.trimIndent(),
        )

        val forcedKeys: Set<TextAttributesKey> = myFixture.doHighlighting()
            .mapNotNull { it.forcedTextAttributesKey }
            .toSet()

        assertTrue(forcedKeys.contains(FirebaseRulesHighlightingColors.FUNCTION_DECLARATION))
    }

    fun testUnknownAllowOperationIsError() {
        val errors = errorsFor(
            inCity("allow read, fetch, write: if true;"),
        )
        assertEquals(1, errors.size)
        assertContainsDescription(errors, "Unknown Firestore Rules operation 'fetch'")
    }

    fun testKnownOperationsProduceNoError() {
        assertEmpty(errorsFor(inCity("allow get, list, read, create, update, delete, write: if true;")))
    }

    fun testAllowWithoutConditionIsNotError() {
        // A condition-less `allow` is legal Firestore syntax, so the annotator must
        // not raise an error for it.
        assertEmpty(errorsFor(inCity("allow read;")))
    }

    fun testAllowWithoutOperationsIsError() {
        assertContainsDescription(errorsFor(inCity("allow : if true;")), "requires at least one operation")
    }

    fun testDuplicateParameterNameIsError() {
        val errors = errorsFor("function ownsDoc(uid, doc, uid) { return true; }")
        assertEquals(1, errors.size)
        assertContainsDescription(errors, "Duplicate parameter name 'uid'")
    }

    fun testWellFormedFunctionProducesNoError() {
        // Distinct parameters and a body ending in `return <expr>;` must not trip
        // the duplicate-parameter, missing-return, or empty-return checks.
        assertEmpty(errorsFor("function ownsDoc(uid, doc) { return uid == doc; }"))
    }

    fun testFunctionWithoutReturnIsError() {
        val errors = errorsFor("function helper(x) { let y = x; }")
        assertEquals(1, errors.size)
        assertContainsDescription(errors, "Function 'helper' must end with a 'return' statement")
    }

    fun testReturnWithoutExpressionIsError() {
        val errors = errorsFor("function helper(x) { return; }")
        assertEquals(1, errors.size)
        assertContainsDescription(errors, "'return' requires an expression")
    }

    fun testRecursiveWildcardNonLastUnderV2IsNotError() {
        // In rules_version '2' a recursive wildcard may appear anywhere in the path.
        assertEmpty(
            errorsFor(
                """
                rules_version = '2';
                service cloud.firestore {
                  match /databases/{database}/documents {
                    match /{path=**}/songs/{song} {
                      allow read: if true;
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    fun testRecursiveWildcardNonLastUnderV1IsError() {
        val errors = errorsFor(
            """
            rules_version = '1';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /{path=**}/songs {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertContainsDescription(errors, "must be the last segment of a match path")
    }

    fun testRecursiveWildcardNonLastWithoutRulesVersionIsError() {
        val errors = errorsFor(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /{path=**}/songs {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertContainsDescription(errors, "must be the last segment of a match path")
    }

    fun testMultipleRecursiveWildcardsIsError() {
        val errors = errorsFor(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /a/{x=**}/b/{y=**} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertContainsDescription(errors, "at most one recursive wildcard")
    }

    fun testRecursiveWildcardAsLastSegmentIsNotError() {
        assertEmpty(
            errorsFor(
                """
                rules_version = '2';
                service cloud.firestore {
                  match /databases/{database}/documents {
                    match /cities/{city}/{document=**} {
                      allow read: if true;
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    fun testFunctionWithReturnNotLastIsError() {
        // The grammar permits statements after a return, so a body that contains a
        // return but does not *end* with one must still be flagged.
        val errors = errorsFor("function helper(x) { return x; let y = x; }")
        assertEquals(1, errors.size)
        assertContainsDescription(errors, "Function 'helper' must end with a 'return' statement")
    }

    fun testMultipleRecursiveWildcardsUnderV1DoNotStackErrors() {
        // Two recursive wildcards under v1: the extra is flagged "at most one" and the
        // first is flagged "must be last"; no single segment collects both messages.
        val errors = errorsFor(
            """
            rules_version = '1';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /a/{x=**}/{y=**}/b {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals(2, errors.size)
        assertContainsDescription(errors, "at most one recursive wildcard")
        assertContainsDescription(errors, "must be the last segment of a match path")
    }

    private fun errorsFor(text: String): List<HighlightInfo> {
        myFixture.configureByText(FirebaseRulesFileType, text)
        return myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    }

    private fun assertContainsDescription(infos: List<HighlightInfo>, fragment: String) {
        assertTrue(
            "expected a diagnostic containing \"$fragment\" but got: ${infos.map { it.description }}",
            infos.any { it.description?.contains(fragment) == true },
        )
    }

    /** Wraps an allow statement in a minimal, otherwise-valid v2 rules file. */
    private fun inCity(allowStatement: String): String =
        """
        rules_version = '2';
        service cloud.firestore {
          match /databases/{database}/documents {
            match /cities/{city} {
              $allowStatement
            }
          }
        }
        """.trimIndent()
}
