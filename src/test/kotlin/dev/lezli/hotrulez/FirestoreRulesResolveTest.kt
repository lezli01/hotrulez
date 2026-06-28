package dev.lezli.hotrulez

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.psi.FirestoreRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesLetStatement
import dev.lezli.hotrulez.psi.FirestoreRulesMatchDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesParameter
import dev.lezli.hotrulez.psi.FirestoreRulesPathWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesRecursiveWildcard
import dev.lezli.hotrulez.references.FirestoreRulesBuiltins

/**
 * Covers the PSI reference/resolve layer: a use of a name resolving (or
 * deliberately not resolving) to its declaration, with Firestore scoping and
 * path-variable shadowing. Go-to-Declaration rides directly on this.
 */
class FirestoreRulesResolveTest : BasePlatformTestCase() {

    // --- Functions -------------------------------------------------------

    fun testCallResolvesToFunctionDeclaration() {
        val target = resolveAtCaret(
            inDocuments(
                """
                function isSignedIn() { return request.auth != null; }
                match /cities/{city} {
                  allow read: if isSign${CARET}edIn();
                }
                """,
            ),
        )
        assertFunctionNamed(target, "isSignedIn")
    }

    fun testForwardCallResolves() {
        // The call appears before the declaration; resolution is scope-based, not order-based.
        val target = resolveAtCaret(
            inDocuments(
                """
                match /cities/{city} {
                  allow read: if isSign${CARET}edIn();
                }
                function isSignedIn() { return request.auth != null; }
                """,
            ),
        )
        assertFunctionNamed(target, "isSignedIn")
    }

    fun testCallResolvesToEnclosingScopeFunction() {
        val target = resolveAtCaret(
            inDocuments(
                """
                function isSignedIn() { return request.auth != null; }
                match /cities/{city} {
                  match /landmarks/{landmark} {
                    allow read: if isSign${CARET}edIn();
                  }
                }
                """,
            ),
        )
        assertFunctionNamed(target, "isSignedIn")
    }

    fun testCallToFunctionInSiblingScopeDoesNotResolve() {
        val target = resolveAtCaret(
            inDocuments(
                """
                match /a/{id} {
                  function helper() { return true; }
                }
                match /b/{id} {
                  allow read: if help${CARET}er();
                }
                """,
            ),
        )
        assertNull("a function in a sibling match must not be visible", target)
    }

    // --- Parameters and let bindings -------------------------------------

    fun testParameterUseResolvesToParameter() {
        val target = resolveAtCaret(
            inDocuments(
                """
                function isOwner(uid) { return resource.data.owner == u${CARET}id; }
                match /cities/{city} { allow read: if isOwner(request.auth.uid); }
                """,
            ),
        )
        assertTrue("expected a parameter, got $target", target is FirestoreRulesParameter)
        assertEquals("uid", (target as FirestoreRulesParameter).name)
    }

