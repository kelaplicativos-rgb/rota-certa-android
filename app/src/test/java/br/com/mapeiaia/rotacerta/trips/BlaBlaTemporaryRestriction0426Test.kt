package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaTemporaryRestriction0426Test {
    @Test
    fun portugueseRestrictionFixtureIsSemanticAndExtractsIncidentReference() {
        val incident = "d7eb9bab-99a5-0f1b-6d21-d3708fb3e817"
        val detection = BlaBlaSourceAccessDetector0426.detect(
            BlaBlaSourceAccessProbe0426(
                finalUrl = "https://www.blablacar.com.br/rides",
                title = "Acesso temporariamente restrito",
                bodyText = """
                    O acesso está temporariamente restrito.
                    Isso pode acontecer por velocidade excessiva de navegação/cliques,
                    JavaScript não funcionando adequadamente ou comportamento automatizado detectado na rede.
                    Incidente: $incident
                """.trimIndent(),
                httpStatus = 403,
            ),
        )

        assertEquals(BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED, detection.status)
        assertEquals(incident, detection.incidentReference)
        assertTrue(detection.detector.isNotBlank())
    }

    @Test
    fun restrictionClassificationDoesNotDependOnlyOnLocalizedPhrase() {
        val detection = BlaBlaSourceAccessDetector0426.detect(
            BlaBlaSourceAccessProbe0426(
                finalUrl = "https://www.blablacar.com.br/rides",
                title = "Too many requests",
                bodyText = "Reference 11111111-2222-3333-4444-555555555555",
                httpStatus = 429,
            ),
        )
        assertEquals(BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED, detection.status)
        assertEquals("main_frame_http_429", detection.detector)
    }

    @Test
    fun genericForbiddenOrNormalPageDoesNotBecomeTemporaryRestriction() {
        val forbidden = BlaBlaSourceAccessDetector0426.detect(
            BlaBlaSourceAccessProbe0426(
                finalUrl = "https://www.blablacar.com.br/rides",
                title = "Forbidden",
                bodyText = "You do not have access to this resource.",
                httpStatus = 403,
            ),
        )
        val normal = BlaBlaSourceAccessDetector0426.detect(
            BlaBlaSourceAccessProbe0426(
                finalUrl = "https://www.blablacar.com.br/rides",
                title = "Suas viagens",
                bodyText = "Próximas viagens Viagens anteriores",
                httpStatus = 200,
            ),
        )
        assertEquals(BlaBlaSourceAccessStatus0426.AVAILABLE, forbidden.status)
        assertEquals(BlaBlaSourceAccessStatus0426.AVAILABLE, normal.status)
    }

    @Test
    fun sourceAccessCircuitMetadataSerializesWithoutTouchingTrips() {
        val trip = BlaBlaCollectorTrip(
            profile_uuid = "11111111-1111-4111-8111-111111111111",
            date = "2030-09-10",
            departure_time = "11:00",
            search_from = "Origem",
            search_to = "Destino",
            actual_departure = "Origem",
            actual_arrival = "Destino",
            trip_href = "https://www.blablacar.com.br/rides/offer/admin-trip-0426",
            public_trip_href = "https://www.blablacar.com.br/trip?id=public-trip-0426",
            trip_id = "admin-trip-0426",
            availability = "available",
        )
        val snapshot = BlaBlaDynamicSessionSnapshot(
            accountId = "account-0426",
            profileUuid = trip.profile_uuid,
            identityVerified = true,
            trips = listOf(trip),
            sourceAccessStatus0426 = BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED,
            sourceAccessDetector0426 = "temporary_restriction_text+incident_reference",
            sourceRestrictionCount0426 = 1,
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<BlaBlaDynamicSessionSnapshot>(json.encodeToString(snapshot))

        assertEquals(snapshot.trips, restored.trips)
        assertEquals(BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED, restored.sourceAccessStatus0426)
        assertEquals(1, restored.sourceRestrictionCount0426)
    }

    @Test
    fun restrictedHandlerPreservesSnapshotAndCannotPublishEmptyCollection() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val start = source.indexOf("private fun handleTemporaryRestriction0426")
        val end = source.indexOf("private fun handleMainFrameTransportFailure0426", start)
        val handler = source.substring(start, end)

        assertTrue(handler.contains("store.markTemporarilyRestricted0426"))
        assertFalse(handler.contains("saveFinalSnapshotOnce"))
        assertFalse(handler.contains("saveProgressSnapshot"))
        assertFalse(handler.contains("publishCurrentSessions"))
        assertTrue(handler.contains("TEMPORARILY_RESTRICTED"))
    }

    @Test
    fun automaticCircuitStopsNavigationAndDoesNotRepublishOnRestriction() {
        val coordinator = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAutomaticCollection0400.kt").readText()
        val background = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBackgroundSync0392.kt").readText()

        assertTrue(coordinator.contains("isSourceCircuitOpen0426(account)"))
        assertTrue(coordinator.contains("externalNavigationStarted=false"))
        assertTrue(coordinator.contains("action=stop_profile_continue_batch"))
        assertTrue(coordinator.contains("action=continue_other_profiles"))
        assertTrue(coordinator.contains("onAccountTemporarilyRestricted0426"))
        val terminalStart = coordinator.indexOf("fun onAccountTemporarilyRestricted0426")
        val terminalEnd = coordinator.indexOf("fun onAccountTransientFailure0426", terminalStart)
        assertFalse(coordinator.substring(terminalStart, terminalEnd).contains("publishCurrentSessions"))
        assertTrue(background.contains("gate=runPendingHeadless"))
        assertTrue(background.contains("externalNavigationForOpenCircuit=false"))
        assertFalse(background.contains("filterNot(dynamicSessionStore::isSourceCircuitOpen0426)"))
    }

    @Test
    fun sameProfileUsesSingleFlightAndNoParallelSchedulerWasAdded() {
        val session = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorSessionModule.kt").readText()
        val browser = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()

        assertTrue(session.contains("externalFlightOwners0426.putIfAbsent"))
        assertTrue(session.contains("profile:"))
        assertTrue(browser.contains("tryAcquireExternalFlight0426"))
        assertTrue(browser.contains("externalFlightLease0426 != null"))
        val acquireStart = browser.indexOf("private fun acquireExternalFlight0426")
        val acquireEnd = browser.indexOf("private fun releaseExternalFlight0426", acquireStart)
        val acquire = browser.substring(acquireStart, acquireEnd)
        assertTrue(acquire.indexOf("phase != Phase.IDLE") < acquire.indexOf("externalFlightLease0426 != null"))
        assertTrue(acquire.contains("action=ignore_duplicate_trigger"))
        assertTrue(browser.contains("manage_browser"))
        assertTrue(browser.contains("interactive_browser"))
        assertTrue(browser.contains("BLABLACAR_PROFILE_SINGLE_FLIGHT_DEDUPED_0426"))
        assertFalse(session.contains("WorkManager"))
        assertFalse(session.contains("CoroutineWorker"))
    }

    @Test
    fun webViewKeepsLegitimateSessionFeaturesWithoutFingerprintSpoofing() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()

        assertTrue(source.contains("setAcceptCookie(true)"))
        assertTrue(source.contains("setAcceptThirdPartyCookies(webView, true)"))
        assertTrue(source.contains("webView.settings.javaScriptEnabled = true"))
        assertTrue(source.contains("webView.settings.domStorageEnabled = true"))
        assertTrue(source.contains("WebViewCompat.setProfile(webView, account.webProfileName)"))
        assertFalse(source.contains("userAgentString ="))
        assertFalse(source.contains("navigator.webdriver"))
    }

    @Test
    fun publicUrlUsesLoadedStructuredSourceBeforeAnyFallbackAndShareDoesNotLoop() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val network = source.indexOf("val networkPublicLink =")
        val share = source.indexOf("PUBLIC_TRIP_LINK_SHARE_FALLBACK")
        val exactSearch = source.indexOf("beginExactPublicTripSearch")

        assertTrue(network >= 0)
        assertTrue(share > network)
        assertTrue(exactSearch > share)
        assertTrue(source.contains("private const val MAX_PUBLIC_TRIP_SHARE_READ_ATTEMPTS = 0"))
        assertTrue(source.contains("networkFirst="))
    }

    @Test
    fun productionDetectorDoesNotHardcodeFixtureIncident() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorSessionModule.kt").readText()
        assertFalse(source.contains("d7eb9bab-99a5-0f1b-6d21-d3708fb3e817"))
    }
}
