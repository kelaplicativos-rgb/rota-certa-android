from __future__ import annotations

from pathlib import Path
import re

ROOT = Path.cwd()
APP = ROOT / "app"
SRC = APP / "src/main/java/br/com/mapeiaia/rotacerta"
TEST = APP / "src/test/java/br/com/mapeiaia/rotacerta"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def remove_balanced_declaration(text: str, marker: str, include_annotation: bool = True) -> str:
    start = text.find(marker)
    if start < 0:
        return text
    line_start = text.rfind("\n", 0, start) + 1
    if include_annotation:
        prev_start = text.rfind("\n", 0, max(0, line_start - 1)) + 1
        prev_line = text[prev_start:line_start].strip()
        if prev_line.startswith("@Composable") or prev_line.startswith("@androidx.annotation"):
            line_start = prev_start
    brace = text.find("{", start)
    if brace < 0:
        raise RuntimeError(f"Opening brace not found for {marker}")
    depth = 0
    in_string = False
    triple = False
    escape = False
    i = brace
    while i < len(text):
        if triple:
            if text.startswith('"""', i):
                triple = False
                i += 3
                continue
            i += 1
            continue
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            i += 1
            continue
        if text.startswith('"""', i):
            triple = True
            i += 3
            continue
        if ch == '"':
            in_string = True
            i += 1
            continue
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                while end < len(text) and text[end] in " \t":
                    end += 1
                if end < len(text) and text[end] == "\n":
                    end += 1
                return text[:line_start] + text[end:]
        i += 1
    raise RuntimeError(f"Closing brace not found for {marker}")


