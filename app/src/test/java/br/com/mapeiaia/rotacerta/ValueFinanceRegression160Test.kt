package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ValueFinanceRegression160Test {
    @Test
    fun captureHasGenerationWatchdogAndNeverStaysSilentlyLocked() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue(source.contains("passengerValueCaptureGeneration160"))
        assertTrue(source.contains("armPassengerValueWatchdog160"))
        assertTrue(source.contains("finishPassengerValueCapture160"))
        assertTrue(source.contains("Leitura em andamento. Aguarde um instante."))
        assertTrue(source.contains("Leitura liberada. Toque em Valor novamente."))
    }

    @Test
    fun financeUsesLazyListAndShowsPendingRevenueFirst() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/FinancialActivity.kt").readText()
        assertTrue(source.contains("LazyColumn("))
        assertTrue(source.contains("Receitas pendentes"))
        assertTrue(source.indexOf("Receitas pendentes") < source.indexOf("Resumo de hoje"))
        assertTrue(source.contains("override fun onNewIntent"))
    }
}
