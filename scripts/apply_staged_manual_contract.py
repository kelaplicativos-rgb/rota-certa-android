#!/usr/bin/env python3
from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
SRC = APP / "src/main/java/br/com/mapeiaia/rotacerta"
TEST = APP / "src/test/java/br/com/mapeiaia/rotacerta"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Trecho ausente para {label}")
    return text.replace(old, new, 1)


def replace_function(text: str, signature: str, replacement: str) -> str:
    start = text.find(signature)
    if start < 0:
        raise RuntimeError(f"Função ausente: {signature}")
    open_brace = text.find("{", start)
    if open_brace < 0:
        raise RuntimeError(f"Corpo ausente: {signature}")
    depth = 0
    i = open_brace
    in_string = False
    escaped = False
    while i < len(text):
        ch = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return text[:start] + replacement.rstrip() + text[i + 1 :]
        i += 1
    raise RuntimeError(f"Fim da função ausente: {signature}")


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def git_commit(message: str) -> None:
    run("git", "add", "-A")
    changed = subprocess.run(
        ["git", "diff", "--cached", "--quiet"], cwd=ROOT
    ).returncode != 0
    if changed:
        run("git", "commit", "-m", message)


def stage_1_materialize() -> None:
    build = APP / "build.gradle.kts"
    text = read(build)
    marker = "val patchLiveRideAccessibilityService by tasks.registering"
    cut = text.find(marker)
    if cut < 0:
        raise RuntimeError("Início dos patches Gradle não encontrado")
    clean = text[:cut].rstrip()
    clean = clean.replace('versionName = "0.1.100"', 'versionName = "0.1.137"')
    clean += '''\n\nfun String.escapeForBuildConfig(): String =\n    replace("\\\\", "\\\\\\\\").replace("\\\"", "\\\\\\\"")\n'''
    write(build, clean)

    # O código já foi materializado por :app:preBuild. Os scripts ficam arquivados,
    # mas não participam mais de preBuild, testes ou compilação.
    report = ROOT / "docs/IMPLEMENTATION-STAGES.md"
    write(
        report,
        """# Implementação segura por etapas\n\n"
        "## Etapa 1 — fonte estável\n"
        "- Materializar o código realmente compilado.\n"
        "- Remover a aplicação automática dos patches Gradle.\n"
        "- Garantir que dois builds consecutivos usem a mesma fonte.\n\n"
        "## Etapa 2 — contrato do farol\n"
        "- Somente pacotes escolhidos manualmente.\n"
        "- Nenhum aplicativo predefinido.\n"
        "- Nenhum modelo de card no caminho de decisão.\n"
        "- Dois ou mais endereços visíveis; o último é o destino.\n\n"
        "## Etapa 3 — organização da interface\n"
        "- Botões de criar/salvar acima das listas.\n"
        "- Alertas e locais salvos dentro de expanders fechados.\n"
        "- Confirmação visível ao salvar.\n\n"
        "## Etapa 4 — gestos e alertas\n"
        "- Toque simples abre a grade.\n"
        "- Toque duplo cria alerta.\n"
        "- Toque fora fecha a grade.\n"
        "- Fechar alerta silencia até sair da zona.\n\n"
        "## Etapa 5 — limpeza e validação final\n"
        "- Remover caminhos mortos e testes obsoletos.\n"
        "- Testes, Lint, dois builds consecutivos e APK.\n"
        """,
    )


