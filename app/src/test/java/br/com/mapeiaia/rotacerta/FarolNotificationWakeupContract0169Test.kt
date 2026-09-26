package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNotificationWakeupContract0169Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val xml = File("src/main/res/xml/rota_certa_accessibility.xml").readText()

    @Test
    fun notificationEventIsSubscribedAndHandledBeforeStrictRootResolution() {
        assertTrue(xml.contains("typeNotificationStateChanged"))
        val handler = service.indexOf("handleNotificationWakeup0169")
        val resolver = service.indexOf("DriverCardEventResolver0162.resolve")
        assertTrue(handler >= 0)
        assertTrue(resolver > handler)
    }

    @Test
    fun notificationWakeupIsBoundedAndHasNoPollingLoop() {
        assertTrue(service.contains("NOTIFICATION_INITIAL_RETRY_DELAY_MILLIS_0169"))
        assertTrue(service.contains("NOTIFICATION_VERIFY_DELAY_MILLIS_0169"))
        assertFalse(service.contains("while (notificationWake"))
        assertFalse(service.contains("Timer("))
    }
}
