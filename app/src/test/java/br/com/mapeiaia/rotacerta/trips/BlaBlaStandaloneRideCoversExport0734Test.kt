package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaStandaloneRideCoversExport0734Test {
    @Test
    fun completePayloadIsStrictlyDownloadOnlyAndRoundTrips() {
        val uuid = "7371f028-9c55-4903-8444-308015823efd"
        val payload = BlaBlaStandaloneRideCoversPayload0734(
            capturedAt = "2026-10-05T20:00:00Z",
            sourceAppVersion = "0.1.734",
            sourceVersionCode = 6025,
            sourceCommitSha = "abc123",
            sourceBranch = "agent/blablacar-standalone-covers-0.1.734",
            result = RESULT_COMPLETE_0734,
            totalProfiles = 1,
            totalCards = 1,
            profiles = listOf(
                BlaBlaStandaloneRideCoversProfile0734(
                    displayName = "Conta teste",
                    profileUuid = uuid,
                    identityConfirmed = true,
                    status = RESULT_COMPLETE_0734,
                    observedCardCount = 1,
                    exportedCardCount = 1,
                    reachedEnd = true,
                    stabilized = true,
                    cards = listOf(
                        BlaBlaStandaloneRideCover0734(
                            tripId = "01a0359a-8431-7398-90d8-e06cc43ae977",
                            administrativeHref = "https://www.blablacar.com.br/ride-plan/trip-edit/01a0359a-8431-7398-90d8-e06cc43ae977",
                            dateIso = "2026-11-15",
                            dateText = "15 de novembro de 2026",
                            departureTime = "19:00",
                            arrivalTime = "23:10",
                            origin = "Três Corações",
                            destination = "Santo André",
                            price = "R$ 93,00",
                        ),
                    ),
                ),
            ),
        )

        val raw = encodeStandaloneRideCoversPayload0734(payload)
        val decoded = decodeStandaloneRideCoversPayload0734(raw)

        assertEquals(payload, decoded)
        assertTrue(decoded.isolation.downloadOnly)
        assertFalse(decoded.isolation.writesTimeline)
        assertFalse(decoded.isolation.writesAgenda)
        assertFalse(decoded.isolation.writesCanonicalTrips)
        assertFalse(decoded.isolation.writesAvailability)
        assertFalse(decoded.isolation.writesCapacity)
        assertFalse(decoded.isolation.writesTodayState)
        assertFalse(decoded.isolation.readsPassengers)
        assertFalse(decoded.isolation.opensTripDetails)
        assertFalse(raw.contains("\"passengers\""))
        assertFalse(raw.contains("\"passengerId\""))
        assertFalse(raw.contains("\"bookings\""))
    }

    @Test
    fun partialProfileRemainsUnknownInsteadOfPretendingEmpty() {
        val payload = BlaBlaStandaloneRideCoversPayload0734(
            capturedAt = "2026-10-05T20:00:00Z",
            sourceAppVersion = "0.1.734",
            sourceVersionCode = 6025,
            sourceCommitSha = "abc123",
            sourceBranch = "agent/blablacar-standalone-covers-0.1.734",
            result = RESULT_PARTIAL_0734,
            totalProfiles = 1,
            totalCards = 0,
            profiles = listOf(
                BlaBlaStandaloneRideCoversProfile0734(
                    displayName = "Conta teste",
                    profileUuid = "",
                    identityConfirmed = false,
                    status = RESULT_PARTIAL_0734,
                    observedCardCount = 0,
                    exportedCardCount = 0,
                    reachedEnd = false,
                    stabilized = false,
                    errorCode = "PROFILE_UUID_NOT_CONFIRMED",
                ),
            ),
        )

        val decoded = decodeStandaloneRideCoversPayload0734(
            encodeStandaloneRideCoversPayload0734(payload),
        )

        assertEquals(RESULT_PARTIAL_0734, decoded.result)
        assertFalse(decoded.profiles.single().reachedEnd)
        assertFalse(decoded.profiles.single().identityConfirmed)
        assertEquals("PROFILE_UUID_NOT_CONFIRMED", decoded.profiles.single().errorCode)
    }

    @Test
    fun profileUuidMustBeCanonical() {
        assertEquals(
            "175a7068-50d8-40c3-a27a-214b9c6e0461",
            normalizeStandaloneProfileUuid0734("175a7068-50d8-40c3-a27a-214b9c6e0461"),
        )
        assertNull(normalizeStandaloneProfileUuid0734("Barbosa"))
        assertNull(normalizeStandaloneProfileUuid0734(""))
    }
    @Test
    fun repeatCollectionRetriesOnlyTransientFailuresAndKeepsBestEvidence() {
        val uuid = "7371f028-9c55-4903-8444-308015823efd"
        val unstable = BlaBlaStandaloneRideCoversProfile0734(
            displayName = "Conta teste",
            profileUuid = uuid,
            identityConfirmed = true,
            status = RESULT_PARTIAL_0734,
            observedCardCount = 0,
            exportedCardCount = 0,
            reachedEnd = false,
            stabilized = false,
            errorCode = "COVER_LIST_NOT_STABLE",
        )
        val mismatch = unstable.copy(errorCode = "PROFILE_UUID_MISMATCH")
        val partialWithEvidence = unstable.copy(
            observedCardCount = 2,
            exportedCardCount = 2,
            cards = listOf(
                BlaBlaStandaloneRideCover0734(
                    tripId = "trip-1",
                    dateIso = "2026-11-13",
                ),
                BlaBlaStandaloneRideCover0734(
                    tripId = "trip-2",
                    dateIso = "2026-11-14",
                ),
            ),
        )
        val recovered = partialWithEvidence.copy(
            status = RESULT_COMPLETE_0734,
            reachedEnd = true,
            stabilized = true,
            errorCode = "",
        )

        assertTrue(shouldRetryStandaloneProfile0735(unstable))
        assertFalse(shouldRetryStandaloneProfile0735(mismatch))
        assertEquals(
            partialWithEvidence,
            chooseBetterStandaloneProfile0735(unstable, partialWithEvidence),
        )
        assertEquals(
            recovered,
            chooseBetterStandaloneProfile0735(partialWithEvidence, recovered),
        )
    }

    @Test
    fun recoveredCompletePayloadRecordsMoreThanOneAttemptWithoutChangingIsolation() {
        val uuid = "175a7068-50d8-40c3-a27a-214b9c6e0461"
        val payload = BlaBlaStandaloneRideCoversPayload0734(
            capturedAt = "2026-10-05T22:10:00Z",
            sourceAppVersion = "0.1.735",
            sourceVersionCode = 6026,
            sourceCommitSha = "def456",
            sourceBranch = "agent/blablacar-standalone-covers-reliable-0.1.735",
            result = RESULT_COMPLETE_0734,
            totalProfiles = 1,
            totalCards = 1,
            profiles = listOf(
                BlaBlaStandaloneRideCoversProfile0734(
                    displayName = "Conta teste",
                    profileUuid = uuid,
                    identityConfirmed = true,
                    status = RESULT_COMPLETE_0734,
                    observedCardCount = 1,
                    exportedCardCount = 1,
                    reachedEnd = true,
                    stabilized = true,
                    collectionAttempts = 2,
                    recoveredTransiently = true,
                    cards = listOf(
                        BlaBlaStandaloneRideCover0734(
                            tripId = "trip-recovered",
                            dateIso = "2026-11-15",
                        ),
                    ),
                ),
            ),
        )

        val decoded = decodeStandaloneRideCoversPayload0734(
            encodeStandaloneRideCoversPayload0734(payload),
        )

        assertEquals(2, decoded.profiles.single().collectionAttempts)
        assertTrue(decoded.profiles.single().recoveredTransiently)
        assertFalse(decoded.isolation.writesTimeline)
        assertFalse(decoded.isolation.writesAgenda)
        assertFalse(decoded.isolation.writesCanonicalTrips)
    }

}
