package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationalTripCardFooter0654Test {
    @Test
    fun viagensCardsRenderCanonicalPassengersInlineWithoutRedundantFooter() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()

        assertTrue(source.contains("EnhancedPassengerTimelineSection("))
        assertTrue(source.contains("compactEmbeddedControls0593 = true"))
        assertTrue(source.contains("embedChronologicalStops0667 = true"))
        assertTrue(source.contains("canonicalBookings0494 = bookings0654"))
        assertTrue(source.contains("Text(\"Integridade\", maxLines = 1)"))
        assertFalse(source.contains("Text(\"Atalhos\", maxLines = 1)"))
        assertFalse(source.contains("passengerCountByTripId0654"))
        assertTrue(source.contains("onOperationsChanged0654"))
        assertTrue(source.contains("onRefreshLocal()"))
    }

    @Test
    fun inlineOperatorUsesCanonicalTripIdentity() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()

        assertTrue(source.contains("operationalCanonicalTripId0654(row)"))
        assertTrue(source.contains("row.canonicalTrip0633?.id"))
        assertTrue(source.contains("row.entry.localTripId"))
        assertTrue(source.contains("val canonicalTrip0667 = row.canonicalTrip0633"))
    }

    @Test
    fun tripsActivityRefreshesAfterShortcutMutationAndKeepsIntegrityAccess() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()

        assertTrue(source.contains("onRefreshLocal = { refreshUi0705() }"))
        assertTrue(source.contains("val refreshUi0705: () -> Unit = {"))
        assertTrue(source.contains("onOpenTripIntegrity = { tripId ->"))
        assertTrue(source.contains("screen = TripScreen.CENTRAL_DAY"))
    }
}
