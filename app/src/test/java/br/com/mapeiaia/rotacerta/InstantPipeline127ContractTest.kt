package br.com.mapeiaia.rotacerta

import java.io.File
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

        assertTrue("Leitura de acessibilidade precisa continuar imediata", "scheduleVisibleTextAnalysis(delayMs = 0L" in eventRegion)
        assertTrue("OCR deve ser apenas fallback", "scheduleScreenshotFallback127(resolvedPackage)" in eventRegion)
        assertFalse(
            "Evento nao deve iniciar OCR simultaneamente com a arvore de acessibilidade",
            "requestScreenshotAnalysis(allowPopupCandidate = true)" in eventRegion,
        )
        assertTrue("Fallback deve permanecer abaixo de 100 ms", "delay(90L)" in service)
        assertTrue("Leitura aceita deve cancelar OCR pendente", "accessibility_confirmed_cancel_ocr_0_1_127" in service)
    }

    @Test
    fun exactRoutesAndDestinationGeocodeRunConcurrently() {
        val service = serviceSource()
        val analysisStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
        val analysisEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", analysisStart)
        val analysisRegion = service.substring(analysisStart, analysisEnd)

        assertTrue("Geocodificacao do destino deve iniciar junto das rotas", "val destinationCoordinateDeferred128 = async" in analysisRegion)
        assertTrue("Rota principal precisa ser assincrona", "val homeRouteDeferred127 = async" in analysisRegion)
        assertTrue("Rota alternativa precisa ser assincrona", "val alternativeRouteDeferred127 = async" in analysisRegion)
        assertTrue("As rotas devem permanecer no mesmo coroutineScope", "direct_address_routes_parallel_0_1_128" in analysisRegion)
        assertTrue("Contrato historico de rotas paralelas deve permanecer", "parallel_exact_routes_0_1_127" in analysisRegion)
        assertTrue("As duas rotas devem ser aguardadas somente depois de iniciadas", "homeRouteDeferred127.await()" in analysisRegion && "alternativeRouteDeferred127.await()" in analysisRegion)
        assertFalse("Bloco sequencial antigo nao pode permanecer", "val homeRouteStartedAt" in analysisRegion)
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
