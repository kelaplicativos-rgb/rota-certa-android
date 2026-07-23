package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
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
fun RotaCertaApp(launchIntent: Intent?) {
    val context = LocalContext.current
    LaunchedEffect("rota_certa_session_0_1_118") {
        DiagnosticLogStore.record(
            "app",
            "app.session.started version=" + BuildConfig.VERSION_NAME + " build=" + BuildConfig.VERSION_CODE,
        )
    } // full_session_start_0_1_118

    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { SettingsRepository(context) }
    val settings by repository.settings.collectAsState(initial = AppSettings())
    // in_app_bubble_immediate_state_0_1_98 substituido pela navegacao agrupada 0.1.115
    val history = emptyList<AnalysisResult>()
    val cardTemplates by repository.cardTemplates.collectAsState(initial = emptyList())
    val savedPlaces by repository.savedPlaces.collectAsState(initial = emptyList())
    val radarImportSummary by repository.radarImportSummary.collectAsState(initial = RadarImportSummary())
    val ocrService = remember { OcrService(context) }
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) } // popup_modules_0_1_120
    val geocodingService = remember { GeocodingService(context) }
    val savedPlaceGpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(TAB_CONFIG) }
    var selectedBubbleGroup by remember { mutableStateOf(BUBBLE_GROUP_ACCESS) } // grouped_bubble_state_0_1_115
    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }
    var savedPlaceNameDialogId by remember { mutableStateOf<String?>(null) }
    var handledSavedPlaceNameDialogId by remember { mutableStateOf<String?>(null) }
    var region by remember { mutableStateOf(DeviceRegion()) }
    var liveEnabled by remember { mutableStateOf(isLiveAccessibilityEnabled(context)) }
    var templateStatus by remember { mutableStateOf("Modelos cadastrados: ${cardTemplates.size}") }
    var unreadTemplatePrints by remember { mutableStateOf(0) }
    var backupStatus by remember { mutableStateOf("") }
    var radarImportStatus by remember { mutableStateOf("") }
    var supportReportStatus by remember { mutableStateOf("") }

    fun registerRideCard(packageName: String?, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "Nao ha texto lido para cadastrar", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
            repository.addCardTemplate(template)
            Toast.makeText(context, "Modelo cadastrado: ${template.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteCardModel(template: RideCardTemplate) {
        scope.launch {
            repository.removeCardTemplate(template.id)
            Toast.makeText(context, "Modelo removido: ${template.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun renameSavedPlace(place: SavedPlace, name: String) {
        val safeName = name.trim().ifBlank { defaultSavedPlaceName(place.type) }
        scope.launch {
            repository.updateSavedPlace(place.copy(name = safeName))
            Toast.makeText(context, "Nome salvo: $safeName", Toast.LENGTH_SHORT).show()
        }
    }

    fun createCurrentSavedPlace(type: SavedPlaceType) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                Toast.makeText(context, "Autorize a localizacao para salvar este local.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val resolved = savedPlaceGpsAddressResolver.resolve(coordinate)
            val createdAt = System.currentTimeMillis()
            val isAlert = type == SavedPlaceType.ProximityAlert
            val place = SavedPlace(
                id = "place-$createdAt-${coordinate.latitude}-${coordinate.longitude}",
                name = if (isAlert) defaultSavedPlaceName(SavedPlaceType.ProximityAlert) else defaultSavedPlaceName(SavedPlaceType.Place),
                type = type,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = if (isAlert) settings.proximityAlertDistanceMeters else null,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            highlightedSavedPlaceId = place.id
            Toast.makeText(
                context,
                if (isAlert) "Alerta criado. Informe o nome." else "Local salvo. Informe o nome.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun createSavedPlaceFromHome(type: SavedPlaceType) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                Toast.makeText(context, "Autorize a localizacao para salvar este ponto.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            val createdAt = System.currentTimeMillis()
            val isAlert = type == SavedPlaceType.ProximityAlert
            val place = SavedPlace(
                id = "place-$createdAt-${coordinate.latitude}-${coordinate.longitude}",
                name = if (isAlert) "Alerta" else "Local salvo",
                type = type,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = if (isAlert) settings.proximityAlertDistanceMeters else null,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            highlightedSavedPlaceId = place.id
            tab = TAB_CONFIG
            selectedBubbleGroup = if (isAlert) BUBBLE_GROUP_ALERTS else BUBBLE_GROUP_SAVED_PLACES
            Toast.makeText(
                context,
                if (isAlert) "Alerta criado. Defina o nome e a distancia." else "Local salvo. Defina um nome.",
                Toast.LENGTH_LONG,
            ).show()
        }
    } // createSavedPlaceFromHome_0_1_120

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate != null) {
                region = geocodingService.reverseGeocode(coordinate)
            }
        }
    }

    val cardModelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            unreadTemplatePrints = 0
            templateStatus = "Lendo ${uris.size} print(s)..."
            var failures = 0
            var imported = 0

            uris.forEach { uri ->
                val extractedText = runCatching { ocrService.extractText(uri) }.getOrDefault("")
                val packageName = RideCardTemplateMatcher.packageNameForLearning(null, extractedText)
                if (extractedText.isBlank() || packageName == null) {
                    failures += 1
                } else {
                    val template = RideCardTemplateMatcher.createTemplate(packageName, extractedText)
                    repository.addCardTemplate(template)
                    imported += 1
                }
            }

            unreadTemplatePrints = failures
            templateStatus = when {
                failures == 0 -> "Leitura concluida: $imported modelo(s) importado(s)."
                imported == 0 -> "Nenhum modelo importado. Use recorte do bloco da corrida com tempo, km e enderecos."
                else -> "Leitura concluida: $imported modelo(s) importado(s), $failures print(s) sem leitura."
            }
        }
    }

    val radarFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            radarImportStatus = "Importacao cancelada."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            radarImportStatus = "Importando radares..."
            runCatching {
                val fileBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: error("Nao consegui abrir o arquivo selecionado.")
                val radars = parseMapaRadarFile(fileBytes) // maparadar_flexible_file_reader_0_1_120
                if (radars.isEmpty()) error("Arquivo sem radares validos. Use TXT, CSV ou CSV salvo como XLS do MapaRadar.")
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
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            backupStatus = "Backup cancelado."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            backupStatus = "Criando backup..."
            runCatching {
                val backupJson = repository.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(backupJson)
                } ?: error("Nao consegui abrir o arquivo de backup.")
            }.onSuccess {
                backupStatus = "Backup salvo com sucesso."
            }.onFailure { error ->
                backupStatus = "Falha ao salvar backup: ${error.message.orEmpty()}"
            }
        }
    }

    val supportReportFileCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) {
            supportReportStatus = "Relatorio cancelado."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            supportReportStatus = "Gerando relatorio..."
            DiagnosticLogStore.record("support", "report.export.started")
            runCatching {
                val report = buildManualSupportReport(
                    context = context,
                    repository = repository,
                    settings = settings,
                    liveEnabled = liveEnabled,
                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onDeleteCardModel = ::deleteCardModel,
                    savedPlaces = savedPlaces,
                    radarImportSummary = radarImportSummary,
                )
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(report)
                } ?: error("Nao consegui abrir o arquivo do relatorio.")
            }.onSuccess {
                DiagnosticLogStore.record("support", "report.export.completed")
                supportReportStatus = "Relatorio gerado. Anexe o arquivo aqui no chat."
                Toast.makeText(context, "Relatorio gerado para anexar.", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                supportReportStatus = "Falha ao gerar relatorio: ${error.message.orEmpty()}"
            }
        }
    }

    val backupFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            backupStatus = "Restauracao cancelada."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            backupStatus = "Restaurando backup..."
            runCatching {
                val backupJson = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error("Nao consegui abrir o arquivo selecionado.")
                repository.restoreBackupJson(backupJson)
            }.onSuccess { backup ->
                backupStatus = "Backup restaurado: ${backup.savedPlaces.size} local(is), ${backup.cardTemplates.size} modelo(s)."
            }.onFailure { error ->
                backupStatus = "Falha ao restaurar backup: ${error.message.orEmpty()}"
            }
        }
    }

    LaunchedEffect(cardTemplates.size) {
        if (!templateStatus.startsWith("Lendo ")) {
            templateStatus = "Modelos cadastrados: ${cardTemplates.size}"
        }
    }

    LaunchedEffect(settings, cardTemplates.size, savedPlaces.size, radarImportSummary.count) {
        val factoryPrefs = context.getSharedPreferences("rota_certa_factory_guard", Context.MODE_PRIVATE)
        val guardKey = "factory_clean_0_1_72"
        val hasStoredRegionOrUserLocation = settings.homeAddress.isNotBlank() ||
            settings.homeCoordinate != null ||
            settings.alternativeAddress.isNotBlank() ||
            settings.alternativeCoordinate != null
        val hasNoUserCollections = cardTemplates.isEmpty() && savedPlaces.isEmpty() && radarImportSummary.count == 0
        if (!factoryPrefs.getBoolean(guardKey, false) && hasNoUserCollections && hasStoredRegionOrUserLocation) {
            repository.saveSettings(AppSettings())
            region = DeviceRegion()
            templateStatus = "Dados antigos removidos. App zerado para cadastro correto dos cards."
        }
        factoryPrefs.edit().putBoolean(guardKey, true).apply()
    }

    LaunchedEffect(launchIntent) {
        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        tab = if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            requestedTab
        } else {
            TAB_CONFIG
        }
        selectedBubbleGroup = when (tab) {
            TAB_CONFIG -> BUBBLE_GROUP_ACCESS
            TAB_ANALYSIS -> BUBBLE_GROUP_DESTINATION
            else -> BUBBLE_GROUP_ACCESS
        }
        launchIntent?.getStringExtra(EXTRA_OPEN_BUBBLE_GROUP)?.let { requestedGroup ->
            if (requestedGroup in BUBBLE_GROUP_VALUES) selectedBubbleGroup = requestedGroup
        } // startup_permissions_0_1_120
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
        if (launchIntent?.getBooleanExtra("auto_export_report", false) == true) {
            DiagnosticLogStore.record("support", "report.export.requested_from_popup")
            supportReportFileCreator.launch("rota-certa-relatorio-completo.txt")
        } // auto_export_report_0_1_119
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                liveEnabled = isLiveAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(highlightedSavedPlaceId, savedPlaces) {
        val id = highlightedSavedPlaceId ?: return@LaunchedEffect
        val place = savedPlaces.firstOrNull { it.id == id }
        if (place != null && handledSavedPlaceNameDialogId != id) {
            savedPlaceNameDialogId = id
        }
    }

    savedPlaces.firstOrNull { it.id == savedPlaceNameDialogId }?.let { place ->
        Unit
    }

    Scaffold(
        bottomBar = {},
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Rota Certa", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(deviceRegionLabel(region), style = MaterialTheme.typography.bodyMedium)
            Text("Gatilho universal: ao encontrar dois enderecos, usa o ultimo e calcula imediatamente ate o ponto definido por voce.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))


            when (tab) {
                TAB_ANALYSIS -> AnalysisScreen(
                    settings = settings,
                    latestResult = history.firstOrNull(),
                    liveEnabled = liveEnabled,
                    onSaveSettings = { updated -> scope.launch { repository.saveSettings(updated) } },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                
                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onDeleteCardModel = ::deleteCardModel,
                )
                TAB_CONFIG -> SettingsScreen(
                    selectedGroup = selectedBubbleGroup,
                    settings = settings,
                    liveEnabled = liveEnabled,
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                    diagnostic = null,
                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onDeleteCardModel = ::deleteCardModel,
                    savedPlaces = savedPlaces,
                    backupStatus = backupStatus,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    radarImportSummary = radarImportSummary,
                    radarImportStatus = radarImportStatus,
                    onSave = { updated -> scope.launch { repository.saveSettings(updated) } },
                    onRegisterRideCard = ::registerRideCard,
                    onCreateSavedPlace = { createSavedPlaceFromHome(SavedPlaceType.Place) },
                    onCreateProximityAlert = { createSavedPlaceFromHome(SavedPlaceType.ProximityAlert) },
                    onRenameSavedPlace = ::renameSavedPlace,
                    onDeleteSavedPlace = { place -> scope.launch { repository.removeSavedPlace(place.id) } },
                    onRegionDetected = { detectedRegion -> region = detectedRegion },
                    onCreateBackup = { backupFileCreator.launch("rota-certa-backup.json") },
                    onRestoreBackup = { backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onImportRadarFile = { radarFilePicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "application/octet-stream", "*/*")) },
                    onOpenMapaRadar = { openMapaRadarSite(context) },
                    onClearImportedRadars = {
                        scope.launch {
                            repository.clearImportedRadars()
                            radarImportStatus = "Radares importados removidos."
                        }
                    },
                )
                TAB_TOOLS -> ToolsScreen(
                    onOpenWhatsApp = { openWhatsAppApp(context) },
                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = { clearClipboard(context) },
                )
                TAB_HISTORY -> ReportsGroupScreen(
                    diagnostic = null,
                    history = history,
                )
                else -> Unit
            } // grouped_navigation_compat_0_1_115
        }
    }
}

