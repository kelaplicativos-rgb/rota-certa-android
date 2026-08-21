package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualAppScreenCapture138Test {
    @Test fun `captura manual existe mas nao participa do farol`() {
        val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val store = File("src/main/java/br/com/mapeiaia/rotacerta/ManualAppScreenCaptureStore.kt").readText()
        assertTrue(service.contains("CaptureCurrentAppAndScreen"))
        assertTrue(service.contains("ManualAppScreenCaptureStore.save"))
        assertTrue(store.contains("Nunca participa da decisão do farol"))
        assertTrue(!store.contains("DecisionEngine"))
    }
}
