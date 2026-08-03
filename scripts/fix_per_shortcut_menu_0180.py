from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MARKER = "per_shortcut_menu_0_1_180"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def write(relative: str, content: str) -> None:
    (ROOT / relative).write_text(content, encoding="utf-8")


def replace_once(relative: str, old: str, new: str) -> None:
    text = read(relative)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{relative}: expected exactly one occurrence, found {count}: {old[:120]!r}")
    write(relative, text.replace(old, new, 1))


def replace_all_exact(relative: str, old: str, new: str, expected: int) -> None:
    text = read(relative)
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{relative}: expected {expected} occurrences, found {count}: {old[:120]!r}")
    write(relative, text.replace(old, new))


# Version.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 5400\n        versionName = "0.1.179"',
    '        versionCode = 5410\n        versionName = "0.1.180"',
)

# Persisted/resolved shortcut model and "do nothing" action.
customization = "app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt"
replace_once(
    customization,
    """data class ResolvedShortcutGridEntry0179(
    val entryId: String,
    val spec: BubbleShortcutSpec,
)""",
    """data class ResolvedShortcutGridEntry0179(
    val entryId: String,
    val shortcutId: String,
    val spec: BubbleShortcutSpec,
    val executesOnTap: Boolean,
)""",
)
replace_once(
    customization,
    """object ShortcutGesturePolicy0179 {
    const val SHORTCUT_LONG_PRESS_MILLIS: Long = 2_000L
    const val MAIN_CUSTOMIZATION_HOLD_MILLIS: Long = 5_000L""",
    """object ShortcutGesturePolicy0179 {
    const val SHORTCUT_LONG_PRESS_MILLIS: Long = 5_000L
    const val MAIN_CUSTOMIZATION_HOLD_MILLIS: Long = 5_000L""",
)
replace_once(
    customization,
    """object ShortcutGridCustomizationPolicy0179 {
    val iconChoices: List<String>""",
    """object ShortcutGridCustomizationPolicy0179 {
    const val NO_ACTION_SHORTCUT_ID: String = "no_action_0180"

    val iconChoices: List<String>""",
)
replace_once(
    customization,
    """        val validIds = BubbleShortcutCatalog.modules.map { it.spec.id }.toSet()
        val usedEntryIds = mutableSetOf<String>()
        return entries.asSequence()
            .filter { it.shortcutId in validIds }
            .take(ShortcutGesturePolicy0179.MAX_GRID_ITEMS)
            .mapIndexed { index, item ->
                val spec = requireNotNull(BubbleShortcutCatalog.findSpec(item.shortcutId))
                val rawEntryId = item.entryId.trim().take(80).ifBlank { "entry:${item.shortcutId}:$index" }""",
    """        val validIds = BubbleShortcutCatalog.modules.map { it.spec.id }.toSet() + NO_ACTION_SHORTCUT_ID
        val usedEntryIds = mutableSetOf<String>()
        return entries.asSequence()
            .filter { it.shortcutId in validIds }
            .take(ShortcutGesturePolicy0179.MAX_GRID_ITEMS)
            .mapIndexed { index, item ->
                val catalogSpec = BubbleShortcutCatalog.findSpec(item.shortcutId)
                val fallbackLabel = if (item.shortcutId == NO_ACTION_SHORTCUT_ID) {
                    "Não fazer nada"
                } else {
                    requireNotNull(catalogSpec).displayLabel
                }
                val fallbackEmoji = if (item.shortcutId == NO_ACTION_SHORTCUT_ID) {
                    "⏸"
                } else {
                    requireNotNull(catalogSpec).emoji
                }
                val rawEntryId = item.entryId.trim().take(80).ifBlank { "entry:${item.shortcutId}:$index" }""",
)
replace_once(
    customization,
    """                item.copy(
                    entryId = entryId,
                    label = sanitizeLabel(item.label, spec.displayLabel),
                    emoji = sanitizeEmoji(item.emoji, spec.emoji),
                )""",
    """                item.copy(
                    entryId = entryId,
                    label = sanitizeLabel(item.label, fallbackLabel),
                    emoji = sanitizeEmoji(item.emoji, fallbackEmoji),
                )""",
)
replace_once(
    customization,
    """            .mapNotNull { entry ->
                val original = BubbleShortcutCatalog.findSpec(entry.shortcutId) ?: return@mapNotNull null
                ResolvedShortcutGridEntry0179(
                    entryId = entry.entryId,
                    spec = original.copy(
                        emoji = entry.emoji,
                        label = entry.label,
                        displayLabel = entry.label,
                    ),
                )
            }""",
    """            .mapNotNull { entry ->
                val noAction = entry.shortcutId == NO_ACTION_SHORTCUT_ID
                val original = if (noAction) {
                    BubbleShortcutSpec(
                        id = NO_ACTION_SHORTCUT_ID,
                        emoji = "⏸",
                        label = "Não fazer nada",
                        displayLabel = "Não fazer nada",
                        action = BubbleShortcutAction.OpenSettings,
                    )
                } else {
                    BubbleShortcutCatalog.findSpec(entry.shortcutId) ?: return@mapNotNull null
                }
                ResolvedShortcutGridEntry0179(
                    entryId = entry.entryId,
                    shortcutId = entry.shortcutId,
                    spec = original.copy(
                        emoji = entry.emoji,
                        label = entry.label,
                        displayLabel = entry.label,
                    ),
                    executesOnTap = !noAction,
                )
            }""",
)
replace_once(
    customization,
    """    fun nextShortcutId(currentId: String): String {
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }""",
    """    fun nextShortcutId(currentId: String): String {
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id } + NO_ACTION_SHORTCUT_ID""",
)
with_marker = read(customization)
if MARKER not in with_marker:
    write(customization, with_marker + f"\n// {MARKER}\n")

