package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaBrowserOrchestrator0318Test {
    private val base = BlaBlaBrowserExecutionContext(
        accountId = "account-a",
        expectedProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
        syncGeneration = 7,
        navigationGeneration = 3,
        cardKey = "trip|abc",
        tripId = "abc",
        passengerKey = "passenger|one",
    )

    @Test
    fun onlyOneBrowserRequestIsAuthoritativeAtATime() {
        val orchestrator = BlaBlaBrowserOrchestrator()
        val first = orchestrator.start(BlaBlaBrowserRequest.RIDE_LIST, base, "list")
        assertTrue(orchestrator.isCurrent(first, base))
        val second = orchestrator.start(BlaBlaBrowserRequest.TRIP_DETAIL, base, "detail")
        assertFalse(orchestrator.isCurrent(first, base))
        assertTrue(orchestrator.isCurrent(second, base))
    }

    @Test
    fun stalePassengerOrNavigationCallbackIsRejected() {
        val orchestrator = BlaBlaBrowserOrchestrator()
        val token = orchestrator.start(BlaBlaBrowserRequest.PASSENGER_CONTACT, base, "contact")
        assertFalse(orchestrator.isCurrent(token, base.copy(navigationGeneration = 4)))
        assertFalse(orchestrator.isCurrent(token, base.copy(passengerKey = "passenger|two")))
        assertTrue(orchestrator.isCurrent(token, base))
    }

    @Test
    fun everyDocumentedRequestHasItsOwnAssetScript() {
        val root = File("src/main/assets/blablacar/scripts")
        val missing = BlaBlaBrowserRequest.values().filterNot { request ->
            File(root, request.assetName).isFile
        }
        assertTrue("Missing scripts: $missing", missing.isEmpty())
    }

    @Test
    fun browserCollectorNoLongerEmbedsLargeRequestScriptsInline() {
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val publicSearch = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchActivity.kt").readText()
        assertFalse(dynamic.contains("private val IDENTITY_JS"))
        assertFalse(dynamic.contains("private val RIDE_LIST_JS"))
        assertFalse(dynamic.contains("private val TRIP_DETAIL_DYNAMIC_JS"))
        assertFalse(dynamic.contains("private val PASSENGER_CONTACT_JS"))
        assertFalse(publicSearch.contains("EXTRACT_PUBLIC_SEARCH_JS"))
        assertTrue(dynamic.contains("BlaBlaBrowserScriptRegistry"))
        assertTrue(dynamic.contains("BlaBlaBrowserOrchestrator"))
        assertTrue(publicSearch.contains("BlaBlaBrowserScriptRegistry"))
        assertTrue(publicSearch.contains("BlaBlaBrowserOrchestrator"))
    }

    @Test
    fun undocumentedRevealAndRosterExpansionAreNeverClickedAutomatically() {
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertFalse(dynamic.contains("CLICK_CALL_ACTION_JS"))
        assertFalse(dynamic.contains("EXPAND_ROSTER_JS"))
        assertTrue(dynamic.contains("revealAutomated=false"))
        assertTrue(dynamic.contains("ROSTER_EXPANSION_NOT_AUTOMATED"))
    }

    @Test
    fun scriptsAreNamedAfterTheDocumentedBrowserRequests() {
        val expected = setOf(
            "session_identity.js",
            "driver_profile.js",
            "driver_reviews.js",
            "ride_list.js",
            "trip_open.js",
            "trip_detail.js",
            "trip_itinerary.js",
            "passenger_roster.js",
            "passenger_open.js",
            "passenger_identity.js",
            "passenger_contact.js",
            "passenger_fare.js",
            "passenger_segment.js",
            "passenger_addresses.js",
            "trip_edit.js",
            "seat_options.js",
            "seat_change.js",
            "seat_save.js",
            "public_search_form.js",
            "public_search_results.js",
            "public_result_open.js",
            "public_driver_profile_open.js",
            "public_driver_profile.js",
            "public_driver_reviews.js",
            "message_passenger_open.js",
            "message_thread.js",
            "archived_ride_list.js",
            "archived_ride_open.js",
            "page_state.js",
            "dom_snapshot.js",
        )
        val actual = File("src/main/assets/blablacar/scripts")
            .listFiles()
            .orEmpty()
            .filter { it.extension == "js" }
            .map { it.name }
            .toSet()
        assertTrue("Missing documented scripts: ${expected - actual}", actual.containsAll(expected))
    }
    @Test
    fun placeholderRegexEscapesBothClosingBracesForAndroidIcu() {
        val registry = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaBrowserScriptRegistry.kt").readText()
        assertTrue(registry.contains("""Regex("\\{\\{[A-Z0-9_]+\\}\\}")"""))
        assertFalse(registry.contains("""Regex("\\{\\{[A-Z0-9_]+}}")"""))
    }

    @Test
    fun blablaAccountManagementPanelStillExistsBehindSyncToolbar() {
        val collectorUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt").readText()
        val timelineUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(collectorUi.contains("Text(\"Contas BlaBlaCar\")"))
        assertTrue(collectorUi.contains("Text(\"+ Adicionar conta\")"))
        assertTrue(timelineUi.contains("BlaBlaCollectorPanel("))
        assertTrue(timelineUi.contains("Sincronizar BlaBlaCar"))
    }

}
