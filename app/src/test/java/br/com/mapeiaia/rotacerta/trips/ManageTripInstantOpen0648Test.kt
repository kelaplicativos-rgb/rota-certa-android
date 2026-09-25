package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManageTripInstantOpen0648Test {
    private val tripsActivity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val quickPassenger = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripQuickPassengerUi.kt").readText()

    @Test
    fun manageTripListIsLazyAndReusesAlreadyLoadedBookings() {
        assertTrue(
            tripsActivity.contains(
                "screen == TripScreen.TIMELINE || screen == TripScreen.DEBUG_REPORT || screen == TripScreen.LIST",
            ),
        )
        assertTrue(
            tripsActivity.contains(
                "val bookingsByTripId0648 = remember(bookings) { bookings.groupBy { it.tripId } }",
            ),
        )

        val listStart = tripsActivity.indexOf("TripScreen.LIST -> {\n                    val onlineSettings")
        val listEnd = tripsActivity.indexOf("@Composable\nprivate fun TripExtraSeatsScreen0416", listStart)
        assertTrue(listStart >= 0 && listEnd > listStart)
        val listBlock = tripsActivity.substring(listStart, listEnd)

        assertTrue(listBlock.contains("LazyColumn("))
        assertTrue(listBlock.contains("items(\n                                items = sortedManageTrips0648"))
        assertTrue(listBlock.contains("bookings = bookingsByTripId0648[trip.id].orEmpty()"))
        assertFalse(listBlock.contains("trips.sortedBy { it.departureAtMillis }.forEach { trip ->"))

        val cardStart = tripsActivity.indexOf("private fun TripCard(")
        val cardEnd = tripsActivity.indexOf("private fun settingsConfiguredForNativeTrip0633", cardStart + 1)
            .takeIf { it > cardStart }
            ?: tripsActivity.length
        val cardBlock = tripsActivity.substring(cardStart, cardEnd)
        assertTrue(cardBlock.contains("bookings: List<Booking>"))
        assertFalse(cardBlock.contains("val bookings = store.bookingsFor(trip.id)"))
        assertTrue(cardBlock.contains("canonicalBookings0494 = bookings"))
    }

    @Test
    fun quickPassengerIdentityReadsNeverBlockInitialComposition() {
        assertTrue(quickPassenger.contains("import androidx.compose.runtime.LaunchedEffect"))
        assertTrue(quickPassenger.contains("import kotlinx.coroutines.Dispatchers"))
        assertTrue(quickPassenger.contains("import kotlinx.coroutines.withContext"))

        assertFalse(quickPassenger.contains("val passengerSuggestions = remember("))
        assertTrue(
            quickPassenger.contains(
                "withContext(Dispatchers.IO) { passengerRepository.search(query0648, 6) }",
            ),
        )
        assertTrue(
            quickPassenger.contains(
                "withContext(Dispatchers.IO) { passengerStore.exactContactMatches(contact) }",
            ),
        )
        assertTrue(quickPassenger.contains("query0648.isBlank()"))
    }

    @Test
    fun expandedManageCardPassesBookingSnapshotIntoQuickPassengerPanel() {
        val cardStart = tripsActivity.indexOf("private fun TripCard(")
        assertTrue(cardStart >= 0)
        val quickCall = tripsActivity.indexOf("QuickPassengerPanel(", cardStart)
        assertTrue(quickCall > cardStart)
        val call = tripsActivity.substring(quickCall, (quickCall + 800).coerceAtMost(tripsActivity.length))

        assertTrue(call.contains("canonicalBookings0494 = bookings"))
        assertFalse(call.contains("store.bookingsFor(trip.id)"))
    }
}