# Overlay keeps entry identity and opens its editor after five seconds.
overlay = "app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt"
replace_all_exact(
    overlay,
    """        onShortcut: (BubbleShortcutSpec) -> Unit,
        onShortcutLongPress: (BubbleShortcutSpec) -> Unit,""",
    """        onShortcut: (ResolvedShortcutGridEntry0179) -> Unit,
        onShortcutLongPress: (ResolvedShortcutGridEntry0179) -> Unit,""",
    expected=2,
)
replace_once(overlay, "onShortcut(entry0179.spec)", "onShortcut(entry0179)")
replace_once(overlay, "onShortcutLongPress(entry0179.spec)", "onShortcutLongPress(entry0179)")
replace_once(
    overlay,
    """        contentDescription = "${spec.label}. Toque para executar; mantenha pressionado por dois segundos para abrir o módulo."""",
    """        contentDescription = "${spec.label}. Toque para executar; mantenha pressionado por cinco segundos para configurar esta bolinha."""",
)

# Service dispatches only enabled actions and opens the selected entry editor on hold.
service = "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
replace_once(
    service,
    """            onShortcut = ::executeShortcutModule,
            onShortcutLongPress = ::executeShortcutLongPress0179,""",
    """            onShortcut = ::executeShortcutEntry0180,
            onShortcutLongPress = ::openShortcutEntryCustomization0180,""",
)
replace_once(
    service,
    """    private fun executeShortcutLongPress0179(spec: BubbleShortcutSpec) {
        UnifiedDebugEventStore.record(
            "SHORTCUT_LONG_PRESS_OPEN_MODULE_0179",
            universalResolvedForegroundPackage(),
            "id=${spec.id}",
        )
        openShortcutModule0171(spec)
    }

""",
    """    private fun executeShortcutEntry0180(entry0180: ResolvedShortcutGridEntry0179) {
        if (!entry0180.executesOnTap) {
            UnifiedDebugEventStore.record(
                "SHORTCUT_TAP_NO_ACTION_0180",
                universalResolvedForegroundPackage(),
                "entry=${entry0180.entryId}",
            )
            return
        }
        executeShortcutModule(entry0180.spec)
    }

    private fun openShortcutEntryCustomization0180(entry0180: ResolvedShortcutGridEntry0179) {
        UnifiedDebugEventStore.record(
            "SHORTCUT_HOLD_OPEN_EDITOR_0180",
            universalResolvedForegroundPackage(),
            "entry=${entry0180.entryId}; id=${entry0180.shortcutId}",
        )
        openShortcutCustomization0179(entry0180.entryId)
    }

""",
)
replace_once(
    service,
    """    private fun openShortcutCustomization0179() {
        val intent0179 = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
            .putExtra(EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179, true)
        launchShortcutActivity0176(""",
    """    private fun openShortcutCustomization0179(entryId0180: String? = null) {
        val intent0179 = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
            .putExtra(EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179, true)
        entryId0180?.let { intent0179.putExtra(EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180, it) }
        launchShortcutActivity0176(""",
)

