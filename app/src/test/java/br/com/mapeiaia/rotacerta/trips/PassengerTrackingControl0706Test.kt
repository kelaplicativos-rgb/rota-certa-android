package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.PassengerTrackingLinkRequest0668
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerTrackingControl0706Test {
    @Test
    fun passengerWhatsappUsesTheExactCardPhone() {
        assertEquals("5511998765432", passengerWhatsAppDigits0515("(11) 99876-5432"))
        assertEquals("5535987654321", passengerWhatsAppDigits0515("+55 35 98765-4321"))
        assertEquals("5511998765432", passengerWhatsAppDigits0515("5511998765432"))

        val request = PassengerTrackingLinkRequest0668(
            tripId = "trip-0706",
            passengerKey = "passenger-0706",
            passengerName = "Passageiro",
            passengerPhone = "+55 11 99876-5432",
            destinationLatitude = -23.5505,
            destinationLongitude = -46.6333,
            destinationLabel = "Destino",
            expiresAtMillis = 1_900_000_000_000L,
        )
        assertEquals("+55 11 99876-5432", request.passengerPhone)
    }

    @Test
    fun cardShortcutIsSingleSurfaceWithTwoSecondRevocation() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt",
        ).readText()

        assertContains(source, "PassengerTrackingShortcut0676(")
        assertContains(source, "TRACKING_LONG_PRESS_MILLIS_0676 = 2_000L")
        assertContains(source, "primary.copy(alpha = 0.24f)")
        assertContains(source, "openPassengerTrackingWhatsApp0676(")
        assertContains(source, "https://wa.me/\$digits0676?text=")
        assertContains(source, "onLongPress0676 = { stopPassengerTracking0676")
        assertFalse(source.contains("Text(\"AO VIVO\""))
    }

    @Test
    fun localActiveStateOnlyClearsAfterServerAcknowledgesRevocation() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/LiveTrackingShare0668.kt",
        ).readText()

        assertContains(source, "fun isPassengerShareActive(passengerKey: String)")
        assertContains(source, "Servidor não confirmou o encerramento do acompanhamento.")
        assertTrue(source.indexOf("check(response.ok)") < source.indexOf("shares = session.shares.map"))
    }
}
