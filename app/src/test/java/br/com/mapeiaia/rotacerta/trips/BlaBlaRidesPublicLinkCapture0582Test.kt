package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaRidesPublicLinkCapture0582Test {
    private val profileUuid = "7371f028-9c55-4903-8444-308015823efd"
    private val administrativeTripId = "01a03566-5325-7526-abd3-7f54222bcfb7"
    private val publicToken = "AaA1moQxcjiQ1-tY2UOr6kAE2q6YFjakQATP8vIER6w"
    private val administrativeUrl =
        "https://www.blablacar.com.br/rides/offer?id=$administrativeTripId&source=CARPOOLING"
    private val publicUrl =
        "https://www.blablacar.com.br/trip?source=CARPOOLING&id=$publicToken&p0%5Bac%5D=adult"

    @Test
    fun ridesSnapshotKeepsAdministrativeAndPassengerFacingLinksSeparate() {
        val links = buildRidesTripLinks0582(
            profileUuid = profileUuid,
            tripIds = listOf(administrativeTripId),
            administrativeUrlsByTripId = mapOf(administrativeTripId to administrativeUrl),
            collectorTrips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = profileUuid,
                    date = "2026-09-20",
                    trip_id = administrativeTripId,
                    trip_href = administrativeUrl,
                    public_trip_href = publicUrl,
                    public_trip_href_source = "share_action",
                    public_trip_href_binding =
                        BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
                ),
            ),
        )

        val link = links.single()
        assertEquals(administrativeUrl, link.administrativeUrl)
        assertEquals(publicUrl, link.publicTripUrl)
        assertEquals("share_action", link.publicTripUrlSource)
        assertEquals(
            BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
            link.publicTripUrlBinding,
        )
        assertEquals("COMPLETE", link.publicTripStatus)
        assertTrue(validateRidesTripLinks0582(listOf(administrativeTripId), links))
    }

    @Test
    fun administrativeOfferUrlIsNeverPromotedToPublicTripUrl() {
        val links = buildRidesTripLinks0582(
            profileUuid = profileUuid,
            tripIds = listOf(administrativeTripId),
            administrativeUrlsByTripId = mapOf(administrativeTripId to administrativeUrl),
            collectorTrips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = profileUuid,
                    date = "2026-09-20",
                    trip_id = administrativeTripId,
                    trip_href = administrativeUrl,
                    public_trip_href = administrativeUrl,
                ),
            ),
        )

        val link = links.single()
        assertTrue(link.publicTripUrl.isBlank())
        assertEquals("PENDING_UNKNOWN", link.publicTripStatus)
        assertTrue(validateRidesTripLinks0582(listOf(administrativeTripId), links))
    }

    @Test
    fun authoritativePublicTokenMayDifferFromAdministrativeTripId() {
        assertEquals(
            publicUrl,
            BlaBlaCollectorUrlModule.publicTripFromAuthoritativeOrchestratorNavigation(
                raw = publicUrl,
                expectedAdministrativeTripId = administrativeTripId,
                boundAdministrativeTripId = administrativeTripId,
            ),
        )
        assertFalse(publicToken == administrativeTripId)
    }

    @Test
    fun shareScriptAcceptsHttpPayloadThenCanonicalizesToHttps() {
        val script = File("src/main/assets/blablacar/scripts/trip_public_share.js").readText()
        assertTrue(script.contains("['http:', 'https:'].includes(url.protocol)"))
        assertTrue(script.contains("url.protocol = 'https:'"))
        assertTrue(script.contains("sourceParam && sourceParam !== 'CARPOOLING'"))
    }
}
