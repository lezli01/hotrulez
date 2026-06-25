package dev.lezli.hotrulez

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.psi.FirestoreRulesTypes as T

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

        assertNoErrors(file)
        val root = file.node
        assertNotNull(root.findDescendant(T.RULES_VERSION_STATEMENT))
        assertNotNull(root.findDescendant(T.SERVICE_DECLARATION))
        assertNotNull(root.findDescendant(T.MATCH_DECLARATION))
        assertNotNull(root.findDescendant(T.ALLOW_STATEMENT))
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

        assertNoErrors(file)
        assertEquals(2, file.node.countDescendants(T.MATCH_DECLARATION))
        assertNotNull(file.node.findDescendant(T.RECURSIVE_WILDCARD))
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

        assertNoErrors(file)
        val methodList = file.node.findDescendant(T.METHOD_LIST)
        assertNotNull(methodList)
        assertEquals(7, methodList!!.countDescendants(T.IDENTIFIER))
    }

    fun testAllowWithoutConditionIsAccepted() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read;
              }
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
        assertNotNull(file.node.findDescendant(T.ALLOW_STATEMENT))
    }

    fun testParsesFunctionWithHelperCallAndPathInterpolation() {
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

        assertNoErrors(file)
        assertNotNull(file.node.findDescendant(T.FUNCTION_DECLARATION))
        assertNotNull(file.node.findDescendant(T.PARAMETER_LIST))
        assertNotNull(file.node.findDescendant(T.RETURN_STATEMENT))
        assertNotNull(file.node.findDescendant(T.CALL_EXPRESSION))
        assertNotNull(file.node.findDescendant(T.PATH_ARGUMENT))
        assertEquals(2, file.node.countDescendants(T.PATH_INTERPOLATION))
    }

    fun testParsesLetBindingsInFunction() {
        val file = parse(
            """
            function isOwner(uid) {
              let signedIn = request.auth != null;
              let owner = request.auth.uid == uid;
              return signedIn && owner;
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
        assertEquals(2, file.node.countDescendants(T.LET_STATEMENT))
    }

    fun testOperatorPrecedenceBuildsExpectedTree() {
        // `a || b && c` must parse as `a || (b && c)`: the top operator is ||.
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if a || b && c;
              }
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
        val or = file.node.findDescendant(T.OR_EXPRESSION)
        assertNotNull("expected a top-level || expression", or)
        // The && must be nested *inside* the ||, not the other way around.
        assertNotNull(or!!.findDescendant(T.AND_EXPRESSION))
        assertNull(
            "&& should bind tighter than ||",
            file.node.findDescendant(T.AND_EXPRESSION)?.findDescendant(T.OR_EXPRESSION),
        )
    }

    fun testParsesRichExpressionConstructs() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /c/{id} {
                  allow read: if
                    (1 + 2 * 3 - 4 % 2) >= 3 &&
                    'admin' in ['editor', 'admin'] &&
                    request.auth.uid is string &&
                    resource.data.tags[0:2].size() == 2 &&
                    {role: 'admin', "ok": true}['role'] == 'admin' &&
                    (request.auth == null ? false : true);
                }
              }
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
        assertNotNull(file.node.findDescendant(T.IN_EXPRESSION))
        assertNotNull(file.node.findDescendant(T.IS_EXPRESSION))
        assertNotNull(file.node.findDescendant(T.TERNARY_EXPRESSION))
        assertNotNull(file.node.findDescendant(T.LIST_LITERAL))
        assertNotNull(file.node.findDescendant(T.MAP_LITERAL))
        assertNotNull(file.node.findDescendant(T.INDEX_EXPRESSION))
        assertNotNull(file.node.findDescendant(T.SLICE))
    }

    fun testParsesKitchenSinkOfBuiltinsAndPathCalls() {
        // Exercises builtin namespaces, deep method chains, cross-service calls,
        // the (default) path literal, slicing and trailing commas in one file.
        val file = parse(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /m/{id} {
                  allow write: if
                    math.pow(2, 3) == 8 && !math.isNaN(resource.data.v) &&
                    duration.value(1, 'h').seconds() == 3600 &&
                    timestamp.date(2024, 1, 1).year() == 2024 &&
                    request.time.toMillis() > 0 &&
                    request.resource.data.diff(resource.data).addedKeys().hasOnly(['ts',]) &&
                    get(/databases/$(database)/documents/users/$(request.auth.uid)).data['role'] == 'admin' &&
                    firestore.exists(/databases/(default)/documents/users/$(request.auth.uid)) &&
                    resource.data.items[0:2].size() <= request.resource.data.tags[1:].size();
                }
              }
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
        assertNotNull(file.node.findDescendant(T.PAREN_PATH_SEGMENT))
        assertNotNull(file.node.findDescendant(T.SLICE))
        assertTrue(file.node.countDescendants(T.CALL_EXPRESSION) > 5)
    }

    fun testEqualityAndRelationalShareOnePrecedenceLevel() {
        // CEL semantics: `a == b < c` is left-associative same-level => `(a == b) < c`.
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /c/{id} { allow read: if a == b < c; }
              }
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
        val lt = file.node.findDescendant(T.LT_EXPRESSION)
        assertNotNull("expected `<` to be the outer operator", lt)
        assertNotNull("`==` must be nested inside `<`", lt!!.findDescendant(T.EQ_EXPRESSION))
        assertNull(
            "`<` must not be nested inside `==`",
            file.node.findDescendant(T.EQ_EXPRESSION)!!.findDescendant(T.LT_EXPRESSION),
        )
    }

    fun testParsesDefaultDatabaseInMatchPath() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/(default)/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
        assertNotNull(file.node.findDescendant(T.PAREN_PATH_SEGMENT))
    }

    fun testParsesLiteralPathSegmentsBeyondIdentifiers() {
        val file = parse(
            """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /match/123/user-profiles/{id} {
                  allow read: if true;
                }
              }
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
        assertEquals(2, file.node.countDescendants(T.MATCH_DECLARATION))
        assertEquals(5, file.node.countDescendants(T.PATH_NAME_SEGMENT))
    }

    fun testParsesScientificAndLeadingDotNumbers() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /c/{id} {
                  allow read: if resource.data.score > 1e9 && resource.data.rate >= .5 && x == 1.5e-3;
                }
              }
            }
            """.trimIndent(),
        )

        assertNoErrors(file)
    }

    fun testUnterminatedStringDoesNotSwallowFollowingStatements() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                match /a/{id} { allow read: if x == 'oops; }
                match /b/{id} { allow read: if true; }
              }
            }
            """.trimIndent(),
        )

        // The stray quote must not consume the second match block.
        assertEquals(3, file.node.countDescendants(T.MATCH_DECLARATION))
    }

    fun testRecoversAfterMalformedFunction() {
        val file = parse(
            """
            service cloud.firestore {
              match /databases/{database}/documents {
                function broken( { return true; }
                match /b/{id} { allow read: if true; }
              }
            }
            """.trimIndent(),
        )

        // A malformed function must not prevent the following match from parsing.
        assertNotNull(file.node.findDescendant(T.MATCH_DECLARATION))
        assertEquals(2, file.node.countDescendants(T.MATCH_DECLARATION))
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

        assertNotNull(file.node.findDescendant(T.FUNCTION_DECLARATION))
        assertEquals(2, file.node.countDescendants(T.MATCH_DECLARATION))
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

        assertNotNull(file.node.findDescendant(T.SERVICE_DECLARATION))
        assertNotNull(file.node.findDescendant(T.MATCH_DECLARATION))
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

        // The malformed `allow read if true;` must not prevent the rest from parsing.
        assertEquals(2, file.node.countDescendants(T.MATCH_DECLARATION))
        assertEquals(2, file.node.countDescendants(T.ALLOW_STATEMENT))
    }

    fun testRecoversFromUnknownItemInsideBlock() {
        val file = parse(
            """
            service cloud.firestore {
              oops;
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )

        // A stray block item must not eject the following match from the service block.
        val service = file.node.findDescendant(T.SERVICE_DECLARATION)
        assertNotNull(service)
        assertEquals(1, service!!.countDescendants(T.MATCH_DECLARATION))
        assertEquals(1, service.countDescendants(T.ALLOW_STATEMENT))
    }

    fun testRecoversFromJunkBeforeFirstDeclaration() {
        val file = parse(
            """
            oops
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                allow read: if true;
              }
            }
            """.trimIndent(),
        )

        // A stray leading token must not collapse the rest of the file into one error
        // region: the declarations after it still parse.
        assertEquals(1, file.node.countDescendants(T.RULES_VERSION_STATEMENT))
        assertEquals(1, file.node.countDescendants(T.SERVICE_DECLARATION))
        assertNotNull(file.node.findDescendant(T.MATCH_DECLARATION))
    }

    fun testRecoversFromMalformedStatementInsideFunctionBody() {
        val file = parse(
            """
            function f() {
              let x;
              return true;
            }
            """.trimIndent(),
        )

        // The malformed `let x;` must not swallow the following return: per-statement
        // recovery keeps `return true;` as a parsed return statement.
        assertNotNull(file.node.findDescendant(T.RETURN_STATEMENT))
    }

    private fun parse(text: String): PsiFile =
        myFixture.configureByText(FirestoreRulesFileType, text)

    private fun assertNoErrors(file: PsiFile) {
        assertFalse(
            "unexpected syntax errors: " +
                PsiTreeUtil.collectElements(file) { it is com.intellij.psi.PsiErrorElement }
                    .joinToString { it.text },
            PsiTreeUtil.hasErrorElements(file),
        )
    }

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
        var total = if (elementType == type) 1 else 0
        var child = firstChildNode
        while (child != null) {
            total += child.countDescendants(type)
            child = child.treeNext
        }
        return total
    }
}
