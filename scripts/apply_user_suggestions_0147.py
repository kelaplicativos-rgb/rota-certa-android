from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Trecho não encontrado em {path}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))

root = Path(__file__).resolve().parents[1]
models = root / "app/src/main/java/br/com/mapeiaia/rotacerta/Models.kt"
main = root / "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
service = root / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
shortcuts = root / "app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt"
collector = root / "app/src/main/java/br/com/mapeiaia/rotacerta/BlaBlaCarCollectorActivity.kt"
build = root / "app/build.gradle.kts"

replace_once(build, 'versionCode = 5070\n        versionName = "0.1.146"', 'versionCode = 5080\n        versionName = "0.1.147"')

replace_once(
    models,
    '    val bubbleOpacity: Double = 1.0,\n    val bubbleDarkMode: Boolean = false,',
    '    val bubbleOpacity: Double = 1.0,\n    val bubbleSizeDp: Int = 66,\n    val bubbleDarkMode: Boolean = false,',
)

replace_once(
    main,
    '        BubbleOpacitySlider(\n            value = settings.bubbleOpacity,\n            onValueChange = { onChange(settings.copy(bubbleOpacity = it)) },\n            onValueChangeFinished = {},\n        )',
    '''        Text("Tamanho da bolinha central: ${settings.bubbleSizeDp.coerceIn(52, 96)} dp", fontWeight = FontWeight.Bold)
        Slider(
            value = settings.bubbleSizeDp.coerceIn(52, 96).toFloat(),
            onValueChange = { raw -> onChange(settings.copy(bubbleSizeDp = raw.roundToInt().coerceIn(52, 96))) },
            valueRange = 52f..96f,
            steps = 43,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("A alteração aparece na bolinha central sem mudar a leitura do farol.", style = MaterialTheme.typography.bodySmall)
        BubbleOpacitySlider(
            value = settings.bubbleOpacity,
            onValueChange = { onChange(settings.copy(bubbleOpacity = it)) },
            onValueChangeFinished = {},
        )''',
)

replace_once(
    main,
    '    val items = SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == type })',
    '''    val matchingItems = savedPlaces.filter { it.type == type }
    val items = if (type == SavedPlaceType.ProximityAlert) {
        matchingItems.sortedByDescending { it.createdAtMillis }
    } else {
        SavedPlaceUiPolicy.sortedByName(matchingItems)
    }''',
)

replace_once(
    service,
    '        dp(66),\n        dp(66),',
    '        dp(currentSettings.bubbleSizeDp.coerceIn(52, 96)),\n        dp(currentSettings.bubbleSizeDp.coerceIn(52, 96)),',
)

replace_once(
    service,
    '        private var lastTapUpMillis = 0L\n        private var pendingSingleTapJob: kotlinx.coroutines.Job? = null',
    '        private var longPressJob: kotlinx.coroutines.Job? = null\n        private var longPressTriggered = false',
)

replace_once(
    service,
    '''                    view.animate().cancel()
                    Unit /* diagnostics_off_checklist_4 */
                    return true''',
    '''                    view.animate().cancel()
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
                    Unit /* diagnostics_off_checklist_4 */
                    return true''',
)

replace_once(
    service,
    '''                    if (moved) {
                        bubblePrefs.edit()
                            .putInt(KEY_BUBBLE_X, params.x)
                            .putInt(KEY_BUBBLE_Y, params.y)
                            .apply()
                        Unit /* diagnostics_off_checklist_4 */
                    } else {
                        val tapAt = event.eventTime
                        val timeout = android.view.ViewConfiguration.getDoubleTapTimeout().toLong()
                        if (lastTapUpMillis > 0L && tapAt - lastTapUpMillis <= timeout) {
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = null
                            lastTapUpMillis = 0L
                            shortcutOverlayController.hideShortcuts()
                            persistResourceShortcutState()
                            saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, "Alerta")
                        } else {
                            lastTapUpMillis = tapAt
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = scope.launch {
                                delay(timeout)
                                if (lastTapUpMillis == tapAt) {
                                    lastTapUpMillis = 0L
                                    view.performClick()
                                }
                            }
                        }
                    }''',
    '''                    longPressJob?.cancel()
                    longPressJob = null
                    if (moved) {
                        bubblePrefs.edit()
                            .putInt(KEY_BUBBLE_X, params.x)
                            .putInt(KEY_BUBBLE_Y, params.y)
                            .apply()
                        Unit /* diagnostics_off_checklist_4 */
                    } else if (!longPressTriggered) {
                        view.performClick()
                    }''',
)

replace_once(
    service,
    '''                MotionEvent.ACTION_CANCEL -> {
                    bubbleGestureActive = false
                    Unit /* diagnostics_off_checklist_4 */''',
    '''                MotionEvent.ACTION_CANCEL -> {
                    bubbleGestureActive = false
                    longPressJob?.cancel()
                    longPressJob = null
                    Unit /* diagnostics_off_checklist_4 */''',
)

replace_once(
    shortcuts,
    '    CopyTripConfirmation,\n    OpenQuickReplies,',
    '    CopyTripConfirmation,\n    CopyPassengerFare,\n    OpenQuickReplies,',
)

