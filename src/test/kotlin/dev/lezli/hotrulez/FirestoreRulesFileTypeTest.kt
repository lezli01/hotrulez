package dev.lezli.hotrulez

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FirestoreRulesFileTypeTest {
    @Test
    fun defaultExtensionIsRules() {
        assertEquals("rules", FirestoreRulesFileType.defaultExtension)
    }

    @Test
    fun iconResourceIsBundled() {
        assertNotNull(
            "Firestore Rules file icon must be on the classpath at the path the loader uses",
            FirestoreRulesIcons::class.java.getResource("/icons/firestoreRules.svg"),
        )
    }
}