# Main Activity focuses the requested entry and exposes "do nothing" and delete.
main = "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
replace_once(
    main,
    """    var shortcutCustomizationVisible0179 by remember { mutableStateOf(false) }
    var moduleNavigationActive0172 by remember { mutableStateOf(false) }""",
    """    var shortcutCustomizationVisible0179 by remember { mutableStateOf(false) }
    var selectedShortcutEntryId0180 by remember { mutableStateOf<String?>(null) }
    var moduleNavigationActive0172 by remember { mutableStateOf(false) }""",
)
replace_once(
    main,
    """        shortcutCustomizationVisible0179 = launchIntent?.getBooleanExtra(EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179, false) == true
        moduleNavigationActive0172 = shortcutCustomizationVisible0179 || highlightedShortcutModule0171 != null || tab != TAB_CONFIG || selectedBubbleGroup != BUBBLE_GROUP_GENERAL""",
    """        shortcutCustomizationVisible0179 = launchIntent?.getBooleanExtra(EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179, false) == true
        selectedShortcutEntryId0180 = launchIntent?.getStringExtra(EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180)
        moduleNavigationActive0172 = shortcutCustomizationVisible0179 || highlightedShortcutModule0171 != null || tab != TAB_CONFIG || selectedBubbleGroup != BUBBLE_GROUP_GENERAL""",
)
replace_once(
    main,
    """        if (shortcutCustomizationVisible0179) {
            shortcutCustomizationVisible0179 = false
            moduleNavigationActive0172 = false""",
    """        if (shortcutCustomizationVisible0179) {
            shortcutCustomizationVisible0179 = false
            selectedShortcutEntryId0180 = null
            moduleNavigationActive0172 = false""",
)
replace_once(
    main,
    """                        ShortcutGridCustomizationScreen0179(
                            onClose = {
                                shortcutCustomizationVisible0179 = false
                                moduleNavigationActive0172 = false
                            },
                        )""",
    """                        ShortcutGridCustomizationScreen0179(
                            selectedEntryId0180 = selectedShortcutEntryId0180,
                            onClose = {
                                shortcutCustomizationVisible0179 = false
                                selectedShortcutEntryId0180 = null
                                moduleNavigationActive0172 = false
                            },
                        )""",
)
replace_once(
    main,
    """                        onOpenCustomization = {
                            shortcutCustomizationVisible0179 = true
                            moduleNavigationActive0172 = true
                        },""",
    """                        onOpenCustomization = {
                            selectedShortcutEntryId0180 = null
                            shortcutCustomizationVisible0179 = true
                            moduleNavigationActive0172 = true
                        },""",
)
replace_once(
    main,
    """const val EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179 = "open_shortcut_customization_0179"
const val EXTRA_IMPORTED_RADAR_ID_0178""",
    """const val EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179 = "open_shortcut_customization_0179"
const val EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180 = "edit_shortcut_entry_id_0180"
const val EXTRA_IMPORTED_RADAR_ID_0178""",
)
replace_once(
    main,
    """private fun ShortcutGridCustomizationScreen0179(
    onClose: () -> Unit,
) {""",
    """private fun ShortcutGridCustomizationScreen0179(
    selectedEntryId0180: String?,
    onClose: () -> Unit,
) {""",
)
replace_once(
    main,
    """                    replaceEntries0179(entries0179.filterNot { it.entryId == entryId0179 })
                    pendingDeleteEntryId0179 = null""",
    """                    val updated0180 = entries0179.filterNot { it.entryId == entryId0179 }
                    replaceEntries0179(updated0180)
                    pendingDeleteEntryId0179 = null
                    if (selectedEntryId0180 == entryId0179) {
                        store0179.write(updated0180)
                        onClose()
                    }""",
)
replace_once(
    main,
    """        Text("Central de atalhos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Personalize somente a grade flutuante. A Home continua com todos os módulos disponíveis.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Toque curto executa a ação escolhida. Segure um atalho por dois segundos para abrir o módulo correspondente. Segure a bolinha principal por cinco segundos para voltar a esta Central.",
            style = MaterialTheme.typography.bodySmall,
        )""",
    """        Text(
            if (selectedEntryId0180 == null) "Central de atalhos" else "Configurar bolinha",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (selectedEntryId0180 == null) {
                "Personalize a grade flutuante. Cada bolinha pode ter nome, ícone e ação próprios."
            } else {
                "Esta configuração pertence somente à bolinha pressionada por cinco segundos."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Toque curto executa a ação escolhida. Segure qualquer bolinha por cinco segundos para abrir a configuração individual.",
            style = MaterialTheme.typography.bodySmall,
        )""",
)

