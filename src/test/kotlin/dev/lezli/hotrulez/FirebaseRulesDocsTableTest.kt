package dev.lezli.hotrulez

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.documentation.FirebaseRulesDocs
import dev.lezli.hotrulez.references.FirebaseRulesBuiltins
import dev.lezli.hotrulez.references.RulesService

/**
 * Doc-table integrity: guarantees that every name the vocabulary tables
 * ([RulesService.members] / `.globals` / `.bareHelpers`, [FirebaseRulesBuiltins.OPERATIONS]
 * / `.TYPE_NAMES`) can surface in the editor also has doc prose in [FirebaseRulesDocs].
 * A name present in a vocabulary table but missing prose would show a blank quick-doc
 * rather than crash — these tests turn that silent gap into a red build so vocabulary and
 * prose cannot drift apart.
 *
 * Pure-data assertions; no editor is required, but the suite rides on
 * [BasePlatformTestCase] to match this module's test conventions.
 *
 * ### Integrity subtlety
 * `RulesService.STORAGE.members` carries `firestore -> [get, exists]`; those member paths
 * (`firestore.get`/`firestore.exists`) are documented via [FirebaseRulesDocs.forHelper]
 * (the cross-service helpers), **not** via `forMember`. The member sweep therefore treats
 * a `firestore` receiver leaf as helper-backed.
 */
class FirebaseRulesDocsTableTest : BasePlatformTestCase() {

    /** Every composed `receiver.leaf` member path (both dialects) has doc prose. */
    fun testEveryMemberPathHasProse() {
        val offenders = mutableListOf<String>()
        for (service in RulesService.entries) {
            for ((receiver, leaves) in service.members) {
                for (leaf in leaves) {
                    val path = "$receiver.$leaf"
                    val byMember = FirebaseRulesDocs.forMember(path) != null
                    val byHelper = receiver == "firestore" &&
                        leaf in RulesService.CROSS_SERVICE_HELPERS &&
                        FirebaseRulesDocs.forHelper(leaf) != null
                    if (!byMember && !byHelper) {
                        offenders += "${service.name}: $path"
                    }
                }
            }
        }
        assertTrue("member paths missing FirebaseRulesDocs prose: $offenders", offenders.isEmpty())
    }

    /** Every member-table receiver that is itself a global name has global-level prose. */
    fun testEveryGlobalReceiverHasProse() {
        val globalNames = RulesService.entries.flatMap { it.globals }.toSet()
        val offenders = mutableListOf<String>()
        for (service in RulesService.entries) {
            for (receiver in service.members.keys) {
                if (receiver in globalNames && FirebaseRulesDocs.forGlobal(receiver) == null) {
                    offenders += "${service.name}: $receiver"
                }
            }
        }
        assertTrue("global receivers missing FirebaseRulesDocs.forGlobal prose: $offenders", offenders.isEmpty())
    }

    /** The `request`/`resource`/`firestore` globals are each documented. */
    fun testDeclaredGlobalsHaveProse() {
        val offenders = RulesService.entries
            .flatMap { it.globals }
            .toSet()
            .filter { FirebaseRulesDocs.forGlobal(it) == null }
        assertTrue("globals missing FirebaseRulesDocs.forGlobal prose: $offenders", offenders.isEmpty())
    }

    /** Every `allow` operation has doc prose. */
    fun testEveryOperationHasProse() {
        val offenders = FirebaseRulesBuiltins.OPERATIONS.filter { FirebaseRulesDocs.forOperation(it) == null }
        assertTrue("operations missing FirebaseRulesDocs.forOperation prose: $offenders", offenders.isEmpty())
    }

    /** Every type name plus the `debug` global has namespace-level prose. */
    fun testEveryTypeNamePlusDebugHasProse() {
        val names = FirebaseRulesBuiltins.TYPE_NAMES + "debug"
        val offenders = names.filter { FirebaseRulesDocs.forNamespace(it) == null }
        assertTrue("namespaces missing FirebaseRulesDocs.forNamespace prose: $offenders", offenders.isEmpty())
    }

    /** Every bare Firestore path helper is documented and its title carries the `(path)` signature. */
    fun testEveryBareHelperHasSignedProse() {
        val missing = mutableListOf<String>()
        val unsigned = mutableListOf<String>()
        for (name in RulesService.FIRESTORE.bareHelpers) {
            val entry = FirebaseRulesDocs.forHelper(name)
            if (entry == null) {
                missing += name
            } else if (!entry.title.contains("(path)")) {
                unsigned += "$name -> '${entry.title}'"
            }
        }
        assertTrue("bare helpers missing FirebaseRulesDocs.forHelper prose: $missing", missing.isEmpty())
        assertTrue("bare helper titles missing '(path)' signature: $unsigned", unsigned.isEmpty())
    }

