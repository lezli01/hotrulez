package dev.lezli.hotrulez

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.highlighting.FirestoreRulesHighlightingColors

class FirestoreRulesAnnotatorTest : BasePlatformTestCase() {
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

        assertTrue(forcedKeys.contains(FirestoreRulesHighlightingColors.SERVICE_NAME))
        assertTrue(forcedKeys.contains(FirestoreRulesHighlightingColors.FUNCTION_DECLARATION))
        assertTrue(forcedKeys.contains(FirestoreRulesHighlightingColors.PATH_VARIABLE))
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

        assertTrue(forcedKeys.contains(FirestoreRulesHighlightingColors.FUNCTION_DECLARATION))
    }
}
