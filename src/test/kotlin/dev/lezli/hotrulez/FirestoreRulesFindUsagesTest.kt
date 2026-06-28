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

/**
 * Find Usages for each symbol kind, including the path-variable shadowing
 * boundary. Counts are the number of resolving *uses* (the declaration itself is
 * not a usage).
 */
class FirestoreRulesFindUsagesTest : BasePlatformTestCase() {

    fun testFunctionUsages() {
        configure(
            inDocuments(
                """
                function isSignedIn() { return request.auth != null; }
                match /a/{id} { allow read: if isSignedIn(); }
                match /b/{id} { allow write: if isSignedIn(); }
                """,
            ),
        )
        val function = first<FirestoreRulesFunctionDeclaration> { it.name == "isSignedIn" }
        assertEquals(2, myFixture.findUsages(function).size)
    }

    fun testParameterUsages() {
        configure(
            inDocuments(
                """
                function isOwner(uid) { return uid == 'a' || uid == 'b'; }
                match /cities/{city} { allow read: if isOwner(city); }
                """,
            ),
        )
        val parameter = first<FirestoreRulesParameter> { it.name == "uid" }
        assertEquals(2, myFixture.findUsages(parameter).size)
    }

    fun testLetUsages() {
        configure(
            inDocuments(
                """
                function check() {
                  let ok = request.auth != null;
                  return ok && ok;
                }
                match /cities/{city} { allow read: if check(); }
                """,
            ),
        )
        val binding = first<FirestoreRulesLetStatement> { it.name == "ok" }
        assertEquals(2, myFixture.findUsages(binding).size)
    }

    fun testPathVariableUsages() {
        configure(
            inDocuments(
                """
                match /cities/{city} {
                  allow read: if city == 'paris';
                  allow write: if city != 'london';
                }
                """,
            ),
        )
        val wildcard = first<FirestoreRulesPathWildcard> { it.name == "city" }
        assertEquals(2, myFixture.findUsages(wildcard).size)
    }

    fun testPathVariableUsagesStopAtShadowBoundary() {
        configure(
            inDocuments(
                """
                match /a/{city} {
                  allow read: if city == 'outer';
                  match /b/{city} {
                    allow read: if city == 'inner';
                    allow write: if city == 'inner2';
                  }
                }
                """,
            ),
        )
        // The outer binding has exactly one use (the inner subtree shadows it).
        assertEquals(1, myFixture.findUsages(pathWildcardIn("/a/")).size)
        // The inner binding owns its two uses.
        assertEquals(2, myFixture.findUsages(pathWildcardIn("/b/")).size)
    }

    fun testRecursiveWildcardUsages() {
        configure(
            inDocuments(
                """
                match /logs/{entry=**} {
                  allow read: if entry != null;
                  allow write: if entry.size() > 0;
                }
                """,
            ),
        )
        val wildcard = first<FirestoreRulesRecursiveWildcard> { it.name == "entry" }
        assertEquals(2, myFixture.findUsages(wildcard).size)
    }

    // --- Helpers ---------------------------------------------------------

    private fun configure(text: String) = myFixture.configureByText(FirestoreRulesFileType, text)

    private inline fun <reified T : PsiElement> first(predicate: (T) -> Boolean): T =
        PsiTreeUtil.findChildrenOfType(myFixture.file, T::class.java).first(predicate)

    private fun pathWildcardIn(matchPathFragment: String): FirestoreRulesPathWildcard =
        first { wildcard ->
            PsiTreeUtil.getParentOfType(wildcard, FirestoreRulesMatchDeclaration::class.java)
                ?.matchPath?.text?.contains(matchPathFragment) == true
        }

    private fun inDocuments(body: String): String =
        "rules_version = '2';\n" +
            "service cloud.firestore {\n" +
            "  match /databases/{database}/documents {\n" +
            body.trimIndent() + "\n" +
            "  }\n" +
            "}\n"
}
