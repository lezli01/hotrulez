package dev.lezli.hotrulez

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.psi.FirebaseRulesMatchDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesPathWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesRecursiveWildcard
import dev.lezli.hotrulez.refactoring.FirebaseRulesNamesValidator

/**
 * Rename refactoring across the four symbol kinds, including path-variable
 * shadowing (rename touches only the correct match subtree) and reserved-keyword
 * rejection via the names validator.
 */
class FirebaseRulesRenameTest : BasePlatformTestCase() {

    fun testRenameFunctionUpdatesAllCallSites() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function isOw<caret>ner(uid) { return uid == 'x'; }
                match /cities/{city} {
                  allow read: if isOwner(city);
                  allow write: if isOwner(city);
                }
              }
            }
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("owns")
        myFixture.checkResult(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function owns(uid) { return uid == 'x'; }
                match /cities/{city} {
                  allow read: if owns(city);
                  allow write: if owns(city);
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testRenameParameterUpdatesUsesButNotSameNamedMember() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function isOwner(u<caret>id) { return uid == request.auth.uid; }
                match /cities/{city} { allow read: if isOwner(city); }
              }
            }
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("user")
        // The `uid` reference becomes `user`; `request.auth.uid` (a member) is untouched.
        myFixture.checkResult(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function isOwner(user) { return user == request.auth.uid; }
                match /cities/{city} { allow read: if isOwner(city); }
              }
            }
            """.trimIndent(),
        )
    }

    fun testRenameLetUpdatesBindingAndInBodyUses() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function isSignedIn() {
                  let signe<caret>dIn = request.auth != null;
                  return signedIn;
                }
              }
            }
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("loggedIn")
        // The binding and its later in-body use both change.
        myFixture.checkResult(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function isSignedIn() {
                  let loggedIn = request.auth != null;
                  return loggedIn;
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testRenamePathVariableRespectsShadowBoundary() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /a/{city} {
                  allow read: if city == 'outer';
                  match /b/{city} {
                    allow read: if city == 'inner';
                  }
                }
              }
            }
            """.trimIndent(),
        )
        myFixture.renameElement(pathWildcardIn("/a/"), "town")
        // Only the outer binding and its use change; the shadowing inner {city} is untouched.
        myFixture.checkResult(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /a/{town} {
                  allow read: if town == 'outer';
                  match /b/{city} {
                    allow read: if city == 'inner';
                  }
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testRenameRecursiveWildcardPreservesRecursiveForm() {
        myFixture.configureByText(
            FirebaseRulesFileType,
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /logs/{entry=**} {
                  allow read: if entry != null;
                }
              }
            }
            """.trimIndent(),
        )
        val wildcard = PsiTreeUtil.findChildrenOfType(myFixture.file, FirebaseRulesRecursiveWildcard::class.java).first()
        myFixture.renameElement(wildcard, "trail")
        // The binding keeps its `=**` recursive form and the use updates.
        myFixture.checkResult(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /logs/{trail=**} {
                  allow read: if trail != null;
                }
              }
            }
            """.trimIndent(),
        )
    }

    fun testNamesValidatorRejectsReservedKeywords() {
        val validator = FirebaseRulesNamesValidator()
        assertTrue(validator.isKeyword("match", project))
        assertFalse("a reserved keyword is not a legal rename target", validator.isIdentifier("match", project))
        assertTrue(validator.isIdentifier("city2", project))
        assertFalse(validator.isIdentifier("2city", project))
        assertFalse(validator.isIdentifier("has space", project))
    }

    private fun pathWildcardIn(matchPathFragment: String): FirebaseRulesPathWildcard =
        PsiTreeUtil.findChildrenOfType(myFixture.file, FirebaseRulesPathWildcard::class.java).first { wildcard ->
            PsiTreeUtil.getParentOfType(wildcard, FirebaseRulesMatchDeclaration::class.java)
                ?.matchPath?.text?.contains(matchPathFragment) == true
        }
}
