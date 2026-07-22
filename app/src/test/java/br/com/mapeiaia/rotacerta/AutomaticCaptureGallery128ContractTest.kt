package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCaptureGallery128ContractTest {
    private fun sourceFile(name: String): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists)?.readText() ?: error("$name nao encontrado")

    @Test
    fun galleryLivesInsideTheSameCardModelsModule() {
        val main = sourceFile("MainActivity.kt")
        val start = main.indexOf("private fun CardModelsCard(")
        val end = main.indexOf("private fun DiagnosticExpander(", start)
        val region = main.substring(start, end)

        assertTrue("Galeria precisa ficar junto dos modelos manuais", "AutomaticRideCapturesCard128()" in region)
        assertTrue("Titulo da funcao deve ser claro", "Captura de Tela Automatica" in region)
        assertTrue("Usuario precisa visualizar os dados", "Ver detalhes" in region)
        assertTrue("Usuario precisa excluir a captura", "Apagar captura" in region)
        assertTrue("Atalho de embarque precisa existir", "Ver embarque" in region)
        assertTrue("Atalho de destino precisa existir", "Ver destino" in region)
        assertTrue("Dados precisam poder ser copiados", "Copiar dados" in region)
    }

    @Test
    fun imageReadingStaysOutsideTheLiveBubblePipeline() {
        val main = sourceFile("MainActivity.kt")
        val service = sourceFile("LiveRideAccessibilityService.kt")

        assertTrue("Preview so deve ser decodificado na galeria", "BitmapFactory.decodeFile" in main)
        assertFalse("Servico ao vivo nao pode decodificar arquivo da galeria", "BitmapFactory.decodeFile" in service)
        assertTrue("Gravacao da captura deve permanecer em IO", "scope.launch(Dispatchers.IO)" in service)
        assertTrue("Trava da captura deve continuar separada do OCR", "automatic_capture_independent_gate_0_1_128" in service)
    }

    @Test
    fun retentionIsPrivateBoundedAndPeriodicallyCleaned() {
        val store = sourceFile("AutomaticRideCaptureStore.kt")
        val service = sourceFile("LiveRideAccessibilityService.kt")

        assertTrue("Retencao padrao deve ser 14 dias", "DEFAULT_RETENTION_DAYS = 14" in store)
        assertTrue("Quantidade precisa ser limitada", "MAX_CAPTURE_COUNT = 80" in store)
        assertTrue("Arquivos devem usar filesDir privado", "appContext.filesDir" in store)
        assertFalse("Capturas nao podem ir para armazenamento externo", "getExternal" in store)
        assertTrue("Servico precisa limpar expirados periodicamente", "automatic_capture_cleanup_loop_0_1_128" in service)
        assertTrue("Intervalo de limpeza deve ser de 12 horas", "delay(12L * 60L * 60L * 1000L)" in service)
    }
}
