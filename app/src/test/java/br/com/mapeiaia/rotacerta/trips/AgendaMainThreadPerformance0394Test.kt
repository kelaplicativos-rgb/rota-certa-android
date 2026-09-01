package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaMainThreadPerformance0394Test {
    @Test
    fun passengerTimelineBuildsItsHeavyRenderSnapshotOnIo() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()

        assertTrue(source.contains("buildPassengerTimelineRenderSnapshot0394("))
        assertTrue(source.contains("renderSnapshot0394 = withContext(Dispatchers.IO)"))
        assertTrue(source.contains("passengerStore.externalMetadataSnapshot0394()"))
        assertTrue(source.contains("passengerStore.persistentHistorySnapshot("))
        assertTrue(source.contains("renderSnapshot.profilesByRowKey[rowKey0394]"))
        assertTrue(source.contains("renderSnapshot.historiesByProfileId::get"))
        assertTrue(source.contains("rowKey0394 in renderSnapshot.completedRowKeys"))
    }

    @Test
    fun externalPassengerObservationNoLongerMutatesStoresOnComposeMainThread() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        val observation = source
            .substringAfter("LaunchedEffect(entry.tripId, entry.blablaTripId, entry.blablaProfileUuid, externalObservationKey)")
            .substringBefore("@Suppress(\"UNUSED_VARIABLE\")")

        assertTrue(observation.contains("withContext(Dispatchers.IO)"))
        assertTrue(observation.contains("passengerStore.observeExternalPassenger("))
    }

    @Test
    fun thirtySecondGpsTickDoesNotReDecodeTimelineStores() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertTrue(source.contains("val startupSnapshot = withContext(Dispatchers.IO)"))
        assertTrue(source.contains("delay(30_000L)"))
        assertTrue(source.contains("val publicExternalBindings = remember(trips, bookings, collectorResponse)"))
        assertTrue(source.contains("val internallyCancelledExternalReservationKeys = remember(trips, bookings, collectorResponse)"))
        assertTrue(source.contains("val seatSyncStates = remember(entries)"))
        assertTrue(source.contains("val seatPlan = remember(entry, trip) { timelineDesiredSeatSyncPlan(entry, trip, store) }"))
        assertFalse(source.contains("autoSyncToken"))
        assertFalse(source.contains("forceAllSyncToken"))
        assertFalse(source.contains("showSync"))
        assertFalse(source.contains("var collectorResponse by remember { mutableStateOf(collectorStore.lastResponseRecoveringDynamicSessions()) }"))
    }

    @Test
    fun notificationCredentialReadIsOffMainAndFarolIsNotPartOfFix() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val block = activity
            .substringAfter("val refreshDriverNotifications: suspend () -> Unit = {")
            .substringBefore("    androidx.compose.runtime.SideEffect")

        assertTrue(block.contains("withContext(kotlinx.coroutines.Dispatchers.IO)"))
        assertTrue(block.contains("store.onlineSettings()"))
        assertFalse(activity.contains("LiveRideAccessibilityService"))
    }
}
