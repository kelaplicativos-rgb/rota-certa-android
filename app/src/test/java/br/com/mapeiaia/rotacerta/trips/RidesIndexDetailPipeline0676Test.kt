package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RidesIndexDetailPipeline0676Test {
    private val profile = "7371f028-9c55-4903-8444-308015823efd"
    private val tripA = "01a0359e-de23-78ab-ab26-6cc973c5c3d1"
    private val tripB = "01a058be-73c8-7845-9ad2-076aaef9883c"

    private fun ride(id: String, url: String) = ParsedExternalRide0535(
        tripId = id,
        listPosition = 0,
        date = "2026-10-04",
        dateText = "4 out",
        dateYearExplicit = true,
        dateResolution = "EXPLICIT",
        departureTime = "11:20",
        arrivalTime = "16:00",
        origin = "São Paulo",
        destination = "Minas Gerais",
        status = "",
        administrativeUrl = url,
    )

    @Test
    fun relativeOfferHrefBecomesExactPersistedTripLink0676() {
        val links = directObservedTripLinks0676(
            profileUuid = profile,
            tripIds = listOf(tripA, tripB),
            observedTripHrefs = listOf(
                "/rides/offer?id=$tripA&source=CARPOOLING",
                "/rides/offer?id=$tripB&source=CARPOOLING",
            ),
            parsedRides = listOf(
                ride(tripA, "/rides/offer?id=$tripA&source=CARPOOLING"),
                ride(tripB, "/rides/offer?id=$tripB&source=CARPOOLING"),
            ),
            now = LocalDateTime.of(2026, 9, 27, 15, 0),
        )
        assertEquals(2, links.size)
        assertTrue(validateRidesTripLinks0582(listOf(tripA, tripB), links))
        assertEquals(
            "https://www.blablacar.com.br/rides/offer?id=$tripA&source=CARPOOLING",
            links.first { it.tripId == tripA }.administrativeUrl,
        )
    }

    @Test
    fun wrongTripBindingFailsClosed0676() {
        val links = directObservedTripLinks0676(
            profileUuid = profile,
            tripIds = listOf(tripA, tripB),
            observedTripHrefs = listOf("/rides/offer?id=$tripA&source=CARPOOLING"),
            parsedRides = listOf(ride(tripA, "/rides/offer?id=$tripA")),
            now = LocalDateTime.of(2026, 9, 27, 15, 0),
        )
        assertEquals(2, links.size)
        assertFalse(validateRidesTripLinks0582(listOf(tripA, tripB), links))
    }

    @Test
    fun persistedIndexLinkWinsForExactSameTrip0676() {
        val parsed = listOf(ride(tripA, "https://www.blablacar.com.br/rides/offer?id=$tripA"))
        val persisted =
            "https://www.blablacar.com.br/rides/offer?id=$tripA&source=CARPOOLING"
        val rebound = rebindParsedRidesToPersistedTripLinks0676(
            rides = parsed,
            links = listOf(
                BlaBlaRidesTripLink0582(
                    tripId = tripA,
                    administrativeUrl = persisted,
                ),
            ),
        )
        assertEquals(persisted, rebound.single().administrativeUrl)
    }

    @Test
    fun mismatchedPersistedLinkCanNeverReplaceParsedRide0676() {
        val original = "https://www.blablacar.com.br/rides/offer?id=$tripA"
        val rebound = rebindParsedRidesToPersistedTripLinks0676(
            rides = listOf(ride(tripA, original)),
            links = listOf(
                BlaBlaRidesTripLink0582(
                    tripId = tripA,
                    administrativeUrl = "https://www.blablacar.com.br/rides/offer?id=$tripB",
                ),
            ),
        )
        assertEquals(original, rebound.single().administrativeUrl)
    }

    @Test
    fun detailFailureTaxonomyIsSpecificAndRetryable0676() {
        assertEquals(
            "TRIP_DETAIL_LOAD_FAILED_0676",
            tripDetailVerificationError0676(false, false, false, tripA, null, 0),
        )
        assertEquals(
            "TRIP_DETAIL_PAYLOAD_DECODE_FAILED_0676",
            tripDetailVerificationError0676(true, true, false, tripA, null, 0),
        )
        assertEquals(
            "TRIP_DETAIL_ID_MISMATCH_0676",
            tripDetailVerificationError0676(true, true, true, tripA, tripB, 100),
        )
        assertEquals(
            "TRIP_DETAIL_DOM_EMPTY_0676",
            tripDetailVerificationError0676(true, true, true, tripA, tripA, 0),
        )
        assertEquals(
            "",
            tripDetailVerificationError0676(true, true, true, tripA, tripA, 100),
        )
        listOf(
            "TRIP_DETAIL_LOAD_FAILED_0676",
            "TRIP_DETAIL_PAYLOAD_DECODE_FAILED_0676",
            "TRIP_DETAIL_ID_MISMATCH_0676",
            "TRIP_DETAIL_DOM_EMPTY_0676",
        ).forEach { assertTrue(shouldRetryTripDetailFailure0676(it), it) }
    }

    @Test
    fun sourceNoLongerWritesEmptyTripLinksOrGenericDetailFailure0676() {
        val direct = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDirectAccountCapture0608.kt",
        ).readText()
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()
        assertTrue(direct.contains("tripLinks = initialTripLinks0676"))
        assertTrue(direct.contains("BLABLACAR_RIDES_INDEX_LINKS_PERSISTED_0676"))
        assertFalse(direct.contains("tripLinks = emptyList()"))
        assertTrue(capture.contains("rebindParsedRidesToPersistedTripLinks0676"))
        assertTrue(capture.contains("BLABLACAR_TRIP_DETAIL_REJECTED_0676"))
        assertFalse(capture.contains("TRIP_DETAIL_HTML_UNVERIFIED"))
    }
}