# Wrap the add card so it is shown only in the full Central.
text = read(main)
anchor = '        Text(\n            "Ativos: ${entries0179.count { it.enabled }} de ${entries0179.size}. Limite: ${ShortcutGesturePolicy0179.MAX_GRID_ITEMS}.",\n            fontWeight = FontWeight.Bold,\n        )\n\n'
anchor_index = text.find(anchor)
if anchor_index < 0:
    raise RuntimeError("MainActivity.kt: active-count anchor not found")
card_start = text.find("        Card(modifier = Modifier.fillMaxWidth()) {", anchor_index + len(anchor))
loop_start = text.find("        entries0179.forEachIndexed { index0179, entry0179 ->", card_start)
if card_start < 0 or loop_start < 0:
    raise RuntimeError("MainActivity.kt: add-card or editor-loop anchor not found")
card_block = text[card_start:loop_start]
indented_card = "".join(("    " + line if line.strip() else line) for line in card_block.splitlines(keepends=True))
wrapped_card = "        if (selectedEntryId0180 == null) {\n" + indented_card + "        }\n\n"
text = text[:card_start] + wrapped_card + text[loop_start:]
write(main, text)

replace_once(
    main,
    """        entries0179.forEachIndexed { index0179, entry0179 ->
            ShortcutGridEditorCard0179(""",
    """        val editorEntries0180 = selectedEntryId0180
            ?.let { selected0180 -> entries0179.filter { it.entryId == selected0180 } }
            ?: entries0179.toList()
        editorEntries0180.forEach { entry0179 ->
            val index0179 = entries0179.indexOfFirst { it.entryId == entry0179.entryId }
            ShortcutGridEditorCard0179(""",
)
replace_once(
    main,
    """        TextButton(onClick = { confirmReset0179 = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Restaurar grade original")
        }""",
    """        if (selectedEntryId0180 == null) {
            TextButton(onClick = { confirmReset0179 = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Restaurar grade original")
            }
        }""",
)
replace_once(
    main,
    """    val actionSpec0179 = BubbleShortcutCatalog.findSpec(entry.shortcutId)
    Card(modifier = Modifier.fillMaxWidth()) {""",
    """    val actionSpec0179 = BubbleShortcutCatalog.findSpec(entry.shortcutId)
    val actionLabel0180 = if (entry.shortcutId == ShortcutGridCustomizationPolicy0179.NO_ACTION_SHORTCUT_ID) {
        "Não fazer nada"
    } else {
        actionSpec0179?.displayLabel ?: entry.shortcutId
    }
    Card(modifier = Modifier.fillMaxWidth()) {""",
)
replace_once(
    main,
    """                Text("Ação: ${actionSpec0179?.displayLabel ?: entry.shortcutId}")""",
    """                Text("Ação: $actionLabel0180")""",
)

