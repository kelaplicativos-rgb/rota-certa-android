package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDriverPublicAgendaStage47R3Test {
    @Test
    fun fareIsSummedAcrossSelectedSegmentsPerSeatAndParty() {
        val trip = Trip(
            id = "priced-route",
            title = "A → C",
            departureAtMillis = 1_800_000_000_000L,
            capacity = 20,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A", priceToNextCents = 1250L),
                TripStop(id = "b", order = 1, name = "B", priceToNextCents = 2075L),
                TripStop(id = "c", order = 2, name = "C"),
            ),
        )

        assertEquals(1250L, TripFareEngine.farePerSeatCents(trip, "a", "b"))
        assertEquals(3325L, TripFareEngine.farePerSeatCents(trip, "a", "c"))
        assertEquals(6650L, TripFareEngine.totalFareCents(trip, "a", "c", seats = 2))
    }

    @Test
    fun driverUsernameIsStableReadableAndSafeForPublicLinks() {
        assertEquals("ezequiel-silva-99", DriverIdentityRules.normalizeUsername("  Ezequiel Silva 99  "))
        assertEquals("joao-da-serra", DriverIdentityRules.normalizeUsername("João da Serra"))
        assertTrue(DriverIdentityRules.isValidUsername("joao-da-serra"))
    }
}
