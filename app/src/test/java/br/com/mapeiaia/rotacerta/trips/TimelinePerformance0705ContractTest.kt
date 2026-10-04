package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelinePerformance0705ContractTest {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun agendaPersistenceNeverRunsDirectlyFromComposeRefreshCallbacks() {
        val activity = source("TripsActivity.kt")
        assertTrue(activity.contains("val refresh0705: suspend (String) -> Unit"))
        assertTrue(activity.contains("withContext(kotlinx.coroutines.Dispatchers.IO)"))
        assertTrue(activity.contains("val refreshUi0705: () -> Unit = {"))
        assertTrue(activity.contains("localRefreshCoordinator0726.request(\"ui_callback\")"))
        assertTrue(activity.contains("BookingRealtimeEvents0356.changes.collect {"))
        assertTrue(activity.contains("localRefreshCoordinator0726.request(\"booking_realtime\")"))
        assertFalse(activity.contains(".collectLatest"))
        assertFalse(activity.contains("val refresh = {\n        trips = store.trips()"))
        assertFalse(activity.contains("var trips by remember {\n        val operation"))
    }

    @Test
    fun passengerIdentityUsesOneProcessIntegrityAuthorityAndBatchSnapshot() {
        val identity = source("PassengerIdentityStore.kt")
        val timeline = source("PassengerTimelineUi.kt")
        assertTrue(identity.contains("processIntegrityCheckedTenants0705"))
        assertTrue(identity.contains("invalidateCanonicalIntegrity0705()"))
        assertTrue(identity.contains("PassengerResolutionSnapshot0705"))
        assertTrue(identity.contains("resolveCanonicalPassengersBatch0705("))
        assertTrue(identity.contains("historicalContacts0705 = decode<List<PassengerIdentityObservation>>"))
        assertFalse(identity.contains("profile.id to observations(profile.id).map"))
        assertTrue(timeline.contains("resolveCanonicalPassengersBatch0705("))
        assertTrue(timeline.contains("hasLegacyPassengers0705"))
        assertTrue(timeline.contains("if (localBookings.isEmpty() && !hasLegacyPassengers0705)"))
    }

    @Test
    fun cardCompletionReusesTheExistingIdentityStore() {
        val timeline = source("PassengerTimelineUi.kt")
        val completion = source("PassengerCompletionService.kt")
        assertTrue(timeline.contains("PassengerCompletionService(context, passengerStore)"))
        assertTrue(completion.contains("private val store: PassengerIdentityStore = PassengerIdentityStore(context.applicationContext)"))
    }
}
