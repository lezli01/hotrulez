package dev.lezli.hotrulez.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/**
 * Exposes Firebase Rules highlight categories under
 * Settings | Editor | Color Scheme | Firebase Rules so users can customize them
 * and see a live preview. Tags in [DEMO_TEXT] map to the annotator-driven attributes
 * (service name, function declaration, path variable) via
 * [getAdditionalHighlightingTagToDescriptorMap].
 */
class FirebaseRulesColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "Firebase Rules"

    override fun getIcon(): Icon? = null

    override fun getHighlighter(): SyntaxHighlighter = FirebaseRulesSyntaxHighlighter()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

    override fun getDemoText(): String = DEMO_TEXT

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", FirebaseRulesHighlightingColors.KEYWORD),
            AttributesDescriptor("Allow operation", FirebaseRulesHighlightingColors.OPERATION),
            AttributesDescriptor("Boolean and null", FirebaseRulesHighlightingColors.CONSTANT),
            AttributesDescriptor("Built-in variable and helper", FirebaseRulesHighlightingColors.BUILTIN),
            AttributesDescriptor("Type and built-in namespace", FirebaseRulesHighlightingColors.TYPE),
            AttributesDescriptor("Service name", FirebaseRulesHighlightingColors.SERVICE_NAME),
            AttributesDescriptor("Function declaration", FirebaseRulesHighlightingColors.FUNCTION_DECLARATION),
            AttributesDescriptor("Function call", FirebaseRulesHighlightingColors.FUNCTION_CALL),
            AttributesDescriptor("Path variable", FirebaseRulesHighlightingColors.PATH_VARIABLE),
            AttributesDescriptor("Recursive wildcard", FirebaseRulesHighlightingColors.RECURSIVE_WILDCARD),
            AttributesDescriptor("Path separator", FirebaseRulesHighlightingColors.PATH_SEPARATOR),
            AttributesDescriptor("Identifier", FirebaseRulesHighlightingColors.IDENTIFIER),
            AttributesDescriptor("String", FirebaseRulesHighlightingColors.STRING),
            AttributesDescriptor("Number", FirebaseRulesHighlightingColors.NUMBER),
            AttributesDescriptor("Line comment", FirebaseRulesHighlightingColors.LINE_COMMENT),
            AttributesDescriptor("Block comment", FirebaseRulesHighlightingColors.BLOCK_COMMENT),
            AttributesDescriptor("Operator", FirebaseRulesHighlightingColors.OPERATOR),
            AttributesDescriptor("Braces", FirebaseRulesHighlightingColors.BRACES),
            AttributesDescriptor("Parentheses", FirebaseRulesHighlightingColors.PARENTHESES),
            AttributesDescriptor("Brackets", FirebaseRulesHighlightingColors.BRACKETS),
            AttributesDescriptor("Comma", FirebaseRulesHighlightingColors.COMMA),
            AttributesDescriptor("Dot", FirebaseRulesHighlightingColors.DOT),
            AttributesDescriptor("Semicolon", FirebaseRulesHighlightingColors.SEMICOLON),
            AttributesDescriptor("Bad character", FirebaseRulesHighlightingColors.BAD_CHARACTER),
        )

        val TAGS = mapOf(
            "service" to FirebaseRulesHighlightingColors.SERVICE_NAME,
            "fdecl" to FirebaseRulesHighlightingColors.FUNCTION_DECLARATION,
            "pathvar" to FirebaseRulesHighlightingColors.PATH_VARIABLE,
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
                  /* type test, ternary, and membership */
                  allow read: if resource.data.value is int
                    ? resource.data.value >= 100
                    : resource.data.tier in ['gold', 'silver'];
                }

                match /logs/{<pathvar>document</pathvar>=**} {
                  allow read: if false;
                }
              }
            }
            """.trimIndent()
    }
}