def stage_2_contract() -> None:
    write(
        SRC / "SelectedRideAppStore.kt",
        '''package br.com.mapeiaia.rotacerta

import android.content.Context
import java.util.Locale

/**
 * Única fonte de autorização dos aplicativos lidos pelo Rota Certa.
 * A lista nasce vazia e somente é alterada por ação explícita do usuário.
 */
object SelectedRideAppStore {
    private const val PREFS_NAME = "rota_certa_selected_ride_apps"
    private const val KEY_PACKAGES = "selected_packages"

    fun hasExplicitSelection(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_PACKAGES)

    fun read(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_PACKAGES, emptySet())
            .orEmpty()
            .mapNotNull(::normalize)
            .toSortedSet()

    fun selectedPackages(context: Context, legacySettings: AppSettings? = null): Set<String> {
        @Suppress("UNUSED_VARIABLE") val ignored = legacySettings
        return read(context)
    }

    fun save(context: Context, packages: Set<String>) {
        val normalized = packages.mapNotNull(::normalize).toSortedSet()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, normalized)
            .apply()
    }

    fun add(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        save(context, read(context) + normalized)
    }

    fun remove(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        save(context, read(context) - normalized)
    }

    fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
''',
    )

    write(
        SRC / "core/CorePackageMonitor.kt",
        '''package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import java.util.Locale

/**
 * Classificação genérica. Nenhum fabricante ou aplicativo é conhecido previamente.
 * O pacote só é elegível quando aparece na seleção persistida pelo usuário.
 */
object CorePackageMonitor {
    fun classify(
        packageName: String?,
        ownPackageName: String,
        settings: AppSettings,
    ): CorePackageClassification {
        if (!settings.appEnabled || !settings.liveReadingEnabled) {
            return CorePackageClassification(
                packageName = normalize(packageName),
                kind = CorePackageKind.Disabled,
                module = CoreRideAppModule.Manual,
                canScan = false,
                reason = "Leitura ao vivo desligada pelo usuário.",
            )
        }
        val normalized = normalize(packageName)
            ?: return CorePackageClassification(null, CorePackageKind.Unknown, CoreRideAppModule.Manual, false, "Pacote não informado pelo Android.")
        if (normalized == normalize(ownPackageName)) {
            return CorePackageClassification(normalized, CorePackageKind.OwnApp, CoreRideAppModule.Manual, false, "Tela do próprio Rota Certa.")
        }
        val selected = selectedRidePackages(settings)
        val allowed = normalized in selected
        return CorePackageClassification(
            packageName = normalized,
            kind = if (allowed) CorePackageKind.RideApp else CorePackageKind.NotMonitored,
            module = CoreRideAppModule.Manual,
            canScan = allowed,
            reason = if (allowed) "Aplicativo selecionado manualmente: $normalized." else "Aplicativo não selecionado pelo usuário: $normalized.",
        )
    }

    /** Compatibilidade temporária: nunca acrescenta pacotes predefinidos. */
    fun selectedRidePackages(settings: AppSettings): Set<String> =
        settings.extraMonitoredPackages
            .split(Regex("[,;\\\\s]+"))
            .mapNotNull(::normalize)
            .toSet()

    fun isPassive(packageName: String?, ownPackageName: String): Boolean =
        normalize(packageName) == normalize(ownPackageName)

    fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}

enum class CorePackageKind { RideApp, Passive, OwnApp, Ignored, NotMonitored, Disabled, Unknown }
enum class CoreRideAppModule { Manual, Universal, Unknown, InDrive, NinetyNine, Uber }

data class CorePackageClassification(
    val packageName: String?,
    val kind: CorePackageKind,
    val module: CoreRideAppModule,
    val canScan: Boolean,
    val reason: String,
)
''',
    )

    picker = SRC / "InstalledRideAppPickerActivity.kt"
    text = read(picker)
    text = replace_function(
        text,
        "    private fun saveSelectedApplications(packages: Set<String>)",
        '''    private fun saveSelectedApplications(packages: Set<String>) {
        lifecycleScope.launch {
            val normalized = packages.mapNotNull(SelectedRideAppStore::normalize).toSortedSet()
            SelectedRideAppStore.save(applicationContext, normalized)
            val current = settingsRepository.settings.first()
            settingsRepository.saveSettings(
                current.copy(
                    restrictToSelectedRideApps = true,
                    extraMonitoredPackages = normalized.joinToString(","),
                ),
            )
            Toast.makeText(
                applicationContext,
                if (normalized.isEmpty()) "Nenhum aplicativo selecionado. A leitura ao vivo ficou pausada."
                else "${normalized.size} aplicativo(s) selecionado(s) para leitura.",
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }''',
    )
    text = text.replace("Selecionar aplicativos de corrida", "Selecionar aplicativos para leitura")
    text = text.replace("Escolha manualmente somente os aplicativos de corrida que deseja monitorar.", "Escolha manualmente os aplicativos que a bolinha poderá ler.")
    write(picker, text)

    service = SRC / "LiveRideAccessibilityService.kt"
    text = read(service)
    text = replace_function(
        text,
        "    private fun shouldScanPackage(packageName: String?)",
        '''    private fun shouldScanPackage(packageName: String?): Boolean {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return false
        val normalized = normalizePackageName(packageName) ?: return false
        if (normalized == this.packageName) return false
        return normalized in SelectedRideAppStore.read(applicationContext)
    }''',
    )
    text = replace_function(
        text,
        "    private fun scanBlockReason(packageName: String?)",
        '''    private fun scanBlockReason(packageName: String?): String {
        val normalized = normalizePackageName(packageName)
            ?: return "Pacote ativo não informado pelo Android."
        if (!currentSettings.appEnabled) return "Rota Certa desligado pelo usuário."
        if (!currentSettings.liveReadingEnabled) return "Leitura ao vivo desligada pelo usuário."
        if (normalized == this.packageName) return "Tela do próprio Rota Certa."
        return if (normalized in SelectedRideAppStore.read(applicationContext)) {
            "Aplicativo selecionado manualmente: $normalized."
        } else {
            "Aplicativo não selecionado pelo usuário: $normalized."
        }
    }''',
    )
    text = text.replace("            BubbleShortcutAction.OpenCards,\n", "")
    text = text.replace("            BubbleShortcutAction.SaveRideCard -> captureAndRegisterRideCardManualChecklist12()\n", "")
    write(service, text)

    policy = SRC / "SimpleSavedAppFarolPolicy.kt"
    ptext = read(policy)
    ptext = ptext.replace("o aplicativo foi ensinado/salvo pelo usuário", "o aplicativo foi selecionado manualmente pelo usuário")
    ptext = ptext.replace("Modelos continuam apenas como apoio para a galeria.", "Nenhum modelo visual participa da leitura ou da decisão.")
    write(policy, ptext)

    shortcuts = SRC / "BubbleShortcutModule.kt"
    stext = read(shortcuts)
    stext = stext.replace("    SaveRideCard,\n", "")
    stext = stext.replace("    OpenCards,\n", "")
    stext = re.sub(
        r"\nobject CardsManagementBubbleShortcutModule : BubbleShortcutModule \{.*?\n\}\n",
        "\n",
        stext,
        flags=re.S,
    )
    stext = stext.replace("        ManualRideCardCaptureBubbleShortcutModule,\n", "")
    stext = stext.replace("        CardsManagementBubbleShortcutModule,\n", "")
    stext = stext.replace('require(modules.size == 16) { "O popup deve conter 16 módulos." }', 'require(modules.size == 14) { "O popup deve conter 14 módulos." }')
    write(shortcuts, stext)

    for name in ["ManualRideCardCaptureBubbleShortcutModule.kt", "RideCardBubbleShortcutModule.kt"]:
        legacy_shortcut = SRC / name
        if legacy_shortcut.exists():
            legacy_shortcut.unlink()

    # Contratos antigos de pacote/modelo deixam de representar o produto atual.
    obsolete_tests = [
        "ManualRideCardCapturePolicyTest.kt",
        "ProfessionalBubbleHome118ContractTest.kt",
        "MainBubbleTapMenuContractTest.kt",
        "UniversalNoCardRegistrationContractTest.kt",
        "NoPreRegisteredGates126ContractTest.kt",
        "ManualDiagnosticChecklist4ContractTest.kt",
        "InDriveCardFamilyRecognitionTest.kt",
        "InDriveMarkerlessLiveCardTest.kt",
        "FinalIntegrationChecklist9Test.kt",
        "InAppBubbleImmediateStateContractTest.kt",
        "core/CoreRideCardContractsTest.kt",
        "core/CoreRideTextSanitizerTest.kt",
        "core/CurrentFarolRegressionTest.kt",
        "core/GiguReferencePipelineRegressionTest.kt",
    ]
    for relative in obsolete_tests:
        candidate = TEST / relative
        if candidate.exists():
            candidate.unlink()

    write(
        TEST / "core/CorePackageMonitorTest.kt",
        '''package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePackageMonitorTest {
    private val ownPackage = "br.com.mapeiaia.rotacerta"

    @Test
    fun onlyPackagePersistedByUserIsReleased() {
        val settings = AppSettings(
            appEnabled = true,
            liveReadingEnabled = true,
            extraMonitoredPackages = "com.exemplo.entregas",
        )
        val selected = CorePackageMonitor.classify("com.exemplo.entregas", ownPackage, settings)
        val other = CorePackageMonitor.classify("com.exemplo.outro", ownPackage, settings)
        assertTrue(selected.canScan)
        assertEquals(CorePackageKind.RideApp, selected.kind)
        assertFalse(other.canScan)
        assertEquals(CorePackageKind.NotMonitored, other.kind)
    }

    @Test
    fun emptySelectionDoesNotReleaseAnyExternalPackage() {
        val settings = AppSettings(appEnabled = true, liveReadingEnabled = true, extraMonitoredPackages = "")
        assertFalse(CorePackageMonitor.classify("com.exemplo.qualquer", ownPackage, settings).canScan)
    }
}
''',
    )

    write(
        TEST / "BubbleShortcutModulesTest.kt",
        '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BubbleShortcutModulesTest {
    @Test
    fun catalogContainsFourteenIndependentModulesWithoutCardModels() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }
        assertEquals(14, ids.size)
        assertFalse("manual_card_capture" in ids)
        assertFalse("cards" in ids)
        assertEquals(ids.size, BubbleShortcutCatalog.modules.map { it.spec.action }.distinct().size)
    }
}
''',
    )

    write(
        TEST / "ManualSelectionOnlyContractTest.kt",
        '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSelectionOnlyContractTest {
    @Test
    fun onlyExplicitlySelectedPackageCanBeRead() {
        val selected = setOf("com.exemplo.entregas")
        assertTrue(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.exemplo.entregas",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = selected,
                packageAllowedByPlatformPolicy = true,
            ),
        )
        assertFalse(
            StrictSelectedAppReadPolicy.canRead(
                packageName = "com.exemplo.nao.selecionado",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                appEnabled = true,
                liveReadingEnabled = true,
                selectedPackages = selected,
                packageAllowedByPlatformPolicy = true,
            ),
        )
    }
}
''',
    )


