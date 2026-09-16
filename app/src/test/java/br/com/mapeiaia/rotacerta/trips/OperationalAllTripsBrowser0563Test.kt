package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalAllTripsBrowser0563Test {
    @Test
    fun `keeps only connected profiles and sorts globally by departure`() {
        val accountA = account("a", "Perfil A", "profile-a")
        val accountB = account("b", "Perfil B", "profile-b")
        val disconnected = entry("trip-x", "profile-x", 500L)
        val laterA = entry("trip-a-later", "profile-a", 300L)
        val firstB = entry("trip-b-first", "profile-b", 100L)
        val middleA = entry("trip-a-middle", "profile-a", 200L)

        val result = operationalConnectedEntries0563(
            entries = listOf(disconnected, laterA, firstB, middleA),
            accounts = listOf(accountA, accountB),
        )

        assertEquals(
            listOf("trip-b-first", "trip-a-middle", "trip-a-later"),
            result.map(TripTimelineEntry::tripId),
        )
        assertTrue(result.none { it.blablaProfileUuid == "profile-x" })
    }

    @Test
    fun `profile matching is normalized and deterministic`() {
        val account = account("a", "Perfil A", "PROFILE-A")
        val sameTimeSecond = entry("trip-2", "profile-a", 100L, providerTripId = "z")
        val sameTimeFirst = entry("trip-1", "Profile-A", 100L, providerTripId = "a")

        val result = operationalConnectedEntries0563(
            entries = listOf(sameTimeSecond, sameTimeFirst),
            accounts = listOf(account),
        )

        assertEquals(listOf("trip-1", "trip-2"), result.map(TripTimelineEntry::tripId))
    }

    private fun account(id: String, label: String, profileUuid: String): BlaBlaDynamicAccount =
        BlaBlaDynamicAccount(
            id = id,
            label = label,
            webProfileName = "web-$id",
            profileUuid = profileUuid,
            profileName = label,
        )

    private fun entry(
        id: String,
        profileUuid: String,
        departure: Long,
        providerTripId: String = id,
    ): TripTimelineEntry = TripTimelineEntry(
        tripId = id,
        profileId = profileUuid,
        profileLabel = profileUuid,
        departureAtMillis = departure,
        arrivalAtMillis = departure + 1L,
        origin = "Origem",
        destination = "Destino",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = 0,
        maximumOccupiedSeats = 0,
        sourcePassengerSeats = emptyMap(),
        localTripId = id,
        blablaTripId = providerTripId,
        blablaTripHref = "https://www.blablacar.com.br/rides/offer/$providerTripId",
        blablaProfileUuid = profileUuid,
    )
}
