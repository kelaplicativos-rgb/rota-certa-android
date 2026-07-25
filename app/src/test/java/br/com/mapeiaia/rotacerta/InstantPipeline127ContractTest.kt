package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantPipeline127ContractTest {
    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists) ?: error("$name nao encontrado")

    private fun serviceSource(): String = sourceFile("LiveRideAccessibilityService.kt").readText()

    @Test
    fun accessibilityRunsBeforeOcrFallback() {
        val service = serviceSource()
        val eventStart = service.indexOf("override fun onAccessibilityEvent(")
        val eventEnd = service.indexOf("override fun onInterrupt()", eventStart)
        val eventRegion = service.substring(eventStart, eventEnd)

        assertTrue("Leitura de acessibilidade precisa processar imediatamente", "CoroutineStart.UNDISPATCHED" in eventRegion)
        assertTrue("OCR deve ser apenas fallback", "scheduleScreenshotFallback127(resolvedPackage)" in eventRegion)
        assertFalse("Evento nao deve solicitar screenshot diretamente", "requestScreenshotAnalysis(allowPopupCandidate = true)" in eventRegion)
        assertTrue("Fallback precisa usar o orçamento centralizado", "delay(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS)" in service)
        assertTrue("Leitura aceita deve cancelar OCR pendente", "screenshotFallbackJob127?.cancel()" in eventRegion)
    }

    @Test
    fun exactRoutesForConfiguredTargetsUseOneAddressMatrixCall() {
        val service = serviceSource()
        val analysisStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val analysisEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", analysisStart)
        val analysisRegion = service.substring(analysisStart, analysisEnd)

        assertTrue("Casa e alfinetes precisam compartilhar a mesma matriz", "single_exact_route_matrix_checklist_13" in analysisRegion)
        assertTrue("A origem precisa ser o último endereço captado", "originAddress = fields.destination.orEmpty()" in analysisRegion)
        assertTrue("Alvos previamente resolvidos precisam alimentar a matriz", "targetsChecklist13.destinations" in analysisRegion)
        assertTrue("Decisão final precisa considerar Casa e alfinetes", "decideFastWorkRegionChecklist13(" in analysisRegion)
        assertEquals(1, Regex("drivingDistancesFromAddressKm\\(").findAll(analysisRegion).count())
        assertFalse("Chamadas sequenciais por alvo não podem voltar", "routeDistanceKm(" in analysisRegion)
        assertFalse("Linha reta nao pode liberar verde", "fastInsideResult" in analysisRegion)
    }

    @Test
    fun diagnosticRetentionDoesNotShiftTheWholeList() {
        val diagnostic = sourceFile("DiagnosticLogStore.kt").readText()

        assertTrue("Fila de diagnostico deve ser constante", "ArrayDeque<String>" in diagnostic)
        assertTrue("Entrada deve ser adicionada no fim", "events.addLast" in diagnostic)
        assertTrue("Descarte deve remover somente o primeiro item", "events.removeFirst()" in diagnostic)
        assertFalse("Lista antiga deslocava todos os eventos", "events.removeAt(0)" in diagnostic)
    }
}
