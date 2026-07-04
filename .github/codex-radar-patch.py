from pathlib import Path

BUILD_WORKFLOW = '''name: Build Debug APK

on:
  workflow_dispatch:
  push:
    branches: [main]

permissions:
  contents: write

jobs:
  build:
    name: Build debug APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.10.2

      - name: Check Google Maps secret
        env:
          GOOGLE_MAPS_API_KEY: ${{ secrets.GOOGLE_MAPS_API_KEY }}
        run: |
          if [ -z "$GOOGLE_MAPS_API_KEY" ]; then
            echo "GOOGLE_MAPS_API_KEY secret is empty. APK will still build; configure the key inside the app."
          else
            echo "GOOGLE_MAPS_API_KEY secret found."
          fi

      - name: Record debug APK run start
        run: |
          set -euo pipefail
          mkdir -p .github
          {
            echo "Build Debug APK run: https://github.com/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
            echo "Artifact: pending"
            echo "Commit: ${GITHUB_SHA}"
          } > .github/latest-debug-apk-run.txt
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add .github/latest-debug-apk-run.txt
          git commit -m "Record debug APK run start" || exit 0
          git push

      - name: Run unit tests
        env:
          GOOGLE_MAPS_API_KEY: ${{ secrets.GOOGLE_MAPS_API_KEY }}
        run: gradle testDebugUnitTest --no-daemon --stacktrace

      - name: Build debug APK
        env:
          GOOGLE_MAPS_API_KEY: ${{ secrets.GOOGLE_MAPS_API_KEY }}
        run: gradle assembleDebug --no-daemon --stacktrace

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: rota-certa-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error

      - name: Record latest APK run link
        run: |
          set -euo pipefail
          mkdir -p .github
          {
            echo "Build Debug APK run: https://github.com/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
            echo "Artifact: rota-certa-debug-apk"
            echo "Commit: ${GITHUB_SHA}"
          } > .github/latest-debug-apk-run.txt
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add .github/latest-debug-apk-run.txt
          git commit -m "Record latest debug APK run" || exit 0
          git push

# Keeps the debug APK workflow active after 99 parser and OCR noise fixes.
'''

RADAR_IMPORT_KT = r'''package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

const val MAPA_RADAR_URL = "https://maparadar.com/"

fun parseMapaRadarCsv(content: String, importedAtMillis: Long = System.currentTimeMillis()): List<ImportedRadar> {
    return content.lineSequence()
        .dropWhile { it.trim().startsWith("X,", ignoreCase = true) || it.trim().isBlank() }
        .mapNotNull { line -> parseMapaRadarLine(line, importedAtMillis) }
        .distinctBy { it.id }
        .toList()
}

private fun parseMapaRadarLine(line: String, importedAtMillis: Long): ImportedRadar? {
    val parts = line.split(',').map { it.trim() }
    if (parts.size < 4) return null
    val longitude = parts[0].toDoubleOrNull() ?: return null
    val latitude = parts[1].toDoubleOrNull() ?: return null
    val type = parts[2].toIntOrNull() ?: return null
    val speed = parts[3].toIntOrNull() ?: 0
    if (latitude !in -34.0..6.0 || longitude !in -75.0..-30.0) return null
    val id = "radar-${type}-${speed}-${"%.6f".format(Locale.US, latitude)}-${"%.6f".format(Locale.US, longitude)}"
    return ImportedRadar(
        id = id,
        type = type,
        speed = speed,
        coordinate = Coordinate(latitude = latitude, longitude = longitude),
        importedAtMillis = importedAtMillis,
    )
}

fun importedRadarTypeLabel(type: Int): String = when (type) {
    1 -> "Radar fixo"
    2 -> "Radar movel"
    3 -> "Semaforo com camera"
    4 -> "Semaforo com camera"
    5 -> "Semaforo com radar"
    6 -> "Policia rodoviaria"
    7 -> "Pedagio"
    9 -> "Lombada"
    else -> "Ponto de alerta"
}

fun importedRadarSpeech(radar: ImportedRadar): String {
    val speed = radar.speed.takeIf { it > 0 }?.let { " $it km por hora" }.orEmpty()
    return "${importedRadarTypeLabel(radar.type)}$speed se aproximando"
}

fun openMapaRadarSite(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MAPA_RADAR_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "Nao consegui abrir o MapaRadar neste aparelho.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RadarImportCard(
    summary: RadarImportSummary?,
    status: String,
    onImportRadarFile: () -> Unit,
    onOpenMapaRadar: () -> Unit,
    onClearImportedRadars: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Radares importados", fontWeight = FontWeight.Bold)
            Text(
                "Anexe o TXT ou CSV baixado do MapaRadar. O Rota Certa vai avisar por voz quando voce se aproximar desses pontos.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onImportRadarFile, modifier = Modifier.weight(1f)) {
                    Text("Importar TXT/CSV")
                }
                OutlinedButton(onClick = onOpenMapaRadar, modifier = Modifier.weight(1f)) {
                    Text("Abrir site")
                }
            }
            summary?.takeIf { it.count > 0 }?.let {
                Text("Importados: ${it.count} radares", fontWeight = FontWeight.Bold)
                Text("Origem: ${it.sourceName.ifBlank { "arquivo selecionado" }}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onClearImportedRadars, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpar radares importados")
                }
            } ?: Text("Nenhum radar importado ainda.", style = MaterialTheme.typography.bodySmall)
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
'''


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