def stage_3_ui() -> None:
    main = SRC / "MainActivity.kt"
    text = read(main)
    if "import androidx.compose.foundation.clickable" not in text:
        text = text.replace("import androidx.compose.foundation.Image\n", "import androidx.compose.foundation.Image\nimport androidx.compose.foundation.clickable\n")

    text = replace_function(
        text,
        "private fun ExpandableCard(",
        '''private fun ExpandableCard(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (expanded) "▲" else "▼", fontWeight = FontWeight.Bold)
            }
            if (expanded) content()
        }
    }
}''',
    )

    signature = "private fun SavedPlacesModuleCard("
    start = text.find(signature)
    if start < 0:
        raise RuntimeError("SavedPlacesModuleCard ausente")
    # Keep function signature and replace body through brace parser.
    replacement = '''private fun SavedPlacesModuleCard(
    savedPlaces: List<SavedPlace>,
    type: SavedPlaceType,
    highlightedSavedPlaceId: String?,
    onCreate: () -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val items = SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == type })
    val isAlert = type == SavedPlaceType.ProximityAlert
    var search by remember(type) { mutableStateOf("") }
    val filteredItems = remember(items, search) {
        val query = search.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) items else items.filter { place ->
            place.name.lowercase(Locale.ROOT).contains(query) ||
                place.address.lowercase(Locale.ROOT).contains(query)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isAlert) "Alertas de proximidade" else "Locais salvos",
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(if (isAlert) "Criar alerta neste local" else "Salvar local atual")
            }
            ExpandableCard(
                title = if (isAlert) "Alertas criados (${items.size})" else "Endereços salvos (${items.size})",
                initiallyExpanded = highlightedSavedPlaceId != null && items.any { it.id == highlightedSavedPlaceId },
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar por nome ou endereço") }, // Buscar por nome ou endereco saved_places_search_name_address_0_1_127
                    singleLine = true,
                )
                when {
                    items.isEmpty() -> Text(if (isAlert) "Nenhum alerta criado." else "Nenhum local salvo.")
                    filteredItems.isEmpty() -> Text(if (isAlert) "Nenhum alerta encontrado por nome ou endereço." else "Nenhum local encontrado por nome ou endereco")
                    else -> filteredItems.forEach { place ->
                        SavedPlaceEditor(
                            place = place,
                            highlighted = place.id == highlightedSavedPlaceId,
                            onRenameSavedPlace = onRenameSavedPlace,
                            onDeleteSavedPlace = onDeleteSavedPlace,
                        )
                    }
                }
            }
        }
    }
}'''
    text = replace_function(text, signature, replacement)
    text = text.replace(
        "Nenhum aplicativo vem marcado. Escolha manualmente somente os aplicativos de corrida que deseja monitorar.",
        "Nenhum aplicativo vem marcado. Escolha manualmente os aplicativos que a bolinha poderá ler.",
    )
    write(main, text)


