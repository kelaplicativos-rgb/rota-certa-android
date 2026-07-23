package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticRideCapture129Test {
    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists) ?: error("$name nao encontrado")

    private fun sampleCapture(
        createdAt: Long = 1_000L,
        expiresAt: Long = createdAt + AutomaticRideCapturePolicy.RETENTION_MILLIS,
        packageName: String = RideCardTemplateMatcher.INDRIVE_PACKAGE,
        textHash: Int = 42,
    ) = AutomaticRideCapture(
        id = "capture",
        createdAtMillis = createdAt,
        expiresAtMillis = expiresAt,
        packageName = packageName,
        imageFileName = "capture.jpg",
        textHash = textHash,
        textPreview = "Pedido de viagem",
    )

    @Test
    fun retentionIsFourteenDaysAndExpirationIsDeterministic() {
        assertTrue(AutomaticRideCapturePolicy.RETENTION_DAYS == 14)
        val capture = sampleCapture()
        assertFalse(AutomaticRideCapturePolicy.isExpired(capture, capture.expiresAtMillis - 1L))
        assertTrue(AutomaticRideCapturePolicy.isExpired(capture, capture.expiresAtMillis))
    }

    @Test
    fun duplicateNeedsSamePackageAndSameNormalizedTextHash() {
        val capture = sampleCapture()
        assertTrue(AutomaticRideCapturePolicy.isDuplicate(capture, RideCardTemplateMatcher.INDRIVE_PACKAGE, 42))
        assertFalse(AutomaticRideCapturePolicy.isDuplicate(capture, RideCardTemplateMatcher.UBER_PACKAGE, 42))
        assertFalse(AutomaticRideCapturePolicy.isDuplicate(capture, RideCardTemplateMatcher.INDRIVE_PACKAGE, 43))
    }

    @Test
    fun generatedServiceDefersMatchedCaptureUntilAfterFarolAndNeverBlocksRoute() {
        val service = sourceFile("LiveRideAccessibilityService.kt").readText()
        val manualGate = service.indexOf("manual_registered_card_gate_0_1_127")
        val deferred = service.indexOf("automatic_capture_after_farol_final_checklist_6")
        val routeLaunch = service.indexOf("universalRouteJob = scope.launch", deferred)
        val applyResult = service.indexOf("private suspend fun applyUniversalTwoAddressResult")
        val overlay = service.indexOf("overlay_before_storage_final_checklist_6", applyResult)
        val releaseCapture = service.indexOf("releaseMatchedCaptureFinalChecklist6", overlay)
        val helperStart = service.indexOf("private fun requestAutomaticRideCapture129(")
        val helperEnd = service.indexOf("private fun requestScreenshotAnalysis(", helperStart)
        val helper = service.substring(helperStart, helperEnd)

        assertTrue("portaria manual deve vir antes de memorizar a captura", manualGate >= 0 && deferred > manualGate)
        assertTrue("rota deve iniciar sem aguardar screenshot", routeLaunch > deferred)
        assertTrue("captura deve ser liberada somente depois do overlay", overlay > applyResult && releaseCapture > overlay)
        assertTrue("captura precisa recusar rota ativa", "universalRouteJob?.isActive == true" in helper)
        assertTrue("gravação precisa ocorrer em IO", "scope.launch(Dispatchers.IO)" in helper)
        assertFalse("captura automática não deve executar OCR", "ocrService.extractText" in helper)
        assertFalse(
            "assinatura antiga não pode continuar no caminho crítico",
            "requestAutomaticRideCapture129(\n                snapshotText" in service,
        )
    }

    @Test
    fun storageIsPrivateDeduplicatedAndUiSeparatesCandidatesFromMatchedCards() {
        val store = sourceFile("AutomaticRideCaptureStore.kt").readText()
        val main = sourceFile("MainActivity.kt").readText()

        assertTrue("imagem deve ficar em filesDir privado", "appContext.filesDir" in store)
        assertTrue("capturas repetidas precisam ser deduplicadas", "AutomaticRideCapturePolicy.isDuplicate" in store)
        assertTrue("arquivos antigos precisam ser removidos", "cleanupExpired" in store)
        assertTrue("limite evita consumo indefinido", "MAX_CAPTURES = 30" in store)
        assertTrue("candidatas devem ter retenção menor", "CANDIDATE_RETENTION_DAYS = 7" in store)
        assertTrue("galeria deve estar dentro de Modelos de cards", "automatic_capture_gallery_inside_models_0_1_129" in main)
        assertTrue("candidatas devem poder virar modelo", "Confirmar e cadastrar como modelo" in main)
        assertTrue("reconhecidos não devem duplicar modelo", "não criam modelos duplicados" in main)
        assertTrue("detalhes devem ter atalho de mapa", "Abrir destino no mapa" in main)
        assertTrue("detalhes devem poder ser copiados", "Copiar detalhes" in main)
    }
}
