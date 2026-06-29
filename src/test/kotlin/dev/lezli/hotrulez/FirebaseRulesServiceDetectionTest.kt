package dev.lezli.hotrulez

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.references.RulesService

/**
 * Content-based service detection: the dialect of a `.rules` file is read from
 * its `service` declaration (both dialects share the `.rules` extension), with a
 * neutral `null` result for a missing or unrecognised service. The detected
 * dialect is what drives service-aware diagnostics and completion.
 */
class FirebaseRulesServiceDetectionTest : BasePlatformTestCase() {

    fun testDetectsFirestore() {
        assertEquals(RulesService.FIRESTORE, serviceOf("service cloud.firestore { }"))
    }

    fun testDetectsStorage() {
        assertEquals(RulesService.STORAGE, serviceOf("service firebase.storage { }"))
    }

    fun testDetectsServiceWithInterveningWhitespace() {
        // service_name is `IDENTIFIER ('.' IDENTIFIER)*`; detection strips whitespace.
        assertEquals(RulesService.STORAGE, serviceOf("service firebase . storage { }"))
    }

    fun testMissingServiceIsNeutral() {
        assertNull(serviceOf("rules_version = '2';\n"))
    }

    fun testUnknownServiceIsNeutral() {
        // Another product (firebase.database uses JSON rules, not this language) or a typo.
        assertNull(serviceOf("service firebase.database { }"))
    }

    fun testFromServiceNameMapping() {
        assertEquals(RulesService.FIRESTORE, RulesService.fromServiceName("cloud.firestore"))
        assertEquals(RulesService.STORAGE, RulesService.fromServiceName("firebase.storage"))
        assertNull(RulesService.fromServiceName("firebase.database"))
        assertNull(RulesService.fromServiceName(null))
    }

    fun testServiceNamesCoverBothDialects() {
        assertContainsElements(RulesService.SERVICE_NAMES, "cloud.firestore", "firebase.storage")
    }

    fun testStorageHasNoBareHelpersWhileFirestoreDoes() {
        assertEmpty(RulesService.STORAGE.bareHelpers)
        assertContainsElements(RulesService.FIRESTORE.bareHelpers, "get", "exists", "getAfter", "existsAfter")
    }

    fun testStorageResourceTableExcludesFirestoreData() {
        val resource = RulesService.STORAGE.members["resource"].orEmpty()
        assertContainsElements(resource, "size", "contentType", "metadata", "name", "timeCreated")
        assertFalse("storage resource has no .data member", "data" in resource)
    }

    fun testStorageGlobalsIncludeFirestoreNamespace() {
        assertContainsElements(RulesService.STORAGE.globals, "request", "resource", "firestore")
        assertFalse("Firestore dialect has no firestore namespace", "firestore" in RulesService.FIRESTORE.globals)
    }

    fun testBuiltinRecognitionIsServiceAgnostic() {
        // The resolver recognises built-ins across dialects so none is flagged undefined.
        assertContainsElements(
            RulesService.ALL_BUILTIN_NAMES.toList(),
            "request", "resource", "firestore", "get", "exists", "getAfter", "existsAfter",
        )
    }

    private fun serviceOf(text: String): RulesService? {
        val file = myFixture.configureByText(FirebaseRulesFileType, text) as FirebaseRulesFile
        return RulesService.forFile(file)
    }
}
