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
          CURRENT_SHA="$(git rev-parse HEAD)"
          mkdir -p .github
          {
            echo "Build Debug APK run: https://github.com/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
            echo "Artifact: pending"
            echo "Commit: ${CURRENT_SHA}"
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
          CURRENT_SHA="$(git rev-parse HEAD)"
          mkdir -p .github
          {
            echo "Build Debug APK run: https://github.com/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
            echo "Artifact: rota-certa-debug-apk"
            echo "Commit: ${CURRENT_SHA}"
          } > .github/latest-debug-apk-run.txt
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add .github/latest-debug-apk-run.txt
          git commit -m "Record latest debug APK run" || exit 0
          git push

# Keeps the debug APK workflow active after radar import and continuous GPS guidance.
'''


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


def patch_main_activity():
    path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    text = path.read_text()

    if "fun RotaCertaApp()" in text:
        text = replace_once(
            text,
            '''class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RotaCertaApp()
            }
        }
    }
}

@Composable
fun RotaCertaApp() {''',
            '''class MainActivity : ComponentActivity() {
    private var launchIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchIntent = intent
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RotaCertaApp(launchIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntent = intent
    }
}

@Composable
fun RotaCertaApp(launchIntent: Intent?) {''',
            "activity intent handling",
        )
        text = replace_once(
            text,
            '''    var tab by remember { mutableStateOf("analise") }
    var region by remember { mutableStateOf(DeviceRegion()) }''',
            '''    var tab by remember { mutableStateOf(TAB_ANALYSIS) }
    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }
    var region by remember { mutableStateOf(DeviceRegion()) }''',
            "tab state",
        )
        text = text.replace('tab == "analise"', 'tab == TAB_ANALYSIS')
        text = text.replace('tab = "analise"', 'tab = TAB_ANALYSIS')
        text = text.replace('tab == "config"', 'tab == TAB_CONFIG')
        text = text.replace('tab = "config"', 'tab = TAB_CONFIG')
        text = text.replace('tab == "historico"', 'tab == TAB_HISTORY')
        text = text.replace('tab = "historico"', 'tab = TAB_HISTORY')
        text = text.replace('"analise" -> AnalysisScreen(', 'TAB_ANALYSIS -> AnalysisScreen(')
        text = text.replace('"config" -> SettingsScreen(', 'TAB_CONFIG -> SettingsScreen(')
        text = text.replace('"historico" -> HistoryScreen(history)', 'TAB_HISTORY -> HistoryScreen(history)')

    if "radarImportSummary" not in text:
        text = replace_once(
            text,
            '''    val savedPlaces by repository.savedPlaces.collectAsState(initial = emptyList())
    val ocrService = remember { OcrService(context) }''',
            '''    val savedPlaces by repository.savedPlaces.collectAsState(initial = emptyList())
    val radarImportSummary by repository.radarImportSummary.collectAsState(initial = RadarImportSummary())
    val ocrService = remember { OcrService(context) }''',
            "radar summary state",
        )
        text = replace_once(
            text,
            '''    var unreadTemplatePrints by remember { mutableStateOf(0) }
    var backupStatus by remember { mutableStateOf("") }''',
            '''    var unreadTemplatePrints by remember { mutableStateOf(0) }
    var backupStatus by remember { mutableStateOf("") }
    var radarImportStatus by remember { mutableStateOf("") }''',
            "radar import status",
        )
        text = replace_once(
            text,
            '''    val backupFileCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),''',
            '''    val radarFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            radarImportStatus = "Importacao cancelada."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            radarImportStatus = "Importando radares..."
            runCatching {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error("Nao consegui abrir o arquivo selecionado.")
                val radars = parseMapaRadarCsv(content)
                if (radars.isEmpty()) error("Arquivo sem radares validos. Use TXT/CSV do MapaRadar.")
                repository.replaceImportedRadars(radars)
                radars.size
            }.onSuccess { count ->
                radarImportStatus = "Importacao concluida: $count radar(es)."
                Toast.makeText(context, "Radares importados: $count", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                radarImportStatus = "Falha ao importar radares: ${error.message.orEmpty()}"
            }
        }
    }

    val backupFileCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),''',
            "radar file picker",
        )
        text = replace_once(
            text,
            '''    LaunchedEffect(cardTemplates.size) {
        if (!templateStatus.startsWith("Lendo ")) {
            templateStatus = "Modelos cadastrados: ${cardTemplates.size}"
        }
    }

    LaunchedEffect(Unit) {''',
            '''    LaunchedEffect(cardTemplates.size) {
        if (!templateStatus.startsWith("Lendo ")) {
            templateStatus = "Modelos cadastrados: ${cardTemplates.size}"
        }
    }

    LaunchedEffect(launchIntent) {
        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_HISTORY) {
            tab = requestedTab
        }
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
    }

    LaunchedEffect(Unit) {''',
            "launch intent effect",
        )
        text = replace_once(
            text,
            '''                    savedPlaces = savedPlaces,
                    backupStatus = backupStatus,''',
            '''                    savedPlaces = savedPlaces,
                    backupStatus = backupStatus,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    radarImportSummary = radarImportSummary,
                    radarImportStatus = radarImportStatus,''',
            "settings arguments",
        )
        text = replace_once(
            text,
            '''                    onRestoreBackup = { backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },''',
            '''                    onRestoreBackup = { backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onImportRadarFile = { radarFilePicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream", "*/*")) },
                    onOpenMapaRadar = { openMapaRadarSite(context) },
                    onClearImportedRadars = {
                        scope.launch {
                            repository.clearImportedRadars()
                            radarImportStatus = "Radares importados removidos."
                        }
                    },''',
            "settings radar callbacks",
        )
        text = replace_once(
            text,
            '''    savedPlaces: List<SavedPlace>,
    backupStatus: String,
    onSave: (AppSettings) -> Unit,''',
            '''    savedPlaces: List<SavedPlace>,
    backupStatus: String,
    highlightedSavedPlaceId: String?,
    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    onSave: (AppSettings) -> Unit,''',
            "settings signature",
        )
        text = replace_once(
            text,
            '''    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {''',
            '''    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onImportRadarFile: () -> Unit,
    onOpenMapaRadar: () -> Unit,
    onClearImportedRadars: () -> Unit,
) {''',
            "settings callback signature",
        )
        text = replace_once(
            text,
            '''        DiagnosticExpander(
            diagnostic = diagnostic,
            cardTemplates = cardTemplates,
            onRegisterRideCard = onRegisterRideCard,
        )''',
            '''        AlwaysLocationPermissionCard(
            hasAlwaysPermission = hasAlwaysLocationPermission(context),
            onOpenLocationSettings = { openAppLocationSettings(context) },
        )
        DiagnosticExpander(
            diagnostic = diagnostic,
            cardTemplates = cardTemplates,
            onRegisterRideCard = onRegisterRideCard,
        )''',
            "always location card",
        )
        text = replace_once(
            text,
            '''        SavedPlacesCard(
            savedPlaces = savedPlaces,
            onRenameSavedPlace = onRenameSavedPlace,
            onDeleteSavedPlace = onDeleteSavedPlace,
        )''',
            '''        SavedPlacesCard(
            savedPlaces = savedPlaces,
            highlightedSavedPlaceId = highlightedSavedPlaceId,
            onRenameSavedPlace = onRenameSavedPlace,
            onDeleteSavedPlace = onDeleteSavedPlace,
        )
        RadarImportCard(
            summary = radarImportSummary,
            importStatus = radarImportStatus,
            onPickFile = onImportRadarFile,
            onOpenMapaRadar = onOpenMapaRadar,
            onClearRadars = onClearImportedRadars,
        )''',
            "saved places and radar card",
        )

    if "highlightedSavedPlaceId: String?" not in text[text.find("private fun SavedPlacesCard"):text.find("private fun SavedPlaceEditor")]:
        text = replace_once(
            text,
            '''private fun SavedPlacesCard(
    savedPlaces: List<SavedPlace>,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val places = savedPlaces.filter { it.type == SavedPlaceType.Place }
    val alerts = savedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }

    ExpandableCard(title = "Locais salvos (${places.size})", initiallyExpanded = false) {''',
            '''private fun SavedPlacesCard(
    savedPlaces: List<SavedPlace>,
    highlightedSavedPlaceId: String?,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val places = savedPlaces.filter { it.type == SavedPlaceType.Place }
    val alerts = savedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
    val highlightedType = savedPlaces.firstOrNull { it.id == highlightedSavedPlaceId }?.type

    if (highlightedType != null) {
        Text("Item criado pela bolinha. Informe um nome claro e toque em Salvar.", style = MaterialTheme.typography.bodySmall)
    }

    ExpandableCard(title = "Locais salvos (${places.size})", initiallyExpanded = highlightedType == SavedPlaceType.Place) {''',
            "saved place card highlight",
        )
        text = text.replace("SavedPlaceEditor(place, onRenameSavedPlace, onDeleteSavedPlace)", "SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)")
        text = text.replace('ExpandableCard(title = "Alertas de proximidade (${alerts.size})", initiallyExpanded = false)', 'ExpandableCard(title = "Alertas de proximidade (${alerts.size})", initiallyExpanded = highlightedType == SavedPlaceType.ProximityAlert)')
        text = replace_once(
            text,
            '''private fun SavedPlaceEditor(
    place: SavedPlace,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {''',
            '''private fun SavedPlaceEditor(
    place: SavedPlace,
    highlighted: Boolean = false,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {''',
            "saved place editor signature",
        )
        text = replace_once(
            text,
            '''        Text(savedPlaceTypeLabel(place), fontWeight = FontWeight.Bold)
        OutlinedTextField(''',
            '''        Text(savedPlaceTypeLabel(place), fontWeight = FontWeight.Bold)
        if (highlighted) {
            Text("Informe o nome deste item agora. Esse nome aparece na lista e no alerta de voz.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(''',
            "saved place highlighted hint",
        )

    if "EXTRA_OPEN_TAB" not in text:
        text = replace_once(
            text,
            "private enum class LocationTarget { Home, Alternative }",
            '''private const val EXTRA_OPEN_TAB = "br.com.mapeiaia.rotacerta.extra.OPEN_TAB"
private const val EXTRA_SAVED_PLACE_ID = "br.com.mapeiaia.rotacerta.extra.SAVED_PLACE_ID"
private const val TAB_ANALYSIS = "analise"
private const val TAB_CONFIG = "config"
private const val TAB_HISTORY = "historico"

private enum class LocationTarget { Home, Alternative }''',
            "main constants",
        )
    if "LaunchedEffect(initiallyExpanded)" not in text:
        text = replace_once(
            text,
            '''    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Card(modifier = Modifier.fillMaxWidth()) {''',
            '''    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) expanded = true
    }
    Card(modifier = Modifier.fillMaxWidth()) {''',
            "expandable auto open",
        )
    path.write_text(text)


def patch_service():
    path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    text = path.read_text()

    if "currentImportedRadars" not in text:
        text = replace_once(
            text,
            '''    private var currentCardTemplates = emptyList<RideCardTemplate>()
    private var currentSavedPlaces = emptyList<SavedPlace>()''',
            '''    private var currentCardTemplates = emptyList<RideCardTemplate>()
    private var currentSavedPlaces = emptyList<SavedPlace>()
    private var currentImportedRadars = emptyList<ImportedRadar>()''',
            "service radar state",
        )
        text = replace_once(
            text,
            '''        scope.launch { repository.cardTemplates.collect { currentCardTemplates = it } }
        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }''',
            '''        scope.launch { repository.cardTemplates.collect { currentCardTemplates = it } }
        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }
        scope.launch { repository.importedRadars.collect { currentImportedRadars = it } }''',
            "service radar collect",
        )
        text = replace_once(
            text,
            '''                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                if (alerts.isNotEmpty()) checkProximityAlerts(alerts)''',
            '''                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                val radars = currentImportedRadars
                if (alerts.isNotEmpty() || radars.isNotEmpty()) checkProximityAlerts(alerts, radars)''',
            "service proximity loop",
        )
        text = replace_once(
            text,
            '''    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>) {
        val coordinate = locationService.currentCoordinate() ?: return
        val now = System.currentTimeMillis()
        val activeIds = alerts.map { it.id }.toSet()
        proximityAlertRuntime.keys.retainAll(activeIds)

        alerts.forEach { alert ->''',
            '''    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>, radars: List<ImportedRadar>) {
        val coordinate = locationService.currentCoordinate() ?: return
        val now = System.currentTimeMillis()
        val activeIds = alerts.map { it.id }.toSet() + radars.map { "imported-${it.id}" }.toSet()
        proximityAlertRuntime.keys.retainAll(activeIds)
        checkImportedRadars(radars, coordinate, now)

        alerts.forEach { alert ->''',
            "check proximity signature",
        )
        text = replace_once(
            text,
            '''    private fun scheduleVisibleTextAnalysis(delayMs: Long) {''',
            '''    private fun checkImportedRadars(radars: List<ImportedRadar>, coordinate: Coordinate, now: Long) {
        if (radars.isEmpty()) return
        val threshold = currentSettings.proximityAlertDistanceMeters.coerceIn(200, 1000)
        val nearest = radars.asSequence()
            .map { radar -> radar to distanceMeters(coordinate, radar.coordinate) }
            .filter { (_, distance) -> distance <= threshold }
            .minByOrNull { (_, distance) -> distance }
            ?: return
        val radar = nearest.first
        val distanceMeters = nearest.second
        val runtime = proximityAlertRuntime.getOrPut("imported-${radar.id}") { ProximityAlertRuntime() }
        if (
            runtime.spokenCount < MAX_PROXIMITY_ALERT_SPEECH_COUNT &&
            now - runtime.lastSpokenAtMillis >= PROXIMITY_ALERT_REPEAT_GAP_MS
        ) {
            speakImportedRadar(radar, distanceMeters)
            runtime.spokenCount += 1
            runtime.lastSpokenAtMillis = now
            recordDiagnostic(
                stage = "imported_radar_spoken",
                color = currentRadarColor,
                reason = "Radar importado falado: ${importedRadarSpeech(radar, distanceMeters)}",
            )
        }
    }

    private fun scheduleVisibleTextAnalysis(delayMs: Long) {''',
            "check imported radars function",
        )
        text = replace_once(
            text,
            '''    private fun speakProximityAlert(place: SavedPlace) {''',
            '''    private fun speakImportedRadar(radar: ImportedRadar, distanceMeters: Double) {
        if (!textToSpeechReady) return
        textToSpeech?.speak(
            importedRadarSpeech(radar, distanceMeters),
            TextToSpeech.QUEUE_ADD,
            null,
            "imported-radar-${radar.id}-${System.currentTimeMillis()}",
        )
    }

    private fun speakProximityAlert(place: SavedPlace) {''',
            "speak imported radar",
        )

    if "openSavedPlaceEditor(place)" not in text:
        text = replace_once(
            text,
            '''            repository.addSavedPlace(place)
            toast(if (isAlert) "Alerta de proximidade criado." else "Local salvo.")''',
            '''            repository.addSavedPlace(place)
            openSavedPlaceEditor(place)
            toast(if (isAlert) "Alerta criado. Informe o nome." else "Local salvo. Informe o nome.")''',
            "open editor after bubble save",
        )
        text = replace_once(
            text,
            '''    private fun openApp() {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private fun toggleActionMenu() {''',
            '''    private fun openApp() {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private fun openSavedPlaceEditor(place: SavedPlace) {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
            )
        }
    }

    private fun toggleActionMenu() {''',
            "open saved place editor helper",
        )

    if "EXTRA_OPEN_TAB" not in text:
        text = replace_once(
            text,
            '''        const val KEY_BUBBLE_Y = "bubble_y"
        const val PACKAGE_99_DRIVER = "com.app99.driver"''',
            '''        const val KEY_BUBBLE_Y = "bubble_y"
        const val EXTRA_OPEN_TAB = "br.com.mapeiaia.rotacerta.extra.OPEN_TAB"
        const val EXTRA_SAVED_PLACE_ID = "br.com.mapeiaia.rotacerta.extra.SAVED_PLACE_ID"
        const val TAB_CONFIG = "config"
        const val PACKAGE_99_DRIVER = "com.app99.driver"''',
            "service constants",
        )
    path.write_text(text)


def main():
    patch_main_activity()
    patch_service()
    Path(".github/workflows/build-debug-apk.yml").write_text(BUILD_WORKFLOW)
    stale_workflow = Path(".github/workflows/apply-saved-place-naming-flow.yml")
    if stale_workflow.exists():
        stale_workflow.unlink()
    script = Path(".github/codex-radar-patch.py")
    if script.exists():
        script.unlink()


if __name__ == "__main__":
    main()