def patch_models():
    path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
    text = path.read_text()
    text = replace_once(text,
        """    val capturedScreens: List<CapturedRideScreen> = emptyList(),\n    val savedPlaces: List<SavedPlace> = emptyList(),\n)""",
        """    val capturedScreens: List<CapturedRideScreen> = emptyList(),\n    val savedPlaces: List<SavedPlace> = emptyList(),\n    val importedRadars: List<ImportedRadar> = emptyList(),\n)""",
        "backup imported radars")
    text = replace_once(text,
        """data class SavedPlace(\n    val id: String,\n    val name: String,\n    val type: SavedPlaceType = SavedPlaceType.Place,\n    val address: String = \"\",\n    val coordinate: Coordinate,\n    val alertDistanceMeters: Int? = null,\n    val createdAtMillis: Long = 0L,\n    val lastTriggeredAtMillis: Long? = null,\n    val triggerCountInCurrentApproach: Int = 0,\n)\n\nenum class Recommendation""",
        """data class SavedPlace(\n    val id: String,\n    val name: String,\n    val type: SavedPlaceType = SavedPlaceType.Place,\n    val address: String = \"\",\n    val coordinate: Coordinate,\n    val alertDistanceMeters: Int? = null,\n    val createdAtMillis: Long = 0L,\n    val lastTriggeredAtMillis: Long? = null,\n    val triggerCountInCurrentApproach: Int = 0,\n)\n\n@Serializable\ndata class ImportedRadar(\n    val id: String,\n    val type: Int,\n    val speed: Int = 0,\n    val coordinate: Coordinate,\n    val importedAtMillis: Long = 0L,\n)\n\n@Serializable\ndata class RadarImportSummary(\n    val sourceName: String = \"\",\n    val count: Int = 0,\n    val importedAtMillis: Long = 0L,\n)\n\nenum class Recommendation""",
        "imported radar models")
    path.write_text(text)


