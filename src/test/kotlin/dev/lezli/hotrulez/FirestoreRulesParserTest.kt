package dev.lezli.hotrulez

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.parser.FirestoreRulesElementTypes

class FirestoreRulesParserTest : BasePlatformTestCase() {
    fun testParsesMinimalValidFile() {
        val file = parse(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )

        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.RULES_VERSION_DECLARATION))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.SERVICE_DECLARATION))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.MATCH_BLOCK))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.ALLOW_STATEMENT))
    }

    fun testParsesNestedMatchAndRecursiveWildcard() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /cities/{city}/{document=**} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(2, file.countDescendants(FirestoreRulesElementTypes.MATCH_BLOCK))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.RECURSIVE_WILDCARD))
    }

    fun testParsesEverySupportedAllowOperation() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow get, list, read, create, update, delete, write: if true;
              }
            }
            """.trimIndent(),
        )

        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.OPERATION_LIST))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.EXPRESSION))
    }

    fun testParsesFunctionAndHelperCallsAsRecoverableExpressions() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                function ownsCity(uid, city) {
                  return exists(/databases/$(database)/documents/cities/$(city));
                }
              }
            }
            """.trimIndent(),
        )

        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.FUNCTION_DECLARATION))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.PARAMETER_LIST))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.RETURN_STATEMENT))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.EXPRESSION))
    }

    fun testParsesMapLiteralInExpressionWithoutBraceStealing() {
        // A map literal's inner `}` must not be mistaken for the block close; the
        // following match block must still parse (and parsing must terminate).
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                function hasFields() {
                  return { 'a': 1, 'b': 2 }.keys().hasOnly(['a', 'b']);
                }
                match /items/{id} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )

        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.FUNCTION_DECLARATION))
        assertEquals(2, file.countDescendants(FirestoreRulesElementTypes.MATCH_BLOCK))
    }

    fun testRecoversFromStrayClosingBrace() {
        // An unbalanced top-level `}` must not send the parser into an infinite loop.
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            }
            """.trimIndent(),
        )

        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.SERVICE_DECLARATION))
        assertNotNull(file.findDescendant(FirestoreRulesElementTypes.MATCH_BLOCK))
    }

    fun testRecoversAfterMalformedAllowStatement() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read if true;
                match /cities/{city} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(2, file.countDescendants(FirestoreRulesElementTypes.MATCH_BLOCK))
        assertEquals(2, file.countDescendants(FirestoreRulesElementTypes.ALLOW_STATEMENT))
    }

    private fun parse(text: String): ASTNode =
        myFixture.configureByText(FirestoreRulesFileType, text).node

    private fun ASTNode.findDescendant(type: IElementType): ASTNode? {
        if (elementType == type) {
            return this
        }

        var child = firstChildNode
        while (child != null) {
            val match = child.findDescendant(type)
            if (match != null) {
                return match
            }
            child = child.treeNext
        }
        return null
    }

    private fun ASTNode.countDescendants(type: IElementType): Int {
        val self = if (elementType == type) 1 else 0
        var total = self
        var child = firstChildNode
        while (child != null) {
            total += child.countDescendants(type)
            child = child.treeNext
        }
        return total
    }
}
