package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerPickerCanonical0352Test {
    @Test
    fun samePassengerIdWithNameAndPhoneFormattingVariantsIsOnePickerOption() {
        val profiles = listOf(
            PassengerProfile(
                id = "passenger-kel",
                displayName = "Kel",
                whatsapp = "+5511947434112",
                createdAtMillis = 10L,
                updatedAtMillis = 20L,
            ),
            PassengerProfile(
                id = "passenger-kel",
                displayName = "kel",
                whatsapp = "(11) 94743-4112",
                createdAtMillis = 10L,
                updatedAtMillis = 30L,
            ),
        )

        val snapshot = buildPassengerPickerSnapshot(profiles)

        assertEquals(2, snapshot.rawProfileCount)
        assertEquals(1, snapshot.distinctPassengerIdCount)
        assertEquals(1, snapshot.profiles.size)
        assertEquals("passenger-kel", snapshot.profiles.single().id)
        assertEquals("kel", snapshot.profiles.single().displayName)
    }

    @Test
    fun brazilianPhoneFormattingUsesOneOfficialNormalizationAndFriendlyDisplay() {
        val variants = listOf(
            "+5511947434112",
            "5511947434112",
            "11947434112",
            "(11) 94743-4112",
            "11 94743-4112",
        )
        assertEquals(setOf("11947434112"), variants.map(::passengerContactKey).toSet())
        assertEquals("+55 11 94743-4112", formatPassengerContactForDisplay("+5511947434112"))
        assertEquals("telefone não informado", formatPassengerContactForDisplay(""))
    }

    @Test
    fun safeLegacyDuplicatesAreConsolidatedOnlyForPickerAndOldRecordsRemainUntouched() {
        val original = listOf(
            PassengerProfile(
                id = "old-kel",
                displayName = "Kel",
                whatsapp = "+5511947434112",
                createdAtMillis = 10L,
                updatedAtMillis = 20L,
            ),
            PassengerProfile(
                id = "new-kel",
                displayName = "kel",
                whatsapp = "11947434112",
                createdAtMillis = 30L,
                updatedAtMillis = 40L,
            ),
        )

        val snapshot = buildPassengerPickerSnapshot(original)

        assertEquals(2, original.size)
        assertEquals(setOf("old-kel", "new-kel"), original.map(PassengerProfile::id).toSet())
        assertEquals(2, snapshot.distinctPassengerIdCount)
        assertEquals(1, snapshot.profiles.size)
        assertEquals(1, snapshot.resolvedDuplicateCount)
        assertEquals("old-kel", snapshot.profiles.single().id)
    }

    @Test
    fun sameNameAloneNeverMergesDifferentPeople() {
        val snapshot = buildPassengerPickerSnapshot(
            listOf(
                PassengerProfile(id = "a", displayName = "Kel", whatsapp = "11911111111"),
                PassengerProfile(id = "b", displayName = "kel", whatsapp = "11922222222"),
            ),
        )
        assertEquals(2, snapshot.profiles.size)
    }

    @Test
    fun conflictingStrongExternalIdsDoNotAutoMergeDifferentPassengerIds() {
        val snapshot = buildPassengerPickerSnapshot(
            listOf(
                PassengerProfile(
                    id = "a",
                    displayName = "Kel",
                    whatsapp = "11947434112",
                    externalPassengerIds = setOf("member-a111"),
                ),
                PassengerProfile(
                    id = "b",
                    displayName = "kel",
                    whatsapp = "+5511947434112",
                    externalPassengerIds = setOf("member-b222"),
                ),
            ),
        )
        assertEquals(2, snapshot.profiles.size)
        assertEquals(0, snapshot.resolvedDuplicateCount)
    }

    @Test
    fun blockedConflictIsNeverHiddenByLegacyConsolidation() {
        val snapshot = buildPassengerPickerSnapshot(
            listOf(
                PassengerProfile(id = "a", displayName = "Kel", whatsapp = "11947434112", blocked = false),
                PassengerProfile(id = "b", displayName = "kel", whatsapp = "+5511947434112", blocked = true),
            ),
        )
        assertEquals(2, snapshot.profiles.size)
    }

    @Test
    fun sameCanonicalPassengerIdKeepsProtectiveBlockedStateIfPersistedTwice() {
        val snapshot = buildPassengerPickerSnapshot(
            listOf(
                PassengerProfile(
                    id = "same-id",
                    displayName = "Kel",
                    whatsapp = "11947434112",
                    blocked = false,
                    updatedAtMillis = 50L,
                ),
                PassengerProfile(
                    id = "same-id",
                    displayName = "Kel",
                    whatsapp = "+5511947434112",
                    blocked = true,
                    updatedAtMillis = 40L,
                ),
            ),
        )
        assertEquals(1, snapshot.profiles.size)
        assertTrue(snapshot.profiles.single().blocked)
    }

    @Test
    fun largePickerProjectionKeepsOneRowPerDistinctPassengerWithoutEagerUiLimit() {
        val profiles = (1..1000).map { index ->
            PassengerProfile(
                id = "passenger-" + index,
                displayName = "Passageiro " + index,
                whatsapp = "11" + (900000000 + index),
                createdAtMillis = index.toLong(),
            )
        }
        val snapshot = buildPassengerPickerSnapshot(profiles)
        assertEquals(1000, snapshot.profiles.size)
    }

    @Test
    fun activeSamePassengerCannotBeAddedTwiceToSameTrip() {
        val trip = sampleTrip()
        val existing = Booking(
            id = "booking-existing",
            tripId = trip.id,
            passengerName = "Kel",
            passengerContact = "11947434112",
            passengerId = "passenger-kel",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.PRIVATE,
            capacityClaimType = CapacityClaimType.PASSENGER,
        )

        assertTrue(QuickPassengerEngine.hasActivePassengerBooking(listOf(existing), "passenger-kel"))
        assertThrows(IllegalArgumentException::class.java) {
            QuickPassengerEngine.build(
                trip = trip,
                existingBookings = listOf(existing),
                request = QuickPassengerRequest(
                    passengerName = "Kel",
                    passengerContact = "+5511947434112",
                    passengerId = "passenger-kel",
                    boardingStopId = "a",
                    dropoffStopId = "b",
                    seats = 1,
                    fareMinorUnits = 1000L,
                    fareCurrencyCode = "BRL",
                ),
            )
        }
    }

    @Test
    fun cancelledPreviousBookingDoesNotBlockARealNewOccurrence() {
        val trip = sampleTrip()
        val cancelled = Booking(
            id = "booking-old",
            tripId = trip.id,
            passengerName = "Kel",
            passengerContact = "11947434112",
            passengerId = "passenger-kel",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CANCELLED,
            source = BookingSource.PRIVATE,
            capacityClaimType = CapacityClaimType.PASSENGER,
        )

        assertFalse(QuickPassengerEngine.hasActivePassengerBooking(listOf(cancelled), "passenger-kel"))
        val plan = QuickPassengerEngine.build(
            trip = trip,
            existingBookings = listOf(cancelled),
            request = QuickPassengerRequest(
                passengerName = "Kel",
                passengerContact = "11947434112",
                passengerId = "passenger-kel",
                boardingStopId = "a",
                dropoffStopId = "b",
                seats = 1,
                fareMinorUnits = 1000L,
                fareCurrencyCode = "BRL",
            ),
            idFactory = { "new-booking" },
        )
        assertEquals("passenger-kel", plan.passenger.passengerId)
    }

    @Test
    fun pickerSourceLayoutAndDebugContractAreCanonicalAndLazy() {
        val flow = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripGlobalPassengerFlow0256.kt").readText()
        val identity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerIdentityStore.kt").readText()
        val quickUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripQuickPassengerUi.kt").readText()

        assertTrue(flow.contains("passengerStore.pickerSnapshot()"))
        assertTrue(flow.contains("LazyColumn("))
        assertTrue(flow.contains("items(passengerCandidates, key = PassengerProfile::id)"))
        assertTrue(flow.contains("formatPassengerContactForDisplay(profile.agendaAccessContact())"))
        assertFalse(flow.contains(".take(30)"))
        assertTrue(flow.contains("title = { Text(\"➕ Adicionar a uma viagem\") }"))
        assertTrue(flow.contains("dismissButton = {"))
        assertTrue(flow.contains("PASSENGER_PICKER_OPENED"))
        assertTrue(flow.contains("PASSENGER_SOURCE_COUNT"))
        assertTrue(flow.contains("PASSENGER_CANONICAL_COUNT"))
        assertTrue(flow.contains("PASSENGER_SELECTED"))
        assertTrue(identity.contains("PASSENGER_IDENTITY_AMBIGUOUS"))
        assertTrue(identity.contains("action=preserve_without_new_profile"))
        assertTrue(quickUi.contains("PASSENGER_ALREADY_IN_TRIP"))
        assertTrue(quickUi.contains("PASSENGER_ADDED_TO_TRIP"))
    }

    private fun sampleTrip(): Trip = Trip(
        id = "trip-0352",
        title = "A → B",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
        ),
    )
}
