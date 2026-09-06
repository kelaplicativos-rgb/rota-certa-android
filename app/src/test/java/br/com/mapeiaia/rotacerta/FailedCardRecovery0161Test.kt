package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedCardRecovery0161Test {
    private val packageName = "sinet.startup.indriver"
    private val selected = setOf(packageName)

    @Test
    fun recognizedCardNeverStartsExtraCapture() {
        val gate = FailedCardAutoCaptureGate0161()
        assertFalse(
            gate.tryStart(
                signature = "recognized",
                probableCard = true,
                parserActive = true,
                routeInFlight = false,
                hasDecision = false,
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun failedProbableCardStartsOnlyOnceAndNewCardIsReleased() {
        val gate = FailedCardAutoCaptureGate0161()
        assertTrue(gate.tryStart("card-a", true, false, false, false, 1_000L))
        gate.finish("card-a", 1_200L)
        assertFalse(gate.tryStart("card-a", true, false, false, false, 2_000L))
        assertTrue(gate.tryStart("card-b", true, false, false, false, 2_100L))
    }

    @Test
    fun timeoutReleasesADeadScreenshotReservation() {
        val gate = FailedCardAutoCaptureGate0161(lockTimeoutMillis = 500L)
        assertTrue(gate.tryStart("dead", true, false, false, false, 100L))
        assertFalse(gate.tryStart("dead", true, false, false, false, 400L))
        assertTrue(gate.tryStart("dead", true, false, false, false, 700L))
    }

    @Test
    fun routeOrDecisionAlwaysHasPriority() {
        val gate = FailedCardAutoCaptureGate0161()
        assertFalse(gate.tryStart("route", true, false, true, false, 1_000L))
        assertFalse(gate.tryStart("decision", true, false, false, true, 1_000L))
    }

    @Test
    fun accessibilityAndOcrAreMergedToRecoverTwoAddresses() {
        val result = FailedCardRecoveryEngine0161.recover(
            packageName = packageName,
            savedPackages = selected,
            accessibilityText = """
                Pedido de viagem
                10 min
                5,4 km
                R$ 32,00
                Origem
                Rua Miguel Martins Lisboa, 140 - São Paulo - SP
            """.trimIndent(),
            ocrText = """
                Destino
                Avenida Nordestina, 6680 - São Paulo - SP
            """.trimIndent(),
            nodes = emptyList(),
        )

        assertNotNull(result)
        assertEquals("Rua Miguel Martins Lisboa, 140 - São Paulo - SP", result?.fields?.pickup)
        assertEquals("Avenida Nordestina, 6680 - São Paulo - SP", result?.fields?.destination)
        assertEquals("acessibilidade_mais_ocr", result?.strategy)
    }

    @Test
    fun labeledLocationsCreateAHighConfidenceLocalModel() {
        val text = """
            Pedido de viagem
            12 min
            8,1 km
            R$ 41,00
            Origem
            Jardim Aurora, São Paulo - SP
            Destino
            Parque Guaianazes, São Paulo - SP
            Aceitar
        """.trimIndent()

        val result = FailedCardRecoveryEngine0161.recover(
            packageName = packageName,
            savedPackages = selected,
            accessibilityText = text,
            ocrText = "",
            nodes = emptyList(),
        )

        assertNotNull(result)
        assertEquals("marcadores_confirmados", result?.strategy)
        assertTrue((result?.confidence ?: 0) >= 90)
        assertNotNull(result?.modelCandidate)
        val fieldsFromModel = FailedCardRecoveryEngine0161.recoverWithModel(text, result!!.modelCandidate!!)
        assertEquals("Jardim Aurora, São Paulo - SP", fieldsFromModel?.pickup)
        assertEquals("Parque Guaianazes, São Paulo - SP", fieldsFromModel?.destination)
    }

    @Test
    fun ambiguousDestinationNeverInventsARoute() {
        val result = FailedCardRecoveryEngine0161.recover(
            packageName = packageName,
            savedPackages = selected,
            accessibilityText = """
                Pedido de viagem
                9 min
                7 km
                R$ 35,00
                Origem
                Jardim Aurora, São Paulo - SP
                Destino
                Parque Guaianazes, São Paulo - SP
                Destino
                Vila Matilde, São Paulo - SP
            """.trimIndent(),
            ocrText = "",
            nodes = emptyList(),
        )

        assertNull(result)
    }

    @Test
    fun androidAutoAndSystemUiCannotReplaceASelectedRoot() {
        assertTrue(
            TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot(
                eventPackageName = "com.google.android.projection.gearhead",
                rootPackageName = packageName,
                selectedPackages = selected,
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
        assertTrue(
            TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot(
                eventPackageName = "com.android.systemui",
                rootPackageName = packageName,
                selectedPackages = selected,
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
        assertFalse(
            TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot(
                eventPackageName = "com.openai.chatgpt",
                rootPackageName = "com.openai.chatgpt",
                selectedPackages = selected,
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }

    @Test
    fun signatureChangesWhenCardOrWindowChanges() {
        val first = FailedCardRecoveryEngine0161.signature(packageName, 10, "Origem A\nDestino B", emptyList())
        val same = FailedCardRecoveryEngine0161.signature(packageName, 10, "Origem A\nDestino B", emptyList())
        val otherCard = FailedCardRecoveryEngine0161.signature(packageName, 10, "Origem A\nDestino C", emptyList())
        val otherWindow = FailedCardRecoveryEngine0161.signature(packageName, 11, "Origem A\nDestino B", emptyList())
        assertEquals(first, same)
        assertTrue(first != otherCard)
        assertTrue(first != otherWindow)
    }
}