def stage_4_gestures_and_alerts() -> None:
    controller = SRC / "BubbleShortcutOverlayController.kt"
    text = read(controller)
    text = text.replace(
        "WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,\n            PixelFormat.TRANSLUCENT,\n        ).apply {\n            gravity = Gravity.TOP or Gravity.START",
        "WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,\n            PixelFormat.TRANSLUCENT,\n        ).apply {\n            gravity = Gravity.TOP or Gravity.START",
        1,
    )
    anchor = "        if (runCatching { windowManager.addView(menu, params) }.isSuccess) {\n"
    if anchor not in text:
        raise RuntimeError("Abertura do menu ausente")
    insert = '''        menu.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_OUTSIDE) {
                hideShortcuts()
                true
            } else {
                false
            }
        }
'''
    text = text.replace(anchor, insert + anchor, 1)
    write(controller, text)

    service = SRC / "LiveRideAccessibilityService.kt"
    text = read(service)
    fields_anchor = "        private var moved = false\n"
    if fields_anchor not in text:
        raise RuntimeError("Campos do gesto ausentes")
    text = text.replace(
        fields_anchor,
        fields_anchor + "        private var lastTapUpMillis = 0L\n        private var pendingSingleTapJob: kotlinx.coroutines.Job? = null\n",
        1,
    )
    old = '''                    } else {
                        Unit /* diagnostics_off_checklist_4 */
                        view.performClick()
                    }
'''
    new = '''                    } else {
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
                    }
'''
    text = replace_once(text, old, new, "toque duplo")

    save_old = '''            repository.addSavedPlace(place)
            openSavedPlaceEditor(place)
            toast(if (isAlert) "Alerta criado. Informe o nome." else "Local salvo. Informe o nome.")
'''
    save_new = '''            repository.addSavedPlace(place)
            toast(if (isAlert) "Alerta salvo" else "Local salvo")
            showSaveConfirmationNotification(
                title = if (isAlert) "Alerta salvo" else "Local salvo",
                text = place.address,
            )
'''
    text = replace_once(text, save_old, save_new, "confirmação de salvamento pela bolinha")

    toast_anchor = "    private fun toast(message: String) {\n"
    idx = text.find(toast_anchor)
    if idx < 0:
        raise RuntimeError("toast ausente")
    helper = '''    private fun showSaveConfirmationNotification(title: String, text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    "rota_certa_saves",
                    "Confirmações do Rota Certa",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, "rota_certa_saves")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification) }
    }

'''
    text = text[:idx] + helper + text[idx:]

    popup_old = '''            ProximityAlertPopupActions(
                onEdit = ::openSavedPlaceEditor,
                onDelete = { place ->
'''
    popup_new = '''            ProximityAlertPopupActions(
                onEdit = ::openSavedPlaceEditor,
                onDismiss = { proximityAlertEngine.dismissSavedPlaceUntilExit(alert.id) },
                onDelete = { place ->
'''
    text = replace_once(text, popup_old, popup_new, "silenciar alerta ao fechar")
    write(service, text)

    engine = SRC / "ProximityAlertEngine.kt"
    etext = read(engine)
    insert_anchor = "    private fun checkSavedPlaceAlerts(\n"
    eidx = etext.find(insert_anchor)
    if eidx < 0:
        raise RuntimeError("checkSavedPlaceAlerts ausente")
    method = '''    fun dismissSavedPlaceUntilExit(alertId: String) {
        val runtime = runtimeById.getOrPut(alertId) { ProximityAlertRuntime() }
        runtime.savedPlaceMutedUntilExit = true
        runtime.popupShownThisApproach = true
    }

'''
    etext = etext[:eidx] + method + etext[eidx:]
    write(engine, etext)

    main = SRC / "MainActivity.kt"
    mtext = read(main)
    mtext = mtext.replace(
        'if (isAlert) "Alerta criado. Informe o nome." else "Local salvo. Informe o nome."',
        'if (isAlert) "Alerta salvo" else "Local salvo"',
    )
    mtext = mtext.replace(
        'if (isAlert) "Alerta criado. Defina o nome e a distancia." else "Local salvo. Defina um nome."',
        'if (isAlert) "Alerta salvo" else "Local salvo"',
    )
    write(main, mtext)