# Update inherited tests to the corrected interaction contract.
replace_once(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179Test.kt",
    "assertEquals(2_000L, ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS)",
    "assertEquals(5_000L, ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS)",
)
contract = "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomizationContract0179Test.kt"
replace_once(
    contract,
    """    fun shortAndLongGesturesHaveSeparateDeterministicActions() {
        assertTrue(overlay.contains("SHORTCUT_LONG_PRESS_MILLIS"))
        assertTrue(service.contains("onShortcut = ::executeShortcutModule"))
        assertTrue(service.contains("onShortcutLongPress = ::executeShortcutLongPress0179"))
        assertTrue(service.contains("openShortcutModule0171(spec)"))
        assertFalse(service.contains("private fun executeShortcutLongPress0173"))
    }""",
    """    fun shortTapExecutesConfiguredEntryAndFiveSecondHoldOpensItsEditor() {
        assertTrue(overlay.contains("SHORTCUT_LONG_PRESS_MILLIS"))
        assertTrue(overlay.contains("onShortcut(entry0179)"))
        assertTrue(overlay.contains("onShortcutLongPress(entry0179)"))
        assertTrue(service.contains("onShortcut = ::executeShortcutEntry0180"))
        assertTrue(service.contains("onShortcutLongPress = ::openShortcutEntryCustomization0180"))
        assertTrue(service.contains("EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180"))
        assertFalse(service.contains("private fun executeShortcutLongPress0179"))
    }""",
)
legacy_contract = "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutLongPressContract0171Test.kt"
replace_once(
    legacy_contract,
    """    fun floatingOverlayUsesPersonalizedEntriesAndLongPressOpensModule() {
        assertTrue(overlay.contains("ResolvedShortcutGridEntry0179"))
        assertTrue(overlay.contains("shortcut_add_0179"))
        assertFalse(overlay.contains("(doubleAction ?: singleAction).invoke()"))
        assertTrue(service.contains("executeShortcutLongPress0179"))
        assertTrue(service.contains("SHORTCUT_LONG_PRESS_OPEN_MODULE_0179"))
        assertTrue(service.contains("openShortcutModule0171(spec)"))
        assertFalse(service.contains("private fun executeShortcutLongPress0173"))
    }""",
    """    fun floatingOverlayKeepsEntryIdentityAndLongPressOpensIndividualEditor() {
        assertTrue(overlay.contains("ResolvedShortcutGridEntry0179"))
        assertTrue(overlay.contains("shortcut_add_0179"))
        assertFalse(overlay.contains("(doubleAction ?: singleAction).invoke()"))
        assertTrue(service.contains("executeShortcutEntry0180"))
        assertTrue(service.contains("openShortcutEntryCustomization0180"))
        assertTrue(service.contains("SHORTCUT_HOLD_OPEN_EDITOR_0180"))
        assertFalse(service.contains("private fun executeShortcutLongPress0179"))
    }""",
)
replace_once(
    legacy_contract,
    """        assertTrue(service.contains("ShortcutGridPreferenceStore0179"))
        assertTrue(service.contains("openShortcutCustomization0179"))
        assertTrue(service.contains("MAIN_CUSTOMIZATION_HOLD_MILLIS"))""",
    """        assertTrue(service.contains("ShortcutGridPreferenceStore0179"))
        assertTrue(service.contains("openShortcutCustomization0179"))
        assertTrue(service.contains("EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180"))""",
)
replace_once(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutActivityLaunchContract0176Test.kt",
    'assertTrue(overlay.contains("onShortcut(entry0179.spec)"))',
    'assertTrue(overlay.contains("onShortcut(entry0179)"))',
)