def patch_repository():
    path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt")
    text = path.read_text()
    text = replace_once(text,
        """    private val capturedRideScreens = stringPreferencesKey(\"captured_ride_screens\")\n    private val savedPlacesKey = stringPreferencesKey(\"saved_places\")\n    private val json = Json { ignoreUnknownKeys = true }""",
        """    private val capturedRideScreens = stringPreferencesKey(\"captured_ride_screens\")\n    private val savedPlacesKey = stringPreferencesKey(\"saved_places\")\n    private val importedRadarsKey = stringPreferencesKey(\"imported_radars\")\n    private val radarImportSummaryKey = stringPreferencesKey(\"radar_import_summary\")\n    private val json = Json { ignoreUnknownKeys = true }""",
        "repository keys")
    text = replace_once(text,
        """    val savedPlaces: Flow<List<SavedPlace>> = context.dataStore.data.map { prefs ->\n        runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }\n            .getOrDefault(emptyList())\n    }\n\n    suspend fun saveSettings(settings: AppSettings)""",
        """    val savedPlaces: Flow<List<SavedPlace>> = context.dataStore.data.map { prefs ->\n        runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }\n            .getOrDefault(emptyList())\n    }\n\n    val importedRadars: Flow<List<ImportedRadar>> = context.dataStore.data.map { prefs ->\n        runCatching { json.decodeFromString<List<ImportedRadar>>(prefs[importedRadarsKey].orEmpty()) }\n            .getOrDefault(emptyList())\n    }\n\n    val radarImportSummary: Flow<RadarImportSummary?> = context.dataStore.data.map { prefs ->\n        runCatching { json.decodeFromString<RadarImportSummary>(prefs[radarImportSummaryKey].orEmpty()) }.getOrNull()\n    }\n\n    suspend fun saveSettings(settings: AppSettings)""",
        "radar flows")
    text = replace_once(text,
        """    suspend fun removeSavedPlace(placeId: String) {\n        context.dataStore.edit { prefs ->\n            val current = runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }\n                .getOrDefault(emptyList())\n            prefs[savedPlacesKey] = json.encodeToString(current.filterNot { it.id == placeId })\n        }\n    }\n\n    suspend fun exportBackupJson(): String""",
        """    suspend fun removeSavedPlace(placeId: String) {\n        context.dataStore.edit { prefs ->\n            val current = runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }\n                .getOrDefault(emptyList())\n            prefs[savedPlacesKey] = json.encodeToString(current.filterNot { it.id == placeId })\n        }\n    }\n\n    suspend fun replaceImportedRadars(radars: List<ImportedRadar>, summary: RadarImportSummary) {\n        context.dataStore.edit { prefs ->\n            prefs[importedRadarsKey] = json.encodeToString(radars)\n            prefs[radarImportSummaryKey] = json.encodeToString(summary.copy(count = radars.size))\n        }\n    }\n\n    suspend fun clearImportedRadars() {\n        context.dataStore.edit { prefs ->\n            prefs.remove(importedRadarsKey)\n            prefs.remove(radarImportSummaryKey)\n        }\n    }\n\n    suspend fun exportBackupJson(): String""",
        "radar repository methods")
    text = replace_once(text,
        """            capturedScreens = capturedScreens.first(),\n            savedPlaces = savedPlaces.first(),\n        )""",
        """            capturedScreens = capturedScreens.first(),\n            savedPlaces = savedPlaces.first(),\n            importedRadars = importedRadars.first(),\n        )""",
        "backup export radars")
    text = replace_once(text,
        """            prefs[capturedRideScreens] = json.encodeToString(backup.capturedScreens.take(20))\n            prefs[savedPlacesKey] = json.encodeToString(backup.savedPlaces.take(200))\n            prefs.remove(liveDiagnostic)""",
        """            prefs[capturedRideScreens] = json.encodeToString(backup.capturedScreens.take(20))\n            prefs[savedPlacesKey] = json.encodeToString(backup.savedPlaces.take(200))\n            prefs[importedRadarsKey] = json.encodeToString(backup.importedRadars)\n            prefs[radarImportSummaryKey] = json.encodeToString(\n                RadarImportSummary(sourceName = \"Backup restaurado\", count = backup.importedRadars.size, importedAtMillis = System.currentTimeMillis()),\n            )\n            prefs.remove(liveDiagnostic)""",
        "backup restore radars")
    path.write_text(text)