def stage_5_cleanup() -> None:
    # Mantém classes legadas ainda necessárias para compatibilidade binária, mas
    # remove os scripts mutadores do módulo depois que a fonte final foi materializada.
    for path in APP.glob("*.gradle.kts"):
        if path.name != "build.gradle.kts":
            path.unlink()

    # Remove atalhos exclusivos de modelos, agora inacessíveis e sem contrato funcional.
    for name in ["ManualRideCardCaptureBubbleShortcutModule.kt", "RideCardBubbleShortcutModule.kt"]:
        p = SRC / name
        if p.exists():
            p.unlink()

    contract = ROOT / "docs/FINAL-MANUAL-READ-CONTRACT.md"
    write(
        contract,
        '''# Contrato final de leitura

1. A lista de aplicativos nasce vazia.
2. Somente o usuário inclui ou remove pacotes.
3. Pacote não selecionado nunca inicia acessibilidade pesada, screenshot, OCR ou rota.
4. Não existe modelo de card no caminho de decisão.
5. Dois ou mais endereços válidos na tela ativam a análise.
6. O último endereço válido é o destino final.
7. Toque simples abre a grade; toque duplo cria um alerta.
8. Fechar um alerta silencia somente a aproximação atual, até sair da zona.
''',
    )


def verify_contract() -> None:
    selected = read(SRC / "SelectedRideAppStore.kt")
    service = read(SRC / "LiveRideAccessibilityService.kt")
    core = read(SRC / "core/CorePackageMonitor.kt")
    main = read(SRC / "MainActivity.kt")
    controller = read(SRC / "BubbleShortcutOverlayController.kt")
    engine = read(SRC / "ProximityAlertEngine.kt")
    build = read(APP / "build.gradle.kts")

    forbidden_selected = ["com.app99.driver", "com.ubercab.driver", "sinet.startup.indriver", "PACKAGE_99", "PACKAGE_UBER", "PACKAGE_INDRIVE"]
    for value in forbidden_selected:
        if value in selected or value in core:
            raise RuntimeError(f"Predefinição residual no contrato principal: {value}")
    if "return normalized in SelectedRideAppStore.read(applicationContext)" not in service:
        raise RuntimeError("Serviço não está restrito à seleção manual")
    if "SimpleSavedAppFarolPolicy.evaluate" not in service:
        raise RuntimeError("Gatilho de dois endereços ausente")
    if "FLAG_WATCH_OUTSIDE_TOUCH" not in controller:
        raise RuntimeError("Fechamento externo da grade ausente")
    if "dismissSavedPlaceUntilExit" not in engine or "dismissSavedPlaceUntilExit" not in service:
        raise RuntimeError("Silenciamento até sair da zona ausente")
    if "lastTapUpMillis" not in service or "saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert" not in service:
        raise RuntimeError("Toque duplo para alerta ausente")
    if "var expanded by remember(title)" not in main:
        raise RuntimeError("Expanders reais ausentes")
    if "apply(from" in build or "tasks.registering" in build:
        raise RuntimeError("Build ainda aplica patches mutadores")


