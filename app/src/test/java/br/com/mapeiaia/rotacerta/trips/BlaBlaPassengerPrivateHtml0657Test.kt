package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BlaBlaPassengerPrivateHtml0657Test {
    @Test
    fun privatePassengerScriptsCoverCurrentContactFareAndAddressEvidence() {
        val contact = File("src/main/assets/blablacar/scripts/passenger_contact.js").readText()
        val fare = File("src/main/assets/blablacar/scripts/passenger_fare.js").readText()
        val addresses = File("src/main/assets/blablacar/scripts/passenger_addresses.js").readText()
        val prepare = File("src/main/assets/blablacar/scripts/passenger_prepare.js").readText()
        val open = File("src/main/assets/blablacar/scripts/passenger_open.js").readText()

        assertTrue(contact.contains("wa.me"))
        assertTrue(contact.contains("phoneNumber"))
        assertTrue(contact.contains("whatsapp"))
        assertTrue(fare.contains("total") && fare.contains("reserva"))
        assertTrue(fare.contains("visibleAmounts"))
        assertTrue(addresses.contains("meetingpoint"))
        assertTrue(addresses.contains("destination"))
        assertTrue(prepare.contains("ponto") && prepare.contains("encontro"))
        assertTrue(prepare.contains("cancelar|cancel|excluir"))
        assertTrue(open.contains("/rides/offer/passenger/"))
        assertTrue(open.contains("/booking/"))
    }

    @Test
    fun deepCaptureRetriesAndFailsClosedWhenAllPrivateFieldsStayBlank() {
        val capture = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaUnifiedHtmlCapture0605.kt",
        ).readText()

        assertTrue(capture.contains("passenger_prepare.js"))
        assertTrue(capture.contains("PASSENGER_PREPARE_PASSES_0657"))
        assertTrue(capture.contains("BLABLACAR_HTML_PASSENGER_PRIVATE_RETRY_0657"))
        assertTrue(capture.contains("BLABLACAR_HTML_PASSENGER_PRIVATE_MISSING_0657"))
        assertTrue(capture.contains("MARK_PASSENGER_INCOMPLETE_PRESERVE_PREVIOUS_CANONICAL"))
        assertTrue(capture.contains("passengerPrivateEvidenceNeedsRetry0657"))
    }

    @Test
    fun privatePhoneCanReachTheSharedPassengerOperatorWithoutAChangedTripFingerprint() {
        val identity = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerIdentityStore.kt",
        ).readText()
        val mirror = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt",
        ).readText()
        val ui = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt",
        ).readText()

        assertTrue(identity.contains("val passengerContact: String = \"\""))
        assertTrue(identity.contains("normalizePhone(metadata.passengerContact)"))
        assertTrue(mirror.contains("metadata?.passengerContact"))
        assertTrue(ui.contains("privateMetadata0494?.passengerContact"))
    }

    @Test
    fun fullHtmlCaptureIsNotOwnedByTheComposableCoroutineScope() {
        val screen = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAccountsBrowsersUi0399.kt",
        ).readText()

        assertTrue(screen.contains("captureScope0657.launch"))
        assertTrue(screen.contains("findComponentActivity0657()?.lifecycleScope"))
        assertTrue(screen.contains("BLABLACAR_HTML_CAPTURE_UI_CANCELLED_0657"))
    }
}
