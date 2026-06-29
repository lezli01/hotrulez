package dev.lezli.hotrulez

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FirebaseRulesFileTypeTest {
    @Test
    fun defaultExtensionIsRules() {
        assertEquals("rules", FirebaseRulesFileType.defaultExtension)
    }

    @Test
    fun iconResourceIsBundled() {
        assertNotNull(
            "Firestore Rules file icon must be on the classpath at the path the loader uses",
            FirebaseRulesIcons::class.java.getResource("/icons/firebaseRules.svg"),
        )
    }
}