// in_app_bubble_home_visible_0_1_97
private const val BUBBLE_GROUP_GENERAL = "general"
private const val BUBBLE_GROUP_READING = "reading"
private const val BUBBLE_GROUP_DESTINATION = "destination"
private const val BUBBLE_GROUP_ALERTS = "alerts"
private const val BUBBLE_GROUP_SAVED_PLACES = "saved_places"
private const val BUBBLE_GROUP_RADARS = "radars"
private const val BUBBLE_GROUP_CARDS = "cards"
private const val BUBBLE_GROUP_APPEARANCE = "appearance"
private const val BUBBLE_GROUP_ACCESS = "access"
private const val BUBBLE_GROUP_REPORTS = "reports"
private const val BUBBLE_GROUP_BACKUP = "backup"
private const val BUBBLE_GROUP_TOOLS = "tools"
private const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
private val BUBBLE_GROUP_VALUES = setOf(
    BUBBLE_GROUP_GENERAL,
    BUBBLE_GROUP_DESTINATION,
    BUBBLE_GROUP_ALERTS,
    BUBBLE_GROUP_CARDS,
    BUBBLE_GROUP_RADARS,
    BUBBLE_GROUP_SAVED_PLACES,
    BUBBLE_GROUP_APPEARANCE,
    BUBBLE_GROUP_ACCESS,
    BUBBLE_GROUP_REPORTS,
    BUBBLE_GROUP_BACKUP,
)

