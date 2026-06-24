package dev.lezli.hotrulez.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/**
 * Exposes Firestore Rules highlight categories under
 * Settings | Editor | Color Scheme | Firestore Rules so users can customize them
 * and see a live preview. Tags in [DEMO_TEXT] map to the annotator-driven attributes
 * (service name, function declaration, path variable) via
 * [getAdditionalHighlightingTagToDescriptorMap].
 */
class FirestoreRulesColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "Firestore Rules"

    override fun getIcon(): Icon? = null

    override fun getHighlighter(): SyntaxHighlighter = FirestoreRulesSyntaxHighlighter()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

    override fun getDemoText(): String = DEMO_TEXT

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", FirestoreRulesHighlightingColors.KEYWORD),
            AttributesDescriptor("Allow operation", FirestoreRulesHighlightingColors.OPERATION),
            AttributesDescriptor("Boolean and null", FirestoreRulesHighlightingColors.CONSTANT),
            AttributesDescriptor("Built-in variable and helper", FirestoreRulesHighlightingColors.BUILTIN),
            AttributesDescriptor("Service name", FirestoreRulesHighlightingColors.SERVICE_NAME),
            AttributesDescriptor("Function declaration", FirestoreRulesHighlightingColors.FUNCTION_DECLARATION),
            AttributesDescriptor("Function call", FirestoreRulesHighlightingColors.FUNCTION_CALL),
            AttributesDescriptor("Path variable", FirestoreRulesHighlightingColors.PATH_VARIABLE),
            AttributesDescriptor("Recursive wildcard", FirestoreRulesHighlightingColors.RECURSIVE_WILDCARD),
            AttributesDescriptor("Path separator", FirestoreRulesHighlightingColors.PATH_SEPARATOR),
            AttributesDescriptor("Identifier", FirestoreRulesHighlightingColors.IDENTIFIER),
            AttributesDescriptor("String", FirestoreRulesHighlightingColors.STRING),
            AttributesDescriptor("Number", FirestoreRulesHighlightingColors.NUMBER),
            AttributesDescriptor("Line comment", FirestoreRulesHighlightingColors.LINE_COMMENT),
            AttributesDescriptor("Block comment", FirestoreRulesHighlightingColors.BLOCK_COMMENT),
            AttributesDescriptor("Operator", FirestoreRulesHighlightingColors.OPERATOR),
            AttributesDescriptor("Braces", FirestoreRulesHighlightingColors.BRACES),
            AttributesDescriptor("Parentheses", FirestoreRulesHighlightingColors.PARENTHESES),
            AttributesDescriptor("Brackets", FirestoreRulesHighlightingColors.BRACKETS),
            AttributesDescriptor("Comma", FirestoreRulesHighlightingColors.COMMA),
            AttributesDescriptor("Dot", FirestoreRulesHighlightingColors.DOT),
            AttributesDescriptor("Semicolon", FirestoreRulesHighlightingColors.SEMICOLON),
            AttributesDescriptor("Bad character", FirestoreRulesHighlightingColors.BAD_CHARACTER),
        )

        val TAGS = mapOf(
            "service" to FirestoreRulesHighlightingColors.SERVICE_NAME,
            "fdecl" to FirestoreRulesHighlightingColors.FUNCTION_DECLARATION,
            "pathvar" to FirestoreRulesHighlightingColors.PATH_VARIABLE,
        )

        val DEMO_TEXT =
            """
            rules_version = '2';
            service <service>cloud</service>.<service>firestore</service> {
              match /databases/{<pathvar>database</pathvar>}/documents {
                // Allow signed-in owners to read and write their own city
                function <fdecl>isOwner</fdecl>(uid) {
                  return request.auth != null && resource.data.owner == uid;
                }

                match /cities/{<pathvar>city</pathvar>} {
                  allow read, write: if isOwner(request.auth.uid);
                }

                match /scores/{<pathvar>score</pathvar>} {
                  /* numeric and membership conditions */
                  allow read: if resource.data.value >= 100 && resource.data.tier in ['gold', 'silver'];
                }

                match /logs/{<pathvar>document</pathvar>=**} {
                  allow read: if false;
                }
              }
            }
            """.trimIndent()
    }
}
