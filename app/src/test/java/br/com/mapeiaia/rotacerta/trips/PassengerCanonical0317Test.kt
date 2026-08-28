package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerCanonical0317Test {
    private val uuidA = "7371f028-9c55-4903-8444-308015823efd"
    private val uuidB = "175a7068-50d8-40c3-a27a-214b9c6e0461"

    @Test // A
    fun sameExternalUuidWithDifferentNameIsSameCanonicalPassenger() {
        val profile = PassengerProfile(id = "p1", displayName = "Nome antigo", externalPassengerIds = setOf(uuidA))
        assertSame(profile, selectCanonicalPassenger(listOf(profile), externalPassengerId = uuidA))
    }

    @Test // B
    fun sameExternalUuidWinsEvenWhenPhoneChanged() {
        val profile = PassengerProfile(id = "p1", displayName = "Pessoa", whatsapp = "11911112222", externalPassengerIds = setOf(uuidA))
        val resolved = selectCanonicalPassenger(listOf(profile), externalPassengerId = uuidA, whatsapp = "11999998888")
        assertEquals("p1", resolved?.id)
    }

    @Test // C
    fun sameNameWithDifferentStrongUuidsRemainsDifferentPeople() {
        val first = PassengerProfile(id = "a", displayName = "Maria", externalPassengerIds = setOf(uuidA))
        val second = PassengerProfile(id = "b", displayName = "Maria", externalPassengerIds = setOf(uuidB))
        assertEquals("a", selectCanonicalPassenger(listOf(first, second), externalPassengerId = uuidA)?.id)
        assertEquals("b", selectCanonicalPassenger(listOf(first, second), externalPassengerId = uuidB)?.id)
    }

    @Test // D
    fun similarNameWithoutStrongIdentityNeverAutoLinks() {
        val profile = PassengerProfile(id = "p1", displayName = "Kel Silva", whatsapp = "11911112222")
        assertNull(selectCanonicalPassenger(listOf(profile), whatsapp = null))
    }

    @Test // E
    fun exactUniqueNormalizedWhatsappReusesCanonicalPassenger() {
        val profile = PassengerProfile(id = "p1", displayName = "Kel", whatsapp = "+55 (11) 91111-2222")
        assertEquals("p1", selectCanonicalPassenger(listOf(profile), whatsapp = "11 91111-2222")?.id)
    }

    @Test // F
    fun partialKelSearchFindsCurrentAndHistoricalAliases() {
        val current = PassengerProfile(id = "p1", displayName = "Kelly Atual")
        val oldAlias = PassengerProfile(id = "p2", displayName = "Maria")
        val observations = mapOf(
            "p2" to listOf(PassengerIdentityObservation(passengerId = "p2", displayName = "Kellen Antiga")),
        )
        val result = searchCanonicalPassengers(listOf(current, oldAlias), observations, "Kel")
        assertEquals(listOf("p1", "p2"), result.map { it.id }.sorted())
    }

    @Test // G
    fun legacy0316RideRecordDefaultsToObservedAndDoesNotCountAsCompleted() {
        val profile = PassengerProfile(id = "p1", displayName = "Pessoa")
        val legacyShape = PassengerRideRecord(passengerId = "p1", rideKey = "legacy")
        val history = PassengerPersistentHistory(profile, emptyList(), listOf(legacyShape))
        assertEquals(PassengerOccurrenceStatus.OBSERVED, legacyShape.status)
        assertEquals(0, history.totalRides)
        assertEquals(1, history.totalOccurrences)
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerIdentityStore.kt").readText()
        assertTrue(source.contains("status = PassengerOccurrenceStatus.CAPTURED"))
    }

    @Test // H
    fun explicitCompletedOccurrenceAddsExactlyOneCompletedRide() {
        val profile = PassengerProfile(id = "p1", displayName = "Pessoa")
        val record = PassengerRideRecord(passengerId = "p1", rideKey = "trip:segment", status = PassengerOccurrenceStatus.COMPLETED)
        assertEquals(1, PassengerPersistentHistory(profile, emptyList(), listOf(record)).totalRides)
        assertTrue(File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerCompletionService.kt").readText().contains("status = PassengerOccurrenceStatus.COMPLETED"))
    }

    @Test // I
    fun repeatedCompletionCannotDowngradeOrCreateSecondPhysicalKey() {
        assertEquals(
            PassengerOccurrenceStatus.COMPLETED,
            mergePassengerOccurrenceStatus(PassengerOccurrenceStatus.COMPLETED, PassengerOccurrenceStatus.COMPLETED),
        )
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerIdentityStore.kt").readText()
        assertTrue(source.contains("it.passengerId == canonicalPassengerId && it.rideKey == key"))
        assertTrue(source.contains("listOf(next) + withoutSamePhysicalOccurrence"))
    }

    @Test // J
    fun completionIsIndependentPerPassengerOnSameTrip() {
        val a = PassengerProfile(id = "a", displayName = "A")
        val b = PassengerProfile(id = "b", displayName = "B")
        val aRecord = PassengerRideRecord(passengerId = "a", rideKey = "same-trip:segment", status = PassengerOccurrenceStatus.COMPLETED)
        val bRecord = PassengerRideRecord(passengerId = "b", rideKey = "same-trip:segment", status = PassengerOccurrenceStatus.RESERVED)
        assertEquals(1, PassengerPersistentHistory(a, emptyList(), listOf(aRecord)).totalRides)
        assertEquals(0, PassengerPersistentHistory(b, emptyList(), listOf(bRecord)).totalRides)
    }

    @Test // K/L/M
    fun clearTimelinePreservesPassengersBookingsAndCompletedHistory() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val store = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripStore.kt").readText()
        assertTrue(timeline.contains("TIMELINE_VISUAL_CLEARED_BY_USER"))
        assertTrue(timeline.contains("passengerHistoryPreserved=true"))
        assertTrue(timeline.contains("localBookingsPreserved=true"))
        assertTrue(timeline.contains("physical.filter { !it.localTripId.isNullOrBlank() }"))
        assertTrue(timeline.contains("localCardsArchived="))
        assertFalse(timeline.contains("store.clearTimelineLocalData()"))
        assertTrue(store.contains("fun clearTimelineLocalData(): Pair<Int, Int> = 0 to 0"))
        assertTrue(File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerIdentityStore.kt").readText().contains("KEY_RIDE_RECORDS"))
    }

    @Test // N
    fun blockedPassengerReturnsBlockedAfterRenameWhenUuidIsSame() {
        val blocked = PassengerProfile(id = "p1", displayName = "Nome antigo", blocked = true, externalPassengerIds = setOf(uuidA))
        val resolved = selectCanonicalPassenger(listOf(blocked.copy(displayName = "Nome novo")), externalPassengerId = uuidA)
        assertTrue(resolved?.blocked == true)
        assertEquals("p1", resolved?.id)
    }

    @Test // O/P
    fun adminCardOpensHistoryWhileWhatsappHasIndependentAction() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
        assertTrue(source.contains("onClick = { openCandidateHistory(candidate) }"))
        assertTrue(source.contains("onClick = { openPassengerWhatsApp(context, candidate.whatsapp) }"))
        assertTrue(source.contains("R.drawable.ic_whatsapp_action"))
        assertFalse(source.contains(") { Text(\"Histórico\") }"))
    }

    @Test // Q
    fun adminOrderingIsPendingThenBlockedThenActive() {
        val blocked = PassengerProfile(id = "blocked", displayName = "Bloqueado", blocked = true)
        val candidates = mergePassengerAdminCandidates(
            localProfiles = listOf(blocked),
            collectedPassengers = emptyList(),
            remotePassengers = listOf(
                DriverPassengerAccess(id = "onlinepending1", passengerContact = "11955556666", displayName = "Pendente", status = "PENDING"),
                DriverPassengerAccess(id = "onlineactive01", passengerContact = "11977778888", displayName = "Ativo", status = "ACTIVE"),
            ),
        )
        assertEquals(listOf("Pendente", "Bloqueado", "Ativo"), candidates.take(3).map { it.displayName })
    }

    @Test // R
    fun publicQueryDoesNotRenderSeparateDateResultList() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ResponsiveTripActions.kt").readText()
        assertFalse(source.contains("BlaBlaPublicSearchTimelineResults("))
        assertTrue(source.contains("Resultados inseridos cronologicamente na Timeline"))
    }

    @Test // S
    fun publicCardsAreInsertedChronologicallyInsideTimelineDays() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(source.contains("publicSearchCardDepartureSortMillis(dayPublicCards[publicCardIndex]) <= entry.departureAtMillis"))
        assertTrue(source.contains("BlaBlaPublicTimelineCard("))
    }

    @Test // T
    fun emptyPublicSearchMonthStillProducesVisibleDayLines() {
        val response = BlaBlaPublicSearchResponse(
            status = "complete",
            request = BlaBlaPublicSearchRequest(
                targetNames = listOf("Ezequiel"),
                from = "Santo André",
                to = "São Thomé das Letras",
                period = "2026-10",
            ),
        )
        val days = combinedTimelineCalendarDays(emptyList(), response)
        assertEquals(31, days.size)
        assertTrue(days.all { it.items.isEmpty() })
    }

    @Test // U
    fun publicCardModelStaysSeparateFromOperationalPassengerModel() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(source.contains("internal fun BlaBlaPublicTimelineCard"))
        assertTrue(source.contains("Card público independente"))
        assertTrue(source.contains("não são mesclados com o card operacional"))
    }

    @Test // V/W
    fun pendingExternalSyncBannerFiltersAllDatesAndCanBeCleared() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(source.contains("Sincronização externa pendente ⚠️"))
        assertTrue(source.contains("syncPendingOnly = true"))
        assertTrue(source.contains("searchQuery = \"\""))
        assertTrue(source.contains("Filtro ativo • exibindo somente cards pendentes em todas as datas."))
        assertTrue(source.contains("✕ Limpar filtro"))
        assertTrue(source.contains("syncPendingOnly = false"))
    }
}
