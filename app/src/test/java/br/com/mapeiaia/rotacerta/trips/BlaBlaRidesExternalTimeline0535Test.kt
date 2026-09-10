package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaRidesExternalTimeline0535Test {
    private val captureDate = LocalDate.of(2026, 9, 10)
    private val ezequiel = "7371f028-9c55-4903-8444-308015823efd"
    private val barbosa = "175a7068-50d8-40c3-a27a-214b9c6e0461"

    private fun card(
        position: Int,
        tripId: String,
        date: String,
        departure: String = "11:00",
        arrival: String? = "12:50",
        origin: String = "Santo André",
        destination: String = "Extrema",
    ): String = """
        <article data-testid="e2e-your-rides-trip-card-$position">
          <h2>$date</h2>
          <a href="/rides/offer?id=$tripId&amp;source=CARPOOLING">
            <p data-testid="e2e-itinerary-departure-time">$departure</p>
            ${arrival?.let { "<p data-testid=\"e2e-itinerary-arrival-time\">$it</p>" }.orEmpty()}
            <div data-testid="e2e-itinerary-departure-station"><p>$origin</p></div>
            <div data-testid="e2e-itinerary-arrival-station"><p>$destination</p></div>
          </a>
        </article>
    """.trimIndent()

    private fun project(
        uuid: String = ezequiel,
        trips: List<String>,
        html: String,
        mhtml: String = html,
    ): BlaBlaExternalTimelineProfile0535 = BlaBlaRidesExternalTimeline0535.projectProfile0535(
        profileUuid = uuid,
        accountKey = "0123456789abcdef",
        displayName = if (uuid == ezequiel) "Ezequiel" else "Barbosa",
        identityConfirmed = true,
        inventoryTripIds = trips,
        htmlRaw = html,
        mhtmlRaw = mhtml,
        captureDate = captureDate,
    )

    @Test
    fun oneCardProducesOneRecord() {
        val id = "trip_admin_0001"
        val profile = project(trips = listOf(id), html = card(0, id, "Sex. 11 Set."))
        assertEquals(1, profile.timeline.size)
        assertEquals(id, profile.timeline.single().tripId)
        assertEquals("2026-09-11", profile.timeline.single().date)
        assertEquals("CAPTURE_YEAR_UI_CONTEXT", profile.timeline.single().evidence.dateResolution)
        assertTrue(profile.reconciliation.sameTripSet)
    }

    @Test
    fun multipleCardsKeepInventoryCountAndChronologicalOrder() {
        val first = "trip_admin_0001"
        val second = "trip_admin_0002"
        val html = card(0, second, "Dom. 13 Set.") + card(1, first, "Sex. 11 Set.")
        val profile = project(trips = listOf(first, second), html = html)
        assertEquals(2, profile.timeline.size)
        assertEquals(listOf(first, second), profile.timeline.map { it.tripId })
        assertEquals(listOf(1, 0), profile.timeline.map { it.listPosition })
    }

    @Test
    fun twoProfilesRemainIsolatedAndSameTripIdHasDifferentStrongIdentity() {
        val id = "trip_shared_0001"
        val a = project(uuid = ezequiel, trips = listOf(id), html = card(0, id, "Sex. 11 Set."))
        val b = project(uuid = barbosa, trips = listOf(id), html = card(0, id, "Sex. 11 Set."))
        assertEquals(id, a.timeline.single().tripId)
        assertEquals(id, b.timeline.single().tripId)
        assertTrue(a.profileUuid != b.profileUuid)
        assertEquals("$ezequiel:$id", "${a.profileUuid}:${a.timeline.single().tripId}")
        assertEquals("$barbosa:$id", "${b.profileUuid}:${b.timeline.single().tripId}")
    }

    @Test
    fun duplicateTripIdInsideSameProfileIsExplicitError() {
        val id = "trip_admin_0001"
        val html = card(0, id, "Sex. 11 Set.") + card(1, id, "Dom. 13 Set.")
        val error = assertThrows(IllegalArgumentException::class.java) {
            project(trips = listOf(id), html = html, mhtml = "")
        }
        assertTrue(error.message.orEmpty().contains("DUPLICATE_TRIP_ID"))
    }

    @Test
    fun missingArrivalIsKnownFalseWithoutInventingTime() {
        val id = "trip_admin_0001"
        val ride = project(
            trips = listOf(id),
            html = card(0, id, "Sex. 11 Set.", arrival = null),
        ).timeline.single()
        assertEquals("", ride.arrivalTime)
        assertFalse(ride.arrivalTimeKnown)
        assertEquals("COMPLETE", ride.parseStatus)
    }

    @Test
    fun unparseableRouteBecomesPartialAndIsNeverDropped() {
        val id = "trip_admin_0001"
        val ride = project(
            trips = listOf(id),
            html = card(0, id, "Sex. 11 Set.", origin = "", destination = ""),
        ).timeline.single()
        assertEquals(id, ride.tripId)
        assertTrue(ride.cardFound)
        assertFalse(ride.routeParsed)
        assertEquals("PARTIAL", ride.parseStatus)
    }

    @Test
    fun missingCardStillReconcilesInventoryAsPartialRecord() {
        val known = "trip_admin_0001"
        val missing = "trip_admin_0002"
        val profile = project(trips = listOf(known, missing), html = card(0, known, "Sex. 11 Set."), mhtml = "")
        assertEquals(2, profile.timeline.size)
        assertTrue(profile.reconciliation.sameTripSet)
        assertEquals(listOf(missing), profile.reconciliation.missingCards)
        val partial = profile.timeline.single { it.tripId == missing }
        assertFalse(partial.cardFound)
        assertEquals("PARTIAL", partial.parseStatus)
    }

    @Test
    fun consistentHtmlAndMhtmlAreMarkedAsDualEvidence() {
        val id = "trip_admin_0001"
        val html = card(0, id, "Sex. 11 Set.")
        val ride = project(trips = listOf(id), html = html, mhtml = html).timeline.single()
        assertTrue(ride.evidence.html)
        assertTrue(ride.evidence.mhtml)
        assertTrue(ride.inconsistencies.isEmpty())
    }

    @Test
    fun divergentHtmlMhtmlIsExplicitAndConflictingFieldIsNotChosenSilently() {
        val id = "trip_admin_0001"
        val html = card(0, id, "Sex. 11 Set.", destination = "Extrema")
        val mhtml = card(0, id, "Sex. 11 Set.", destination = "Pouso Alegre")
        val ride = project(trips = listOf(id), html = html, mhtml = mhtml).timeline.single()
        assertEquals("", ride.destination)
        assertTrue(ride.inconsistencies.contains("HTML_MHTML_DESTINATION_MISMATCH"))
        assertEquals("PARTIAL", ride.parseStatus)
    }

    @Test
    fun continuityConfirmedUnknownAndConflictAreSeparated() {
        val a = BlaBlaExternalTimelineRide0535("trip_a_0001", destination = "Extrema", origin = "Santo André")
        val b = BlaBlaExternalTimelineRide0535("trip_b_0002", origin = "Extrema", destination = "Pouso Alegre")
        val c = BlaBlaExternalTimelineRide0535("trip_c_0003", origin = "", destination = "")
        val d = BlaBlaExternalTimelineRide0535("trip_d_0004", origin = "São Paulo", destination = "Santos")
        val results = analyzeContinuity0535(listOf(a, b, c, d))
        assertEquals("CONTINUITY_CONFIRMED", results[0].classification)
        assertEquals("CONTINUITY_UNKNOWN", results[1].classification)
        assertEquals("CONTINUITY_UNKNOWN", results[2].classification)
        val conflict = analyzeContinuity0535(listOf(b, d)).single()
        assertEquals("CONTINUITY_CONFLICT", conflict.classification)
    }

    @Test
    fun alternatingMatchAndBreakAreDiagnosticOnly() {
        fun ride(id: String, date: String) = BlaBlaExternalTimelineRide0535(id, date = date)
        val match = analyzeAlternating0535(
            listOf(ride("trip_a_0001", "2026-09-11"), ride("trip_b_0002", "2026-09-13"), ride("trip_c_0003", "2026-09-15")),
        )
        val broken = analyzeAlternating0535(
            listOf(ride("trip_a_0001", "2026-09-11"), ride("trip_b_0002", "2026-09-12")),
        )
        assertEquals("ALTERNATING_MATCH", match.classification)
        assertEquals("ALTERNATING_BREAK", broken.classification)
    }

    @Test
    fun fourByTwoUsesExplicitBaseDateAndFindsNextWorkDay() {
        val cycle = BlaBlaExternalScheduleCycle0535(
            workDays = 4,
            restDays = 2,
            baseDate = LocalDate.of(2026, 9, 10),
        )
        assertTrue(cycle.isWorkDay(LocalDate.of(2026, 9, 10)))
        assertTrue(cycle.isWorkDay(LocalDate.of(2026, 9, 13)))
        assertFalse(cycle.isWorkDay(LocalDate.of(2026, 9, 14)))
        assertFalse(cycle.isWorkDay(LocalDate.of(2026, 9, 15)))
        assertTrue(cycle.isWorkDay(LocalDate.of(2026, 9, 16)))
        assertEquals(LocalDate.of(2026, 9, 16), cycle.nextWorkDay(LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun evidenceTextContainsOnlyAllowlistedExternalFields() {
        val id = "trip_admin_0001"
        val html = card(0, id, "Sex. 11 Set.") + "<span>Nome Passageiro Privado</span>"
        val ride = project(trips = listOf(id), html = html, mhtml = "").timeline.single()
        assertFalse(ride.normalizedEvidenceText.contains("Nome Passageiro Privado"))
        assertTrue(ride.normalizedEvidenceText.contains("tripId=$id"))
        assertTrue(ride.normalizedEvidenceText.contains("origin=Santo André"))
    }

    @Test
    fun explicitYearInCardIsPreservedAsExplicitEvidence() {
        val id = "trip_admin_0001"
        val ride = project(trips = listOf(id), html = card(0, id, "Qua. 30 Jun. 2027"), mhtml = "").timeline.single()
        assertEquals("2027-06-30", ride.date)
        assertTrue(ride.evidence.dateYearExplicit)
        assertEquals("EXPLICIT_YEAR", ride.evidence.dateResolution)
    }
}