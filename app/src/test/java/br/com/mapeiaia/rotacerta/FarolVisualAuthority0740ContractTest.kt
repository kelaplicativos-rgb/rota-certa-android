package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarolVisualAuthority0740ContractTest {
    private fun serviceSource(): String {
        val candidates = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("LiveRideAccessibilityService.kt not found")
    }

    @Test
    fun confirmedResultSurvivesHeartbeatAndSnapshotNoObservation() {
        repeat(2) {
            assertEquals(
                FarolOneSecondVisualAuthority0711.Action.KEEP,
                FarolOneSecondVisualAuthority0711.decide(
                    currentAddressSignature = "ride|rua-a-10",
                    observedAddressSignature = null,
                    hasPublicResult = true,
                    nowElapsedMillis = 2_000L,
                    lastConfirmedElapsedMillis = 1_000L,
                ),
            )
        }
    }

    @Test
    fun sameAddressStaysAndNewAddressStartsNewGenerationSemantics() {
        assertEquals(
            FarolOneSecondVisualAuthority0711.Action.KEEP,
            FarolOneSecondVisualAuthority0711.decide("ride|A", "ride|A", true, 1_100L, 1_000L),
        )
        assertEquals(
            FarolOneSecondVisualAuthority0711.Action.CLEAR_THEN_PROCESS,
            FarolOneSecondVisualAuthority0711.decide("ride|A", "ride|A2", true, 1_100L, 1_000L),
        )
    }

    @Test
    fun transientNoObservationPathsPreserveInsteadOfHardClear() {
        val source = serviceSource()
        assertTrue(source.contains("preserveUniversalTwoAddressOnNoObservation0740"))
        assertTrue(source.contains("source = \"AccessibilityImmediate\""))
        assertTrue(source.contains("source = \"ContainedFailure0172\""))
        assertTrue(source.contains("source = \"NotificationWake\""))
        assertTrue(source.contains("source = \"NotificationFailure\""))
        assertTrue(source.contains("source = \"PartialAbsenceConfirmation\""))
        assertTrue(source.contains("reason0695 = \"semantic_reject_after_local_ocr_0740\""))
        assertTrue(source.contains("ausência temporal não prova ContextLost"))
        assertFalse(source.contains("Falha isolada ao confirmar oferta notificada; estado visual limpo."))
        assertFalse(source.contains("O card saiu da tela; cor e quilometros removidos."))
        assertFalse(source.contains("Tela sem dois enderecos validos por tempo suficiente; cor e quilometros removidos."))
    }

    @Test
    fun provenContextLostAndExplicitFarolOffStillClear() {
        val source = serviceSource()
        assertTrue(source.contains("Card confirmadamente ausente: outra aplicação possui a autoridade visual atual."))
        assertTrue(source.contains("hardClearUniversalTwoAddress("))
        assertTrue(source.contains("markExplicitOff(\"manual_reading_disabled_stage43\")"))
        assertTrue(source.contains("markExplicitOff(\"work_mode_disabled\")"))
        assertTrue(source.contains("showOverlay(RadarColor.Idle, null, forcePhysicalCommitStage43 = true)"))
    }

    @Test
    fun referenceChangeReusesLastAcceptedAAndNeverSearchesBOnScreen() {
        val source = serviceSource()
        assertTrue(source.contains("FAROL_REFERENCE_CHANGED_RECALCULATE_0740"))
        assertTrue(source.contains("lastAcceptedEvaluation0740"))
        assertTrue(source.contains("screenLookupOfReference=false"))
        assertTrue(source.contains("configureDriverTarget(currentSettings.homeAddress, currentSettings.homeRadiusKm)"))
        assertTrue(source.contains("sourceStage19 = \"ReferenceChanged0740\""))
        assertFalse(source.contains("observedReferenceAddress"))
    }

    @Test
    fun delayedOpenAiResultIsBoundToOriginalSessionScreenAndWindowGeneration() {
        val source = serviceSource()
        assertTrue(source.contains("val binding0695 = FarolReadBinding0187("))
        assertTrue(source.contains("sessionGeneration = session0695.generation"))
        assertTrue(source.contains("screenGeneration = universalScreenGeneration"))
        assertTrue(source.contains("windowGeneration = universalWindowGeneration"))
        assertTrue(source.contains("isReadBindingFresh0187(binding0695)"))
        assertTrue(source.contains("FAROL_PAID_AI_STALE_DROPPED_0740"))
    }

    @Test
    fun accessibilityFallsBackToOcrAndOcrCanEscalateToExistingOpenAiPipeline() {
        val source = serviceSource()
        assertTrue(source.contains("scheduleScreenshotFallback127("))
        assertTrue(source.contains("schedulePaidAiAddressFallback0695("))
        assertTrue(source.contains("source0695 = \"openai\""))
        assertTrue(source.contains("processRideText("))
        assertTrue(source.contains("downstreamRouteAndColorLocal=true"))
    }

    @Test
    fun fallbackDoesNotUseConfiguredReferenceAsScreenAddressCandidate() {
        val source = serviceSource()
        assertFalse(source.contains("observedAddress = currentSettings.homeAddress"))
        assertFalse(source.contains("destination = currentSettings.homeAddress"))
        assertFalse(source.contains("Destino: \" + currentSettings.homeAddress"))
        assertTrue(source.contains("screenLookupOfReference=false"))
    }
}
