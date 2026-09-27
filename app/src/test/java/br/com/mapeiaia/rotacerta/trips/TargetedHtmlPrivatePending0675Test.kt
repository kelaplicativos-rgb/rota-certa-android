package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetedHtmlPrivatePending0675Test {
    private fun evidence(
        roster: Boolean = true,
        itinerary: Boolean = true,
        segments: Boolean = true,
        seats: Int? = 4,
        publicUrl: String = "https://www.blablacar.com.br/trip/t-0675",
        expectedPrivate: Int = 3,
        resolvedPrivate: Int = 1,
    ) = BlaBlaRidesTripCapture0605(
        tripId = "t-0675",
        normalized = true,
        passengerRosterComplete = roster,
        itineraryAuthoritative = itinerary,
        passengerSegmentsResolved = segments,
        passengerDetailsExpected0653 = expectedPrivate,
        passengerDetailsResolved0653 = resolvedPrivate,
        publishedSeats = seats,
        publicTripUrl = publicUrl,
        status = "INCOMPLETE",
        errorCode = "MISSING_PASSENGER_DETAILS",
    )

    @Test
    fun privatePassengerGapAllowsExactCardCoreCommit() {
        val partial = evidence()
        assertTrue(targetedHtmlCoreOperationalComplete0675(partial))
        assertEquals(
            TargetedHtmlAcceptance0675.CORE_COMMIT_PRIVATE_PENDING,
            targetedHtmlAcceptance0675(partial, operationalComplete = false),
        )
        assertEquals(
            TargetedHtmlAcceptance0675.FULL_COMMIT,
            targetedHtmlAcceptance0675(partial, operationalComplete = true),
        )
    }

    @Test
    fun anyCoreOperationalGapStillFailsClosed() {
        assertEquals(
            TargetedHtmlAcceptance0675.REJECT,
            targetedHtmlAcceptance0675(evidence(roster = false), operationalComplete = false),
        )
        assertEquals(
            TargetedHtmlAcceptance0675.REJECT,
            targetedHtmlAcceptance0675(evidence(itinerary = false), operationalComplete = false),
        )
        assertEquals(
            TargetedHtmlAcceptance0675.REJECT,
            targetedHtmlAcceptance0675(evidence(segments = false), operationalComplete = false),
        )
        assertEquals(
            TargetedHtmlAcceptance0675.REJECT,
            targetedHtmlAcceptance0675(evidence(seats = null), operationalComplete = false),
        )
        assertEquals(
            TargetedHtmlAcceptance0675.REJECT,
            targetedHtmlAcceptance0675(evidence(publicUrl = ""), operationalComplete = false),
        )
    }

    @Test
    fun targetedPartialCommitIsIsolatedAndPreservesPrivateValues() {
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()
        val agenda = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt",
        ).readText()

        assertTrue(capture.contains("TARGETED_HTML_PRIVATE_PENDING_ACCEPTED_0675"))
        assertTrue(capture.contains("canonicalWrite=true sessionContentWrite=false"))
        assertTrue(capture.contains("TARGETED_HTML_PARTIAL_SESSION_ISOLATED_0675"))
        assertTrue(capture.contains("scope=TRIP_ONLY preserveLastPrivateValues=true"))
        assertTrue(agenda.contains("preserveCanonicalPrivateFields0675"))
        assertTrue(agenda.contains("passengerContact = incoming.passengerContact.ifBlank { existing.passengerContact }"))
        assertTrue(agenda.contains("fareMinorUnits = incoming.fareMinorUnits ?: existing.fareMinorUnits"))
        assertTrue(agenda.contains("boardingAddress = incoming.boardingAddress.ifBlank { existing.boardingAddress }"))
        assertTrue(agenda.contains("dropoffAddress = incoming.dropoffAddress.ifBlank { existing.dropoffAddress }"))
        assertTrue(agenda.contains("TARGET_CARD_CORE_COMMIT_PRIVATE_PENDING_0675"))
        assertTrue(agenda.contains("privateFieldsPreserved=true"))
    }

    @Test
    fun globalCaptureStillRequiresPrivateCompleteness() {
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()
        val globalCompletion = capture.substringAfter("val operationalComplete =")
            .substringBefore("val missing = buildList")
        assertTrue(globalCompletion.contains("passengerDetailsComplete0653"))
        assertFalse(globalCompletion.contains("targetedHtmlCoreOperationalComplete0675"))
    }
}