def patch_main_activity():
    path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    text = path.read_text()

    # Pending naming-flow fix from the previous request.
    if "fun RotaCertaApp()" in text:
        text = replace_once(text, """class MainActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        setContent {\n            MaterialTheme(colorScheme = darkColorScheme()) {\n                RotaCertaApp()\n            }\n        }\n    }\n}\n\n@Composable\nfun RotaCertaApp() {""", """class MainActivity : ComponentActivity() {\n    private var launchIntent by mutableStateOf<Intent?>(null)\n\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        launchIntent = intent\n        setContent {\n            MaterialTheme(colorScheme = darkColorScheme()) {\n                RotaCertaApp(launchIntent)\n            }\n        }\n    }\n\n    override fun onNewIntent(intent: Intent) {\n        super.onNewIntent(intent)\n        setIntent(intent)\n        launchIntent = intent\n    }\n}\n\n@Composable\nfun RotaCertaApp(launchIntent: Intent?) {""", "activity intent handling")
        text = replace_once(text, """    var tab by remember { mutableStateOf(\"analise\") }\n    var region by remember { mutableStateOf(DeviceRegion()) }""", """    var tab by remember { mutableStateOf(TAB_ANALYSIS) }\n    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }\n    var region by remember { mutableStateOf(DeviceRegion()) }""", "tab state")
        text = replace_once(text, """    LaunchedEffect(cardTemplates.size) {\n        if (!templateStatus.startsWith(\"Lendo \")) {\n            templateStatus = \"Modelos cadastrados: ${cardTemplates.size}\"\n        }\n    }\n\n    LaunchedEffect(Unit) {""", """    LaunchedEffect(cardTemplates.size) {\n        if (!templateStatus.startsWith(\"Lendo \")) {\n            templateStatus = \"Modelos cadastrados: ${cardTemplates.size}\"\n        }\n    }\n\n    LaunchedEffect(launchIntent) {\n        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)\n        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_HISTORY) {\n            tab = requestedTab\n        }\n        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)\n    }\n\n    LaunchedEffect(Unit) {""", "launch intent effect")
        text = text.replace('tab == "analise"', 'tab == TAB_ANALYSIS').replace('tab = "analise"', 'tab = TAB_ANALYSIS')
        text = text.replace('tab == "config"', 'tab == TAB_CONFIG').replace('tab = "config"', 'tab = TAB_CONFIG')
        text = text.replace('tab == "historico"', 'tab == TAB_HISTORY').replace('tab = "historico"', 'tab = TAB_HISTORY')
        text = text.replace('"analise" -> AnalysisScreen(', 'TAB_ANALYSIS -> AnalysisScreen(')
        text = text.replace('"config" -> SettingsScreen(', 'TAB_CONFIG -> SettingsScreen(')
        text = text.replace('"historico" -> HistoryScreen(history)', 'TAB_HISTORY -> HistoryScreen(history)')

    text = replace_once(text,
        """    val savedPlaces by repository.savedPlaces.collectAsState(initial = emptyList())\n    val ocrService = remember { OcrService(context) }""",
        """    val savedPlaces by repository.savedPlaces.collectAsState(initial = emptyList())\n    val radarImportSummary by repository.radarImportSummary.collectAsState(initial = null)\n    val ocrService = remember { OcrService(context) }""",
        "radar summary state")
    text = replace_once(text,
        """    var unreadTemplatePrints by remember { mutableStateOf(0) }\n    var backupStatus by remember { mutableStateOf(\"\") }""",
        """    var unreadTemplatePrints by remember { mutableStateOf(0) }\n    var backupStatus by remember { mutableStateOf(\"\") }\n    var radarImportStatus by remember { mutableStateOf(\"\") }""",
        "radar status state")
    text = replace_once(text,
        """    val backupFileCreator = rememberLauncherForActivityResult(\n        ActivityResultContracts.CreateDocument(\"application/json\"),""",
        """    val radarFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->\n        if (uri == null) {\n            radarImportStatus = \"Importacao cancelada.\"\n            return@rememberLauncherForActivityResult\n        }\n        scope.launch {\n            radarImportStatus = \"Importando radares...\"\n            runCatching {\n                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->\n                    reader.readText()\n                } ?: error(\"Nao consegui abrir o arquivo selecionado.\")\n                val importedAt = System.currentTimeMillis()\n                val radars = parseMapaRadarCsv(content, importedAt)\n                if (radars.isEmpty()) error(\"Arquivo sem radares validos. Use TXT/CSV do MapaRadar.\")\n                val sourceName = uri.lastPathSegment?.substringAfterLast('/') ?: \"maparadar.txt\"\n                repository.replaceImportedRadars(radars, RadarImportSummary(sourceName = sourceName, count = radars.size, importedAtMillis = importedAt))\n                radars.size\n            }.onSuccess { count ->\n                radarImportStatus = \"Importacao concluida: $count radar(es).\"\n                Toast.makeText(context, \"Radares importados: $count\", Toast.LENGTH_SHORT).show()\n            }.onFailure { error ->\n                radarImportStatus = \"Falha ao importar radares: ${error.message.orEmpty()}\"\n            }\n        }\n    }\n\n    val backupFileCreator = rememberLauncherForActivityResult(\n        ActivityResultContracts.CreateDocument(\"application/json\"),""",
        "radar picker")
    text = replace_once(text,
        """                    savedPlaces = savedPlaces,\n                    backupStatus = backupStatus,""",
        """                    savedPlaces = savedPlaces,\n                    backupStatus = backupStatus,\n                    highlightedSavedPlaceId = highlightedSavedPlaceId,\n                    radarImportSummary = radarImportSummary,\n                    radarImportStatus = radarImportStatus,""",
        "settings args")
    text = replace_once(text,
        """                    onRestoreBackup = { backupFilePicker.launch(arrayOf(\"application/json\", \"text/plain\", \"*/*\")) },""",
        """                    onRestoreBackup = { backupFilePicker.launch(arrayOf(\"application/json\", \"text/plain\", \"*/*\")) },\n                    onImportRadarFile = { radarFilePicker.launch(arrayOf(\"text/*\", \"text/comma-separated-values\", \"application/octet-stream\", \"*/*\")) },\n                    onOpenMapaRadar = { openMapaRadarSite(context) },\n                    onClearImportedRadars = {\n                        scope.launch {\n                            repository.clearImportedRadars()\n                            radarImportStatus = \"Radares importados removidos.\"\n                        }\n                    },""",
        "settings radar callbacks")
    text = replace_once(text,
        """    savedPlaces: List<SavedPlace>,\n    backupStatus: String,\n    onSave: (AppSettings) -> Unit,""",
        """    savedPlaces: List<SavedPlace>,\n    backupStatus: String,\n    highlightedSavedPlaceId: String?,\n    radarImportSummary: RadarImportSummary?,\n    radarImportStatus: String,\n    onSave: (AppSettings) -> Unit,""",
        "settings signature")
    text = replace_once(text,
        """    onCreateBackup: () -> Unit,\n    onRestoreBackup: () -> Unit,\n) {""",
        """    onCreateBackup: () -> Unit,\n    onRestoreBackup: () -> Unit,\n    onImportRadarFile: () -> Unit,\n    onOpenMapaRadar: () -> Unit,\n    onClearImportedRadars: () -> Unit,\n) {""",
        "settings callbacks signature")
    text = replace_once(text,
        """        SavedPlacesCard(\n            savedPlaces = savedPlaces,\n            onRenameSavedPlace = onRenameSavedPlace,\n            onDeleteSavedPlace = onDeleteSavedPlace,\n        )""",
        """        SavedPlacesCard(\n            savedPlaces = savedPlaces,\n            highlightedSavedPlaceId = highlightedSavedPlaceId,\n            onRenameSavedPlace = onRenameSavedPlace,\n            onDeleteSavedPlace = onDeleteSavedPlace,\n        )\n        RadarImportCard(\n            summary = radarImportSummary,\n            status = radarImportStatus,\n            onImportRadarFile = onImportRadarFile,\n            onOpenMapaRadar = onOpenMapaRadar,\n            onClearImportedRadars = onClearImportedRadars,\n        )""",
        "settings radar card")

    # Saved place highlight support if not already present.
    if "highlightedSavedPlaceId: String?" not in text[text.find("private fun SavedPlacesCard"):text.find("private fun SavedPlaceEditor")]:
        text = replace_once(text, """private fun SavedPlacesCard(\n    savedPlaces: List<SavedPlace>,\n    onRenameSavedPlace: (SavedPlace, String) -> Unit,\n    onDeleteSavedPlace: (SavedPlace) -> Unit,\n) {\n    val places = savedPlaces.filter { it.type == SavedPlaceType.Place }\n    val alerts = savedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }\n\n    ExpandableCard(title = \"Locais salvos (${places.size})\", initiallyExpanded = false) {""", """private fun SavedPlacesCard(\n    savedPlaces: List<SavedPlace>,\n    highlightedSavedPlaceId: String?,\n    onRenameSavedPlace: (SavedPlace, String) -> Unit,\n    onDeleteSavedPlace: (SavedPlace) -> Unit,\n) {\n    val places = savedPlaces.filter { it.type == SavedPlaceType.Place }\n    val alerts = savedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }\n    val highlightedType = savedPlaces.firstOrNull { it.id == highlightedSavedPlaceId }?.type\n\n    if (highlightedType != null) {\n        Text(\"Item criado pela bolinha. Informe um nome claro e toque em Salvar.\", style = MaterialTheme.typography.bodySmall)\n    }\n\n    ExpandableCard(title = \"Locais salvos (${places.size})\", initiallyExpanded = highlightedType == SavedPlaceType.Place) {""", "saved place highlight")
        text = text.replace("SavedPlaceEditor(place, onRenameSavedPlace, onDeleteSavedPlace)", "SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)")
        text = text.replace('ExpandableCard(title = "Alertas de proximidade (${alerts.size})", initiallyExpanded = false)', 'ExpandableCard(title = "Alertas de proximidade (${alerts.size})", initiallyExpanded = highlightedType == SavedPlaceType.ProximityAlert)')
        text = replace_once(text, """private fun SavedPlaceEditor(\n    place: SavedPlace,\n    onRenameSavedPlace: (SavedPlace, String) -> Unit,\n    onDeleteSavedPlace: (SavedPlace) -> Unit,\n) {""", """private fun SavedPlaceEditor(\n    place: SavedPlace,\n    highlighted: Boolean = false,\n    onRenameSavedPlace: (SavedPlace, String) -> Unit,\n    onDeleteSavedPlace: (SavedPlace) -> Unit,\n) {""", "editor signature")
        text = replace_once(text, """        Text(savedPlaceTypeLabel(place), fontWeight = FontWeight.Bold)\n        OutlinedTextField(""", """        Text(savedPlaceTypeLabel(place), fontWeight = FontWeight.Bold)\n        if (highlighted) {\n            Text(\"Informe o nome deste item agora. Esse nome aparece na lista e no alerta de voz.\", style = MaterialTheme.typography.bodySmall)\n        }\n        OutlinedTextField(""", "editor hint")

    if "EXTRA_OPEN_TAB" not in text:
        text = replace_once(text, "private enum class LocationTarget { Home, Alternative }", """private const val EXTRA_OPEN_TAB = \"br.com.mapeiaia.rotacerta.extra.OPEN_TAB\"\nprivate const val EXTRA_SAVED_PLACE_ID = \"br.com.mapeiaia.rotacerta.extra.SAVED_PLACE_ID\"\nprivate const val TAB_ANALYSIS = \"analise\"\nprivate const val TAB_CONFIG = \"config\"\nprivate const val TAB_HISTORY = \"historico\"\n\nprivate enum class LocationTarget { Home, Alternative }""", "constants")
    if "LaunchedEffect(initiallyExpanded)" not in text:
        text = replace_once(text, """    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }\n    Card(modifier = Modifier.fillMaxWidth()) {""", """    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }\n    LaunchedEffect(initiallyExpanded) {\n        if (initiallyExpanded) expanded = true\n    }\n    Card(modifier = Modifier.fillMaxWidth()) {""", "expandable effect")
    path.write_text(text)