def remove_range(text: str, start_marker: str, end_marker: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        return text
    start = text.rfind("\n", 0, start) + 1
    end = text.find(end_marker, start)
    if end < 0:
        raise RuntimeError(f"End marker missing: {end_marker}")
    return text[:start] + text[end:]


def remove_lines(text: str, patterns: list[str]) -> str:
    return "".join(
        line for line in text.splitlines(True)
        if not any(pattern in line for pattern in patterns)
    )


def normalize_blank_lines(text: str) -> str:
    return re.sub(r"\n{4,}", "\n\n\n", text)


# Stable version and signing path outside app/build, so `clean` cannot delete the key.
build_path = APP / "build.gradle.kts"
build = read(build_path)
build = re.sub(
    r'val ciVersionCode = System\.getenv\("GITHUB_RUN_NUMBER"\)\?\.toIntOrNull\(\)\?\.let \{ 1_000 \+ it \}\nval appVersionCode = ciVersionCode \?: 101',
    'val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 2_000 + it }\nval appVersionCode = ciVersionCode ?: 2_001',
    build,
)
build = build.replace(
    'val stableDebugKeystoreFile = layout.buildDirectory.file("generated/signing/rota-certa-debug.keystore").get().asFile',
    'val stableDebugKeystoreFile = rootProject.file(".gradle/rota-certa-signing/rota-certa-debug.keystore")',
)
write(build_path, build)

# Manual-only generic Core.
write(SRC / "core/CorePackageMonitor.kt", '''package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import java.util.Locale

/** Only packages explicitly persisted by the user may be read. */
object CorePackageMonitor {
    fun classify(
        packageName: String?,
        ownPackageName: String,
        settings: AppSettings,
    ): CorePackageClassification {
        if (!settings.appEnabled || !settings.liveReadingEnabled) {
            return CorePackageClassification(normalize(packageName), CorePackageKind.Disabled, false, "Leitura ao vivo desligada pelo usuário.")
        }
        val normalized = normalize(packageName)
            ?: return CorePackageClassification(null, CorePackageKind.Unknown, false, "Pacote não informado pelo Android.")
        if (normalized == normalize(ownPackageName)) {
            return CorePackageClassification(normalized, CorePackageKind.OwnApp, false, "Tela do próprio Rota Certa.")
        }
        val allowed = normalized in selectedRidePackages(settings)
        return CorePackageClassification(
            packageName = normalized,
            kind = if (allowed) CorePackageKind.SelectedApp else CorePackageKind.NotSelected,
            canScan = allowed,
            reason = if (allowed) "Aplicativo selecionado manualmente: $normalized." else "Aplicativo não selecionado pelo usuário: $normalized.",
        )
    }

    fun selectedRidePackages(settings: AppSettings): Set<String> =
        settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .mapNotNull(::normalize)
            .toSet()

    fun isPassive(packageName: String?, ownPackageName: String): Boolean =
        normalize(packageName) == normalize(ownPackageName)

    fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}

enum class CorePackageKind { SelectedApp, OwnApp, NotSelected, Disabled, Unknown }

data class CorePackageClassification(
    val packageName: String?,
    val kind: CorePackageKind,
    val canScan: Boolean,
    val reason: String,
)
''')

write(SRC / "core/RotaCertaCore.kt", '''package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideFields

/** Generic screen classifier; package authorization is handled by CorePackageMonitor. */
object RotaCertaCore {
    fun classifyScreen(packageName: String?, text: String, fields: RideFields): RideScreenClassification =
        ManualScreenModule.classify(RideScreenSnapshot(packageName, text, fields))
}
''')

write(SRC / "core/GenericRideCoreModules.kt", '''package br.com.mapeiaia.rotacerta.core

object ManualScreenModule : RideAppCoreModule {
    override val moduleName: String = "Manual"
    override val packageNames: Set<String> = emptySet()
    override fun supports(packageName: String?): Boolean = true

    override fun classify(snapshot: RideScreenSnapshot): RideScreenClassification {
        if (snapshot.text.isBlank()) {
            return RideScreenClassification(
                kind = RideScreenKind.PartialRideCard,
                packageName = snapshot.packageName,
                reason = "Texto vazio; aguardando dois endereços visíveis.",
                confidence = 0.0,
            )
        }
        val complete = !snapshot.fields.pickup.isNullOrBlank() && !snapshot.fields.destination.isNullOrBlank()
        return RideScreenClassification(
            kind = if (complete) RideScreenKind.OpenRideCard else RideScreenKind.PartialRideCard,
            packageName = snapshot.packageName,
            reason = if (complete) "Dois endereços visíveis; último endereço definido como destino." else "Aguardando dois endereços visíveis.",
            confidence = if (complete) 1.0 else 0.25,
        )
    }
}
''')

write(SRC / "core/CoreRideTextSanitizer.kt", '''package br.com.mapeiaia.rotacerta.core

import java.text.Normalizer
import java.util.Locale

/** Deterministic package-agnostic OCR/accessibility text cleanup. */
object CoreRideTextSanitizer {
    fun sanitize(text: String, packageName: String?): String {
        @Suppress("UNUSED_VARIABLE") val ignoredPackage = packageName
        if (text.isBlank()) return ""
        val unique = linkedMapOf<String, String>()
        text.lines()
            .map { it.replace('\u00A0', ' ').replace('\u202F', ' ').trim().replace(Regex("\\s+"), " ") }
            .filter(String::isNotBlank)
            .forEach { line -> unique.putIfAbsent(canonical(line), line) }
        return unique.values.joinToString("\n")
    }

    private fun canonical(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
''')

# Package-agnostic parser: first address is pickup and last distinct address is destination.
write(SRC / "RideTextParser.kt", '''package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

class RideTextParser {
    private val fareRegex = Regex("R\\$\\s*\\d{1,4}(?:[.,]\\d{1,2})?", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("\\b\\d+(?:[,.]\\d+)?\\s*km\\b", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("\\b\\d{1,3}\\s*(?:minutos?|min)\\b", RegexOption.IGNORE_CASE)
    private val markerAddress = Regex("^\\s*[AB]\\s+(.{5,})$", RegexOption.IGNORE_CASE)
    private val streetStart = Regex("^(?:rua|r\\.|avenida|av\\.|travessa|alameda|estrada|rodovia|praça|praca|largo|via)\\b", RegexOption.IGNORE_CASE)
    private val addressWords = setOf(
        "rua", "avenida", "travessa", "alameda", "estrada", "rodovia", "praça", "praca", "bairro",
        "condomínio", "condominio", "shopping", "terminal", "estação", "estacao", "hospital", "mercado",
        "restaurante", "lanchonete", "escola", "aeroporto", "rodoviária", "rodoviaria", "hotel", "parque",
    )
    private val noise = setOf(
        "aceitar", "cancelar", "voltar", "selecionar", "configurações", "configuracoes", "permissões", "permissoes",
        "pix", "dinheiro", "reclamar", "ocultar", "copiar", "compartilhar",
    )

    fun parse(text: String, packageName: String? = null): RideFields = parseWithMetadata(text, packageName).fields

    fun parseWithMetadata(text: String, packageName: String? = null): RideParseResult {
        @Suppress("UNUSED_VARIABLE") val ignoredPackage = packageName
        val lines = text.lines()
            .map { it.replace('\u00A0', ' ').replace('\u202F', ' ').trim().replace(Regex("\\s+"), " ") }
            .filter { it.length >= 3 }
            .distinctBy(::canonical)
        val addresses = extractAddresses(lines)
        val pickup = addresses.firstOrNull()
        val destination = addresses.drop(1).lastOrNull { !it.equals(pickup, ignoreCase = true) }
        val scoped = lines.joinToString("\n")
        return RideParseResult(
            fields = RideFields(
                pickup = pickup,
                destination = destination,
                fare = fareRegex.find(scoped)?.value,
                distance = distanceRegex.find(scoped)?.value,
                time = timeRegex.find(scoped)?.value,
            ),
            parserName = "manual-universal-last-address",
        )
    }

    private fun extractAddresses(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val marker = markerAddress.matchEntire(line)?.groupValues?.getOrNull(1)?.trim()
            val candidate = marker ?: line
            if (looksLikeAddress(candidate)) {
                val parts = mutableListOf(clean(candidate))
                var next = index + 1
                while (next < lines.size && parts.size < 3 && looksLikeContinuation(lines[next])) {
                    parts += clean(lines[next])
                    next += 1
                }
                val joined = parts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                if (joined.length >= 6 && result.none { canonical(it) == canonical(joined) }) result += joined
                index = next
            } else {
                index += 1
            }
        }
        return result
    }

    private fun looksLikeAddress(value: String): Boolean {
        val normalized = canonicalWords(value)
        if (value.length < 6 || normalized in noise) return false
        if (fareRegex.matches(value) || distanceRegex.matches(value) || timeRegex.matches(value)) return false
        val hasStreet = streetStart.containsMatchIn(value)
        val hasAddressWord = addressWords.any { normalized.contains(it) }
        val hasNumber = Regex("\\b\\d{1,6}[A-Za-z]?\\b").containsMatchIn(value)
        val hasLocality = value.contains(',') || Regex("\\b[A-Z]{2}\\b").containsMatchIn(value) || value.contains(" - ")
        return hasStreet || (hasAddressWord && (hasNumber || hasLocality)) || (hasNumber && hasLocality)
    }

    private fun looksLikeContinuation(value: String): Boolean {
        val normalized = canonicalWords(value)
        if (value.length < 3 || normalized in noise) return false
        if (fareRegex.containsMatchIn(value) || distanceRegex.containsMatchIn(value) || timeRegex.containsMatchIn(value)) return false
        return value.contains(',') || value.contains(" - ") || Regex("\\b[A-Z]{2}\\b").containsMatchIn(value) ||
            listOf("bairro", "jardim", "centro", "cidade", "state of", "district").any { normalized.contains(it) }
    }

    private fun clean(value: String): String = value
        .replace(Regex("^\\s*[AB]\\s+", RegexOption.IGNORE_CASE), "")
        .trim(' ', '-', '•', '|')

    private fun canonical(value: String): String = canonicalWords(value).replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun canonicalWords(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
''')

# Remove legacy fields and stored card images/models from serializable state.
models_path = SRC / "Models.kt"
models = read(models_path)
models = remove_lines(models, [
    "val monitor99:", "val monitorUber:", "val monitorInDrive:", "val requireRegisteredRideCard:",
    "val cardTemplates:", "val capturedScreens:", "val registeredCardRequired:", "val registeredCardMatched:",
])
models = remove_balanced_declaration(models, "data class RideCardTemplate(", include_annotation=True)
models = remove_balanced_declaration(models, "data class CapturedRideScreen(", include_annotation=True)
write(models_path, normalize_blank_lines(models).replace("strict_model", "strict_manual"))

repo_path = SRC / "Repositories.kt"
repo = read(repo_path)
repo = remove_lines(repo, [
    'private val monitor99 =', 'private val monitorUber =', 'private val monitorInDrive =',
    'private val requireRegisteredRideCard =', 'private val rideCardTemplates =', 'private val capturedRideScreens =',
    'monitor99 = prefs[monitor99]', 'monitorUber = prefs[monitorUber]', 'monitorInDrive = prefs[monitorInDrive]',
    'requireRegisteredRideCard = prefs[requireRegisteredRideCard]',
    'prefs[monitor99] =', 'prefs[monitorUber] =', 'prefs[monitorInDrive] =', 'prefs[requireRegisteredRideCard] =',
    'cardTemplates = cardTemplates.first()', 'capturedScreens = capturedScreens.first()',
    'prefs[rideCardTemplates] =', 'prefs[capturedRideScreens] =',
])
for marker in [
    "val cardTemplates: Flow<List<RideCardTemplate>>",
    "val capturedScreens: Flow<List<CapturedRideScreen>>",
    "suspend fun addCardTemplate(",
    "suspend fun removeCardTemplate(",
    "suspend fun pruneSelectedPackageIfNoCards(",
    "suspend fun addCapturedScreen(",
]:
    repo = remove_balanced_declaration(repo, marker, include_annotation=False)
write(repo_path, normalize_blank_lines(repo))

# Manual technical report reflects only current state and explicit package selection.
write(SRC / "ManualTechnicalReportBuilder.kt", r'''package br.com.mapeiaia.rotacerta

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds a technical snapshot only after an explicit user action. */
object ManualTechnicalReportBuilder {
    fun build(context: Context, settings: AppSettings): String {
        val appContext = context.applicationContext
        val bubble = appContext.getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE)
        val selectedPackages = SelectedRideAppStore.read(appContext).toList().sorted()
        val workPins = WorkRegionTargetPolicy.editablePins(settings)
        val now = System.currentTimeMillis()
        return buildString {
            appendLine("ROTA CERTA — RELATORIO TECNICO MANUAL")
            appendLine("Gerado em: ${formatDate(now)}")
            appendLine("Versao: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Pacote: ${appContext.packageName}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Aparelho: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Logs continuos: DESATIVADOS")
            appendLine()
            appendLine("--- CONTROLE ---")
            appendLine("Rota Certa ligado: ${settings.appEnabled}")
            appendLine("Leitura ao vivo: ${settings.liveReadingEnabled}")
            appendLine("Acessibilidade autorizada: ${isAccessibilityEnabled(appContext)}")
            appendLine("Selecao manual obrigatoria: true")
            appendLine("Politica: aplicativo selecionado + dois ou mais enderecos; o ultimo e o destino")
            appendLine("Alertas de proximidade: ${settings.proximityAlertsEnabled}")
            appendLine()
            appendLine("--- APLICATIVOS SELECIONADOS ---")
            appendLine("Quantidade: ${selectedPackages.size}")
            if (selectedPackages.isEmpty()) appendLine("- nenhum") else selectedPackages.forEach { appendLine("- $it") }
            appendLine()
            appendLine("--- ESTADO MAIS RECENTE DA BOLINHA ---")
            appendLine("Etapa: ${bubble.text(KEY_STATE_STAGE)}")
            appendLine("Cor: ${bubble.text(KEY_STATE_COLOR)}")
            appendLine("Distancia: ${bubble.text(KEY_STATE_DISTANCE_KM)}")
            appendLine("Motivo: ${bubble.text(KEY_STATE_REASON)}")
            appendLine("Janela: ${bubble.text(KEY_STATE_WINDOW_PACKAGE)}")
            appendLine("Pacote ativo: ${bubble.text(KEY_STATE_ACTIVE_PACKAGE)}")
            appendLine("Pacote do texto: ${bubble.text(KEY_STATE_TEXT_PACKAGE)}")
            appendLine("Servico pronto: ${bubble.getBoolean(KEY_STATE_SERVICE_READY, false)}")
            appendLine("Analise em andamento: ${bubble.getBoolean(KEY_STATE_ANALYZING, false)}")
            appendLine("Hash da tela: ${bubble.text(KEY_STATE_LAST_SNAPSHOT_HASH)}")
            appendLine("Hash analisado: ${bubble.text(KEY_STATE_LAST_ANALYZED_HASH)}")
            appendLine("Texto da acessibilidade: tamanho=${bubble.getInt(KEY_STATE_ACCESSIBILITY_TEXT_LENGTH, 0)}")
            appendLine("Texto do OCR: tamanho=${bubble.getInt(KEY_STATE_OCR_TEXT_LENGTH, 0)}")
            appendLine("Tempo da ultima decisao: ${bubble.getLong(KEY_FAST_FAROL_ELAPSED, -1L).takeIf { it >= 0 }?.toString()?.plus(" ms") ?: "nao registrado"}")
            appendLine("Caminho da ultima decisao: ${bubble.text(KEY_FAST_FAROL_PATH)}")
            appendLine("Ultimo destino calculado: ${bubble.text(KEY_FAST_FAROL_DESTINATION)}")
            appendLine()
            appendLine("--- REGIAO DE TRABALHO ---")
            appendLine("Casa ativa: ${settings.homeTargetEnabled}")
            appendLine("Casa: ${settings.homeAddress.ifBlank { "nao informada" }}")
            appendLine("Raio Casa: ${settings.homeRadiusKm} km")
            appendLine("Alfinetes ativos: ${settings.alternativeTargetEnabled}")
            appendLine("Raio dos alfinetes: ${settings.alternativeRadiusKm} km")
            appendLine("Alfinetes cadastrados: ${workPins.size}")
            workPins.forEach { pin ->
                val coordinate = pin.coordinate?.let { "${it.latitude},${it.longitude}" } ?: "nao validada"
                appendLine("- ${if (pin.enabled) "ON" else "OFF"} | ${pin.address} | $coordinate")
            }
        }.trimEnd()
    }

    private fun android.content.SharedPreferences.text(key: String): String =
        getString(key, null)?.takeIf { it.isNotBlank() } ?: "nao registrado"

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val component = ComponentName(context, LiveRideAccessibilityService::class.java).flattenToString()
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.equals(component, ignoreCase = true) }
    }

    private const val BUBBLE_PREFS = "rota_certa_bubble"
    private const val KEY_STATE_STAGE = "state_stage"
    private const val KEY_STATE_REASON = "state_reason"
    private const val KEY_STATE_COLOR = "state_color"
    private const val KEY_STATE_DISTANCE_KM = "state_distance_km"
    private const val KEY_STATE_WINDOW_PACKAGE = "state_window_package"
    private const val KEY_STATE_ACTIVE_PACKAGE = "state_active_package"
    private const val KEY_STATE_TEXT_PACKAGE = "state_text_package"
    private const val KEY_STATE_LAST_SNAPSHOT_HASH = "state_last_snapshot_hash"
    private const val KEY_STATE_LAST_ANALYZED_HASH = "state_last_analyzed_hash"
    private const val KEY_STATE_SERVICE_READY = "state_service_ready"
    private const val KEY_STATE_ANALYZING = "state_analyzing"
    private const val KEY_STATE_ACCESSIBILITY_TEXT_LENGTH = "state_accessibility_text_length"
    private const val KEY_STATE_OCR_TEXT_LENGTH = "state_ocr_text_length"
    private const val KEY_FAST_FAROL_ELAPSED = "fast_farol_last_elapsed_ms"
    private const val KEY_FAST_FAROL_PATH = "fast_farol_last_path"
    private const val KEY_FAST_FAROL_DESTINATION = "fast_farol_last_destination"
}
''')

# Remove brand-specific package assumptions from generic evidence policy.
guards_path = SRC / "UniversalRuntimeGuards.kt"
guards = read(guards_path)
guards = re.sub(r'\s*private val knownRidePackages = setOf\(.*?\n\s*\)\n', '\n', guards, flags=re.S)
guards = guards.replace("val knownRideApp = normalizedPackage != null && normalizedPackage in knownRidePackages", "val selectedApp = normalizedPackage != null")
guards = guards.replace("knownRideApp", "selectedApp")
write(guards_path, guards)

# Main UI: remove visual-model/image-capture state, launchers, composables and parameter plumbing.
main_path = SRC / "MainActivity.kt"
main = read(main_path)
for marker in [
    "    fun registerRideCard(",
    "    fun deleteCardModel(",
    "    val cardModelPicker = rememberLauncherForActivityResult",
    "    LaunchedEffect(cardTemplates.size)",
    "private fun CardModelsCard(",
    "private fun AutomaticRideCaptureGallery129(",
    "private fun AutomaticRideCaptureCardChecklist6(",
    "private fun RegisteredCardsModuleCard(",
]:
    main = remove_balanced_declaration(main, marker)
main = remove_lines(main, [
    "val cardTemplates by repository.cardTemplates", "var templateStatus by remember", "var unreadTemplatePrints by remember",
    "cardTemplates = cardTemplates", "templateStatus = templateStatus", "unreadTemplatePrints = unreadTemplatePrints",
    "onPickCardModels =", "onDeleteCardModel =", "onRegisterRideCard =",
    "cardTemplates: List<RideCardTemplate>", "templateStatus: String", "unreadTemplatePrints: Int",
    "onPickCardModels: () -> Unit", "onDeleteCardModel: (RideCardTemplate)", "onRegisterRideCard: (String?, String)",
    "RegisteredCardsModuleCard(", "Modelos cadastrados agora:", "Modelos cadastrados:", "Modelos de cards (apoio opcional)",
])
main = main.replace('backupStatus = "Backup restaurado: ${backup.savedPlaces.size} local(is), ${backup.cardTemplates.size} modelo(s)."',
                    'backupStatus = "Backup restaurado: ${backup.savedPlaces.size} local(is)."')
main = main.replace("LaunchedEffect(settings, cardTemplates.size, savedPlaces.size, radarImportSummary.count)",
                    "LaunchedEffect(settings, savedPlaces.size, radarImportSummary.count)")
main = main.replace("val hasNoUserCollections = cardTemplates.isEmpty() && savedPlaces.isEmpty() && radarImportSummary.count == 0",
                    "val hasNoUserCollections = savedPlaces.isEmpty() && radarImportSummary.count == 0")
main = main.replace('templateStatus = "Dados antigos removidos. App zerado para cadastro correto dos cards."',
                    'backupStatus = "Dados antigos removidos; seleção de aplicativos permanece manual."')
main = main.replace('BUBBLE_GROUP_CARDS -> "Selecione os aplicativos permitidos e gerencie os modelos de cards."',
                    'BUBBLE_GROUP_CARDS -> "Selecione manualmente os aplicativos permitidos para leitura."')
main = re.sub(r'\n\s*CardModelsCard\(.*?\n\s*\)\n', '\n', main, flags=re.S)
main = re.sub(r'\n\s*RegisteredCardsModuleCard\(.*?\n\s*\)\n', '\n', main, flags=re.S)
main = re.sub(r'ManualTechnicalReportBuilder\.build\(\s*context = context,\s*settings = settings,\s*\)',
              'ManualTechnicalReportBuilder.build(context = context, settings = settings)', main, flags=re.S)
write(main_path, normalize_blank_lines(main))

# Accessibility service: retain the proven manual two-address route and remove dormant legacy paths.
service_path = SRC / "LiveRideAccessibilityService.kt"
service = read(service_path)
service = remove_range(service, "    private suspend fun saveCapturedReadToHistory(", "    private suspend fun geocodeBest(")
for marker in [
    "    private fun scheduleCandidateCaptureFinalChecklist6(",
    "    private fun releaseMatchedCaptureFinalChecklist6(",
    "    private fun requestAutomaticRideCapture129(",
    "    private fun buildVisibleCardSignature(",
    "    private fun hasActiveRegisteredDecision(",
    "    private fun resetStaleRegisteredCardDecision(",
    "    private fun saveCurrentRideCardFromBubble(",
    "    private fun captureAndRegisterRideCardManualChecklist12(",
    "    private fun requestManualRideCardScreenshotChecklist12(",
]:
    service = remove_balanced_declaration(service, marker)
service = re.sub(r'\n\s*if \(FullScreenRideCapturePolicy\.shouldSaveCandidate\(.*?bitmap\.recycle\(\) // full_screen_ocr_capture_checklist_11',
                 '\n                                bitmap.recycle()', service, flags=re.S)
service = remove_balanced_declaration(service, "    private data class PendingLiveAnalysis(", include_annotation=False)
service = remove_lines(service, [
    "manualCardCaptureInProgressChecklist12", "automaticCaptureInProgress129", "lastAutomaticCaptureSignature129",
    "lastAutomaticCaptureRequestedAt129", "deferredCandidateCaptureJobFinalChecklist6", "deferredMatchedCaptureJobFinalChecklist6",
    "pendingMatchedCaptureFinalChecklist6", "lastCandidateCaptureSignatureFinalChecklist6",
    "currentCardTemplates", "automaticRideCaptureStore129", "registeredCardGate", "manualActiveCardTemplateId127",
    "bubbleDecisionPolicy", "coreCardAnalysisCoalescer", "coreLivePipeline", "coreBubbleState", "coreVisibleCardLifecycle",
    "repository.cardTemplates", "requireRegisteredRideCard =", "monitor99 =", "monitorUber =", "monitorInDrive =",
    "cardTemplateMatch:", "cardTemplateMatch =", "cardMatch:", "cardMatch =", "pending.cardMatch",
    "KEY_STATE_TEMPLATE_COUNT", "PACKAGE_99_DRIVER", "PACKAGE_UBER_DRIVER", "PACKAGE_INDRIVE_DRIVER",
])
service = service.replace("if (currentSettings.requireRegisteredRideCard || !currentSettings.restrictToSelectedRideApps)",
                          "if (!currentSettings.restrictToSelectedRideApps)")
service = re.sub(r'\n\s*val hardClearUnregisteredCardDefault =.*?\n\s*\}\n', '\n', service, flags=re.S)
service = remove_lines(service, ["private var pendingAnalysis:", "pendingAnalysis = null", "pendingAnalysis?.snapshotHash", "KEY_STATE_PENDING_HASH"])
service = service.replace("Aplicativo nao ensinado; leitura e rota bloqueadas.", "Aplicativo não selecionado; leitura e rota bloqueadas.")
service = service.replace("looksLikeRegisteredPopupCandidate", "looksLikeTwoAddressCandidate")
service = service.replace("Card cadastrado", "Leitura anterior")
service = re.sub(r'\n// registeredCardRequired.*', '\n', service, flags=re.S)
write(service_path, normalize_blank_lines(service))

# Diagnostics wording.
perf_path = SRC / "PerformanceDiagnosticReporter.kt"
perf = read(perf_path)
perf = perf.replace('val firstModelMatch = events.firstOrNullAfter(firstRideVisible.timeMillis) { it.message.contains("card_model.match") }\n', '')
perf = remove_lines(perf, ["primeiro modelo de card confirmado"])
perf = perf.replace("OCR, modelo do card e geocodificacao", "OCR, extração de endereços e geocodificação")
perf = perf.replace("parser/modelo do card", "parser de endereços")
write(perf_path, perf)

# Pure timing policy, without any deferred image capture contract.
write(SRC / "FarolCriticalPathPolicy.kt", '''package br.com.mapeiaia.rotacerta

/** Pure timing rules kept outside the farol decision path. */
object FarolCriticalPathPolicy {
    const val TARGET_RESULT_MILLIS = 850L
    const val OCR_FALLBACK_DELAY_MILLIS = 40L

    fun shouldSkipOcr(screenshotRequestedAtMillis: Long, accessibilityAcceptedAtMillis: Long): Boolean =
        screenshotRequestedAtMillis > 0L && accessibilityAcceptedAtMillis >= screenshotRequestedAtMillis

    fun elapsedWithinTarget(startedAtMillis: Long, nowMillis: Long): Boolean =
        startedAtMillis > 0L && nowMillis >= startedAtMillis && nowMillis - startedAtMillis <= TARGET_RESULT_MILLIS
}
''')

# Production files dedicated solely to specific apps, visual models or automatic image capture.
legacy_files = [
    "AdaptiveCardLearningEngine.kt", "AutomaticRideCaptureStore.kt", "BubbleAnalysisToken.kt",
    "BubbleCardPresenceDetector.kt", "CardPackageLifecyclePolicy.kt", "FastRideCardMatcher.kt",
    "RegisteredRidePackagePolicy.kt", "RideCardTemplateMatcher.kt", "RealWorldRideCardMatchPolicy.kt",
    "FullScreenRideCapturePolicy.kt", "ManualRideCardCapturePolicy.kt", "LiveRideBubbleDecisionPolicy.kt",
    "BubbleCardSessionStore.kt", "BubbleStateMachine.kt", "StableLiveReadSourceGate.kt",
    "PrimaryVisibleRideCardSelector.kt", "RidePassengerIdentityPolicy.kt", "RegisteredCardAddressGate.kt",
    "RegisteredCardDecisionGate.kt", "RideScreenTextClassifier.kt", "RideOfferDetector.kt", "CardRuntimeStability.kt",
    "core/CoreCardMatchEngine.kt", "core/CoreRideCardContracts.kt", "core/InDriveCoreModule.kt",
    "core/CoreCardAnalysisCoalescer.kt", "core/CoreLiveAnalysisPipeline.kt", "core/CoreRouteEngine.kt",
    "core/CoreBubbleStateController.kt", "core/CoreVisibleCardLifecycle.kt",
]
for relative in legacy_files:
    path = SRC / relative
    if path.exists():
        path.unlink()

# Tests whose asserted contract was explicitly deleted are removed; generic route/alert tests remain.
legacy_test_pattern = re.compile(
    r'RideCardTemplate|FastRideCardMatcher|AutomaticRideCapture|InDriveCoreModule|UberCoreModule|NinetyNineCoreModule|'
    r'com\.app99\.driver|com\.ubercab\.driver|sinet\.startup\.indriver|requireRegisteredRideCard|monitor99|monitorUber|monitorInDrive|'
    r'RealWorldRideCardMatchPolicy|FullScreenRideCapturePolicy|ManualRideCardCapturePolicy|LiveRideBubbleDecisionPolicy|'
    r'BubbleCardSessionStore|CoreCardAnalysisCoalescer|CoreLiveAnalysisPipeline|CoreRouteEngine|CoreBubbleStateController|'
    r'CoreVisibleCardLifecycle|BubbleStateMachine|StableLiveReadSourceGate|PrimaryVisibleRideCardSelector|'
    r'RidePassengerIdentityPolicy|RegisteredCardAddressGate|RegisteredCardDecisionGate|RideScreenTextClassifier|RideOfferDetector|CardRuntimeStability'
)
if TEST.exists():
    for path in list(TEST.rglob("*.kt")):
        if legacy_test_pattern.search(read(path)):
            path.unlink()

# Residual brand-specific classifier markers and obsolete terminology.
for path in SRC.rglob("*.kt"):
    text = read(path)
    text = re.sub(r"(?i)uberx|99pop|\bindrive\b|\bninetynine\b", "corrida", text)
    text = text.replace("Alguns cards do corrida", "Algumas telas selecionadas")
    text = text.replace("modelos de cards", "configuração antiga")
    text = text.replace("Modelos removidos", "Leitura direta ativa")
    text = text.replace("strict_model", "strict_manual")
    text = text.replace("com.app99.driver", "")
    text = text.replace("com.ubercab.driver", "")
    text = text.replace("sinet.startup.indriver", "")
    write(path, text)

# Strict production scan before compilation.
forbidden = re.compile(
    r'com\.app99\.driver|com\.ubercab\.driver|sinet\.startup\.indriver|InDriveCoreModule|UberCoreModule|NinetyNineCoreModule|'
    r'RideCardTemplate|FastRideCardMatcher|AutomaticRideCapture|requireRegisteredRideCard|monitor99|monitorUber|monitorInDrive|'
    r'RealWorldRideCardMatchPolicy|FullScreenRideCapturePolicy|ManualRideCardCapturePolicy|LiveRideBubbleDecisionPolicy|'
    r'BubbleCardSessionStore|BubbleStateMachine|StableLiveReadSourceGate|PrimaryVisibleRideCardSelector|'
    r'RidePassengerIdentityPolicy|RegisteredCardAddressGate|RegisteredCardDecisionGate|RideScreenTextClassifier|RideOfferDetector|CardRuntimeStability|'
    r'uberx|99pop|\bindrive\b|\bninetynine\b',
    re.IGNORECASE,
)
remaining = []
for path in SRC.rglob("*.kt"):
    for line_no, line in enumerate(read(path).splitlines(), 1):
        if forbidden.search(line):
            remaining.append(f"{path.relative_to(ROOT)}:{line_no}:{line}")
if remaining:
    raise SystemExit("Forbidden production references remain:\n" + "\n".join(remaining))

print("Final manual-only cleanup applied; production forbidden-reference scan passed.")