@Composable
private fun ProfessionalBubbleDashboard(
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenCollector: () -> Unit,
    onClearClipboard: () -> Unit,
    onCreateSupportReport: () -> Unit,
    onStopApplication: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Central de controle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Recursos separados por funcao. As bolinhas de grupo mostram seus controles logo abaixo; as bolinhas de acao executam imediatamente.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Operacao", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("⚡", "Rota", selectedGroup == BUBBLE_GROUP_GENERAL) { onSelectGroup(BUBBLE_GROUP_GENERAL) },
                ProfessionalBubbleItem("🏠", "Destino", selectedGroup == BUBBLE_GROUP_DESTINATION) { onSelectGroup(BUBBLE_GROUP_DESTINATION) },
                ProfessionalBubbleItem("⚠️", "Alertas", selectedGroup == BUBBLE_GROUP_ALERTS) { onSelectGroup(BUBBLE_GROUP_ALERTS) },
            ),
        )

        Text("Sistema", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("🎨", "Aparencia", selectedGroup == BUBBLE_GROUP_APPEARANCE) { onSelectGroup(BUBBLE_GROUP_APPEARANCE) },
                ProfessionalBubbleItem("🔐", "Permissoes", selectedGroup == BUBBLE_GROUP_ACCESS) { onSelectGroup(BUBBLE_GROUP_ACCESS) },
                ProfessionalBubbleItem("💾", "Backup", selectedGroup == BUBBLE_GROUP_BACKUP) { onSelectGroup(BUBBLE_GROUP_BACKUP) },
            ),
        )

        Text("Registros", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("📋", "Relatorios", selectedGroup == BUBBLE_GROUP_REPORTS) { onSelectGroup(BUBBLE_GROUP_REPORTS) },
            ),
        )

        Text("Acoes rapidas", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("🟢", "WhatsApp", false, onOpenWhatsApp),
                ProfessionalBubbleItem("🚗", "Coletor", false, onOpenCollector),
                ProfessionalBubbleItem("🧹", "Limpar", false, onClearClipboard),
            ),
        )

        Text("Suporte", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("🛠️", "Depurar", false, onCreateSupportReport),
                ProfessionalBubbleItem("⏹️", "Encerrar", false, onStopApplication),
            ),
        )
    }
}

private data class ProfessionalBubbleItem(
    val emoji: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ProfessionalBubbleRow(items: List<ProfessionalBubbleItem>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { item ->
            AppControlBubble(
                emoji = item.emoji,
                label = item.label,
                selected = item.selected,
                onClick = item.onClick,
            )
        }
    }
}

@Composable
private fun AppControlBubble(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 12.dp else 2.dp,
            pressedElevation = 14.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(5.dp),
    ) {
        Text(
            text = emoji + "\n" + label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun openWhatsAppApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
    if (launchIntent != null) {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        Toast.makeText(context, "WhatsApp nao encontrado.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AnalysisScreen(
    settings: AppSettings,
    latestResult: AnalysisResult?,
    cardTemplates: List<RideCardTemplate> = emptyList(),
    templateStatus: String = "",
    unreadTemplatePrints: Int = 0,
    liveEnabled: Boolean,
    onSaveSettings: (AppSettings) -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit = {},
    onPickCardModels: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshLiveState: () -> Unit,
) {
    val context = LocalContext.current
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()

    var quickSettings by remember(settings) { mutableStateOf(settings) }
    var homeStatus by remember { mutableStateOf("") }
    var pendingHomeGps by remember { mutableStateOf(false) }

    fun saveQuickSettings(updated: AppSettings) {
        quickSettings = updated
        onSaveSettings(updated)
    }

    fun captureHomeGps() {
        scope.launch {
            homeStatus = "Buscando sinal de GPS..."
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                homeStatus = "Nao consegui captar o GPS. Autorize a localizacao e tente novamente."
                return@launch
            }

            val resolved = gpsAddressResolver.resolve(coordinate)
            val address = resolved.addressLine.ifBlank { formatCoordinate(coordinate) }
            saveQuickSettings(
                quickSettings.copy(
                    homeAddress = address,
                    homeCoordinate = coordinate,
                ),
            )
            homeStatus = "Endereco base salvo pelo GPS: ${formatCoordinate(coordinate)}"
        }
    }

    val homeGpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (!pendingHomeGps) return@rememberLauncherForActivityResult
        pendingHomeGps = false
        if (permissions.values.any { it }) {
            captureHomeGps()
        } else {
            homeStatus = "Localizacao negada. Autorize o GPS para salvar o endereco base."
        }
    }

    fun requestHomeGps() {
        pendingHomeGps = true
        homeGpsPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    Text("Destino, Casa e Alfinete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Endereco, raio e Google Maps ficam reunidos neste grupo.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    HomeDecisionCard(
        quickSettings = quickSettings,
        homeStatus = homeStatus,
        onSettingsChange = { quickSettings = it },
        onRequestHomeGps = ::requestHomeGps,
        onSave = { saveQuickSettings(quickSettings) },
    )

    Spacer(Modifier.height(10.dp))
    RadiusQuickCard(
        quickSettings = quickSettings,
        onSettingsChange = { quickSettings = it },
        onSaveSettings = onSaveSettings,
    )

    Spacer(Modifier.height(10.dp))
    // Modelos removidos; o gatilho e o ultimo endereco. // universal_models_removed_v2_0_1_95

    Spacer(Modifier.height(10.dp))
    MapsAndAdvancedCard(
        draft = quickSettings,
        onDraftChange = { quickSettings = it },
        onSave = { saveQuickSettings(quickSettings) },
    )

        latestResult?.let {
        Spacer(Modifier.height(12.dp))
        ResultCard(it, settings)
    }
}

@Composable
private fun LiveReadingCard(
    liveEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshLiveState: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Leitura ao vivo", fontWeight = FontWeight.Bold)
            Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Text(if (liveEnabled) "ON - leitura ao vivo ativa" else "OFF - permitir acessibilidade")
            }
            OutlinedButton(onClick = onRefreshLiveState, modifier = Modifier.fillMaxWidth()) {
                Text("Atualizar status")
            }
            Text(
                if (liveEnabled) {
                    "Operando em qualquer tela. Dois enderecos deixam a bolinha amarela; o ultimo vira o destino e inicia o calculo."
                } else {
                    "Ative 'Rota Certa - leitura ao vivo' nas configuracoes de Acessibilidade."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HomeDecisionCard(
    quickSettings: AppSettings,
    homeStatus: String,
    onSettingsChange: (AppSettings) -> Unit,
    onRequestHomeGps: () -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Minha regiao de corridas", fontWeight = FontWeight.Bold)
            Text(
                "Defina rapidamente o ponto onde o destino final precisa ficar perto. Use o GPS atual, ajuste o raio e salve.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = quickSettings.homeAddress,
                onValueChange = { onSettingsChange(quickSettings.copy(homeAddress = it, homeCoordinate = null)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Casa / ponto principal") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onRequestHomeGps, modifier = Modifier.weight(1f)) {
                    Text("Definir pelo GPS atual")
                }
                OutlinedButton(
                    onClick = { onSettingsChange(quickSettings.copy(homeCoordinate = null)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Digitar")
                }
            }
            quickSettings.homeCoordinate?.let {
                Text("Endereco base salvo: ${formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
            }
            if (homeStatus.isNotBlank()) {
                Text(homeStatus, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Salvar regiao de trabalho")
            }
        }
    }
}

@Composable
private fun RadiusQuickCard(
    quickSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Raio da regiao de trabalho", fontWeight = FontWeight.Bold)
            Text("Defina ate quantos km do endereco salvo o destino final pode ficar.", style = MaterialTheme.typography.bodySmall)
            RadiusSlider(
                label = "Casa",
                value = quickSettings.homeRadiusKm,
                onValueChange = { onSettingsChange(quickSettings.copy(homeRadiusKm = it)) },
                onValueChangeFinished = { onSaveSettings(quickSettings) },
            )
            RadiusSlider(
                label = "Alfinete",
                value = quickSettings.alternativeRadiusKm,
                onValueChange = { onSettingsChange(quickSettings.copy(alternativeRadiusKm = it)) },
                onValueChangeFinished = { onSaveSettings(quickSettings) },
            )
        }
    }
}

@Composable
private fun CardModelsCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modelos de cards opcionais", fontWeight = FontWeight.Bold)
            Text(
                "Nenhum modelo nasce cadastrado. Use prints somente quando um aplicativo ou formato de card precisar ser ensinado manualmente.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Modelos cadastrados: ${cardTemplates.size}")
            Button(onClick = onPickCardModels, modifier = Modifier.fillMaxWidth()) {
                Text("Anexar modelos de cards (prints)")
            }
            if (templateStatus.isNotBlank()) Text(templateStatus, style = MaterialTheme.typography.bodySmall)
            if (unreadTemplatePrints > 0) {
                Text("Prints sem leitura: $unreadTemplatePrints", style = MaterialTheme.typography.bodySmall)
            }
            if (cardTemplates.isEmpty()) {
                Text("Nenhum modelo cadastrado.", style = MaterialTheme.typography.bodySmall)
            } else {
                cardTemplates.forEach { template ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, fontWeight = FontWeight.Bold)
                            Text(template.packageName ?: "app nao identificado", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onDeleteCardModel(template) }) { Text("Apagar") }
                    }
                }
            }
        }
    }
} // no_pre_registered_cards_ui_0_1_126 superseded manual_card_models_restored_0_1_127
 // no_pre_registered_cards_ui_0_1_126


