package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class OperationalTripCardFooter0654Test {
    @Test
    fun viagensCardsMirrorCentralFooterAndExpandCanonicalShortcutsInPlace() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()

        assertTrue(source.contains("Passageiros \${row.passengerCount0654}"))
        assertTrue(source.contains("Text(\"Atalhos\", maxLines = 1)"))
        assertTrue(source.contains("Text(\"Integridade\", maxLines = 1)"))
        assertTrue(source.contains("EnhancedPassengerTimelineSection("))
        assertTrue(source.contains("compactEmbeddedControls0593 = true"))
        assertTrue(source.contains("canonicalBookings0494 = bookings0654"))
        assertTrue(source.contains("onOperationsChanged0654"))
        assertTrue(source.contains("onRefreshLocal()"))
    }

    @Test
    fun footerUsesCanonicalPassengerCountAndCanonicalTripIdentity() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()

        assertTrue(source.contains("passengerCountByTripId0654"))
        assertTrue(source.contains("booking.capacityClaimType != CapacityClaimType.RESERVED_SEAT"))
        assertTrue(source.contains("operationalCanonicalTripId0654(row)"))
        assertTrue(source.contains("row.canonicalTrip0633?.id"))
        assertTrue(source.contains("row.entry.localTripId"))
    }

    @Test
    fun tripsActivityRefreshesAfterShortcutMutationAndKeepsIntegrityAccess() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()

        assertTrue(source.contains("onRefreshLocal = { refresh() }"))
        assertTrue(source.contains("onOpenTripIntegrity = { tripId ->"))
        assertTrue(source.contains("screen = TripScreen.CENTRAL_DAY"))
    }
}
