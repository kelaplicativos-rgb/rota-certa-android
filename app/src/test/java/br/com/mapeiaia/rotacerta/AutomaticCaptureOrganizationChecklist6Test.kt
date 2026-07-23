package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCaptureOrganizationChecklist6Test {
    @Test
    fun `mesmos enderecos ignoram mudanca de preco e horario`() {
        val fields = RideFields(
            pickup = "Rua A, 10",
            destination = "Avenida B, 200",
        )
        val first = AutomaticRideCapturePolicy.semanticHash(
            packageName = "sinet.startup.indriver",
            text = "Rua A, 10\nAvenida B, 200\nR$ 18,00\n10:30",
            fields = fields,
        )
        val second = AutomaticRideCapturePolicy.semanticHash(
            packageName = "sinet.startup.indriver",
            text = "Rua A, 10\nAvenida B, 200\nR$ 25,00\n10:31",
            fields = fields,
        )
        assertEquals(first, second)
    }

    @Test
    fun `candidata expira antes do card reconhecido`() {
        val created = 1_000L
        assertEquals(
            created + AutomaticRideCapturePolicy.CANDIDATE_RETENTION_MILLIS,
            AutomaticRideCapturePolicy.expiresAt(AutomaticRideCaptureKind.Candidate, created),
        )
        assertEquals(
            created + AutomaticRideCapturePolicy.RETENTION_MILLIS,
            AutomaticRideCapturePolicy.expiresAt(AutomaticRideCaptureKind.Matched, created),
        )
        assertTrue(
            AutomaticRideCapturePolicy.CANDIDATE_RETENTION_MILLIS <
                AutomaticRideCapturePolicy.RETENTION_MILLIS,
        )
    }

    @Test
    fun `imagem pequena ou sem destino nao vira captura`() {
        assertFalse(AutomaticRideCapturePolicy.isUseful(RideFields(destination = null), 1080, 1920))
        assertFalse(AutomaticRideCapturePolicy.isUseful(RideFields(destination = "Rua B"), 120, 1920))
        assertTrue(AutomaticRideCapturePolicy.isUseful(RideFields(destination = "Rua B"), 1080, 1920))
    }
}
