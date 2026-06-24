package dev.lezli.hotrulez

import dev.lezli.hotrulez.highlighting.FirestoreRulesColorSettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreRulesColorSettingsPageTest {
    private val page = FirestoreRulesColorSettingsPage()

    @Test
    fun exposesDisplayNameHighlighterAndDemoText() {
        assertEquals("Firestore Rules", page.displayName)
        assertNotNull(page.highlighter)
        assertTrue(page.demoText.isNotBlank())
    }

    @Test
    fun exposesDistinctAttributeDescriptors() {
        val descriptors = page.attributeDescriptors
        assertTrue(descriptors.isNotEmpty())
        val keys = descriptors.map { it.key }
        assertEquals("attribute descriptor keys must be unique", keys.size, keys.toSet().size)
    }

    @Test
    fun demoTextContainsEveryAdditionalHighlightingTag() {
        val demo = page.demoText
        page.additionalHighlightingTagToDescriptorMap.keys.forEach { tag ->
            assertTrue("demo text is missing <$tag> region", demo.contains("<$tag>"))
        }
    }
}
