from __future__ import annotations

import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
service_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
finance_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/FinancialActivity.kt"
gradle_path = root / "app/build.gradle.kts"
test_path = root / "app/src/test/java/br/com/mapeiaia/rotacerta/ValueFinanceRegression160Test.kt"

service = service_path.read_text(encoding="utf-8")
field_anchor = "    private val passengerValueCaptureInProgress159 = AtomicBoolean(false)\n"
field_replacement = """    private val passengerValueCaptureInProgress159 = AtomicBoolean(false)
    private val passengerValueCaptureGeneration160 = java.util.concurrent.atomic.AtomicLong(0L)
    private val passengerValueScreenshotOwner160 = java.util.concurrent.atomic.AtomicLong(0L)
    @Volatile private var passengerValueCaptureStartedAt160: Long = 0L
"""
if field_anchor not in service:
    raise SystemExit("0.1.160 passenger capture field anchor not found")
service = service.replace(field_anchor, field_replacement, 1)

start = service.index("    private fun copyPassengerValue159() {")
end = service.index("    private fun completePassengerValue159", start)
old_capture_section = service[start:end]
new_capture_section = r'''    private fun copyPassengerValue159() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()

        val now = System.currentTimeMillis()
        val previousGeneration = passengerValueCaptureGeneration160.get()
        val activeAge = now - passengerValueCaptureStartedAt160
        if (passengerValueCaptureInProgress159.get() && activeAge in 0L until PASSENGER_VALUE_STALE_AFTER_MS_160) {
            shortcutOverlayController.showSilentStatus159("Leitura em andamento. Aguarde um instante.", false)
            return
        }

        if (passengerValueScreenshotOwner160.compareAndSet(previousGeneration, 0L)) {
            screenshotInProgress.set(false)
        }
        val generation = passengerValueCaptureGeneration160.incrementAndGet()
        passengerValueCaptureStartedAt160 = now
        passengerValueCaptureInProgress159.set(true)
        armPassengerValueWatchdog160(generation)

        val accessibilityText = collectAllVisibleTextForCopy138()
        val immediate = PassengerValueFormatter.extract(accessibilityText)
        if (immediate != null) {
            completePassengerValue159(immediate)
            finishPassengerValueCapture160(generation)
            return
        }
        requestPassengerValueOcr159(accessibilityText, attempt = 0, generation = generation)
    }

    private fun armPassengerValueWatchdog160(generation: Long) {
        scope.launch {
            delay(PASSENGER_VALUE_WATCHDOG_MS_160)
            if (passengerValueCaptureGeneration160.get() == generation && passengerValueCaptureInProgress159.get()) {
                finishPassengerValueCapture160(generation)
                shortcutOverlayController.showSilentStatus159("Leitura liberada. Toque em Valor novamente.", false)
            }
        }
    }

    private fun finishPassengerValueCapture160(generation: Long) {
        if (passengerValueCaptureGeneration160.get() != generation) return
        passengerValueCaptureInProgress159.set(false)
        passengerValueCaptureStartedAt160 = 0L
        if (passengerValueScreenshotOwner160.compareAndSet(generation, 0L)) {
            screenshotInProgress.set(false)
        }
    }

    private fun requestPassengerValueOcr159(accessibilityText: String, attempt: Int, generation: Long) {
        if (passengerValueCaptureGeneration160.get() != generation) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finishPassengerValueCapture160(generation)
            shortcutOverlayController.showSilentStatus159("Deixe nome, rota, lugares e valor visíveis", false)
            return
        }
        if (!screenshotInProgress.compareAndSet(false, true)) {
            if (attempt < PASSENGER_VALUE_SCREENSHOT_RETRIES_160) {
                scope.launch {
                    delay(120L)
                    requestPassengerValueOcr159(accessibilityText, attempt + 1, generation)
                }
            } else {
                finishPassengerValueCapture160(generation)
                shortcutOverlayController.showSilentStatus159("Tente novamente em um instante", false)
            }
            return
        }
        passengerValueScreenshotOwner160.set(generation)
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap: Bitmap? = null
                            try {
                                if (passengerValueCaptureGeneration160.get() != generation) return@launch
                                bitmap = screenshot.toSoftwareBitmap()
                                val ocrText = bitmap?.let { ocrService.extractText(it) }.orEmpty()
                                val combined = listOf(accessibilityText, ocrText)
                                    .filter(String::isNotBlank)
                                    .joinToString("\n")
                                val data = PassengerValueFormatter.extract(combined)
                                if (data == null) {
                                    shortcutOverlayController.showSilentStatus159("Deixe nome, rota, lugares e valor visíveis", false)
                                } else {
                                    completePassengerValue159(data)
                                }
                            } finally {
                                bitmap?.recycle()
                                finishPassengerValueCapture160(generation)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        finishPassengerValueCapture160(generation)
                        shortcutOverlayController.showSilentStatus159("Não foi possível ler esta tela", false)
                    }
                },
            )
        }.onFailure {
            finishPassengerValueCapture160(generation)
            shortcutOverlayController.showSilentStatus159("Não foi possível ler esta tela", false)
        }
    }

'''
service = service[:start] + new_capture_section + service[end:]

companion_anchor = "    private companion object {\n"
if companion_anchor not in service:
    raise SystemExit("0.1.160 service companion anchor not found")
service = service.replace(
    companion_anchor,
    companion_anchor
    + "        const val PASSENGER_VALUE_STALE_AFTER_MS_160 = 4_000L\n"
    + "        const val PASSENGER_VALUE_WATCHDOG_MS_160 = 6_000L\n"
    + "        const val PASSENGER_VALUE_SCREENSHOT_RETRIES_160 = 8\n",
    1,
)
service_path.write_text(service, encoding="utf-8")

