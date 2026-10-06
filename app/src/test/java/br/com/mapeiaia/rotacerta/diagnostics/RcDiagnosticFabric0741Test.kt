package br.com.mapeiaia.rotacerta.diagnostics

import org.junit.Assert.*
import org.junit.Test

class RcDiagnosticFabric0741Test {
    @Test fun redactsSecretsBeforeStorage() {
        val out = RcPrivacyRedactor.sanitize(mapOf("token" to "abc", "route" to "SP-TC"))
        assertEquals("[REDACTED]", out["token"])
        assertEquals("SP-TC", out["route"])
    }

    @Test fun invariantFreezesEvidenceAndCreatesIncident() {
        val trace = RcDiagnosticFabric0741.newTrace("TIMELINE", "TEST")
        RcDiagnosticFabric0741.event("TIMELINE", "STATE", trace, details = mapOf("revision" to "184"))
        val incident = RcDiagnosticFabric0741.invariant("AGENDA", "REVISION_PARITY", false, trace, "184", "183")
        assertNotNull(incident)
        assertTrue(incident!!.evidence.any { it.action == "INVARIANT_VIOLATION" })
        assertTrue(RcDiagnosticFabric0741.moduleSnapshot().contains("AGENDA"))
    }

    @Test fun boundedSnapshotsAndStableDigest() {
        RcDiagnosticFabric0741.event("HEALTH", "PING")
        assertTrue(RcDiagnosticFabric0741.snapshot(limit = 1).size <= 1)
        assertEquals(64, RcDiagnosticFabric0741.diagnosticDigest().length)
    }
}