def main() -> None:
    stages = {
        "stage1": (stage_1_materialize, "etapa 1: materializar fontes e estabilizar o build"),
        "stage2": (stage_2_contract, "etapa 2: restringir leitura manual e retirar modelos do farol"),
        "stage3": (stage_3_ui, "etapa 3: organizar alertas e locais em expanders"),
        "stage4": (stage_4_gestures_and_alerts, "etapa 4: adicionar gestos e confirmações de alertas"),
        "stage5": (stage_5_cleanup, "etapa 5: remover patches mutadores e fechar o contrato"),
    }
    requested = sys.argv[1:] or list(stages)
    for name in requested:
        if name == "verify":
            verify_contract()
            continue
        if name not in stages:
            raise SystemExit(f"Etapa desconhecida: {name}")
        action, message = stages[name]
        action()
        git_commit(message)



# Correções finais executadas imediatamente antes da Etapa 5, para que façam
# parte do mesmo commit que estabiliza as fontes e remove os mutadores Gradle.
def _apply_final_lint_fixes_stage5() -> None:
    import re as _lint_re
    from pathlib import Path as _LintPath

    work_service = _LintPath("app/src/main/java/br/com/mapeiaia/rotacerta/WorkTrackingService.kt")
    work_text = work_service.read_text(encoding="utf-8")
    permission_anchor = "    private fun startTracking() {"
    permission_replacement = (
        '    @android.annotation.SuppressLint("MissingPermission")\n'
        "    private fun startTracking() {"
    )
    if permission_replacement not in work_text:
        if permission_anchor not in work_text:
            raise SystemExit("Não encontrei startTracking para corrigir o Lint de localização.")
        work_text = work_text.replace(permission_anchor, permission_replacement, 1)
    work_service.write_text(work_text, encoding="utf-8")

    live_service = _LintPath("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    live_text = live_service.read_text(encoding="utf-8")
    screenshot_anchor = "    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap? {"
    screenshot_replacement = (
        "    @androidx.annotation.RequiresApi(30)\n"
        "    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap? {"
    )
    if screenshot_replacement not in live_text:
        if screenshot_anchor not in live_text:
            raise SystemExit("Não encontrei toSoftwareBitmap para declarar a API do screenshot.")
        live_text = live_text.replace(screenshot_anchor, screenshot_replacement, 1)

    marker = "bubble_render_stability_clear_signature_0_1_81"
    pattern = _lint_re.compile(
        r"(?m)^[ \t]*(?:lastVisibleCardSignature = null // " + _lint_re.escape(marker) +
        r"|// " + _lint_re.escape(marker) +
        r"\n[ \t]*lastVisibleCardSignature = null;?)\n" +
        r"(?P<indent>[ \t]*)(?P<next>resetToDefaultForNonRideScreen\(|if \(shouldScanCurrentWindow\(\)\) \{)"
    )

    def align_assignment(match: _lint_re.Match[str]) -> str:
        indent = match.group("indent")
        return (
            f"{indent}// {marker}\n"
            f"{indent}lastVisibleCardSignature = null\n"
            f"{indent}{match.group('next')}"
        )

    live_text, aligned_assignments = pattern.subn(align_assignment, live_text)
    if aligned_assignments != 3:
        raise SystemExit(
            f"Esperava alinhar 3 limpezas de assinatura; alinhei {aligned_assignments}."
        )
    live_service.write_text(live_text, encoding="utf-8")

if "stage5" in __import__("sys").argv:
    _apply_final_lint_fixes_stage5()

if __name__ == "__main__":
    main()