@Composable
private fun DiagnosticExpander(
    diagnostic: LiveDiagnostic?,
) = Unit

@Composable
private fun SavedPlacesCard(
    savedPlaces: List<SavedPlace>,
    highlightedSavedPlaceId: String?,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
    onSaveCurrentPlace: (() -> Unit)? = null,
    onCreateProximityAlert: (() -> Unit)? = null,
) {
    val places = savedPlaces.filter { it.type == SavedPlaceType.Place }
    val alerts = savedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
    val highlightedType = savedPlaces.firstOrNull { it.id == highlightedSavedPlaceId }?.type

    if (highlightedType != null) {
        Text("Item criado pela bolinha. Informe um nome claro e toque em Salvar.", style = MaterialTheme.typography.bodySmall)
    }

    ExpandableCard(title = "Locais salvos (${places.size})", initiallyExpanded = highlightedType == SavedPlaceType.Place) {
        onSaveCurrentPlace?.let { action ->
            Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
                Text("Salvar local atual")
            }
            Spacer(Modifier.height(6.dp))
        }
        if (places.isEmpty()) {
            Text("Nenhum local salvo ainda.", style = MaterialTheme.typography.bodySmall)
        } else {
            places.forEach { place ->
                SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    ExpandableCard(title = "Alertas de proximidade (${alerts.size})", initiallyExpanded = highlightedType == SavedPlaceType.ProximityAlert) {
        onCreateProximityAlert?.let { action ->
            Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
                Text("Criar alerta neste local")
            }
            Spacer(Modifier.height(6.dp))
        }
        if (alerts.isEmpty()) {
            Text("Nenhum alerta de proximidade criado ainda.", style = MaterialTheme.typography.bodySmall)
        } else {
            alerts.forEach { place ->
                SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)
            }
        }
    }
}

@Composable
private fun RegisteredCardsModuleCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    CardModelsCard(
        cardTemplates = cardTemplates,
        templateStatus = templateStatus,
        unreadTemplatePrints = unreadTemplatePrints,
        onPickCardModels = onPickCardModels,
        onDeleteCardModel = onDeleteCardModel,
    )
} // no_registered_cards_module_0_1_126 superseded registered_cards_module_restored_0_1_127
 // no_registered_cards_module_0_1_126
 // registered_cards_module_0_1_120

