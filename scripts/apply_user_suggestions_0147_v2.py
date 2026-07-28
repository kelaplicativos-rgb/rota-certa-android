from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(rel: str) -> tuple[Path, str]:
    p = ROOT / rel
    return p, p.read_text()

def write(p: Path, text: str) -> None:
    p.write_text(text)

def sub_once(text: str, pattern: str, replacement: str, label: str, flags: int = 0) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"Falha em {label}: padrão encontrado {count} vez(es)")
    return updated

# Versão
p, text = read("app/build.gradle.kts")
text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5080', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.147"', text, count=1)
write(p, text)

# Configuração persistente do tamanho
p, text = read("app/src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
if "bubbleSizeDp" not in text:
    text = text.replace(
        "    val bubbleOpacity: Double = 1.0,\n",
        "    val bubbleOpacity: Double = 1.0,\n    val bubbleSizeDp: Int = 66,\n",
        1,
    )
write(p, text)

# Aparência e ordenação dos alertas
p, text = read("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
if "Tamanho da bolinha central" not in text:
    anchor = "    ExpandableCard(title = \"Bolinha e aparência\", initiallyExpanded = false) {\n"
    insert = anchor + '''        Text("Tamanho da bolinha central: ${settings.bubbleSizeDp.coerceIn(52, 96)} dp", fontWeight = FontWeight.Bold)
        Slider(
            value = settings.bubbleSizeDp.coerceIn(52, 96).toFloat(),
            onValueChange = { raw -> onChange(settings.copy(bubbleSizeDp = raw.roundToInt().coerceIn(52, 96))) },
            valueRange = 52f..96f,
            steps = 43,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("A alteração aparece na bolinha central sem interferir na leitura do farol.", style = MaterialTheme.typography.bodySmall)
'''
    if anchor not in text:
        raise SystemExit("Falha em aparência: bloco não encontrado")
    text = text.replace(anchor, insert, 1)
old_items = "    val items = SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == type })"
if old_items in text:
    text = text.replace(old_items, '''    val matchingItems = savedPlaces.filter { it.type == type }
    val items = if (type == SavedPlaceType.ProximityAlert) {
        matchingItems.sortedByDescending { it.createdAtMillis }
    } else {
        SavedPlaceUiPolicy.sortedByName(matchingItems)
    }''', 1)
write(p, text)

# Atalho Valor
p, text = read("app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt")
if "CopyPassengerFare" not in text:
    text = text.replace("    CopyTripConfirmation,\n", "    CopyTripConfirmation,\n    CopyPassengerFare,\n", 1)
if "PassengerFareBubbleShortcutModule" not in text:
    marker = "object CollectorBubbleShortcutModule : BubbleShortcutModule {"
    module = '''object PassengerFareBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "passenger_fare",
        emoji = "💰",
        label = "Valor da passagem",
        action = BubbleShortcutAction.CopyPassengerFare,
        displayLabel = "Valor",
    )
}

'''
    if marker not in text:
        raise SystemExit("Falha no módulo Valor: marcador não encontrado")
    text = text.replace(marker, module + marker, 1)
if "PassengerFareBubbleShortcutModule," not in text:
    text = text.replace("        TripConfirmationBubbleShortcutModule,\n", "        TripConfirmationBubbleShortcutModule,\n        PassengerFareBubbleShortcutModule,\n", 1)
text = text.replace("require(modules.size == 15)", "require(modules.size == 16)")
write(p, text)

# Serviço: tamanho, valor e gesto longo
p, text = read("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
text = text.replace("        dp(66),\n        dp(66),", "        dp(currentSettings.bubbleSizeDp.coerceIn(52, 96)),\n        dp(currentSettings.bubbleSizeDp.coerceIn(52, 96)),", 1)
if "BubbleShortcutAction.CopyPassengerFare" not in text:
    text = text.replace(
        "            BubbleShortcutAction.CopyTripConfirmation -> copyTripConfirmationFromBubbleChecklist8() // trip_confirmation_action_checklist_8\n",
        "            BubbleShortcutAction.CopyTripConfirmation -> copyTripConfirmationFromBubbleChecklist8() // trip_confirmation_action_checklist_8\n            BubbleShortcutAction.CopyPassengerFare -> copyPassengerFareFromBubble147()\n",
        1,
    )
if "passenger_fare_copy_0_1_147" not in text:
    marker = "    private fun openCollectorFromBubble() {"
    function = r'''    private fun copyPassengerFareFromBubble147() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        val visible = collectAllVisibleTextForCopy138()
        val values = Regex("""R\$\s*\d{1,3}(?:\.\d{3})*(?:,\d{2})|R\$\s*\d+(?:,\d{2})""", RegexOption.IGNORE_CASE)
            .findAll(visible)
            .map { it.value.replace(Regex("\\s+"), " ").trim() }
            .toList()
        val money = values.lastOrNull()
        if (money == null) {
            toast("Nenhum valor em reais foi encontrado na tela.")
            return
        }
        val message = "O valor da sua passagem ficou em $money."
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Valor da passagem", message))
        toast("Valor copiado: $money")
        overlayView?.announceForAccessibility("Valor da passagem copiado")
    } // passenger_fare_copy_0_1_147

'''
    if marker not in text:
        raise SystemExit("Falha na função Valor: marcador não encontrado")
    text = text.replace(marker, function + marker, 1)

listener_pattern = r"    private inner class BubbleTouchListener : View\.OnTouchListener \{.*?    \} // bubble_instant_drag_0_1_116"
listener = '''    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var longPressJob: kotlinx.coroutines.Job? = null
        private var longPressTriggered = false
        private val touchSlop: Int by lazy {
            android.view.ViewConfiguration.get(this@LiveRideAccessibilityService).scaledTouchSlop.coerceAtLeast(1)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = overlayParams ?: return false
            val manager = windowManager ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleGestureActive = true
                    bubbleDragStartedAtMillis = event.eventTime
                    analyzeJob?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    longPressTriggered = false
                    longPressJob?.cancel()
                    longPressJob = scope.launch {
                        delay(1_500L)
                        if (!moved && bubbleGestureActive) {
                            longPressTriggered = true
                            shortcutOverlayController.hideShortcuts()
                            persistResourceShortcutState()
                            saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, "Alerta")
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    view.animate().cancel()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!moved && BubbleDragPolicy.hasExceededTouchSlop(deltaX, deltaY, touchSlop)) {
                        moved = true
                        longPressJob?.cancel()
                        closeResourceShortcuts()
                    }
                    val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                    params.x = BubbleDragPolicy.clampCoordinate((startX + deltaX).roundToInt(), maxX)
                    params.y = BubbleDragPolicy.clampCoordinate((startY + deltaY).roundToInt(), maxY)
                    runCatching { manager.updateViewLayout(view, params) }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    bubbleGestureActive = false
                    longPressJob?.cancel()
                    longPressJob = null
                    if (moved) {
                        bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    } else if (!longPressTriggered) {
                        view.performClick()
                    }
                    scope.launch {
                        delay(BubbleDragPolicy.ANALYSIS_RESUME_DELAY_MS)
                        if (!bubbleGestureActive) scheduleVisibleTextAnalysis(delayMs = 0L)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    bubbleGestureActive = false
                    longPressJob?.cancel()
                    longPressJob = null
                    return true
                }
            }
            return true
        }
    } // bubble_instant_drag_0_1_116'''
if "private var longPressJob" not in text:
    text = sub_once(text, listener_pattern, listener, "gesto longo", re.S)
write(p, text)

# Coletor: campo do link primeiro e conteúdo condicionado ao link
p, text = read("app/src/main/java/br/com/mapeiaia/rotacerta/BlaBlaCarCollectorActivity.kt")
if "Extrair viagem pelo link" not in text:
    anchor = "            FinanceSummary(record = record)\n"
    section = '''            SectionCard(title = "Extrair viagem pelo link") {
                Text("Cole o link compartilhado da viagem. Os dados e o relatório aparecem abaixo após carregar o link.", style = MaterialTheme.typography.bodySmall)
                TextFieldLine("Link da viagem", record.tripUrl) { update(record.copy(tripUrl = it.trim())) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { update(record.copy(tripUrl = onPasteText().trim())) }, modifier = Modifier.weight(1f)) { Text("Colar") }
                    Button(onClick = {
                        if (record.tripUrl.isBlank()) extractStatus = "Cole o link da viagem primeiro."
                        else {
                            extractStatus = "Link carregado. Abra a página e use Extrair HTML para preencher os dados."
                            onOpenUrl(record.tripUrl)
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Extrair") }
                }
                if (extractStatus.isNotBlank()) Text(extractStatus, style = MaterialTheme.typography.bodySmall)
            }

'''
    if anchor not in text:
        raise SystemExit("Falha no Coletor: resumo financeiro não encontrado")
    text = text.replace(anchor, section + anchor, 1)
write(p, text)

# Teste contratual
contract = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/UserSuggestions147ContractTest.kt"
contract.write_text('''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class UserSuggestions147ContractTest {
    @Test fun suggestionsAreWired() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta")
        assertTrue("bubbleSizeDp: Int = 66" in File(root, "Models.kt").readText())
        assertTrue("Tamanho da bolinha central" in File(root, "MainActivity.kt").readText())
        assertTrue("sortedByDescending { it.createdAtMillis }" in File(root, "MainActivity.kt").readText())
        val service = File(root, "LiveRideAccessibilityService.kt").readText()
        assertTrue("delay(1_500L)" in service)
        assertTrue("passenger_fare_copy_0_1_147" in service)
        assertTrue("CopyPassengerFare" in File(root, "BubbleShortcutModule.kt").readText())
        assertTrue("Extrair viagem pelo link" in File(root, "BlaBlaCarCollectorActivity.kt").readText())
    }
}
''')
print("0.1.147 aplicada com sucesso")
