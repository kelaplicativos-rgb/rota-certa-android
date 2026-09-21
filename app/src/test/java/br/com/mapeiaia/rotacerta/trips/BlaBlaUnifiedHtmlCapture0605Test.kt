package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaUnifiedHtmlCapture0605Test {
    private fun ride(
        date: String,
        departure: String = "11:00",
        arrival: String = "14:00",
        status: String = "",
    ) = ParsedExternalRide0535(
        tripId = "trip-12345678",
        listPosition = 0,
        date = date,
        dateText = date,
        dateYearExplicit = true,
        dateResolution = "EXPLICIT_YEAR",
        departureTime = departure,
        arrivalTime = arrival,
        origin = "Origem",
        destination = "Destino",
        status = status,
        administrativeUrl = "https://www.blablacar.com.br/rides/offer?id=trip-12345678",
    )

    @Test
    fun futureDayIsCaptured() {
        assertTrue(
            shouldCaptureRide0605(
                ride("2026-09-22"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(12, 0),
            ),
        )
    }

    @Test
    fun inProgressRideUsesArrivalCutoff() {
        assertTrue(
            shouldCaptureRide0605(
                ride("2026-09-21", departure = "10:00", arrival = "14:00"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(12, 0),
            ),
        )
    }

    @Test
    fun expiredRideIsNotCaptured() {
        assertFalse(
            shouldCaptureRide0605(
                ride("2026-09-21", departure = "08:00", arrival = "10:00"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(12, 0),
            ),
        )
    }

    @Test
    fun cancelledRideIsNotCaptured() {
        assertFalse(
            shouldCaptureRide0605(
                ride("2026-09-22", status = "Cancelada"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(12, 0),
            ),
        )
    }

    @Test
    fun deterministicSeatOptionsUrlKeepsStrongTripIdentity() {
        val tripId = "01a0886e-44ee-7901-95e5-b69582171242"
        val options = "https://www.blablacar.com.br/rides/offer/edit/$tripId/options"
        assertEquals(tripId, BlaBlaCollectorUrlModule.optionsTripId(options))
    }

    @Test
    fun tripDetailScriptContainsCurrentDomEvidenceFallbacks() {
        val candidates = listOf(
            File("src/main/assets/blablacar/scripts/trip_detail.js"),
            File("app/src/main/assets/blablacar/scripts/trip_detail.js"),
        )
        val script = candidates.firstOrNull(File::isFile)?.readText().orEmpty()
        assertTrue(script.isNotBlank())
        assertTrue(script.contains("strongPassengerLinks"))
        assertTrue(script.contains("/rides/offer/map"))
        assertTrue(script.contains("fallbackItineraryStops"))
        assertTrue(script.contains("optionsHref"))
    }

    @Test
    fun backgroundNeverRequestsLegacyCollector0607() {
        assertFalse(agendaBackgroundSyncRequestsCollector0430("periodic"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("admin_update_now:manual"))
        assertFalse(agendaBackgroundSyncRequestsCollector0430("recovery"))
    }

    @Test
    fun legacyResponseCannotAuthorizeCanonicalTombstones0607() {
        val legacy = BlaBlaCollectorMonthResponse(
            status = "complete",
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = true,
                global_profile_month_complete = true,
            ),
        )
        assertFalse(externalCollectorAllowsTombstones0406(legacy))
    }

    @Test
    fun htmlResponseCanAuthorizeCompleteScope0607() {
        val html = BlaBlaCollectorMonthResponse(
            status = "complete",
            authority_source_0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = true,
                global_profile_month_complete = true,
            ),
        )
        assertTrue(externalCollectorAllowsTombstones0406(html))
    }
    @Test
    fun globalAtomicCommitRequiresEveryProfileAndTripComplete0609() {
        assertTrue(
            globalHtmlAtomicCommitEligible0609(
                manifestResult = "COMPLETE",
                profileStatuses = listOf("COMPLETE", "COMPLETE"),
                tripStatuses = List(19) { "COMPLETE" },
                expectedAccountCount = 2,
                observedAccountCount = 2,
                stagedTripCount = 19,
                authoritySource = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            ),
        )
        assertFalse(
            globalHtmlAtomicCommitEligible0609(
                manifestResult = "COMPLETE",
                profileStatuses = listOf("COMPLETE", "INCOMPLETE"),
                tripStatuses = List(18) { "COMPLETE" } + "INCOMPLETE",
                expectedAccountCount = 2,
                observedAccountCount = 2,
                stagedTripCount = 19,
                authoritySource = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            ),
        )
    }

    @Test
    fun globalAtomicCommitRejectsPartialOrLegacyState0609() {
        assertFalse(
            globalHtmlAtomicCommitEligible0609(
                manifestResult = "COMPLETE",
                profileStatuses = listOf("COMPLETE", "COMPLETE"),
                tripStatuses = List(19) { "COMPLETE" },
                expectedAccountCount = 2,
                observedAccountCount = 2,
                stagedTripCount = 13,
                authoritySource = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            ),
        )
        assertFalse(
            globalHtmlAtomicCommitEligible0609(
                manifestResult = "COMPLETE",
                profileStatuses = listOf("COMPLETE", "COMPLETE"),
                tripStatuses = List(19) { "COMPLETE" },
                expectedAccountCount = 2,
                observedAccountCount = 2,
                stagedTripCount = 19,
                authoritySource = "",
            ),
        )
    }

    @Test
    fun authoritativeHtmlCompletionClearsHistoricalSkippedDebt0610() {
        assertEquals(
            0,
            effectiveSkippedTrips0610(
                authoritativeComplete = true,
                exactTarget = false,
                dateScoped = true,
                currentSkipped = 0,
                previousSkipped = 9,
            ),
        )
        assertEquals(
            0,
            effectiveSkippedTrips0610(
                authoritativeComplete = true,
                exactTarget = false,
                dateScoped = true,
                currentSkipped = 0,
                previousSkipped = 8,
            ),
        )
    }

    @Test
    fun partialDateScopedReadStillPreservesPreviousSkipped0610() {
        assertEquals(
            9,
            effectiveSkippedTrips0610(
                authoritativeComplete = false,
                exactTarget = false,
                dateScoped = true,
                currentSkipped = 0,
                previousSkipped = 9,
            ),
        )
    }

    @Test
    fun globalCaptureProfileNeverWritesSharedSessionDuringPrivateStage0610() {
        val candidates = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt"),
        )
        val source = candidates.firstOrNull(File::isFile)?.readText().orEmpty()
        val captureStart = source.indexOf("suspend fun captureProfile(")
        val targetedStart = source.indexOf("suspend fun captureSingleTrip0607(")
        assertTrue(captureStart >= 0)
        assertTrue(targetedStart > captureStart)
        val globalPath = source.substring(captureStart, targetedStart)
        assertFalse(globalPath.contains("saveSync("))
        assertFalse(globalPath.contains("stageProfile0609"))
        assertTrue(globalPath.contains("stagedTrips0610"))
    }

    @Test
    fun backgroundCanonicalWritesAreDisabledOutsideExplicitHtmlPaths0610() {
        val candidates = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt"),
        )
        val source = candidates.firstOrNull(File::isFile)?.readText().orEmpty()
        assertTrue(source.contains("val reconcileCollectorSnapshot = false"))
        assertTrue(source.contains("LEGACY_COLLECTOR_DELTA_DISABLED_0610"))
        assertTrue(source.contains("HTML_CANONICAL_RECONCILE_BLOCKED_0610"))
    }

    @Test
    fun validatedStatusWithCompleteHtmlCoverageIsCommitEligible0611() {
        assertTrue(
            htmlCanonicalResponseStatusAccepted0611(
                status = "validated",
                completeForScope = true,
                unresolvedTargetCards = 0,
                authoritySource = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            ),
        )
        assertTrue(
            htmlCanonicalResponseStatusAccepted0611(
                status = "complete",
                completeForScope = true,
                unresolvedTargetCards = 0,
                authoritySource = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            ),
        )
        assertFalse(
            htmlCanonicalResponseStatusAccepted0611(
                status = "validated",
                completeForScope = false,
                unresolvedTargetCards = 0,
                authoritySource = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            ),
        )
        assertFalse(
            htmlCanonicalResponseStatusAccepted0611(
                status = "validated",
                completeForScope = true,
                unresolvedTargetCards = 1,
                authoritySource = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            ),
        )
        assertFalse(
            htmlCanonicalResponseStatusAccepted0611(
                status = "validated",
                completeForScope = true,
                unresolvedTargetCards = 0,
                authoritySource = "",
            ),
        )
    }


    @Test
    fun currentRideRemainsCapturableThroughArrivalPlusOneHour0612() {
        val today = LocalDate.of(2026, 9, 21)
        val current = ride("2026-09-21", departure = "19:00", arrival = "23:20")
        assertTrue(shouldCaptureRide0605(current, today, LocalTime.of(23, 59)))
        assertFalse(shouldCaptureRide0605(current, today, LocalTime.of(0, 30)))
        val earlier = ride("2026-09-21", departure = "10:00", arrival = "14:00")
        assertTrue(shouldCaptureRide0605(earlier, today, LocalTime.of(14, 59)))
        assertFalse(shouldCaptureRide0605(earlier, today, LocalTime.of(15, 1)))
    }

    @Test
    fun overnightRideUsesNextDayArrivalGrace0612() {
        assertTrue(
            shouldCaptureRide0605(
                ride("2026-09-21", departure = "23:30", arrival = "01:00"),
                LocalDate.of(2026, 9, 21),
                LocalTime.of(23, 59),
            ),
        )
    }

    @Test
    fun canonicalHtmlCommitHasTombstoneRevivalAndRollback0612() {
        val agenda = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt"),
        ).firstOrNull(File::isFile)?.readText().orEmpty()
        val capture = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt"),
        ).firstOrNull(File::isFile)?.readText().orEmpty()

        assertTrue(agenda.contains("existing?.deleted == true && incomingComplete"))
        assertTrue(capture.contains("snapshotHtmlRollback0612()"))
        assertTrue(capture.contains("restoreHtmlRollback0612"))
        assertTrue(capture.contains("BLABLACAR_GLOBAL_HTML_ROLLBACK_0612"))
        assertTrue(capture.contains("BLABLACAR_GLOBAL_HTML_CANONICAL_IDENTITY_FAILED_0612"))
        assertTrue(capture.contains("passengerSegmentsResolved"))
    }

}
