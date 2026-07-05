package dev.lezli.hotrulez

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lezli.hotrulez.references.RulesService

/**
 * Drift guard for the member inspection's flag authority ([RulesService.closedReceivers]).
 * The inspection's whole safety argument rests on two things the runtime no longer re-checks:
 * that closed receivers agree with the documented member sets, and that no *open* receiver
 * ever leaks into (or sits above) a closed one. This test pins both statically, mirroring the
 * m4 `FirebaseRulesDocsTableTest` pattern.
 *
 * Pure-data assertions; no editor is required, but the suite rides on [BasePlatformTestCase]
 * to match this module's test conventions.
 */
class FirebaseRulesClosedReceiverDriftTest : BasePlatformTestCase() {

    /** Where a receiver is both flag-closed and a completion member, the two sets must agree. */
    fun testClosedReceiversAgreeWithMembersOnOverlap() {
        val mismatches = mutableListOf<String>()
        for (service in RulesService.entries) {
            for ((receiver, closed) in service.closedReceivers) {
                val members = service.members[receiver]?.toSet() ?: continue
                if (closed != members) {
                    mismatches += "${service.name}: `$receiver` closed=$closed members=$members"
                }
            }
        }
        assertTrue("closedReceivers diverge from members where they overlap: $mismatches", mismatches.isEmpty())
    }

    /** No open receiver may ever be a closed key (custom claims / metadata / params are unbounded). */
    fun testNoOpenReceiverIsClosed() {
        val leaks = mutableListOf<String>()
        for (service in RulesService.entries) {
            for (open in RulesService.OPEN_RECEIVERS) {
                if (open in service.closedReceivers) leaks += "${service.name}: `$open`"
            }
        }
        assertTrue("open receivers must never appear in closedReceivers: $leaks", leaks.isEmpty())
    }

    /**
     * No closed receiver may sit *beneath* an open one — this is the invariant that makes the
     * runtime open-receiver short-circuit redundant (so the inspection can use a single
     * closed-key lookup). If it ever broke, a member under an open namespace could be flagged.
     */
    fun testNoClosedReceiverSitsBeneathAnOpenOne() {
        val violations = mutableListOf<String>()
        for (service in RulesService.entries) {
            for (closed in service.closedReceivers.keys) {
                for (open in RulesService.OPEN_RECEIVERS) {
                    if (closed.startsWith("$open.")) violations += "${service.name}: `$closed` is under open `$open`"
                }
            }
        }
        assertTrue("no closed receiver may sit beneath an open receiver: $violations", violations.isEmpty())
    }

    /** `request.auth.token` and `request.auth.token.firebase` are asserted open in both dialects. */
    fun testAuthTokenAndFirebaseAreOpen() {
        for (service in RulesService.entries) {
            assertFalse("${service.name}: request.auth.token must be OPEN", "request.auth.token" in service.closedReceivers)
            assertFalse(
                "${service.name}: request.auth.token.firebase must be OPEN",
                "request.auth.token.firebase" in service.closedReceivers,
            )
        }
        assertTrue("request.auth.token should be listed in OPEN_RECEIVERS", "request.auth.token" in RulesService.OPEN_RECEIVERS)
        assertTrue(
            "request.auth.token.firebase should be listed in OPEN_RECEIVERS",
            "request.auth.token.firebase" in RulesService.OPEN_RECEIVERS,
        )
    }

    /** Firestore has no `request.params` — not in the flag model and not a completion member. */
    fun testFirestoreRequestHasNoParams() {
        assertFalse(
            "Firestore request must not close `params`",
            "params" in RulesService.FIRESTORE.closedReceivers.getValue("request"),
        )
        assertFalse(
            "Firestore request must not offer `params` in completion",
            "params" in RulesService.FIRESTORE.members.getValue("request"),
        )
        // Storage keeps it (valid there, and itself open).
        assertTrue(
            "Storage request must keep `params`",
            "params" in RulesService.STORAGE.closedReceivers.getValue("request"),
        )
    }

    /** `request.query` is a closed Firestore-only sub-receiver, and is not a completion member. */
    fun testRequestQueryIsFirestoreClosedOnly() {
        assertTrue("Firestore should close request.query", "request.query" in RulesService.FIRESTORE.closedReceivers)
        assertFalse("Storage should not close request.query", "request.query" in RulesService.STORAGE.closedReceivers)
        assertFalse(
            "request.query is flag-closed but not a completion member",
            "request.query" in RulesService.FIRESTORE.members,
        )
    }
}