new_test = """package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPerEntryMenu0180Test {
    @Test
    fun noActionRemainsVisibleEditableAndDoesNotExecuteOnTap() {
        val original = ShortcutGridCustomizationPolicy0179.defaults().first()
        val noAction = original.copy(
            shortcutId = ShortcutGridCustomizationPolicy0179.NO_ACTION_SHORTCUT_ID,
            label = "Pausado",
            emoji = "⏸",
        )
        val resolved = ShortcutGridCustomizationPolicy0179.resolve(listOf(noAction)).single()
        assertEquals(original.entryId, resolved.entryId)
        assertEquals(ShortcutGridCustomizationPolicy0179.NO_ACTION_SHORTCUT_ID, resolved.shortcutId)
        assertEquals("Pausado", resolved.spec.displayLabel)
        assertFalse(resolved.executesOnTap)
    }

    @Test
    fun anyShortcutUsesFiveSecondHoldForItsOwnEditor() {
        assertEquals(5_000L, ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS)
        assertEquals(5_000L, ShortcutGesturePolicy0179.MAIN_CUSTOMIZATION_HOLD_MILLIS)
    }

    @Test
    fun actionCycleIncludesDoNothingWithoutAcceptingArbitraryActions() {
        val catalogIds = BubbleShortcutCatalog.modules.map { it.spec.id }
        var current = catalogIds.first()
        val visited = mutableSetOf<String>()
        repeat(catalogIds.size + 1) {
            visited += current
            current = ShortcutGridCustomizationPolicy0179.nextShortcutId(current)
        }
        assertTrue(ShortcutGridCustomizationPolicy0179.NO_ACTION_SHORTCUT_ID in visited)
        assertTrue(ShortcutGridCustomizationPolicy0179.normalize(
            listOf(ShortcutGridCustomizationPolicy0179.defaults().first().copy(shortcutId = "arbitrary"))
        ).isEmpty())
    }
}
"""
write("app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenu0180Test.kt", new_test)

contract_test = """package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPerEntryMenuContract0180Test {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    private val policy = File("src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt").readText()

    @Test
    fun overlaySendsTheExactEntryOnTapAndHold() {
        assertTrue(overlay.contains("onShortcut(entry0179)"))
        assertTrue(overlay.contains("onShortcutLongPress(entry0179)"))
        assertFalse(overlay.contains("onShortcutLongPress(entry0179.spec)"))
        assertTrue(overlay.contains("cinco segundos para configurar esta bolinha"))
    }

    @Test
    fun holdOpensOnlyTheSelectedEntryEditor() {
        assertTrue(service.contains("openShortcutEntryCustomization0180"))
        assertTrue(service.contains("openShortcutCustomization0179(entry0180.entryId)"))
        assertTrue(service.contains("EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180"))
        assertTrue(main.contains("selectedEntryId0180"))
        assertTrue(main.contains("Configurar bolinha"))
    }

    @Test
    fun menuContainsDoNothingAndDelete() {
        assertTrue(policy.contains("NO_ACTION_SHORTCUT_ID"))
        assertTrue(policy.contains("executesOnTap = !noAction"))
        assertTrue(main.contains("Não fazer nada"))
        assertTrue(main.contains("Excluir da grade"))
    }

    @Test
    fun noActionDoesNotFallThroughToARealModule() {
        assertTrue(service.contains("if (!entry0180.executesOnTap)"))
        assertTrue(service.contains("SHORTCUT_TAP_NO_ACTION_0180"))
        assertFalse(service.contains("executeShortcutModule(entry0180.spec)\n        openShortcutCustomization"))
    }
}
"""
write("app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenuContract0180Test.kt", contract_test)

print(MARKER)