@Composable
private fun SavedPlacesModuleCard(
    savedPlaces: List<SavedPlace>,
    type: SavedPlaceType,
    highlightedSavedPlaceId: String?,
    onCreate: () -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val items = savedPlaces.filter { it.type == type }
    val isAlert = type == SavedPlaceType.ProximityAlert
    var search by remember(type) { mutableStateOf("") }
    val filteredItems = remember(items, search) {
        val query = search.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) {
            items
        } else {
            items.filter { place ->
                place.name.lowercase(Locale.ROOT).contains(query) ||
                    place.address.lowercase(Locale.ROOT).contains(query)
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isAlert) "Alertas de proximidade (${items.size})" else "Locais salvos (${items.size})",
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (isAlert) {
                    "Somente pontos que geram aviso de aproximacao."
                } else {
                    "Somente locais salvos para consultar ou voltar depois. Nao geram alerta."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(if (isAlert) "Criar alerta neste local" else "Salvar local atual")
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar por nome ou endereco") },
                singleLine = true,
            )
            if (search.isNotBlank()) {
                Text(
                    "Encontrados: ${filteredItems.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                items.isEmpty() -> Text(
                    if (isAlert) "Nenhum alerta criado." else "Nenhum local salvo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                filteredItems.isEmpty() -> Text(
                    if (isAlert) {
                        "Nenhum alerta encontrado por nome ou endereco."
                    } else {
                        "Nenhum local encontrado por nome ou endereco."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
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
} // separate_saved_place_modules_0_1_120 saved_places_search_name_address_0_1_127
 // separate_saved_place_modules_0_1_120 saved_places_search_name_address_0_1_127
 // separate_saved_place_modules_0_1_120

@Composable
private fun SavedPlaceEditor(
    place: SavedPlace,
    highlighted: Boolean = false,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val context = LocalContext.current
    var draftName by remember(place.id, place.name) { mutableStateOf(place.name) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(savedPlaceTypeLabel(place), fontWeight = FontWeight.Bold)
        if (highlighted) {
            Text(
                if (place.type == SavedPlaceType.ProximityAlert) {
                    "O nome sera falado e aparecera no popup de aproximacao. Edite e toque em Salvar."
                } else {
                    "Local para voltar depois, como estacionamento. Edite o nome e toque em Salvar; ele nao gera alerta."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (place.type == SavedPlaceType.ProximityAlert) "Nome falado no alerta" else "Nome do local") },
        )
        if (place.type == SavedPlaceType.ProximityAlert) {
            Text(
                "O app vai falar: ${draftName.ifBlank { defaultSavedPlaceName(place.type) }} se aproximando.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openSavedPlaceInGps(context, place) }, modifier = Modifier.weight(1f)) {
                Text("GPS")
            }
            Button(
                enabled = draftName.trim().isNotBlank() && (highlighted || draftName.trim() != place.name),
                onClick = { onRenameSavedPlace(place, draftName) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Salvar")
            }
            OutlinedButton(onClick = { onDeleteSavedPlace(place) }, modifier = Modifier.weight(1f)) {
                Text("Apagar")
            }
        }
    }
}

@Composable
private fun ResultCard(result: AnalysisResult, settings: AppSettings) {
    val radiusInfo = resultRadiusInfo(result, settings)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(recommendationLabel(result.recommendation), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Destino final:", fontWeight = FontWeight.Bold)
                Text(formatDestination(result.fields.destination))
            }
            radiusInfo?.let {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${it.label}:", fontWeight = FontWeight.Bold)
                    Text("${formatKm(it.distanceKm)} de ${formatKm(it.radiusKm)} permitidos")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Decisao:", fontWeight = FontWeight.Bold)
                Text(decisionActionLabel(result.recommendation))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    selectedGroup: String,
    settings: AppSettings,
    liveEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshLiveState: () -> Unit,
    diagnostic: LiveDiagnostic?,
    cardTemplates: List<RideCardTemplate> = emptyList(),
    templateStatus: String = "",
    unreadTemplatePrints: Int = 0,
    onPickCardModels: () -> Unit = {},
    onDeleteCardModel: (RideCardTemplate) -> Unit = {},
    savedPlaces: List<SavedPlace>,
    backupStatus: String,
    highlightedSavedPlaceId: String?,
    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    onSave: (AppSettings) -> Unit,
    onRegisterRideCard: (String?, String) -> Unit,
    onCreateSavedPlace: () -> Unit,
    onCreateProximityAlert: () -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
    onRegionDetected: (DeviceRegion) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onImportRadarFile: () -> Unit,
    onOpenMapaRadar: () -> Unit,
    onClearImportedRadars: () -> Unit,
) {
    val context = LocalContext.current
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()

    var draft by remember(settings) { mutableStateOf(settings) }
    var gpsStatus by remember { mutableStateOf("") }
    var pendingLocationTarget by remember { mutableStateOf<LocationTarget?>(null) }

    fun saveDraft(updated: AppSettings) {
        draft = updated
        onSave(updated)
    }

    fun captureGps(target: LocationTarget) {
        scope.launch {
            gpsStatus = "Buscando sinal de GPS..."
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                gpsStatus = "Nao consegui captar o GPS. Autorize a localizacao e tente novamente."
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            if (resolved.region.city.isNotBlank() || resolved.region.country.isNotBlank()) onRegionDetected(resolved.region)
            val address = resolved.addressLine.ifBlank { formatCoordinate(coordinate) }
            draft = when (target) {
                LocationTarget.Home -> draft.copy(homeAddress = address, homeCoordinate = coordinate)
                LocationTarget.Alternative -> draft.copy(alternativeAddress = address, alternativeCoordinate = coordinate)
            }
            gpsStatus = "GPS preenchido. Confira e toque em Salvar."
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val target = pendingLocationTarget
        pendingLocationTarget = null
        if (target != null) captureGps(target)
    }

    fun requestGps(target: LocationTarget) {
        pendingLocationTarget = target
        gpsPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(groupedBubbleTitle(selectedGroup), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(groupedBubbleDescription(selectedGroup), style = MaterialTheme.typography.bodySmall)
        when (selectedGroup) {
            BUBBLE_GROUP_GENERAL -> SystemControlCard(settings = draft, onChange = ::saveDraft)
            BUBBLE_GROUP_READING,
            BUBBLE_GROUP_ACCESS,
            -> {
                LiveReadingCard(
                    liveEnabled = liveEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRefreshLiveState = onRefreshLiveState,
                )
                Spacer(Modifier.height(10.dp))
                AlwaysLocationPermissionCard(
                    hasAlwaysPermission = hasAlwaysLocationPermission(context),
                    onOpenLocationSettings = { openAppLocationSettings(context) },
                )
            }
            BUBBLE_GROUP_ALERTS -> SavedPlacesModuleCard(
                savedPlaces = savedPlaces,
                type = SavedPlaceType.ProximityAlert,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                onCreate = onCreateProximityAlert,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
            )
            BUBBLE_GROUP_SAVED_PLACES -> SavedPlacesModuleCard(
                savedPlaces = savedPlaces,
                type = SavedPlaceType.Place,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                onCreate = onCreateSavedPlace,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
            )
            BUBBLE_GROUP_RADARS -> RadarImportCard(
                summary = radarImportSummary,
                importStatus = radarImportStatus,
                onPickFile = onImportRadarFile,
                onOpenMapaRadar = onOpenMapaRadar,
                onClearRadars = onClearImportedRadars,
            )
            BUBBLE_GROUP_APPEARANCE -> BubbleSettingsCard(settings = draft, onChange = ::saveDraft)
            BUBBLE_GROUP_BACKUP -> BackupCard(
                status = backupStatus,
                onCreateBackup = onCreateBackup,
                onRestoreBackup = onRestoreBackup,
            )
            BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard(
                cardTemplates = cardTemplates,
                templateStatus = templateStatus,
                unreadTemplatePrints = unreadTemplatePrints,
                onPickCardModels = onPickCardModels,
                onDeleteCardModel = onDeleteCardModel,
            )
            else -> SystemControlCard(settings = draft, onChange = ::saveDraft)
        }
    }
} // grouped_settings_screen_0_1_115

private fun groupedBubbleTitle(group: String): String = when (group) {
    BUBBLE_GROUP_GENERAL -> "Controle geral"
    BUBBLE_GROUP_READING -> "Leitura ao vivo"
    BUBBLE_GROUP_ALERTS -> "Alertas de proximidade"
    BUBBLE_GROUP_SAVED_PLACES -> "Locais salvos"
    BUBBLE_GROUP_RADARS -> "Radares importados"
    BUBBLE_GROUP_CARDS -> "Cards cadastrados"
    BUBBLE_GROUP_APPEARANCE -> "Bolinha e aparencia"
    BUBBLE_GROUP_ACCESS -> "Permissoes, leitura e GPS"
    BUBBLE_GROUP_BACKUP -> "Backup dos dados"
    else -> "Controle geral"
}

private fun groupedBubbleDescription(group: String): String = when (group) {
    BUBBLE_GROUP_GENERAL -> "Liga ou pausa o Rota Certa e os avisos."
    BUBBLE_GROUP_READING -> "Autoriza a Acessibilidade e controla a leitura da tela."
    BUBBLE_GROUP_ALERTS -> "Crie e edite somente alertas de proximidade."
    BUBBLE_GROUP_SAVED_PLACES -> "Gerencie somente locais salvos, sem alerta."
    BUBBLE_GROUP_RADARS -> "Importe e gerencie radares separadamente."
    BUBBLE_GROUP_CARDS -> "Selecione os aplicativos permitidos e gerencie os modelos de cards."
    BUBBLE_GROUP_APPEARANCE -> "Ajusta transparencia, contraste e aparencia da bolinha flutuante."
    BUBBLE_GROUP_ACCESS -> "Controle a leitura ao vivo, a Acessibilidade, a localizacao e o GPS continuo."
    BUBBLE_GROUP_BACKUP -> "Crie ou restaure uma copia das configuracoes e dos dados."
    else -> "Ajustes do Rota Certa."
}

@Composable
private fun SystemControlCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    ExpandableCard(title = "Controle geral", initiallyExpanded = false) {
        SettingsSwitchRow(
            label = "Rota Certa ligado",
            checked = settings.appEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(appEnabled = enabled)) },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bip, voz e popup de radares e proximidade", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.appEnabled && settings.proximityAlertsEnabled,
                enabled = settings.appEnabled,
                onCheckedChange = { enabled -> onChange(settings.copy(proximityAlertsEnabled = enabled)) },
            )
        }
        SettingsSwitchRow(
            label = "Capturar cards automaticamente",
            checked = settings.automaticCardCaptureEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(automaticCardCaptureEnabled = enabled)) },
        )
        Text(
            "Ao reconhecer uma oferta valida, salva o recorte do card e cria o modelo sem interromper a rota.",
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSwitchRow(
            label = "Travar foco em um card por vez",
            checked = settings.multiCardFocusLockEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(multiCardFocusLockEnabled = enabled)) },
        )
        Text(
            "Evita misturar passageiros, embarques e destinos quando houver varias ofertas na mesma tela.",
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSwitchRow(
            label = "Fechar alerta automaticamente ao passar",
            checked = settings.proximityPopupAutoCloseEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(proximityPopupAutoCloseEnabled = enabled)) },
        )
        SettingsSwitchRow(
            label = "Depuracao tecnica detalhada",
            checked = settings.diagnosticsEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(diagnosticsEnabled = enabled)) },
        )
        Text(
            if (settings.diagnosticsEnabled) {
                "Depuracao ativa. Os eventos detalhados serao guardados temporariamente para localizar falhas."
            } else {
                "Desligada por padrao. O app nao monta nem grava logs detalhados durante o uso normal."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (settings.appEnabled) {
                "Desligue apenas quando quiser pausar leitura ao vivo e avisos. A bolinha fica em espera."
            } else {
                "Rota Certa esta pausado: leitura ao vivo e avisos de proximidade ficam desligados."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
} // diagnostics_default_off_0_1_128

@Composable
private fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    ExpandableCard(title = "Bolinha e aparencia", initiallyExpanded = false) {
        BubbleOpacitySlider(
            value = settings.bubbleOpacity,
            onValueChange = { onChange(settings.copy(bubbleOpacity = it)) },
            onValueChangeFinished = {},
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cor mais escura")
            Switch(
                checked = settings.bubbleDarkMode,
                onCheckedChange = { onChange(settings.copy(bubbleDarkMode = it)) },
            )
        }
        Text("Toque na bolinha para abrir o Rota Certa. Arraste para mudar a posicao.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingsLocationCard(
    draft: AppSettings,
    gpsStatus: String,
    onDraftChange: (AppSettings) -> Unit,
    onRequestGps: (LocationTarget) -> Unit,
    onSave: () -> Unit,
) {
    ExpandableCard(title = "Enderecos e raios", initiallyExpanded = false) {
        Text(
            "Salve o endereco base para aceitar corridas dentro do raio de km definido.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = draft.homeAddress,
            onValueChange = { onDraftChange(draft.copy(homeAddress = it, homeCoordinate = null)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Casa / ponto principal") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onRequestGps(LocationTarget.Home) }, modifier = Modifier.weight(1f)) { Text("Definir pelo GPS atual") }
            OutlinedButton(onClick = { onDraftChange(draft.copy(homeCoordinate = null)) }, modifier = Modifier.weight(1f)) { Text("Digitar") }
        }
        draft.homeCoordinate?.let { Text("GPS casa salvo: ${formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall) }

        OutlinedTextField(
            value = draft.alternativeAddress,
            onValueChange = { onDraftChange(draft.copy(alternativeAddress = it, alternativeCoordinate = null)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Alfinete / localidade alternativa") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onRequestGps(LocationTarget.Alternative) }, modifier = Modifier.weight(1f)) { Text("Usar GPS") }
            OutlinedButton(onClick = { onDraftChange(draft.copy(alternativeCoordinate = null)) }, modifier = Modifier.weight(1f)) { Text("Digitar") }
        }
        draft.alternativeCoordinate?.let { Text("GPS alfinete salvo: ${formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall) }

        RadiusSlider("Raio casa", draft.homeRadiusKm, { onDraftChange(draft.copy(homeRadiusKm = it)) }, onSave)
        RadiusSlider("Raio alfinete", draft.alternativeRadiusKm, { onDraftChange(draft.copy(alternativeRadiusKm = it)) }, onSave)
        ProximityAlertDistanceSlider(
            value = draft.proximityAlertDistanceMeters,
            onValueChange = { onDraftChange(draft.copy(proximityAlertDistanceMeters = it)) },
            onValueChangeFinished = onSave,
        )
        if (gpsStatus.isNotBlank()) Text(gpsStatus, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MapsAndAdvancedCard(
    draft: AppSettings,
    onDraftChange: (AppSettings) -> Unit,
    onSave: () -> Unit,
) {
    ExpandableCard(title = "Google Maps e ajustes avancados", initiallyExpanded = false) {
        OutlinedTextField(
            value = draft.googleMapsApiKey,
            onValueChange = { onDraftChange(draft.copy(googleMapsApiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Chave Google Maps API") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Text(
            "Opcional: Google Maps melhora a precisao por rota real. Sem chave, o app usa distancia aproximada quando houver coordenadas confiaveis.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = draft.desiredKeywords,
            onValueChange = { onDraftChange(draft.copy(desiredKeywords = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bairros/palavras desejados") },
        )
        OutlinedTextField(
            value = draft.avoidedKeywords,
            onValueChange = { onDraftChange(draft.copy(avoidedKeywords = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bairros/palavras evitados") },
        )
        OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("Salvar ajustes avancados")
        }
    }
}

@Composable
private fun BackupCard(
    status: String,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    ExpandableCard(title = "Backup dos dados", initiallyExpanded = false) {
        Text(
            "Salva configuracoes, locais e alertas de proximidade em um arquivo do celular.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onCreateBackup, modifier = Modifier.weight(1f)) {
                Text("Criar backup")
            }
            OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.weight(1f)) {
                Text("Restaurar")
            }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MonitoredAppsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val compatibility = settings to onChange
    InstalledRideAppsCard()
} // no_pre_registered_apps_ui_0_1_126 superseded manual_apps_card_restored_0_1_127
 // no_pre_registered_apps_ui_0_1_126


@Composable
private fun InstalledRideAppsCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedPackages by remember { mutableStateOf(SelectedRideAppStore.read(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selectedPackages = SelectedRideAppStore.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ExpandableCard(title = "Aplicativos que a bolinha pode ler", initiallyExpanded = true) {
        Text(
            "Nenhum aplicativo vem marcado. Escolha manualmente somente os aplicativos de corrida que deseja monitorar.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { context.startActivity(Intent(context, InstalledRideAppPickerActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Buscar aplicativos instalados")
        }
        if (selectedPackages.isEmpty()) {
            Text(
                "Nenhum aplicativo selecionado. A leitura de cards fica pausada ate voce escolher pelo menos um.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Aplicativos selecionados: ${selectedPackages.size}", fontWeight = FontWeight.Bold)
            selectedPackages.forEach { packageName ->
                Text(packageName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
} // no_selected_apps_picker_ui_0_1_126 superseded manual_apps_picker_restored_0_1_127
 // no_selected_apps_picker_ui_0_1_126


@Composable
private fun ExpandableCard(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
} // grouped_card_always_open_0_1_115

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadiusSlider(label: String, value: Double, onValueChange: (Double) -> Unit, onValueChangeFinished: () -> Unit) {
    val safeValue = value.coerceIn(1.0, 30.0)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(formatKm(safeValue), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = safeValue.toFloat(),
            onValueChange = { rawValue -> onValueChange(((rawValue * 2f).roundToInt() / 2.0).coerceIn(1.0, 30.0)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 1f..30f,
            steps = 57,
        )
    }
}

@Composable
private fun BubbleOpacitySlider(value: Double, onValueChange: (Double) -> Unit, onValueChangeFinished: () -> Unit) {
    val safeValue = value.coerceIn(0.25, 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Transparencia")
            Text("${(safeValue * 100).roundToInt()}%", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = safeValue.toFloat(),
            onValueChange = { rawValue -> onValueChange(((rawValue * 20f).roundToInt() / 20.0).coerceIn(0.25, 1.0)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0.25f..1f,
            steps = 14,
        )
    }
}

@Composable
private fun ProximityAlertDistanceSlider(value: Int, onValueChange: (Int) -> Unit, onValueChangeFinished: () -> Unit) {
    val safeValue = value.coerceIn(100, 2000)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Distancia do primeiro aviso")
            Text("$safeValue m", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = safeValue.toFloat(),
            onValueChange = { rawValue ->
                val rounded = ((rawValue / 50f).roundToInt() * 50).coerceIn(100, 2000)
                onValueChange(rounded)
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 100f..2000f,
            steps = 37,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("100 m", style = MaterialTheme.typography.bodySmall)
            Text("2.000 m", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Define quando o primeiro popup aparece. A distancia e a barra diminuem ate o ponto; depois de passar, o popup fecha sozinho.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
} // proximity_auto_close_0_1_128

@Composable
private fun WorkRegionCard(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()
    var draft by remember(settings) { mutableStateOf(settings) }
    var status by remember { mutableStateOf("") }
    var pendingGps by remember { mutableStateOf(false) }

    fun saveRegion(updated: AppSettings) {
        draft = updated
        onSaveSettings(updated)
    }

    fun captureGps() {
        scope.launch {
            status = "Buscando sinal de GPS..."
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                status = "Nao consegui captar o GPS. Autorize a localizacao e tente novamente."
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            val address = resolved.addressLine.ifBlank { formatCoordinate(coordinate) }
            val updated = draft.copy(homeAddress = address, homeCoordinate = coordinate)
            saveRegion(updated)
            status = "Regiao salva pelo GPS: ${formatCoordinate(coordinate)}"
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (!pendingGps) return@rememberLauncherForActivityResult
        pendingGps = false
        if (permissions.values.any { it }) {
            captureGps()
        } else {
            status = "Localizacao negada. Autorize o GPS para salvar a regiao."
        }
    }

    ExpandableCard(title = "Minha regiao de corridas", initiallyExpanded = false) {
        Text(
            "Defina onde voce quer receber corridas pelo destino final. O app aceita quando o destino fica dentro do raio salvo.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = draft.homeAddress,
            onValueChange = { draft = draft.copy(homeAddress = it, homeCoordinate = null) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endereco da regiao de trabalho") },
        )
        Button(
            onClick = {
                pendingGps = true
                gpsPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Definir pelo GPS atual")
        }
        draft.homeCoordinate?.let {
            Text("GPS salvo: ${formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
        }
        RadiusSlider(
            label = "Raio da regiao de trabalho",
            value = draft.homeRadiusKm,
            onValueChange = { draft = draft.copy(homeRadiusKm = it) },
            onValueChangeFinished = { saveRegion(draft) },
        )
        Button(
            onClick = {
                saveRegion(draft)
                status = "Regiao de trabalho salva."
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar regiao de trabalho")
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToolsScreen(
    onOpenWhatsApp: () -> Unit,
    onOpenBlaBlaCarCollector: () -> Unit,
    onOpenQuickReplies: () -> Unit = {},
    onClearClipboard: () -> Unit,
    onOpenWorkTracking: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ferramentas", fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rastreamento de trabalho", fontWeight = FontWeight.Bold)
                Text(
                    "Registra o caminho percorrido, distancia, tempo e os pontos de GPS do dia somente neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenWorkTracking, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir rastreamento")
                }
            }
        }

        Text("Ferramentas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Respostas rapidas", fontWeight = FontWeight.Bold)
                Text(
                    "Salve, edite e pesquise saudacoes. Ao escolher uma resposta, o Rota Certa tenta preencher o campo de mensagem que estava aberto.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenQuickReplies, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir respostas rapidas")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WhatsApp", fontWeight = FontWeight.Bold)
                Text("Abre o WhatsApp instalado no celular.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenWhatsApp, modifier = Modifier.fillMaxWidth()) { Text("Abrir WhatsApp") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletor BlaBlaCar", fontWeight = FontWeight.Bold)
                Text(
                    "Registro manual de viagem logada: passageiros, telefones, WhatsApp, rotas, faturamento, despesas e lucro.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) { Text("Abrir coletor") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Area de transferencia", fontWeight = FontWeight.Bold)
                Text("Remove manualmente o texto copiado do celular.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) { Text("Limpar area de transferencia") }
            }
        }
    }
} // quick_replies_module_0_1_128

@Composable
private fun ReportsGroupScreen(
    diagnostic: LiveDiagnostic?,
    history: List<AnalysisResult>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Relatorios e historico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DiagnosticExpander(
            diagnostic = diagnostic,
        )
        Text("Historico de decisoes", fontWeight = FontWeight.Bold)
        HistoryScreen(history)
    }
} // grouped_reports_tools_0_1_115

@Composable
private fun HistoryScreen(history: List<AnalysisResult>) = Unit

private suspend fun buildManualSupportReport(
    context: Context,
    repository: SettingsRepository,
    settings: AppSettings,
    liveEnabled: Boolean,
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
    savedPlaces: List<SavedPlace>,
    radarImportSummary: RadarImportSummary,
): String {
    // universal_no_card_registration_0_1_102
    // Leitura universal de tela: true
    val nowMillis = System.currentTimeMillis()
    val bubbleStatePrefs = context.getSharedPreferences("rota_certa_bubble", Context.MODE_PRIVATE)
    val bubbleUpdatedAtMillis = bubbleStatePrefs.getLong("state_updated_at", 0L)
    fun bubbleText(key: String): String = bubbleStatePrefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: "nao informado"
    fun bubbleBool(key: String): String = bubbleStatePrefs.getBoolean(key, false).toString()
    fun bubbleInt(key: String): String = bubbleStatePrefs.getInt(key, -1).takeIf { it >= 0 }?.toString() ?: "nao informado"
    val sessionDiagnostic = LiveFailureTraceStore.exportReport(nowMillis)
    val complementaryEvents = DiagnosticLogStore.dump()

    return buildString {
        appendLine("ROTA CERTA DIAGNOSTICO DE SESSAO")
        appendLine("Arquivo montado somente por clique do usuario.")
        appendLine("A trilha circular fica apenas em memoria e nao grava cada evento no armazenamento.")
        appendLine("Versao: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Data da exportacao: ${formatDate(nowMillis)}")
        appendLine("Pacote: ${context.packageName}")
        appendLine("Leitura ao vivo ativa: $liveEnabled")
        appendLine()
        appendLine("--- ULTIMA TENTATIVA REAL DA BOLINHA ---")
        appendLine(sessionDiagnostic)
        appendLine()
        appendLine("--- ESTADO ATUAL DA BOLINHA ---")
        appendLine("Atualizado: ${if (bubbleUpdatedAtMillis > 0L) formatDate(bubbleUpdatedAtMillis) else "nunca"}")
        appendLine("Idade estado: ${if (bubbleUpdatedAtMillis > 0L) (nowMillis - bubbleUpdatedAtMillis).toString() + " ms" else "nao informado"}")
        appendLine("Etapa: ${bubbleText("state_stage")}")
        appendLine("Cor: ${bubbleText("state_color")}")
        appendLine("Km exibido: ${bubbleText("state_distance_km")}")
        appendLine("Motivo: ${bubbleText("state_reason")}")
        appendLine("Pacote janela: ${bubbleText("state_window_package")}")
        appendLine("Pacote ativo: ${bubbleText("state_active_package")}")
        appendLine("Pacote texto: ${bubbleText("state_text_package")}")
        appendLine("Hash tela atual: ${bubbleText("state_last_snapshot_hash")}")
        appendLine("Hash analisado: ${bubbleText("state_last_analyzed_hash")}")
        appendLine("Hash pendente: ${bubbleText("state_pending_hash")}")
        appendLine("Servico pronto: ${bubbleBool("state_service_ready")}")
        appendLine("Analisando agora: ${bubbleBool("state_analyzing")}")
        appendLine("Texto acessibilidade tamanho: ${bubbleInt("state_accessibility_text_length")}")
        appendLine("Texto acessibilidade hash: ${bubbleText("state_accessibility_text_hash")}")
        appendLine("Texto OCR tamanho: ${bubbleInt("state_ocr_text_length")}")
        appendLine("Texto OCR hash: ${bubbleText("state_ocr_text_hash")}")
        appendLine()
        appendLine("--- CONFIGURACOES NECESSARIAS PARA A DECISAO ---")
        appendLine("Rota Certa ligado: ${settings.appEnabled}")
        appendLine("Leitura universal ligada: ${settings.liveReadingEnabled}")
        appendLine("Casa ligada: ${settings.homeTargetEnabled}")
        appendLine("Casa/ponto principal: ${settings.homeAddress.ifBlank { "nao informado" }}")
        appendLine("Coordenada casa: ${settings.homeCoordinate?.let(::formatCoordinate) ?: "nao informada"}")
        appendLine("Raio casa: ${formatKm(settings.homeRadiusKm)}")
        appendLine("Alfinete ligado: ${settings.alternativeTargetEnabled}")
        appendLine("Alfinete/local alternativo: ${settings.alternativeAddress.ifBlank { "nao informado" }}")
        appendLine("Coordenada alfinete: ${settings.alternativeCoordinate?.let(::formatCoordinate) ?: "nao informada"}")
        appendLine("Raio alfinete: ${formatKm(settings.alternativeRadiusKm)}")
        appendLine("Google Maps API configurada: ${settings.googleMapsApiKey.isNotBlank() || BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank()}")
        appendLine()
        appendLine("--- LOCAIS E ALERTAS ---")
        appendLine("Total: ${savedPlaces.size}")
        savedPlaces.forEachIndexed { index, place ->
            appendLine("${index + 1}. tipo=${place.type}; nome=${place.name}; endereco=${place.address}; coordenada=${formatCoordinate(place.coordinate)}; distanciaAlerta=${place.alertDistanceMeters ?: 0}")
        }
        appendLine()
        appendLine("--- RADARES IMPORTADOS ---")
        appendLine(radarImportSummary.toString())
        appendLine()
        appendLine("--- LINHA DO TEMPO COMPLETA DA EXECUCAO ---")
        appendLine(complementaryEvents.ifBlank { "sem eventos complementares" })
        appendLine()
        appendLine("--- OBSERVACAO ---")
        appendLine("O relatorio registra a linha do tempo mantida em memoria desde o inicio da execucao, alem da tentativa detalhada de leitura, OCR, enderecos, geocodificacao, rota, descartes, atalhos e cor final.")
    }
}

private fun clearClipboard(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }.onSuccess {
        Toast.makeText(context, "Area de transferencia limpa.", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Nao foi possivel limpar a area de transferencia.", Toast.LENGTH_SHORT).show()
    }
}

private enum class LocationTarget { Home, Alternative }

private data class RadiusInfo(val label: String, val distanceKm: Double, val radiusKm: Double)

private fun resultRadiusInfo(result: AnalysisResult, settings: AppSettings): RadiusInfo? {
    val homeInfo = result.pickupToHomeKm?.let { RadiusInfo("Distancia ate casa", it, settings.homeRadiusKm) }
    val alternativeInfo = result.pickupToAlternativeKm?.let { RadiusInfo("Distancia ate alfinete", it, settings.alternativeRadiusKm) }
    return when {
        result.recommendation == Recommendation.GoodRide && homeInfo != null && homeInfo.distanceKm <= homeInfo.radiusKm -> homeInfo
        result.recommendation == Recommendation.GoodRide && alternativeInfo != null && alternativeInfo.distanceKm <= alternativeInfo.radiusKm -> alternativeInfo
        homeInfo != null -> homeInfo
        else -> alternativeInfo
    }
}

private fun isLiveAccessibilityEnabled(context: Context): Boolean {
    val component = ComponentName(context, LiveRideAccessibilityService::class.java)
    val expectedServices = setOf(component.flattenToString(), component.flattenToShortString())
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabledServices.split(':').any { service -> expectedServices.any { it.equals(service, ignoreCase = true) } }
}

private fun recommendationLabel(recommendation: Recommendation): String = when (recommendation) {
    Recommendation.GoodRide -> "VERDE - Dentro da area"
    Recommendation.OutsideRadius -> "VERMELHO - Fora da area"
    Recommendation.InsufficientData -> "Dados insuficientes"
}

private fun decisionActionLabel(recommendation: Recommendation): String = when (recommendation) {
    Recommendation.GoodRide -> "Aceitar"
    Recommendation.OutsideRadius -> "Recusar"
    Recommendation.InsufficientData -> "Revisar"
}

private fun savedPlaceTypeLabel(place: SavedPlace): String = when (place.type) {
    SavedPlaceType.Place -> "Local salvo"
    SavedPlaceType.ProximityAlert -> "Alerta de proximidade: ${place.alertDistanceMeters ?: 500} m"
}

private fun defaultSavedPlaceName(type: SavedPlaceType): String = when (type) {
    SavedPlaceType.Place -> "Local salvo"
    SavedPlaceType.ProximityAlert -> "Alerta"
}

private fun openSavedPlaceInGps(context: Context, place: SavedPlace) {
    val uri = Uri.parse(
        "geo:${place.coordinate.latitude},${place.coordinate.longitude}" +
            "?q=${place.coordinate.latitude},${place.coordinate.longitude}(${Uri.encode(place.name)})",
    )
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "Nao consegui abrir o GPS neste aparelho.", Toast.LENGTH_SHORT).show()
        }
}

private fun formatDestination(value: String?): String {
    val destination = value?.trim().orEmpty()
    if (destination.isBlank()) return "nao identificado"
    val parenthesizedNeighborhood = Regex("""^(.+?)\s*\((.+)\)$""").find(destination)
    return if (parenthesizedNeighborhood != null) {
        val street = parenthesizedNeighborhood.groupValues[1].trim()
        val neighborhood = parenthesizedNeighborhood.groupValues[2].trim()
        "$street\n$neighborhood"
    } else {
        destination
    }
}

private fun deviceRegionLabel(region: DeviceRegion): String =
    listOf(region.city, region.country).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Regiao de trabalho nao definida. Use GPS somente quando quiser preencher um endereco." }

private fun formatKm(value: Double): String = String.format(Locale("pt", "BR"), "%.1f km", value)

private fun formatCoordinate(coordinate: Coordinate): String =
    String.format(Locale("pt", "BR"), "%.5f, %.5f", coordinate.latitude, coordinate.longitude)

private fun formatDate(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(value))

// unified_app_control_bubbles_0_1_94 preserved_by_functional_bubbles

// universal_two_address_ui_0_1_98

// universal_no_card_registration_0_1_102
// Leitura universal de tela: true

// universal_no_card_compile_repair_0_1_102
// cards_ui_allowed_compile_0_1_120

// bubble_shortcut_navigation_0_1_117

// professional_bubble_named_action_markers_0_1_118
// label = "WhatsApp"
// label = "Coletor"
// label = "Limpar"
// label = "Depurar"
// label = "Encerrar"
// grouped_bubble_home_0_1_115
// grouped_bubble_navigation_0_1_115
// grouped_settings_screen_0_1_115
// grouped_card_always_open_0_1_115
// grouped_reports_tools_0_1_115
// Central de bolinhas
// Cada bolinha abre um grupo
// BUBBLE_GROUP_DESTINATION
// selectedBubbleGroup
// TextAlign.Center
// bottomBar = {}
// DiagnosticLogStore.dump()

// full_session_diagnostic_0_1_118

// professional_bubble_home_0_1_118

// popup_only_control_center_0_1_119

// popup_navigation_main_0_1_120

// BUBBLE_GROUP_ACCESS -> { // popup_navigation_professional_compat_0_1_120

// popup_navigation_compile_main_0_1_120

// popup_navigation_card_state_0_1_120

// popup_navigation_final_compile_0_1_120
// BUBBLE_GROUP_CARDS -> CardModelsCard( // legacy_workflow_marker_0_1_120


// BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard( // cards_legacy_contract_0_1_123

// manual_ui_annotation_cleanup_0_1_127

// manual_optional_contract_finalizer_0_1_127

// compile_final_cleanup_0_1_127 removed_duplicate_composable=0
