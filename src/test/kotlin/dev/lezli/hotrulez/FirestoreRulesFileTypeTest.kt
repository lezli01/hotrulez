package dev.lezli.hotrulez

import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreRulesFileTypeTest {
    @Test
    fun defaultExtensionIsRules() {
        assertEquals("rules", FirestoreRulesFileType.defaultExtension)
    }
}
