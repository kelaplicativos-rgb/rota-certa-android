package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgendaSyncUiScope0562Test {
    private fun target(
        tenant: String = "tenant-a",
        account: String = "account-a",
        profile: String = "00000000-0000-4000-8000-000000000001",
        trip: String = "trip-a",
    ) = BlaBlaTripTarget0407(
        tenantId = tenant,
        accountId = account,
        profileUuid = profile,
        tripId = trip,
        tripHref = "https://example.invalid/trip/$trip",
    )

    private fun audit(
        commandId: String,
        pending: Boolean,
    ) = BlaBlaCommandAuditSnapshot0407(
        commandId = commandId,
        status = if (pending) BlaBlaCommandStatus0407.QUEUED else BlaBlaCommandStatus0407.VERIFIED_SUCCESS,
        requestedAtMillis = 1L,
        finishedAtMillis = if (pending) 0L else 2L,
        pending = pending,
    )

    @Test
    fun `single trip pending never means all trips busy`() {
        val trip = target()
        val cardAudit = audit(commandId = "card-1", pending = true)
        val single = agendaSingleTripScope0562(trip, cardAudit)

        assertTrue(single is AgendaSyncUiScope0562.SingleTrip)
        assertFalse(agendaGlobalSyncBusy0562(single, listOf(cardAudit)))
    }

    @Test
    fun `idle ignores automatic or unrelated pending commands`() {
        assertFalse(
            agendaGlobalSyncBusy0562(
                AgendaSyncUiScope0562.None,
                listOf(audit(commandId = "background-1", pending = true)),
            ),
        )
    }

    @Test
    fun `all trips is busy only for commands owned by the global operation`() {
        val global = AgendaSyncUiScope0562.AllTrips(
            operationId = "global-7",
            commandIds = setOf("global-a", "global-b"),
            trigger = "REFRESH_ALL",
            commandRevisionAtStart = 10L,
        )

        assertFalse(
            agendaGlobalSyncBusy0562(
                global,
                listOf(audit(commandId = "card-unrelated", pending = true)),
            ),
        )
        assertTrue(
            agendaGlobalSyncBusy0562(
                global,
                listOf(
                    audit(commandId = "global-a", pending = false),
                    audit(commandId = "global-b", pending = true),
                    audit(commandId = "card-unrelated", pending = true),
                ),
            ),
        )
    }

    @Test
    fun `global operation ends when its own commands finish even if a card command remains pending`() {
        val global = AgendaSyncUiScope0562.AllTrips(
            operationId = "global-8",
            commandIds = setOf("global-a", "global-b"),
            trigger = "REFRESH_ALL",
            commandRevisionAtStart = 20L,
        )
        val audits = listOf(
            audit(commandId = "global-a", pending = false),
            audit(commandId = "global-b", pending = false),
            audit(commandId = "card-still-running", pending = true),
        )

        assertFalse(agendaGlobalSyncBusy0562(global, audits))
    }

    @Test
    fun `global-owned pending command is not reclassified as single trip`() {
        val trip = target()
        val globalAudit = audit(commandId = "global-a", pending = true)

        assertTrue(
            agendaSingleTripScope0562(
                target = trip,
                audit = globalAudit,
                activeGlobalCommandIds = setOf("global-a"),
            ) is AgendaSyncUiScope0562.None,
        )
    }

    @Test
    fun `strong identity separates tenant account profile and trip`() {
        val baseline = target()
        assertNotEquals(baseline.strongIdentityKey, target(tenant = "tenant-b").strongIdentityKey)
        assertNotEquals(baseline.strongIdentityKey, target(account = "account-b").strongIdentityKey)
        assertNotEquals(
            baseline.strongIdentityKey,
            target(profile = "00000000-0000-4000-8000-000000000002").strongIdentityKey,
        )
        assertNotEquals(baseline.strongIdentityKey, target(trip = "trip-b").strongIdentityKey)
    }
}