    /** The cross-service helpers reachable as `firestore.get`/`firestore.exists` are documented. */
    fun testCrossServiceHelpersHaveProse() {
        val offenders = RulesService.CROSS_SERVICE_HELPERS.filter { FirebaseRulesDocs.forHelper(it) == null }
        assertTrue("cross-service helpers missing FirebaseRulesDocs.forHelper prose: $offenders", offenders.isEmpty())
    }

    // --- Reverse drift: every prose key maps back to a live vocabulary name ----------
    // The tests above only guarantee vocab -> prose. Without the reverse direction a member,
    // global, operation, helper, or namespace removed or renamed in the vocabulary tables
    // would leave its prose orphaned (dead, unreachable) yet still green. These close that
    // gap so the vocabulary and the prose table cannot drift apart in *either* direction.

    /**
     * Every documented member path is still composed by some dialect's member table
     * (`receiver -> [leaf]` => `receiver.leaf`), so a member dropped/renamed in
     * [RulesService.members] cannot leave orphaned member prose behind.
     */
    fun testEveryMemberProseKeyIsLive() {
        val liveMemberPaths = RulesService.entries
            .flatMap { service -> service.members.flatMap { (receiver, leaves) -> leaves.map { "$receiver.$it" } } }
            .toSet()
        val orphans = FirebaseRulesDocs.memberKeys - liveMemberPaths
        assertTrue("FirebaseRulesDocs member prose with no live member path: $orphans", orphans.isEmpty())
    }

    /** Every documented operation is still an [FirebaseRulesBuiltins.OPERATIONS] name. */
    fun testEveryOperationProseKeyIsLive() {
        val orphans = FirebaseRulesDocs.operationKeys - FirebaseRulesBuiltins.OPERATIONS.toSet()
        assertTrue("FirebaseRulesDocs operation prose with no live operation: $orphans", orphans.isEmpty())
    }

    /** Every documented global is still declared in some dialect's `globals`. */
    fun testEveryGlobalProseKeyIsLive() {
        val liveGlobals = RulesService.entries.flatMap { it.globals }.toSet()
        val orphans = FirebaseRulesDocs.globalKeys - liveGlobals
        assertTrue("FirebaseRulesDocs global prose with no live global: $orphans", orphans.isEmpty())
    }

    /**
     * Every documented helper is still a live path helper — a bare Firestore helper or a
     * cross-service `firestore.get`/`firestore.exists`.
     */
    fun testEveryHelperProseKeyIsLive() {
        val liveHelpers = RulesService.entries.flatMap { it.bareHelpers }.toSet() + RulesService.CROSS_SERVICE_HELPERS
        val orphans = FirebaseRulesDocs.helperKeys - liveHelpers
        assertTrue("FirebaseRulesDocs helper prose with no live path helper: $orphans", orphans.isEmpty())
    }

    /** Every documented namespace is still a live type name (or the `debug` global). */
    fun testEveryNamespaceProseKeyIsLive() {
        val liveNamespaces = FirebaseRulesBuiltins.TYPE_NAMES + "debug"
        val orphans = FirebaseRulesDocs.namespaceKeys - liveNamespaces
        assertTrue("FirebaseRulesDocs namespace prose with no live type name: $orphans", orphans.isEmpty())
    }

    // --- Reference-page liveness ------------------------------------------------------
    // `Entry.docUrl` is what quick documentation's "open in browser" reaches. Firebase
    // retires reference pages without redirecting them, so the set of pages this table may
    // link is pinned here: a new or edited `docUrl` has to be a page someone actually
    // fetched, not whatever URL the nearest entry happened to carry.

    /** Every `docUrl` is one of the Firebase pages verified reachable on 2026-09-08. */
    fun testEveryDocUrlIsAVerifiedFirebasePage() {
        val verified = setOf(
            "https://firebase.google.com/docs/firestore/security/rules-structure",
            "https://firebase.google.com/docs/rules/rules-language",
            "https://firebase.google.com/docs/firestore/security/rules-conditions",
            "https://firebase.google.com/docs/reference/rules/rules.firestore.Request",
            "https://firebase.google.com/docs/storage/security/rules-conditions",
        )
        val unverified = FirebaseRulesDocs.docUrls - verified
        assertTrue("FirebaseRulesDocs docUrl pointing at an unverified page: $unverified", unverified.isEmpty())
    }

    /**
     * The retired Cloud Storage reference page is not linked anywhere. Firebase withdrew
     * `https://firebase.google.com/docs/reference/rules/rules.storage`; it returns 404, so
     * every Storage entry must cite the storage/security/rules-conditions guide instead.
     */
    fun testRetiredStorageReferencePageIsNotLinked() {
        val retired = FirebaseRulesDocs.docUrls.filter { it.contains("reference/rules/rules.storage") }
        assertTrue("FirebaseRulesDocs still links the retired rules.storage page: $retired", retired.isEmpty())
    }
}
