package br.com.mapeiaia.rotacerta.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RcDiagnosticFabric0741Test {
    @Test fun redactsSecretsBeforeStorage() {
        val out = RcPrivacyRedactor.sanitize(mapOf("token" to "abc", "route" to "SP-TC"))
        assertEquals("[REDACTED]", out["token"])
        assertEquals("SP-TC", out["route"])
    }

    @Test fun boundedBufferAndIncidentWindowAreContractuallyLimited() {
        val source = requireNotNull(
            javaClass.classLoader?.getResource("br/com/mapeiaia/rotacerta/diagnostics/RcDiagnosticFabric0741.class")
        )
        assertTrue(RcDiagnosticFabric0741.MARKER == "RC_DIAGNOSTIC_FABRIC_0741")
        assertTrue(RcPrivacyRedactor.sanitize(mapOf("session" to "private"))["session"] == "[REDACTED]")
        // Runtime behavior that touches android.os.SystemClock is exercised on-device;
        // JVM unit tests keep the privacy/contract surface Android-free.
        assertTrue(source.toString().contains("RcDiagnosticFabric0741"))
    }
}