def patch_service():
    path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    text = path.read_text()
    text = replace_once(text,
        """    private var currentCardTemplates = emptyList<RideCardTemplate>()\n    private var currentSavedPlaces = emptyList<SavedPlace>()""",
        """    private var currentCardTemplates = emptyList<RideCardTemplate>()\n    private var currentSavedPlaces = emptyList<SavedPlace>()\n    private var currentImportedRadars = emptyList<ImportedRadar>()""",
        "service radar state")
    text = replace_once(text,
        """        scope.launch { repository.cardTemplates.collect { currentCardTemplates = it } }\n        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }""",
        """        scope.launch { repository.cardTemplates.collect { currentCardTemplates = it } }\n        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }\n        scope.launch { repository.importedRadars.collect { currentImportedRadars = it } }""",
        "service radar collect")
    text = replace_once(text,
        """                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }\n                if (alerts.isNotEmpty()) checkProximityAlerts(alerts)""",
        """                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }\n                val radars = currentImportedRadars\n                if (alerts.isNotEmpty() || radars.isNotEmpty()) checkProximityAlerts(alerts, radars)""",
        "service loop radars")
    text = replace_once(text,
        """    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>) {\n        val coordinate = locationService.currentCoordinate() ?: return""",
        """    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>, radars: List<ImportedRadar>) {\n        val coordinate = locationService.currentCoordinate() ?: return""",
        "check signature")
    text = replace_once(text,
        """        alerts.forEach { alert ->\n            val threshold = (alert.alertDistanceMeters ?: currentSettings.proximityAlertDistanceMeters).coerceIn(200, 1000)""",
        """        checkImportedRadars(radars, coordinate, now)\n\n        alerts.forEach { alert ->\n            val threshold = (alert.alertDistanceMeters ?: currentSettings.proximityAlertDistanceMeters).coerceIn(200, 1000)""",
        "check imported radars call")
    text = replace_once(text,
        """    private fun scheduleVisibleTextAnalysis(delayMs: Long) {""",
        """    private fun checkImportedRadars(radars: List<ImportedRadar>, coordinate: Coordinate, now: Long) {\n        if (radars.isEmpty()) return\n        val threshold = currentSettings.proximityAlertDistanceMeters.coerceIn(200, 1000)\n        val nearest = radars.asSequence()\n            .map { radar -> radar to distanceMeters(coordinate, radar.coordinate) }\n            .filter { (_, distance) -> distance <= threshold }\n            .minByOrNull { (_, distance) -> distance }\n            ?: return\n        val radar = nearest.first\n        val distanceMeters = nearest.second\n        val runtime = proximityAlertRuntime.getOrPut(\"imported-${radar.id}\") { ProximityAlertRuntime() }\n        if (\n            runtime.spokenCount < MAX_PROXIMITY_ALERT_SPEECH_COUNT &&\n            now - runtime.lastSpokenAtMillis >= PROXIMITY_ALERT_REPEAT_GAP_MS\n        ) {\n            speakImportedRadar(radar)\n            runtime.spokenCount += 1\n            runtime.lastSpokenAtMillis = now\n            recordDiagnostic(\n                stage = \"imported_radar_spoken\",\n                color = currentRadarColor,\n                reason = \"Radar importado falado: ${importedRadarSpeech(radar)} a ${distanceMeters.roundToInt()} metros.\",\n            )\n        }\n    }\n\n    private fun scheduleVisibleTextAnalysis(delayMs: Long) {""",
        "check imported radars function")
    if "openSavedPlaceEditor(place)" not in text:
        text = replace_once(text, """            repository.addSavedPlace(place)\n            toast(if (isAlert) \"Alerta de proximidade criado.\" else \"Local salvo.\")""", """            repository.addSavedPlace(place)\n            openSavedPlaceEditor(place)\n            toast(if (isAlert) \"Alerta criado. Informe o nome.\" else \"Local salvo. Informe o nome.\")""", "open editor after save")
        text = replace_once(text, """    private fun openApp() {\n        hideActionMenu()\n        runCatching {\n            startActivity(\n                Intent(this, MainActivity::class.java)\n                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),\n            )\n        }\n    }\n\n    private fun toggleActionMenu() {""", """    private fun openApp() {\n        hideActionMenu()\n        runCatching {\n            startActivity(\n                Intent(this, MainActivity::class.java)\n                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),\n            )\n        }\n    }\n\n    private fun openSavedPlaceEditor(place: SavedPlace) {\n        hideActionMenu()\n        runCatching {\n            startActivity(\n                Intent(this, MainActivity::class.java)\n                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)\n                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)\n                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),\n            )\n        }\n    }\n\n    private fun toggleActionMenu() {""", "open editor helper")
    text = replace_once(text,
        """    private fun speakProximityAlert(place: SavedPlace) {""",
        """    private fun speakImportedRadar(radar: ImportedRadar) {\n        if (!textToSpeechReady) return\n        textToSpeech?.speak(\n            importedRadarSpeech(radar),\n            TextToSpeech.QUEUE_ADD,\n            null,\n            \"imported-radar-${radar.id}-${System.currentTimeMillis()}\",\n        )\n    }\n\n    private fun speakProximityAlert(place: SavedPlace) {""",
        "speak imported radar")
    if "EXTRA_OPEN_TAB" not in text:
        text = replace_once(text, """        const val KEY_BUBBLE_Y = \"bubble_y\"\n        const val PACKAGE_99_DRIVER = \"com.app99.driver\""" , """        const val KEY_BUBBLE_Y = \"bubble_y\"\n        const val EXTRA_OPEN_TAB = \"br.com.mapeiaia.rotacerta.extra.OPEN_TAB\"\n        const val EXTRA_SAVED_PLACE_ID = \"br.com.mapeiaia.rotacerta.extra.SAVED_PLACE_ID\"\n        const val TAB_CONFIG = \"config\"\n        const val PACKAGE_99_DRIVER = \"com.app99.driver\""", "service constants")
    path.write_text(text)


def main():
    patch_models()
    patch_repository()
    Path("app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt").write_text(RADAR_IMPORT_KT)
    patch_main_activity()
    patch_service()
    Path(".github/workflows/build-debug-apk.yml").write_text(BUILD_WORKFLOW)
    old_workflow = Path(".github/workflows/apply-saved-place-naming-flow.yml")
    if old_workflow.exists():
        old_workflow.unlink()
    script = Path(".github/codex-radar-patch.py")
    if script.exists():
        script.unlink()

if __name__ == "__main__":
    main()