insert_module = '''
object PassengerFareBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "passenger_fare",
        emoji = "💰",
        label = "Valor da passagem",
        action = BubbleShortcutAction.CopyPassengerFare,
        displayLabel = "Valor",
    )
}

'''
replace_once(shortcuts, 'object CollectorBubbleShortcutModule : BubbleShortcutModule {', insert_module + 'object CollectorBubbleShortcutModule : BubbleShortcutModule {')
replace_once(shortcuts, '        TripConfirmationBubbleShortcutModule,\n        CollectorBubbleShortcutModule,', '        TripConfirmationBubbleShortcutModule,\n        PassengerFareBubbleShortcutModule,\n        CollectorBubbleShortcutModule,')
replace_once(shortcuts, 'require(modules.size == 15)', 'require(modules.size == 16)')

replace_once(
    service,
    '            BubbleShortcutAction.CopyTripConfirmation -> copyTripConfirmationFromBubbleChecklist8() // trip_confirmation_action_checklist_8',
    '            BubbleShortcutAction.CopyTripConfirmation -> copyTripConfirmationFromBubbleChecklist8() // trip_confirmation_action_checklist_8\n            BubbleShortcutAction.CopyPassengerFare -> copyPassengerFareFromBubble147()',
)

fare_function = '''
    private fun copyPassengerFareFromBubble147() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        val visible = collectAllVisibleTextForCopy138()
        val money = Regex("""R\\$\\s*\\d{1,3}(?:\\.\\d{3})*(?:,\\d{2})|R\\$\\s*\\d+(?:,\\d{2})""", RegexOption.IGNORE_CASE)
            .findAll(visible)
            .map { it.value.replace(Regex("\\s+"), " ").trim() }
            .lastOrNull()
        if (money == null) {
            toast("Nenhum valor em reais foi encontrado na tela.")
            return
        }
        val passenger = visible.lines()
            .map(String::trim)
            .firstOrNull { line -> line.length in 2..50 && line.matches(Regex("[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ .'-]+")) }
            .orEmpty()
        val message = if (passenger.isBlank()) {
            "O valor da sua passagem ficou em $money."
        } else {
            "Olá, $passenger. O valor da sua passagem ficou em $money."
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Valor da passagem", message))
        toast("Valor copiado: $money")
        overlayView?.announceForAccessibility("Valor da passagem copiado")
    } // passenger_fare_copy_0_1_147

'''
replace_once(service, '    private fun openCollectorFromBubble() {', fare_function + '    private fun openCollectorFromBubble() {')

replace_once(
    collector,
    '            FinanceSummary(record = record)\n\n            SectionCard(title = "Dados da viagem") {',
    '''            SectionCard(title = "Extrair viagem pelo link") {
                Text("Cole o link compartilhado da viagem. Os demais campos aparecem somente depois.", style = MaterialTheme.typography.bodySmall)
                TextFieldLine("Link da viagem", record.tripUrl) { update(record.copy(tripUrl = it.trim())) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { update(record.copy(tripUrl = onPasteText().trim())) }, modifier = Modifier.weight(1f)) { Text("Colar") }
                    Button(
                        onClick = {
                            if (record.tripUrl.isBlank()) extractStatus = "Cole o link da viagem primeiro."
                            else {
                                extractStatus = "Link carregado. Abra a página e use Extrair HTML para preencher os dados."
                                onOpenUrl(record.tripUrl)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Extrair") }
                }
                if (extractStatus.isNotBlank()) Text(extractStatus, style = MaterialTheme.typography.bodySmall)
            }

            if (record.tripUrl.isNotBlank()) {
            FinanceSummary(record = record)

            SectionCard(title = "Dados extraídos da viagem") {''',
)
replace_once(
    collector,
    '            Spacer(modifier = Modifier.height(16.dp))\n        }',
    '            Spacer(modifier = Modifier.height(16.dp))\n            }\n        }',
)

# Contract test
contract = root / "app/src/test/java/br/com/mapeiaia/rotacerta/UserSuggestions147ContractTest.kt"
contract.write_text('''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class UserSuggestions147ContractTest {
    @Test fun suggestionsAreWired() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta")
        val models = File(root, "Models.kt").readText()
        val main = File(root, "MainActivity.kt").readText()
        val service = File(root, "LiveRideAccessibilityService.kt").readText()
        val shortcuts = File(root, "BubbleShortcutModule.kt").readText()
        val collector = File(root, "BlaBlaCarCollectorActivity.kt").readText()
        assertTrue("bubbleSizeDp: Int = 66" in models)
        assertTrue("Tamanho da bolinha central" in main)
        assertTrue("sortedByDescending { it.createdAtMillis }" in main)
        assertTrue("delay(1_500L)" in service)
        assertTrue("CopyPassengerFare" in shortcuts)
        assertTrue("passenger_fare_copy_0_1_147" in service)
        assertTrue("Extrair viagem pelo link" in collector)
    }
}
''')

print("0.1.147 aplicada")
