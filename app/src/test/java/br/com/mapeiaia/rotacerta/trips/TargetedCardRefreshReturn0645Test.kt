package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetedCardRefreshReturn0645Test {
    private val target = BlaBlaTripTarget0407(
        tenantId = "tenant-0645",
        accountId = "account-0645",
        profileUuid = "11111111-1111-4111-8111-111111111111",
        tripId = "trip-0645",
        tripHref = "https://www.blablacar.com.br/rides/offer/trip-0645",
    )

    @Test
    fun targetedCanonicalCommitIsReplayableAndKeepsExactIdentity0645() {
        val before = TargetedTripRefreshEvents0645.commit.value?.revision ?: 0L

        TargetedTripRefreshEvents0645.notifyCanonicalCommitted(
            target = target,
            canonicalTripId = "canonical-0645",
            canonicalRevision = 77L,
            changed = true,
            nowMillis = 123_456L,
        )

        val event = TargetedTripRefreshEvents0645.commit.value
        assertTrue(event != null)
        assertTrue(event.revision > before)
        assertEquals(target.tenantId, event.tenantId)
        assertEquals(target.strongIdentityKey, event.strongIdentityKey)
        assertEquals("canonical-0645", event.canonicalTripId)
        assertEquals(77L, event.canonicalRevision)
        assertTrue(event.changed)
        assertEquals(123_456L, event.committedAtMillis)
    }

    @Test
    fun exactCardLocalCommitPrecedesPublicProjectionDrain0645() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt",
        ).readText()
        val functionStart = source.indexOf("internal suspend fun refreshCanonicalTripFromCollector0517(")
        val functionEnd = source.indexOf("fun enqueueRecoveryIfNeeded", functionStart)
        assertTrue(functionStart >= 0 && functionEnd > functionStart)
        val function = source.substring(functionStart, functionEnd)

        val localCommit = function.indexOf("TargetedTripRefreshEvents0645.notifyCanonicalCommitted(")
        val publicationDrain = function.indexOf("TripMutationCoordinator0387(appContext, store).drainPending(")
        assertTrue(localCommit >= 0)
        assertTrue(publicationDrain > localCommit)
        assertTrue(function.contains("TIMELINE_CARD_TARGET_LOCAL_COMMIT_0645"))
        assertTrue(function.contains("exactTargetOnly=true authority=HTML_DIRECT_0607"))
    }

    @Test
    fun duplicateSingleFlightDoesNotPretendThatNewExactRefreshWasQueued0645() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt",
        ).readText()
        val start = source.indexOf("fun enqueueTripCollectorRefresh0517(")
        val end = source.indexOf("fun enqueueTripReverify0407(", start)
        assertTrue(start >= 0 && end > start)
        val function = source.substring(start, end)

        assertTrue(function.contains("reason=single_flight_already_pending requestedAction=TARGET_HTML_REFRESH_0607 accepted=false"))
        assertTrue(function.contains("return false"))
        assertFalse(function.contains("requestedAction=TARGET_HTML_REFRESH_0607\",\n            )\n            return true"))
    }

    @Test
    fun timelineReloadsDurableLocalStateOnTargetCommitAndForegroundReturn0645() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt",
        ).readText()

        assertTrue(source.contains("TargetedTripRefreshEvents0645.commit.collectAsState()"))
        assertTrue(source.contains("suspend fun reloadLocalCanonicalProjection0645(reason: String)"))
        assertTrue(source.contains("TARGET_CARD_COMMIT:"))
        assertTrue(source.contains("reloadLocalCanonicalProjection0645(\"FOREGROUND_RETURN\")"))
        assertTrue(source.contains("reloadLocalCanonicalProjection0645(\"CANONICAL_CHANGE_EVENT\")"))
        assertTrue(source.contains("⟳ Atualizando somente esta viagem…"))
        assertTrue(source.contains("✓ Atualização individual concluída"))
    }
}