    fun testLetUseResolvesToBinding() {
        val target = resolveAtCaret(
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
        assertTrue("expected a let binding, got $target", target is FirestoreRulesLetStatement)
        assertEquals("signedIn", (target as FirestoreRulesLetStatement).name)
    }

    fun testLetNotVisibleBeforeDeclaration() {
        val target = resolveAtCaret(
            inDocuments(
                """
                function check() {
                  let a = lat${CARET}er + 1;
                  let later = 2;
                  return a;
                }
                match /cities/{city} { allow read: if check(); }
                """,
            ),
        )
        assertNull("a let is not visible before its own declaration", target)
    }

    // --- Path / wildcard variables ---------------------------------------

    fun testPathVariableUseResolvesToWildcard() {
        val target = resolveAtCaret(
            inDocuments(
                """
                match /cities/{city} {
                  allow read: if ci${CARET}ty == 'paris';
                }
                """,
            ),
        )
        assertTrue("expected a path wildcard, got $target", target is FirestoreRulesPathWildcard)
        assertEquals("city", (target as FirestoreRulesPathWildcard).name)
    }

    fun testRecursiveWildcardUseResolves() {
        val target = resolveAtCaret(
            inDocuments(
                """
                match /logs/{entry=**} {
                  allow read: if ent${CARET}ry.size() > 0;
                }
                """,
            ),
        )
        assertTrue("expected a recursive wildcard, got $target", target is FirestoreRulesRecursiveWildcard)
        assertEquals("entry", (target as FirestoreRulesRecursiveWildcard).name)
    }

    fun testPathVariableUseResolvesToOuterMatchBinding() {
        val target = resolveAtCaret(
            inDocuments(
                """
                match /cities/{city} {
                  match /landmarks/{landmark} {
                    allow read: if ci${CARET}ty == 'paris';
                  }
                }
                """,
            ),
        )
        assertTrue(target is FirestoreRulesPathWildcard)
        assertEquals("city", (target as FirestoreRulesPathWildcard).name)
    }

    // --- Scoping negatives -----------------------------------------------

    fun testServiceScopeFunctionDoesNotSeeMatchPathVariable() {
        val target = resolveAtCaret(
            """
            rules_version = '2';
            service cloud.firestore {
              function near() { return ci${CARET}ty == 'paris'; }
              match /databases/{database}/documents {
                match /cities/{city} { allow read: if near(); }
              }
            }
            """.trimIndent(),
        )
        assertNull("a service-scope function cannot see a match-local path variable", target)
    }

    // --- Shadowing -------------------------------------------------------

    fun testNestedShadowingResolvesToNearestBinding() {
        val text = inDocuments(
            """
            match /a/{city} {
              match /b/{city} {
                allow read: if ci${CARET}ty == 'inner';
              }
            }
            """,
        )
        val target = resolveAtCaret(text)
        assertTrue(target is FirestoreRulesPathWildcard)
        val match = PsiTreeUtil.getParentOfType(target, FirestoreRulesMatchDeclaration::class.java)
        assertTrue(
            "inner use must resolve to the nearest (/b) binding, got ${match?.matchPath?.text}",
            match?.matchPath?.text?.contains("/b/") == true,
        )
    }

    fun testOuterUseResolvesToOuterBindingAcrossShadow() {
        val text = inDocuments(
            """
            match /a/{city} {
              allow read: if ci${CARET}ty == 'outer';
              match /b/{city} {
                allow read: if city == 'inner';
              }
            }
            """,
        )
        val target = resolveAtCaret(text)
        val match = PsiTreeUtil.getParentOfType(target, FirestoreRulesMatchDeclaration::class.java)
        assertTrue(
            "outer use must resolve to the /a binding, got ${match?.matchPath?.text}",
            match?.matchPath?.text?.contains("/a/") == true,
        )
    }

    // --- Built-ins and helpers are recognised but non-navigable ----------

    fun testBuiltinRequestIsNonNavigable() {
        val target = resolveAtCaret(
            inDocuments("match /cities/{city} { allow read: if req${CARET}uest.auth != null; }"),
        )
        assertNull("built-in 'request' has no declaration to navigate to", target)
    }

    fun testHelperCallIsNonNavigable() {
        val target = resolveAtCaret(
            inDocuments(
                """
                match /cities/{city} {
                  allow read: if exi${CARET}sts(/databases/x/documents/cities/paris);
                }
                """,
            ),
        )
        assertNull("the built-in 'exists' helper has no declaration to navigate to", target)
    }

    // --- Recovery --------------------------------------------------------

    fun testResolveDegradesGracefullyInMalformedFile() {
        // A broken block must not stop an unrelated, well-formed call from resolving.
        val target = resolveAtCaret(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /broken/ { allow read: if ;;; }
                function ok() { return true; }
                match /cities/{city} { allow read: if o${CARET}k(); }
              }
            }
            """.trimIndent(),
        )
        assertFunctionNamed(target, "ok")
    }

    fun testCallResolvesToTopLevelFunction() {
        // A function declared at file top level (sibling of `service`) is in scope file-wide.
        val target = resolveAtCaret(
            """
            rules_version = '2';
            function isSignedIn() { return request.auth != null; }
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city} { allow read: if isSign${CARET}edIn(); }
              }
            }
            """.trimIndent(),
        )
        assertFunctionNamed(target, "isSignedIn")
    }

    fun testInnerFunctionShadowsOuterSameName() {
        val target = resolveAtCaret(
            inDocuments(
                """
                function check() { return true; }
                match /a/{id} {
                  function check() { return false; }
                  allow read: if che${CARET}ck();
                }
                """,
            ),
        )
        assertTrue("expected a single function declaration, got $target", target is FirestoreRulesFunctionDeclaration)
        val match = PsiTreeUtil.getParentOfType(target, FirestoreRulesMatchDeclaration::class.java)
        assertTrue(
            "the call must resolve to the nearest (inner /a) check(), got ${match?.matchPath?.text}",
            match?.matchPath?.text?.contains("/a/") == true,
        )
    }

    fun testParameterNotVisibleInSiblingFunction() {
        val target = resolveAtCaret(
            inDocuments(
                """
                function a(secret) { return secret != null; }
                function b() { return sec${CARET}ret; }
                match /cities/{city} { allow read: if a(city) && b(); }
                """,
            ),
        )
        assertNull("a parameter declared in a sibling function is not visible", target)
    }

    fun testBuiltinNamesAreRecognized() {
        // The resolver recognises built-ins/helpers (so a future undefined-reference check
        // can skip them) even though they are non-navigable.
        assertTrue(FirestoreRulesBuiltins.isBuiltinName("request"))
        assertTrue(FirestoreRulesBuiltins.isBuiltinName("resource"))
        assertTrue(FirestoreRulesBuiltins.isBuiltinName("exists"))
        assertTrue(FirestoreRulesBuiltins.isBuiltinName("getAfter"))
        assertFalse(FirestoreRulesBuiltins.isBuiltinName("isOwner"))
    }

    // --- Helpers ---------------------------------------------------------

    private fun resolveAtCaret(text: String): PsiElement? {
        myFixture.configureByText(FirestoreRulesFileType, text)
        return myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
    }

    private fun assertFunctionNamed(target: PsiElement?, name: String) {
        assertTrue("expected a function declaration, got $target", target is FirestoreRulesFunctionDeclaration)
        assertEquals(name, (target as FirestoreRulesFunctionDeclaration).name)
    }

    /** Wraps body inside the conventional v2 service + root documents match. */
    private fun inDocuments(body: String): String =
        "rules_version = '2';\n" +
            "service cloud.firestore {\n" +
            "  match /databases/{database}/documents {\n" +
            body.trimIndent() + "\n" +
            "  }\n" +
            "}\n"

    private companion object {
        const val CARET = "<caret>"
    }
}
