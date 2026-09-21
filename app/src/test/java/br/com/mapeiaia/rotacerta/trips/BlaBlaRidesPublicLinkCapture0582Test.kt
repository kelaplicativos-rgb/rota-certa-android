package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDateTime
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
                    public_trip_href_source = "published_offer_href",
                    public_trip_href_binding =
                        BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_PUBLISHED_OFFER_HREF,
                ),
            ),
        )

        val link = links.single()
        assertEquals(administrativeUrl, link.administrativeUrl)
        assertEquals(publicUrl, link.publicTripUrl)
        assertEquals("published_offer_href", link.publicTripUrlSource)
        assertEquals(
            BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_PUBLISHED_OFFER_HREF,
            link.publicTripUrlBinding,
        )
        assertEquals("COMPLETE", link.publicTripStatus)
        assertTrue(validateRidesTripLinks0582(listOf(administrativeTripId), links))
    }

    @Test
    fun legacyNetworkAuthoritativeLinkCannotMasqueradeAsPublishedOfferHref() {
        val provenOfferUuid = "01a08855-7959-7593-929d-ddd4f9f416b7"
        val wrongLegacyUrl =
            "https://www.blablacar.com.br/trip?source=CARPOOLING&id=AaCIVXlZdZOSnd3U-fQWt0AEz_L5OX4PQATwBBw8jCE"
        val correctPublishedUrl =
            "https://www.blablacar.com.br/trip?source=CARPOOLING&id=AaCIVXlZdZOSnd3U-fQWt0AEz_LyBEesQATwBBw8jCE"
        val offerUrl =
            "https://www.blablacar.com.br/rides/offer?id=$provenOfferUuid&source=CARPOOLING"

        val rejectedLegacy = buildRidesTripLinks0582(
            profileUuid = profileUuid,
            tripIds = listOf(provenOfferUuid),
            administrativeUrlsByTripId = mapOf(provenOfferUuid to offerUrl),
            collectorTrips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = profileUuid,
                    date = "2026-09-24",
                    trip_id = provenOfferUuid,
                    trip_href = offerUrl,
                    public_trip_href = wrongLegacyUrl,
                    public_trip_href_source = "persisted_canonical",
                    public_trip_href_binding =
                        BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
                ),
            ),
        ).single()
        assertTrue(rejectedLegacy.publicTripUrl.isBlank())
        assertEquals("PENDING_UNKNOWN", rejectedLegacy.publicTripStatus)

        val acceptedPublished = buildRidesTripLinks0582(
            profileUuid = profileUuid,
            tripIds = listOf(provenOfferUuid),
            administrativeUrlsByTripId = mapOf(provenOfferUuid to offerUrl),
            collectorTrips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = profileUuid,
                    date = "2026-09-24",
                    trip_id = provenOfferUuid,
                    trip_href = offerUrl,
                    public_trip_href = correctPublishedUrl,
                    public_trip_href_source = "published_offer_href",
                    public_trip_href_binding =
                        BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_PUBLISHED_OFFER_HREF,
                ),
            ),
        ).single()
        assertEquals(correctPublishedUrl, acceptedPublished.publicTripUrl)
        assertEquals("COMPLETE", acceptedPublished.publicTripStatus)
        assertTrue(activeRidesPublicLinksComplete0583(listOf(acceptedPublished)))
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
            BlaBlaCollectorUrlModule.publicTripFromPublishedOfferHref(
                raw = publicUrl,
                expectedAdministrativeTripId = administrativeTripId,
                boundAdministrativeTripId = administrativeTripId,
            ),
        )
        assertFalse(publicToken == administrativeTripId)
    }

    @Test
    fun expiredRideDoesNotCountAsMissingPublicLink() {
        val pending = buildRidesTripLinks0582(
            profileUuid = profileUuid,
            tripIds = listOf(administrativeTripId),
            administrativeUrlsByTripId = mapOf(administrativeTripId to administrativeUrl),
            collectorTrips = emptyList(),
        )
        val expiredRide = ParsedExternalRide0535(
            tripId = administrativeTripId,
            listPosition = 0,
            date = "2026-09-18",
            dateText = "Ontem",
            dateYearExplicit = false,
            dateResolution = "RELATIVE",
            departureTime = "19:00",
            arrivalTime = "23:30",
            origin = "Origem",
            destination = "Destino",
            status = "",
            administrativeUrl = administrativeUrl,
        )
        val resolved = applyRidesShareEligibility0583(
            links = pending,
            rides = listOf(expiredRide),
            now = LocalDateTime.of(2026, 9, 19, 16, 0),
        )

        assertEquals("EXPIRED", resolved.single().shareEligibility)
        assertEquals("PENDING_UNKNOWN", resolved.single().publicTripStatus)
        assertFalse(activeRidesPublicLinksComplete0583(resolved))
        assertTrue(validateRidesTripLinks0582(listOf(administrativeTripId), resolved))
    }

    @Test
    fun activeRideWithoutPublicLinkBlocksOperationalCompleteness() {
        val pending = buildRidesTripLinks0582(
            profileUuid = profileUuid,
            tripIds = listOf(administrativeTripId),
            administrativeUrlsByTripId = mapOf(administrativeTripId to administrativeUrl),
            collectorTrips = emptyList(),
        )
        val activeRide = ParsedExternalRide0535(
            tripId = administrativeTripId,
            listPosition = 0,
            date = "2026-09-20",
            dateText = "Amanhã",
            dateYearExplicit = false,
            dateResolution = "RELATIVE",
            departureTime = "10:30",
            arrivalTime = "16:20",
            origin = "Origem",
            destination = "Destino",
            status = "",
            administrativeUrl = administrativeUrl,
        )
        val resolved = applyRidesShareEligibility0583(
            links = pending,
            rides = listOf(activeRide),
            now = LocalDateTime.of(2026, 9, 19, 16, 0),
        )

        assertEquals("ACTIVE", resolved.single().shareEligibility)
        assertEquals("PENDING_UNKNOWN", resolved.single().publicTripStatus)
        assertFalse(activeRidesPublicLinksComplete0583(resolved))
        assertTrue(validateRidesTripLinks0582(listOf(administrativeTripId), resolved))
    }

    @Test
    fun shareScriptAcceptsHttpPayloadThenCanonicalizesToHttps() {
        val script = File("src/main/assets/blablacar/scripts/trip_public_share.js").readText()
        assertTrue(script.contains("['http:', 'https:'].includes(url.protocol)"))
        assertTrue(script.contains("url.protocol = 'https:'"))
        assertTrue(script.contains("sourceParam && sourceParam !== 'CARPOOLING'"))
        assertTrue(script.contains("installClipboardIntercept"))
        assertTrue(script.contains("shareInterceptReady"))
        assertTrue(script.contains("clipboardInterceptReady"))
        assertTrue(script.contains("copiar link"))
        assertTrue(script.contains("ver sua carona publicada"))
        assertTrue(script.contains("publishedOfferHref"))
        assertTrue(script.contains("publishedOfferLinkPresent"))
    }
    @Test
    fun shareScriptCanUseConnectedHiddenShareControlWithoutWeakeningPublicUrlProof() {
        val script = File("src/main/assets/blablacar/scripts/trip_public_share.js").readText()
        assertTrue(script.contains("const enabledShareControl = (node) =>"))
        assertTrue(script.contains("node.disabled === true"))
        assertTrue(script.contains("aria-disabled"))
        assertTrue(script.contains("const allShareControls = Array.from"))
        assertTrue(script.contains("enabledShareControl(node) && shareMarkerMatches(node)"))
        assertTrue(script.contains("const visibleShareControls = allShareControls.filter(visible)"))
        assertTrue(script.contains("const hiddenShareControls = allShareControls.filter((node) => !visible(node))"))
        assertTrue(script.contains("const shareControls = visibleShareControls.concat(hiddenShareControls)"))
        assertTrue(script.contains("shareControls[0].click()"))
        assertTrue(script.contains("authoritativeSharedPublicTripUrl"))
        assertTrue(script.contains("generic page links are not public-share authority"))
    }

    @Test
    fun tripActionsMenuKeepsShareCaptureInAuthoritativeRetryPath() {
        assertTrue(
            shouldRetryPublicTripShare0583(
                readAttempts = 0,
                maxReadAttempts = 2,
                shareControlPresent = false,
                menuControlPresent = true,
                shareInterceptInstalled = true,
            ),
        )
        assertTrue(
            shouldRetryPublicTripShare0583(
                readAttempts = 0,
                maxReadAttempts = 2,
                shareControlPresent = true,
                menuControlPresent = false,
                shareInterceptInstalled = true,
            ),
        )
        assertFalse(
            shouldRetryPublicTripShare0583(
                readAttempts = 0,
                maxReadAttempts = 2,
                shareControlPresent = false,
                menuControlPresent = false,
                shareInterceptInstalled = true,
            ),
        )
        assertFalse(
            shouldRetryPublicTripShare0583(
                readAttempts = 2,
                maxReadAttempts = 2,
                shareControlPresent = false,
                menuControlPresent = true,
                shareInterceptInstalled = true,
            ),
        )
        assertFalse(
            shouldRetryPublicTripShare0583(
                readAttempts = 0,
                maxReadAttempts = 2,
                shareControlPresent = false,
                menuControlPresent = true,
                shareInterceptInstalled = false,
            ),
        )
    }

    @Test
    fun shareScriptOpensOnlyVisibleEnabledTripActionsMenuBeforeWeakSearchFallback() {
        val script = File("src/main/assets/blablacar/scripts/trip_public_share.js").readText()
        assertTrue(script.contains("const menuMarkerMatches = (node) =>"))
        assertTrue(script.contains("const menuControls = Array.from"))
        assertTrue(script.contains("visible(node)"))
        assertTrue(script.contains("enabledShareControl(node)"))
        assertTrue(script.contains("!node.closest('header, nav')"))
        assertTrue(script.contains("shareControls.length === 0"))
        assertTrue(script.contains("menuControls[0].click()"))
        assertTrue(script.contains("menuControlPresent: menuControls.length > 0"))
        assertTrue(script.contains("menuClickCount: state.menuClicks || 0"))
        assertTrue(script.contains("authoritativeSharedPublicTripUrl"))
    }

}