finance = finance_path.read_text(encoding="utf-8")
finance = finance.replace("import android.os.Bundle\n", "import android.content.Intent\nimport android.os.Bundle\n", 1)
finance = finance.replace(
    "import androidx.compose.foundation.layout.Arrangement\n",
    "import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.PaddingValues\n",
    1,
)
finance = finance.replace(
    "import androidx.compose.foundation.rememberScrollState\n",
    "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.rememberScrollState\n",
    1,
)
activity_anchor = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FinancialScreen(onClose = ::finish)
            }
        }
    }
"""
activity_replacement = activity_anchor + """
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
"""
if activity_anchor not in finance:
    raise SystemExit("0.1.160 FinancialActivity anchor not found")
finance = finance.replace(activity_anchor, activity_replacement, 1)

screen_start = finance.index("    Column(\n        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),")
screen_end = finance.index("\n\n    creatingType?.let", screen_start)
old_screen = finance[screen_start:screen_end]
new_screen = r'''    val pendingEntries = entries
        .filter { it.type == FinanceEntryType.REVENUE && it.status == FinanceEntryStatus.PENDING }
        .sortedByDescending(FinanceEntry::createdAtMillis)
    val otherEntries = entries
        .filterNot { it.type == FinanceEntryType.REVENUE && it.status == FinanceEntryStatus.PENDING }
        .sortedByDescending(FinanceEntry::createdAtMillis)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Controle financeiro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Text("Dados locais deste aparelho. Nenhuma informação é enviada para servidores.", style = MaterialTheme.typography.bodySmall)
        }

        if (pendingEntries.isNotEmpty()) {
            item {
                Text("Receitas pendentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(pendingEntries, key = FinanceEntry::id) { entry ->
                FinanceEntryCard(
                    entry = entry,
                    enabled = !closed,
                    onCash = { repository.markReceived(entry.id, FinancePaymentMethod.CASH); refresh() },
                    onPix = { repository.markReceived(entry.id, FinancePaymentMethod.PIX); refresh() },
                    onEdit = { editing = entry },
                    onCancel = { repository.cancel(entry.id); refresh() },
                    onDelete = { deleting = entry },
                )
            }
        } else {
            item { Text("Nenhuma receita pendente.", style = MaterialTheme.typography.bodySmall) }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Resumo de hoje", fontWeight = FontWeight.Bold)
                    Text("Previsto: ${PassengerValueFormatter.formatCurrency(summary.expectedRevenueCents)}")
                    Text("Recebido: ${PassengerValueFormatter.formatCurrency(summary.receivedRevenueCents)}")
                    Text("Pendente: ${PassengerValueFormatter.formatCurrency(summary.pendingRevenueCents)}")
                    Text("Em dinheiro: ${PassengerValueFormatter.formatCurrency(summary.cashReceivedCents)}")
                    Text("Por Pix: ${PassengerValueFormatter.formatCurrency(summary.pixReceivedCents)}")
                    Text("Despesas: ${PassengerValueFormatter.formatCurrency(summary.expensesCents)}")
                    Text("Resultado líquido: ${PassengerValueFormatter.formatCurrency(summary.netResultCents)}", fontWeight = FontWeight.Bold)
                    Text("Dinheiro em mãos: ${PassengerValueFormatter.formatCurrency(summary.cashOnHandCents)}", fontWeight = FontWeight.Bold)
                    if (summary.pendingCount > 0) Text("${summary.pendingCount} receita(s) ainda pendente(s).")
                    Text(if (closed) "Caixa conferido" else "Caixa aberto", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { creatingType = FinanceEntryType.REVENUE }, enabled = !closed, modifier = Modifier.weight(1f)) {
                    Text("+ Receita")
                }
                Button(onClick = { creatingType = FinanceEntryType.EXPENSE }, enabled = !closed, modifier = Modifier.weight(1f)) {
                    Text("+ Despesa")
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    repository.setDayClosed(!closed)
                    closed = !closed
                    status = if (closed) "Caixa de hoje marcado como conferido." else "Caixa de hoje reaberto."
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (closed) "Reabrir caixa de hoje" else "Fechar caixa de hoje") }
        }

        if (status.isNotBlank()) item { Text(status, style = MaterialTheme.typography.bodySmall) }

        item {
            Text("Outros lançamentos de hoje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (otherEntries.isEmpty()) {
            item { Text("Nenhum outro lançamento registrado hoje.") }
        } else {
            items(otherEntries, key = FinanceEntry::id) { entry ->
                FinanceEntryCard(
                    entry = entry,
                    enabled = !closed,
                    onCash = { repository.markReceived(entry.id, FinancePaymentMethod.CASH); refresh() },
                    onPix = { repository.markReceived(entry.id, FinancePaymentMethod.PIX); refresh() },
                    onEdit = { editing = entry },
                    onCancel = { repository.cancel(entry.id); refresh() },
                    onDelete = { deleting = entry },
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
        }
    }'''
finance = finance[:screen_start] + new_screen + finance[screen_end:]
finance_path.write_text(finance, encoding="utf-8")

gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 5200' not in gradle or 'versionName = "0.1.159"' not in gradle:
    raise SystemExit("0.1.160 version anchors not found")
gradle = gradle.replace('versionCode = 5200', 'versionCode = 5210', 1)
gradle = gradle.replace('versionName = "0.1.159"', 'versionName = "0.1.160"', 1)
gradle_path.write_text(gradle, encoding="utf-8")

test_path.write_text(r'''package br.com.mapeiaia.rotacerta

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
''', encoding="utf-8")

print("0.1.160: repeated Value capture watchdog and top-priority scrollable Finance list applied")
