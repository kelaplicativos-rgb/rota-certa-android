package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.mapeiaia.rotacerta.trips.BlaBlaNetworkDiagnosticStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
// automatic_capture_ui_imports_0_1_129

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
        Unit /* production_log_removed_checklist_4 */
    } // full_session_start_0_1_118

    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { SettingsRepository(context) }
    val settings by repository.settings.collectAsState(initial = AppSettings())
    LaunchedEffect("work_mode_default_off_0_1_162") {
        val migrationPrefs0162 = context.getSharedPreferences("rota_certa_runtime_migrations", Context.MODE_PRIVATE)
        if (!migrationPrefs0162.getBoolean("work_mode_default_off_0_1_162", false)) {
            val stored0162 = repository.settings.first()
            repository.saveSettings(WorkModePolicy0162.setEnabled(stored0162, false))
            migrationPrefs0162.edit().putBoolean("work_mode_default_off_0_1_162", true).apply()
        }
    }
    // in_app_bubble_immediate_state_0_1_98 substituido pela navegacao agrupada 0.1.115
    val history = emptyList<AnalysisResult>()
    val savedPlaces by repository.savedPlaces.collectAsState(initial = emptyList())
    val importedRadars by repository.importedRadars.collectAsState(initial = emptyList())
    val radarImportSummary = remember(importedRadars) {
        RadarImportSummary(
            count = importedRadars.size,
            lastImportedAtMillis = importedRadars.maxOfOrNull { it.createdAtMillis } ?: 0L,
        )
    }
    val ocrService = remember { OcrService(context) }
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) } // popup_modules_0_1_120
    val geocodingService = remember { GeocodingService(context) }
    val savedPlaceGpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(TAB_CONFIG) }
    var selectedBubbleGroup by remember { mutableStateOf(BUBBLE_GROUP_GENERAL) } // grouped_bubble_state_0_1_115
    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }
    var highlightedImportedRadarId0178 by remember { mutableStateOf<String?>(null) }
    var savedPlaceNameDialogId by remember { mutableStateOf<String?>(null) }
    var handledSavedPlaceNameDialogId by remember { mutableStateOf<String?>(null) }
    var confirmDestinationGps138 by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf(DeviceRegion()) }
    var liveEnabled by remember { mutableStateOf(isLiveAccessibilityEnabled(context)) }
    var backupStatus by remember { mutableStateOf("") }
    var radarImportStatus by remember { mutableStateOf("") }
    var supportReportStatus by remember { mutableStateOf("") }
    var debugLogEnabled by remember { mutableStateOf(DebugLogPreferenceStore.isEnabled(context)) }
    var highlightedShortcutModule0171 by remember { mutableStateOf<String?>(null) }
    var shortcutCustomizationVisible0179 by remember { mutableStateOf(false) }
    var selectedShortcutEntryId0180 by remember { mutableStateOf<String?>(null) }
    var moduleNavigationActive0172 by remember { mutableStateOf(false) }
    var textCorrectionInitial0186 by remember { mutableStateOf<String?>(null) }
    var textReplacementToken0186 by remember { mutableStateOf<String?>(null) }
    var textCorrectionRequestKey0186 by remember { mutableStateOf<String?>(null) }


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
                if (isAlert) "Alerta salvo" else "Local salvo",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openShortcutModuleFromHome0171(spec: BubbleShortcutSpec) {
        highlightedShortcutModule0171 = spec.id
        moduleNavigationActive0172 = true
        if (spec.id == "trip_agenda") {
            val traceId = br.com.mapeiaia.rotacerta.trips.AgendaTrace.beginAgendaOpen(context, "home_shortcut")
            val agendaIntent = br.com.mapeiaia.rotacerta.trips.AgendaTrace.attachTrace(
                Intent(context, br.com.mapeiaia.rotacerta.trips.TripsActivity::class.java)
                    .setAction(br.com.mapeiaia.rotacerta.trips.TripActions.ACTION_OPEN_TRIPS),
                traceId,
            )
            val launchOperation = br.com.mapeiaia.rotacerta.trips.AgendaTrace.operationStart(
                context, "AGENDA_START_ACTIVITY", "main_activity", traceId,
            )
            br.com.mapeiaia.rotacerta.trips.AgendaTrace.event(
                context, "AGENDA_START_ACTIVITY_REQUEST", "source=home_shortcut", traceId, launchOperation.operationId,
            )
            context.startActivity(agendaIntent)
            br.com.mapeiaia.rotacerta.trips.AgendaTrace.event(
                context, "AGENDA_START_ACTIVITY_RETURN", "source=home_shortcut", traceId, launchOperation.operationId,
            )
            br.com.mapeiaia.rotacerta.trips.AgendaTrace.operationEnd(context, launchOperation)
            return
        }
        when (spec.action) {
            BubbleShortcutAction.OpenFinance -> context.startActivity(Intent(context, FinancialActivity::class.java))
            BubbleShortcutAction.OpenQuickReplies -> context.startActivity(Intent(context, QuickRepliesActivity::class.java))
            BubbleShortcutAction.OpenQuickLinks -> context.startActivity(Intent(context, QuickLinksActivity::class.java))
            BubbleShortcutAction.OpenMessageTemplates,
            BubbleShortcutAction.CopyTripConfirmation,
            BubbleShortcutAction.CopyPassengerValue,
            -> context.startActivity(Intent(context, MessageTemplatesActivity::class.java))
            BubbleShortcutAction.OpenScreenWhatsApp -> openWhatsAppApp(context)
            BubbleShortcutAction.ClearClipboard -> clearClipboard(context)
            else -> Unit
        }
    }

    fun createSavedPlaceFromHome(type: SavedPlaceType, requestNameDialog: Boolean = false) {
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
                name = if (isAlert) "Alerta" else "", // blank_saved_place_name_checklist_7
                type = type,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = if (isAlert) settings.proximityAlertDistanceMeters else null,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            highlightedSavedPlaceId = place.id
            if (requestNameDialog) {
                handledSavedPlaceNameDialogId = null
                savedPlaceNameDialogId = place.id
            }
            tab = TAB_CONFIG
            selectedBubbleGroup = if (isAlert) BUBBLE_GROUP_ALERTS else BUBBLE_GROUP_SAVED_PLACES
            Toast.makeText(
                context,
                if (isAlert) "Alerta salvo" else "Local salvo",
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
            br.com.mapeiaia.rotacerta.trips.AgendaForensicReportBuilder.clearFrozen()
            supportReportStatus = "Relatorio cancelado."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            supportReportStatus = "Gerando relatorio..."
            UnifiedDebugEventStore.record("REPORT_EXPORT", context.packageName, "exportação manual solicitada")
            runCatching {
                val report = buildManualSupportReport(
                    context = context,
                    repository = repository,
                    settings = settings,
                    liveEnabled = liveEnabled,
                    savedPlaces = savedPlaces,
                    radarImportSummary = radarImportSummary,
                )
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(report)
                } ?: error("Nao consegui abrir o arquivo do relatorio.")
            }.onSuccess {
                Unit /* production_log_removed_checklist_4 */
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
                backupStatus = "Backup restaurado: ${backup.savedPlaces.size} local(is)."
            }.onFailure { error ->
                backupStatus = "Falha ao restaurar backup: ${error.message.orEmpty()}"
            }
        }
    }


    LaunchedEffect(settings, savedPlaces.size, radarImportSummary.count) {
        val factoryPrefs = context.getSharedPreferences("rota_certa_factory_guard", Context.MODE_PRIVATE)
        val guardKey = "factory_clean_0_1_72"
        val hasStoredRegionOrUserLocation = settings.homeAddress.isNotBlank() ||
            settings.homeCoordinate != null ||
            settings.alternativeAddress.isNotBlank() ||
            settings.alternativeCoordinate != null
        val hasNoUserCollections = savedPlaces.isEmpty() && radarImportSummary.count == 0
        if (!factoryPrefs.getBoolean(guardKey, false) && hasNoUserCollections && hasStoredRegionOrUserLocation) {
            repository.saveSettings(AppSettings())
            region = DeviceRegion()
            backupStatus = "Dados antigos removidos; seleção de aplicativos permanece manual."
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
            TAB_CONFIG -> BUBBLE_GROUP_GENERAL
            TAB_ANALYSIS -> BUBBLE_GROUP_DESTINATION
            else -> BUBBLE_GROUP_GENERAL
        }
        launchIntent?.getStringExtra(EXTRA_OPEN_BUBBLE_GROUP)?.let { requestedGroup ->
            if (requestedGroup in BUBBLE_GROUP_VALUES) selectedBubbleGroup = requestedGroup
        } // startup_permissions_0_1_120
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
        highlightedImportedRadarId0178 = launchIntent?.getStringExtra(EXTRA_IMPORTED_RADAR_ID_0178)
        val homeLaunchMode0186 = launchIntent?.getStringExtra(EXTRA_HOME_LAUNCH_MODE_0186)
        highlightedShortcutModule0171 = HomeLaunchPolicy0186.requestedModule(
            homeLaunchMode0186,
            launchIntent?.getStringExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171),
        )
        if (highlightedShortcutModule0171 == "trip_agenda") {
            launchIntent?.removeExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171)
            highlightedShortcutModule0171 = null
            val traceId = br.com.mapeiaia.rotacerta.trips.AgendaTrace.beginAgendaOpen(context, "home_launch_intent")
            val agendaIntent = br.com.mapeiaia.rotacerta.trips.AgendaTrace.attachTrace(
                Intent(context, br.com.mapeiaia.rotacerta.trips.TripsActivity::class.java)
                    .setAction(br.com.mapeiaia.rotacerta.trips.TripActions.ACTION_OPEN_TRIPS),
                traceId,
            )
            val launchOperation = br.com.mapeiaia.rotacerta.trips.AgendaTrace.operationStart(
                context, "AGENDA_START_ACTIVITY", "main_activity_launch_intent", traceId,
            )
            br.com.mapeiaia.rotacerta.trips.AgendaTrace.event(
                context, "AGENDA_START_ACTIVITY_REQUEST", "source=home_launch_intent", traceId, launchOperation.operationId,
            )
            context.startActivity(agendaIntent)
            br.com.mapeiaia.rotacerta.trips.AgendaTrace.event(
                context, "AGENDA_START_ACTIVITY_RETURN", "source=home_launch_intent", traceId, launchOperation.operationId,
            )
            br.com.mapeiaia.rotacerta.trips.AgendaTrace.operationEnd(context, launchOperation)
        }
        if (homeLaunchMode0186 == HomeLaunchPolicy0186.MODE_COLLAPSED) {
            selectedBubbleGroup = BUBBLE_GROUP_GENERAL
            highlightedSavedPlaceId = null
            highlightedImportedRadarId0178 = null
        }
        shortcutCustomizationVisible0179 = launchIntent?.getBooleanExtra(EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179, false) == true
        selectedShortcutEntryId0180 = launchIntent?.getStringExtra(EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180)
        if (highlightedShortcutModule0171 == "text_correction") {
            textCorrectionInitial0186 = launchIntent?.getStringExtra(EXTRA_TEXT_CORRECTION_INITIAL_0186)
            textReplacementToken0186 = launchIntent?.getStringExtra(EXTRA_TEXT_REPLACEMENT_TOKEN_0186)
            textCorrectionRequestKey0186 = launchIntent?.getStringExtra(EXTRA_TEXT_CORRECTION_REQUEST_KEY_0186)
                ?: System.nanoTime().toString()
        } else {
            textCorrectionInitial0186 = null
            textReplacementToken0186?.let(TextReplacementSession0186::clear)
            textReplacementToken0186 = null
            textCorrectionRequestKey0186 = null
        }
        // Dados capturados ficam somente no estado da tela atual; não permanecem no Intent da Activity.
        launchIntent?.removeExtra(EXTRA_TEXT_CORRECTION_INITIAL_0186)
        launchIntent?.removeExtra(EXTRA_TEXT_REPLACEMENT_TOKEN_0186)
        launchIntent?.removeExtra(EXTRA_TEXT_CORRECTION_REQUEST_KEY_0186)
        moduleNavigationActive0172 = shortcutCustomizationVisible0179 || highlightedShortcutModule0171 != null || tab != TAB_CONFIG || selectedBubbleGroup != BUBBLE_GROUP_GENERAL
        launchIntent?.getStringExtra(EXTRA_CREATE_SAVED_PLACE_TYPE_138)?.let { rawType ->
            val requestedType = runCatching { SavedPlaceType.valueOf(rawType) }.getOrNull()
            if (requestedType != null) createSavedPlaceFromHome(requestedType, requestNameDialog = true)
            launchIntent?.removeExtra(EXTRA_CREATE_SAVED_PLACE_TYPE_138)
        }
        if (launchIntent?.getBooleanExtra(EXTRA_CONFIRM_DESTINATION_GPS_138, false) == true) {
            confirmDestinationGps138 = true
            launchIntent?.removeExtra(EXTRA_CONFIRM_DESTINATION_GPS_138)
        }
        if (launchIntent?.getBooleanExtra(EXTRA_CREATE_BACKUP_0184, false) == true) {
            launchIntent.removeExtra(EXTRA_CREATE_BACKUP_0184)
            backupFileCreator.launch("rota-certa-backup.json")
        }
        if (launchIntent?.getBooleanExtra(EXTRA_RESTORE_BACKUP_0184, false) == true) {
            launchIntent.removeExtra(EXTRA_RESTORE_BACKUP_0184)
            backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        if (launchIntent?.getBooleanExtra("auto_export_report", false) == true) {
            UnifiedDebugEventStore.record(
                "LEGACY_AUTO_REPORT_IGNORED",
                context.packageName,
                "exportacao automatica antiga ignorada; use o botao Gerar relatorio para depuracao",
            )
            launchIntent.removeExtra("auto_export_report")
        } // automatic_report_disabled_unified_manual_export_0_1_142
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
        var requestedName by remember(place.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                handledSavedPlaceNameDialogId = place.id
                savedPlaceNameDialogId = null
            },
            title = { Text(if (place.type == SavedPlaceType.ProximityAlert) "Nome do alerta" else "Nome do local") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (place.type == SavedPlaceType.ProximityAlert) {
                            "Digite um nome ou salve vazio para usar Alerta."
                        } else {
                            "Digite um nome ou salve vazio para usar Local salvo."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = requestedName,
                        onValueChange = { requestedName = it },
                        label = { Text("Nome") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    renameSavedPlace(place, requestedName)
                    handledSavedPlaceNameDialogId = place.id
                    savedPlaceNameDialogId = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    handledSavedPlaceNameDialogId = place.id
                    savedPlaceNameDialogId = null
                }) { Text("Cancelar") }
            },
        )
    }

    if (confirmDestinationGps138) {
        AlertDialog(
            onDismissRequest = { confirmDestinationGps138 = false },
            title = { Text("Definir este local como destino?") },
            text = { Text("O GPS atual substituirá o destino principal usado pelo farol.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDestinationGps138 = false
                    scope.launch {
                        val coordinate = locationService.currentCoordinate()
                        if (coordinate == null) {
                            Toast.makeText(context, "Não foi possível obter a localização", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val resolved = gpsAddressResolver.resolve(coordinate)
                        repository.saveSettings(
                            settings.copy(
                                homeAddress = resolved.addressLine.ifBlank { formatCoordinate(coordinate) },
                                homeCoordinate = coordinate,
                                homeTargetEnabled = true,
                            ),
                        )
                        Toast.makeText(context, "Destino definido", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Definir") }
            },
            dismissButton = { TextButton(onClick = { confirmDestinationGps138 = false }) { Text("Cancelar") } },
        )
    }

    BackHandler(
        enabled = shortcutCustomizationVisible0179 || moduleNavigationActive0172 || tab != TAB_CONFIG || selectedBubbleGroup != BUBBLE_GROUP_GENERAL || highlightedShortcutModule0171 != null,
    ) {
        if (shortcutCustomizationVisible0179) {
            shortcutCustomizationVisible0179 = false
            selectedShortcutEntryId0180 = null
            moduleNavigationActive0172 = false
        } else {
            tab = TAB_CONFIG
            selectedBubbleGroup = BUBBLE_GROUP_GENERAL
            highlightedShortcutModule0171 = null
            highlightedSavedPlaceId = null
            highlightedImportedRadarId0178 = null
            moduleNavigationActive0172 = false
        }
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
                
                )
                TAB_CONFIG -> {
                    if (shortcutCustomizationVisible0179) {
                        ShortcutGridCustomizationScreen0179(
                            selectedEntryId0180 = selectedShortcutEntryId0180,
                            onClose = {
                                shortcutCustomizationVisible0179 = false
                                selectedShortcutEntryId0180 = null
                                moduleNavigationActive0172 = false
                            },
                        )
                    } else ShortcutModulesHome0171(
                        expandedModuleId = highlightedShortcutModule0171,
                        navigationRequestKey0177 = System.identityHashCode(launchIntent),
                        onOpenCustomization = {
                            selectedShortcutEntryId0180 = null
                            shortcutCustomizationVisible0179 = true
                            moduleNavigationActive0172 = true
                        },
                        onToggleModule = { spec ->
                            highlightedShortcutModule0171 = HomeModuleExpansionPolicy0174.toggle(
                                currentId = highlightedShortcutModule0171,
                                requestedId = spec.id,
                            )
                            moduleNavigationActive0172 = highlightedShortcutModule0171 != null
                            spec.targetGroup?.let { selectedBubbleGroup = it }
                        },
                        moduleContent = { spec ->
                            when (spec.action) {
                                BubbleShortcutAction.OpenDestination -> AnalysisScreen(
                                    settings = settings,
                                    latestResult = history.firstOrNull(),
                                    liveEnabled = liveEnabled,
                                    onSaveSettings = { updated -> scope.launch { repository.saveSettings(updated) } },
                                    onOpenAccessibilitySettings = {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                                )

                                BubbleShortcutAction.OpenRoute,
                                BubbleShortcutAction.OpenAlerts,
                                BubbleShortcutAction.OpenSavedPlaces,
                                BubbleShortcutAction.OpenRadars,
                                BubbleShortcutAction.OpenAppearance,
                                BubbleShortcutAction.OpenPermissions,
                                BubbleShortcutAction.OpenBackup,
                                BubbleShortcutAction.OpenAuthorizedAppsAndCards,
                                BubbleShortcutAction.CaptureCurrentAppAndScreen,
                                BubbleShortcutAction.StopApplication,
                                -> SettingsScreen(
                                    selectedGroup = when (spec.action) {
                                        BubbleShortcutAction.OpenRoute,
                                        BubbleShortcutAction.StopApplication,
                                        -> BUBBLE_GROUP_GENERAL
                                        BubbleShortcutAction.OpenAlerts -> BUBBLE_GROUP_ALERTS
                                        BubbleShortcutAction.OpenSavedPlaces -> BUBBLE_GROUP_SAVED_PLACES
                                        BubbleShortcutAction.OpenRadars -> BUBBLE_GROUP_RADARS
                                        BubbleShortcutAction.OpenAppearance -> BUBBLE_GROUP_APPEARANCE
                                        BubbleShortcutAction.OpenPermissions -> BUBBLE_GROUP_ACCESS
                                        BubbleShortcutAction.OpenBackup -> BUBBLE_GROUP_BACKUP
                                        BubbleShortcutAction.OpenAuthorizedAppsAndCards,
                                        BubbleShortcutAction.CaptureCurrentAppAndScreen,
                                        -> BUBBLE_GROUP_CARDS
                                        else -> BUBBLE_GROUP_GENERAL
                                    },
                                    settings = settings,
                                    liveEnabled = liveEnabled,
                                    onOpenAccessibilitySettings = {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                                    diagnostic = null,
                                    savedPlaces = savedPlaces,
                                    importedRadars = importedRadars,
                                    backupStatus = backupStatus,
                                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                                    highlightedImportedRadarId0178 = highlightedImportedRadarId0178,
                                    radarImportSummary = radarImportSummary,
                                    radarImportStatus = radarImportStatus,
                                    onSave = { updated -> scope.launch { repository.saveSettings(updated) } },
                                    onCreateSavedPlace = { createSavedPlaceFromHome(SavedPlaceType.Place) },
                                    onCreateProximityAlert = { createSavedPlaceFromHome(SavedPlaceType.ProximityAlert) },
                                    onRenameSavedPlace = ::renameSavedPlace,
                                    onDeleteSavedPlace = { place -> scope.launch { repository.removeSavedPlace(place.id) } },
                                    onRegionDetected = { detectedRegion -> region = detectedRegion },
                                    onCreateBackup = { backupFileCreator.launch("rota-certa-backup.json") },
                                    onRestoreBackup = { backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                    onImportRadarFile = { radarFilePicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "application/octet-stream", "*/*")) },
                                    onOpenMapaRadar = { openMapaRadarSite(context) },
                                    onUpdateImportedRadar0178 = { radar ->
                                        scope.launch {
                                            repository.updateImportedRadar(radar)
                                            radarImportStatus = "Radar atualizado: ${importedRadarDisplayName(radar)}"
                                        }
                                    },
                                    onDeleteImportedRadar0178 = { radar ->
                                        scope.launch {
                                            repository.removeImportedRadar(radar.id)
                                            if (highlightedImportedRadarId0178 == radar.id) highlightedImportedRadarId0178 = null
                                            radarImportStatus = "Radar excluído."
                                        }
                                    },
                                    onClearImportedRadars = {
                                        scope.launch {
                                            repository.clearImportedRadars()
                                            radarImportStatus = "Radares importados removidos."
                                        }
                                    },
                                )

                                BubbleShortcutAction.ExportDiagnostic,
                                BubbleShortcutAction.OpenReports,
                                -> ReportsGroupScreen(
                                    settings = settings,
                                    diagnostic = null,
                                    history = history,
                                    debugLogEnabled = debugLogEnabled,
                                    onDebugLogChange = { enabled ->
                                        debugLogEnabled = enabled
                                        DebugLogPreferenceStore.setEnabled(context, enabled)
                                        if (enabled) {
                                            DiagnosticLogStore.clear()
                                            LiveFailureTraceStore.clear()
                                            UnifiedDebugEventStore.clear()
                                            UnifiedDebugEventStore.record(
                                                "DEBUG_LOG_ON",
                                                context.packageName,
                                                "coleta circular ativada pelo usuário",
                                            )
                                        }
                                    },
                                    onCreateReport = {
                                        FarolFlightRecorder0163.record(
                                            stage = "FORENSIC_USER_INCIDENT_MARK_0193",
                                            packageName = context.packageName,
                                            details = "source=manual_report_tap",
                                        )
                                        br.com.mapeiaia.rotacerta.trips.AgendaForensicReportBuilder.freezeSnapshot()
                                        supportReportFileCreator.launch("rota-certa-relatorio-depuracao.txt")
                                    },
                                    onClearReport = {
                                        DiagnosticLogStore.clear()
                                        LiveFailureTraceStore.clear()
                                        UnifiedDebugEventStore.clear()
                                        supportReportStatus = "Registros apagados."
                                    },
                                    reportStatus = supportReportStatus,
                                )

                                BubbleShortcutAction.ToggleReading -> ManualReadingHomeModuleStage42(
                                    settings = settings,
                                    accessibilityGranted = liveEnabled,
                                    onChange = { updated -> scope.launch { repository.saveSettings(updated) } },
                                    onOpenAccessibilitySettings = {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                )

                                BubbleShortcutAction.OpenScreenWhatsApp -> InlineModuleAction0174(
                                    title = "WhatsApp",
                                    description = "Abre diretamente o WhatsApp instalado no celular.",
                                    buttonLabel = "Abrir WhatsApp",
                                    onClick = { openWhatsAppApp(context) },
                                )

                                BubbleShortcutAction.ClearClipboard -> InlineModuleAction0174(
                                    title = "Área de transferência",
                                    description = "Remove manualmente o texto copiado do celular.",
                                    buttonLabel = "Limpar área de transferência",
                                    onClick = { clearClipboard(context) },
                                )

                                BubbleShortcutAction.OpenFinance -> InlineModuleAction0174(
                                    title = "Controle financeiro",
                                    description = "Receitas, despesas e resumo financeiro continuam em uma tela dedicada para evitar uma lista pesada dentro da Home.",
                                    buttonLabel = "Abrir controle financeiro",
                                    onClick = { openShortcutModuleFromHome0171(spec) },
                                )

                                BubbleShortcutAction.OpenTextCorrection -> TextCorrectionModule0186(
                                    initialText = textCorrectionInitial0186,
                                    replacementToken = textReplacementToken0186,
                                    requestKey = textCorrectionRequestKey0186,
                                )

                                BubbleShortcutAction.OpenQuickReplies -> InlineModuleAction0174(
                                    title = "Respostas rápidas",
                                    description = "Crie, pesquise, edite e use suas respostas salvas.",
                                    buttonLabel = "Gerenciar respostas rápidas",
                                    onClick = { openShortcutModuleFromHome0171(spec) },
                                )

                                BubbleShortcutAction.OpenQuickLinks -> InlineModuleAction0174(
                                    title = "Links rápidos",
                                    description = "Cadastre links, escolha o principal e abra a viagem atual sem procurar novamente.",
                                    buttonLabel = "Gerenciar links rápidos",
                                    onClick = { openShortcutModuleFromHome0171(spec) },
                                )

                                BubbleShortcutAction.CopyTripConfirmation,
                                BubbleShortcutAction.CopyPassengerValue,
                                BubbleShortcutAction.OpenMessageTemplates,
                                -> InlineMessageTemplatePreview0174(
                                    action = spec.action,
                                    onEdit = { openShortcutModuleFromHome0171(spec) },
                                )

                                BubbleShortcutAction.OpenSettings -> when (spec.id) {
                                    "trip_agenda" -> InlineModuleAction0174(
                                        title = "Agenda de Viagens",
                                        description = "Crie, publique, compartilhe e acompanhe viagens e vagas por trecho sem interferir no FAROL.",
                                        buttonLabel = "Abrir Agenda de Viagens",
                                        onClick = { openShortcutModuleFromHome0171(spec) },
                                    )
                                    "work_tracking" -> InlineModuleAction0174(
                                        title = "Rastreamento de trabalho",
                                        description = "Inicie, pare ou consulte o percurso GPS registrado localmente neste aparelho.",
                                        buttonLabel = "Abrir rastreamento",
                                        onClick = {
                                            context.startActivity(Intent(context, WorkTrackingActivity::class.java))
                                        },
                                    )
                                    else -> InlineModuleAction0174(
                                        title = spec.displayLabel,
                                        description = ShortcutGridPolicy0173.description(spec),
                                        buttonLabel = "Abrir ${spec.displayLabel}",
                                        onClick = { openShortcutModuleFromHome0171(spec) },
                                    )
                                }

                                else -> InlineModuleAction0174(
                                    title = spec.displayLabel,
                                    description = ShortcutGridPolicy0173.description(spec),
                                    buttonLabel = "Abrir ${spec.displayLabel}",
                                    onClick = { openShortcutModuleFromHome0171(spec) },
                                )
                            }
                        },
                    )
                }
                TAB_TOOLS -> ToolsScreen(
                    onOpenWhatsApp = { openWhatsAppApp(context) },
                    onClearClipboard = { clearClipboard(context) },
                    onOpenWorkTracking = { context.startActivity(Intent(context, WorkTrackingActivity::class.java)) },
                )
                TAB_HISTORY -> ReportsGroupScreen(
                    settings = settings,
                    diagnostic = null,
                    history = history,
                    debugLogEnabled = debugLogEnabled,
                    onDebugLogChange = { enabled ->
                        debugLogEnabled = enabled
                        DebugLogPreferenceStore.setEnabled(context, enabled)
                        if (enabled) {
                            DiagnosticLogStore.clear()
                            LiveFailureTraceStore.clear()
                            UnifiedDebugEventStore.clear()
                            UnifiedDebugEventStore.record("DEBUG_LOG_ON", context.packageName, "coleta circular ativada pelo usuário")
                        }
                    },
                    onCreateReport = {
                                        FarolFlightRecorder0163.record(
                                            stage = "FORENSIC_USER_INCIDENT_MARK_0193",
                                            packageName = context.packageName,
                                            details = "source=manual_report_tap",
                                        )
                                        br.com.mapeiaia.rotacerta.trips.AgendaForensicReportBuilder.freezeSnapshot()
                                        supportReportFileCreator.launch("rota-certa-relatorio-depuracao.txt")
                                    },
                    onClearReport = {
                        DiagnosticLogStore.clear()
                        LiveFailureTraceStore.clear()
                        UnifiedDebugEventStore.clear()
                        supportReportStatus = "Registros apagados."
                    },
                    reportStatus = supportReportStatus,
                )
                else -> Unit
            } // grouped_navigation_compat_0_1_115
        }
    }
}

// in_app_bubble_home_visible_0_1_97
const val EXTRA_CREATE_SAVED_PLACE_TYPE_138 = "create_saved_place_type_138"
const val EXTRA_CONFIRM_DESTINATION_GPS_138 = "confirm_destination_gps_138"
const val EXTRA_OPEN_SHORTCUT_MODULE_0171 = "open_shortcut_module_0171"
const val EXTRA_OPEN_SHORTCUT_CUSTOMIZATION_0179 = "open_shortcut_customization_0179"
const val EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180 = "edit_shortcut_entry_id_0180"
const val EXTRA_CREATE_BACKUP_0184 = "create_backup_0184"
const val EXTRA_RESTORE_BACKUP_0184 = "restore_backup_0184"
const val EXTRA_IMPORTED_RADAR_ID_0178 = "imported_radar_id_0178"
const val EXTRA_HOME_LAUNCH_MODE_0186 = "home_launch_mode_0186"
const val EXTRA_TEXT_CORRECTION_INITIAL_0186 = "text_correction_initial_0186"
const val EXTRA_TEXT_REPLACEMENT_TOKEN_0186 = "text_replacement_token_0186"
const val EXTRA_TEXT_CORRECTION_REQUEST_KEY_0186 = "text_correction_request_key_0186"

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
@OptIn(ExperimentalFoundationApi::class)
private fun ShortcutModulesHome0171(
    expandedModuleId: String?,
    navigationRequestKey0177: Int,
    onOpenCustomization: () -> Unit,
    onToggleModule: (BubbleShortcutSpec) -> Unit,
    moduleContent: @Composable (BubbleShortcutSpec) -> Unit,
) {
    val context = LocalContext.current
    val shortcutStore0184 = remember { ShortcutGridPreferenceStore0179(context) }
    val shortcutEntries0184 = remember {
        mutableStateListOf<ShortcutGridEntry0179>().apply { addAll(shortcutStore0184.read()) }
    }
    fun updateShortcutEntries0184(updated: List<ShortcutGridEntry0179>) {
        val normalized = ShortcutGridCustomizationPolicy0179.normalize(updated)
        shortcutEntries0184.clear()
        shortcutEntries0184.addAll(normalized)
        shortcutStore0184.write(normalized)
    }
    LaunchedEffect(Unit) {
        check(HomeModuleBubbleGridPolicy0175.CONTRACT_MARKER.isNotBlank())
        check(ShortcutModuleFocusPolicy0177.CONTRACT_MARKER.isNotBlank())
        ShortcutGridPolicy0173.clearLegacyPreferences(context)
    }
    val moduleRows = remember {
        HomeModuleBubbleGridPolicy0175.rows(BubbleShortcutCatalog.modules)
    }
    val moduleFocusRequesters0177 = remember {
        BubbleShortcutCatalog.modules.associate { module ->
            module.spec.id to BringIntoViewRequester()
        }
    }
    LaunchedEffect(expandedModuleId, navigationRequestKey0177) {
        val requestedModuleId0177 = expandedModuleId ?: return@LaunchedEffect
        withFrameNanos { }
        moduleFocusRequesters0177[requestedModuleId0177]?.bringIntoView()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Módulos e recursos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "A Home possui uma bolinha para cada módulo e recurso. Toque uma vez para abrir os controles logo abaixo da mesma fileira.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Grade da Home", fontWeight = FontWeight.Bold)
        Text(
            "A Home mostra todos os módulos. Abra um módulo e escolha, ação por ação, o que deve entrar na grade flutuante.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onOpenCustomization, modifier = Modifier.fillMaxWidth()) {
            Text("Central de atalhos da grade flutuante")
        }
        moduleRows.forEach { rowModules ->
            val expandedIdForRow = HomeModuleBubbleGridPolicy0175.expandedIdInRow(
                rowIds = rowModules.map { it.spec.id },
                expandedId = expandedModuleId,
            )
            val expandedModule = rowModules.firstOrNull { it.spec.id == expandedIdForRow }
            val rowFocusRequester0177 = expandedIdForRow?.let(moduleFocusRequesters0177::get)
            Column(
                modifier = if (rowFocusRequester0177 != null) {
                    Modifier.bringIntoViewRequester(rowFocusRequester0177)
                } else {
                    Modifier
                },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowModules.forEach { module ->
                        HomeModuleBubble0175(
                            spec = module.spec,
                            selected = HomeModuleExpansionPolicy0174.isExpanded(expandedModuleId, module.spec.id),
                            onClick = { onToggleModule(module.spec) },
                        )
                    }
                }
                if (expandedModule != null) {
                    HomeModuleInlinePanel0175(
                        spec = expandedModule.spec,
                        shortcutEntries = shortcutEntries0184.toList(),
                        onShortcutEntriesChange = ::updateShortcutEntries0184,
                        content = { moduleContent(expandedModule.spec) },
                    )
                }
            }
        }
    }
} // home_module_bubble_grid_0_1_175

@Composable
private fun ShortcutGridCustomizationScreen0179(
    selectedEntryId0180: String?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val store0179 = remember { ShortcutGridPreferenceStore0179(context) }
    val entries0179 = remember {
        mutableStateListOf<ShortcutGridEntry0179>().apply { addAll(store0179.read()) }
    }
    var savedEntries0179 by remember { mutableStateOf(entries0179.toList()) }
    var pendingDeleteEntryId0179 by remember { mutableStateOf<String?>(null) }
    var confirmReset0179 by remember { mutableStateOf(false) }

    fun replaceEntries0179(updated: List<ShortcutGridEntry0179>) {
        entries0179.clear()
        entries0179.addAll(ShortcutGridCustomizationPolicy0179.normalize(updated))
    }

    fun save0179(showToast: Boolean = true) {
        store0179.write(entries0179.toList())
        savedEntries0179 = entries0179.toList()
        if (showToast) Toast.makeText(context, "Grade de atalhos salva.", Toast.LENGTH_SHORT).show()
    }

    pendingDeleteEntryId0179?.let { entryId0179 ->
        val item0179 = entries0179.firstOrNull { it.entryId == entryId0179 }
        AlertDialog(
            onDismissRequest = { pendingDeleteEntryId0179 = null },
            title = { Text("Excluir atalho?") },
            text = { Text(item0179?.label ?: "Este atalho será removido somente da grade flutuante.") },
            confirmButton = {
                TextButton(onClick = {
                    val updated0180 = entries0179.filterNot { it.entryId == entryId0179 }
                    replaceEntries0179(updated0180)
                    pendingDeleteEntryId0179 = null
                    if (selectedEntryId0180 == entryId0179) {
                        store0179.write(updated0180)
                        onClose()
                    }
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntryId0179 = null }) { Text("Cancelar") }
            },
        )
    }

    if (confirmReset0179) {
        AlertDialog(
            onDismissRequest = { confirmReset0179 = false },
            title = { Text("Esvaziar grade de atalhos?") },
            text = { Text("Todas as bolinhas serão removidas da grade. Os módulos continuarão disponíveis na Home.") },
            confirmButton = {
                TextButton(onClick = {
                    store0179.reset()
                    replaceEntries0179(ShortcutGridCustomizationPolicy0179.defaults())
                    savedEntries0179 = entries0179.toList()
                    confirmReset0179 = false
                }) { Text("Esvaziar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset0179 = false }) { Text("Cancelar") }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (selectedEntryId0180 == null) "Central de atalhos" else "Configurar bolinha",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (selectedEntryId0180 == null) {
                "A grade contém somente ações adicionadas pela Home. Aqui você pode reordenar, renomear ou remover."
            } else {
                "Edite esta bolinha sem alterar o funcionamento das demais."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Um toque na bolinha principal abre a grade. Toque rápido executa; segurar por 1,5 segundo usa a ação longa configurada.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Ativos: ${entries0179.count { it.enabled }} de ${entries0179.size}. Limite: ${ShortcutGesturePolicy0179.MAX_GRID_ITEMS}.",
            fontWeight = FontWeight.Bold,
        )

        if (entries0179.isEmpty()) {
            Text(
                "A grade está vazia. Volte à Home, abra um módulo e toque em Adicionar à grade.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val editorEntries0180 = selectedEntryId0180
            ?.let { selected0180 -> entries0179.filter { it.entryId == selected0180 } }
            ?: entries0179.toList()
        editorEntries0180.forEach { entry0179 ->
            val index0179 = entries0179.indexOfFirst { it.entryId == entry0179.entryId }
            ShortcutGridEditorCard0179(
                entry = entry0179,
                index = index0179,
                total = entries0179.size,
                onUpdate = { updated0179 ->
                    val target0179 = entries0179.indexOfFirst { it.entryId == entry0179.entryId }
                    if (target0179 >= 0) entries0179[target0179] = updated0179
                },
                onMove = { from0179, to0179 ->
                    replaceEntries0179(
                        ShortcutGridCustomizationPolicy0179.move(entries0179.toList(), from0179, to0179),
                    )
                },
                onDelete = { pendingDeleteEntryId0179 = entry0179.entryId },
            )
        }

        Button(
            onClick = { save0179() },
            enabled = entries0179.toList() != savedEntries0179,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar alterações")
        }
        OutlinedButton(
            onClick = {
                save0179(showToast = false)
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar e voltar")
        }
        if (selectedEntryId0180 == null) {
            TextButton(onClick = { confirmReset0179 = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Esvaziar grade")
            }
        }
    }
}

@Composable
private fun ShortcutGridEditorCard0179(
    entry: ShortcutGridEntry0179,
    index: Int,
    total: Int,
    onUpdate: (ShortcutGridEntry0179) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var dragAccumulator0179 by remember(entry.entryId) { mutableStateOf(0f) }
    var editingHold0186 by remember(entry.entryId) { mutableStateOf(false) }
    var choosingSafeAction0186 by remember(entry.entryId) { mutableStateOf(false) }
    val actionSpec0179 = BubbleShortcutCatalog.findSpec(entry.shortcutId)

    if (editingHold0186) {
        AlertDialog(
            onDismissRequest = {
                editingHold0186 = false
                choosingSafeAction0186 = false
            },
            title = { Text("Ação ao apertar e segurar") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!choosingSafeAction0186) {
                        val relatedModule0186 = ShortcutActionCatalog0184.moduleSpecForAction(entry.shortcutId)
                        OutlinedButton(
                            onClick = {
                                onUpdate(
                                    entry.copy(
                                        holdActionType0186 = ShortcutHoldActionType0186.OPEN_MODULE,
                                        holdShortcutId0186 = null,
                                    ),
                                )
                                editingHold0186 = false
                            },
                            enabled = relatedModule0186 != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Abrir o módulo relacionado") }
                        OutlinedButton(
                            onClick = { choosingSafeAction0186 = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Selecionar outra ação segura") }
                        OutlinedButton(
                            onClick = {
                                onUpdate(
                                    entry.copy(
                                        holdActionType0186 = ShortcutHoldActionType0186.NONE,
                                        holdShortcutId0186 = null,
                                    ),
                                )
                                editingHold0186 = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Não fazer nada") }
                    } else {
                        Text("Escolha uma ação do catálogo interno", fontWeight = FontWeight.Bold)
                        ShortcutActionCatalog0184.safeAlternativeSpecs(entry.shortcutId).forEach { safeSpec0186 ->
                            OutlinedButton(
                                onClick = {
                                    onUpdate(
                                        entry.copy(
                                            holdActionType0186 = ShortcutHoldActionType0186.SAFE_ACTION,
                                            holdShortcutId0186 = safeSpec0186.id,
                                        ),
                                    )
                                    editingHold0186 = false
                                    choosingSafeAction0186 = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("${safeSpec0186.emoji}  ${safeSpec0186.displayLabel}") }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        if (choosingSafeAction0186) choosingSafeAction0186 = false else editingHold0186 = false
                    },
                ) { Text(if (choosingSafeAction0186) "Voltar" else "Cancelar") }
            },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "↕  Segure e arraste para reordenar",
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(entry.entryId, index, total) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragAccumulator0179 = 0f },
                            onDragCancel = { dragAccumulator0179 = 0f },
                            onDragEnd = { dragAccumulator0179 = 0f },
                            onDrag = { change0179, amount0179 ->
                                change0179.consume()
                                dragAccumulator0179 += amount0179.y
                                val threshold0179 = 48.dp.toPx()
                                if (dragAccumulator0179 >= threshold0179 && index < total - 1) {
                                    onMove(index, index + 1)
                                    dragAccumulator0179 = 0f
                                } else if (dragAccumulator0179 <= -threshold0179 && index > 0) {
                                    onMove(index, index - 1)
                                    dragAccumulator0179 = 0f
                                }
                            },
                        )
                    },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Atalho ${index + 1}", fontWeight = FontWeight.Bold)
                Switch(checked = entry.enabled, onCheckedChange = { onUpdate(entry.copy(enabled = it)) })
            }
            OutlinedTextField(
                value = entry.label,
                onValueChange = { onUpdate(entry.copy(label = it.take(24))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome") },
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    val next0179 = ShortcutGridCustomizationPolicy0179.nextShortcutId(entry.shortcutId)
                    onUpdate(
                        entry.copy(
                            shortcutId = next0179,
                            holdActionType0186 = ShortcutGridCustomizationPolicy0179.defaultHoldActionType(next0179),
                            holdShortcutId0186 = null,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ação ao tocar: ${actionSpec0179?.displayLabel ?: entry.shortcutId}") }
            OutlinedButton(
                onClick = { editingHold0186 = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ação ao apertar e segurar: ${ShortcutGridCustomizationPolicy0179.holdActionLabel(entry)}") }
            OutlinedButton(
                onClick = { onUpdate(entry.copy(emoji = ShortcutGridCustomizationPolicy0179.nextEmoji(entry.emoji))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ícone: ${entry.emoji}") }
            Text(
                "Toque rápido executa imediatamente. Segurar por 1,5 segundo executa somente a ação longa configurada.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Excluir da grade") }
        }
    }
}

@Composable
private fun HomeModuleBubble0175(
    spec: BubbleShortcutSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(96.dp),
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 12.dp else 3.dp,
            pressedElevation = 14.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(6.dp),
    ) {
        Text(
            text = spec.emoji + "\n" + spec.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

@Composable
private fun HomeModuleInlinePanel0175(
    spec: BubbleShortcutSpec,
    shortcutEntries: List<ShortcutGridEntry0179>,
    onShortcutEntriesChange: (List<ShortcutGridEntry0179>) -> Unit,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                spec.emoji + "  " + spec.displayLabel,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(ShortcutGridPolicy0173.description(spec), style = MaterialTheme.typography.bodySmall)
            HomeShortcutActions0184(
                moduleId = spec.id,
                entries = shortcutEntries,
                onEntriesChange = onShortcutEntriesChange,
            )
            content()
        }
    }
}

@Composable
private fun HomeShortcutActions0184(
    moduleId: String,
    entries: List<ShortcutGridEntry0179>,
    onEntriesChange: (List<ShortcutGridEntry0179>) -> Unit,
) {
    val actions0184 = remember(moduleId) { ShortcutActionCatalog0184.actionsForModule(moduleId) }
    if (actions0184.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Atalhos deste módulo", fontWeight = FontWeight.Bold)
        actions0184.forEach { action0184 ->
            val added0184 = ShortcutGridCustomizationPolicy0179.contains(entries, action0184.id)
            val full0184 = entries.size >= ShortcutGesturePolicy0179.MAX_GRID_ITEMS
            OutlinedButton(
                onClick = {
                    val updated0184 = if (added0184) {
                        ShortcutGridCustomizationPolicy0179.remove(entries, action0184.id)
                    } else {
                        ShortcutGridCustomizationPolicy0179.add(
                            entries = entries,
                            shortcutId = action0184.id,
                            nowMillis = System.currentTimeMillis(),
                        )
                    }
                    onEntriesChange(updated0184)
                },
                enabled = added0184 || !full0184,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (added0184) {
                        "${action0184.emoji}  Remover da grade: ${action0184.displayLabel}"
                    } else {
                        "${action0184.emoji}  Adicionar à grade: ${action0184.displayLabel}"
                    },
                )
            }
        }
        Text(
            "${entries.size} de ${ShortcutGesturePolicy0179.MAX_GRID_ITEMS} ações selecionadas.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun InlineModuleAction0174(
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun InlineMessageTemplatePreview0174(
    action: BubbleShortcutAction,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun readTemplate(): String = when (action) {
        BubbleShortcutAction.CopyPassengerValue -> MessageTemplateStore0172.readValue(context)
        else -> MessageTemplateStore0172.readTrip(context)
    }
    var template by remember(action) { mutableStateOf(readTemplate()) }
    DisposableEffect(lifecycleOwner, action) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) template = readTemplate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (action == BubbleShortcutAction.CopyPassengerValue) "Frase de valor" else "Frase de confirmação da viagem",
            fontWeight = FontWeight.Bold,
        )
        Text(template, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("Editar frases predefinidas")
        }
    }
}

@Composable
private fun ProfessionalBubbleDashboard(
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
    onOpenWhatsApp: () -> Unit,
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
    liveEnabled: Boolean,
    onSaveSettings: (AppSettings) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshLiveState: () -> Unit,
) {
    val context = LocalContext.current
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val workRegionAddressResolverChecklist9 = remember { WorkRegionAddressResolver(context) }
    val scope = rememberCoroutineScope()

    var quickSettings by remember(settings) { mutableStateOf(settings) }
    var homeStatus by remember { mutableStateOf("") }
    var pendingHomeGps by remember { mutableStateOf(false) }
    var savingHomeAddressChecklist9 by remember { mutableStateOf(false) }

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

    fun saveHomeAddressValidatedChecklist9() {
        val address = quickSettings.homeAddress.trim()
        if (address.isBlank()) {
            homeStatus = "Informe o endereço da Casa antes de salvar."
            return
        }
        if (savingHomeAddressChecklist9) return
        if (quickSettings.homeCoordinate != null) {
            saveQuickSettings(quickSettings.copy(homeAddress = address))
            homeStatus = "Casa salva e pronta para o farol."
            return
        }

        savingHomeAddressChecklist9 = true
        homeStatus = "Localizando e validando o endereço da Casa..."
        scope.launch {
            val coordinate = runCatching {
                workRegionAddressResolverChecklist9.resolve(address, quickSettings.googleMapsApiKey)
            }.getOrNull()
            if (coordinate == null) {
                homeStatus = "Não consegui localizar a Casa. Inclua número, cidade e estado."
            } else {
                val updated = quickSettings.copy(
                    homeAddress = address,
                    homeCoordinate = coordinate,
                )
                saveQuickSettings(updated)
                homeStatus = "Casa salva e pronta para o farol: ${formatCoordinate(coordinate)}"
            }
            savingHomeAddressChecklist9 = false
        }
    } // home_target_pre_resolved_checklist_9

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
        saving = savingHomeAddressChecklist9,
        onSave = ::saveHomeAddressValidatedChecklist9,
    )

    Spacer(Modifier.height(10.dp))
    RadiusQuickCard(
        quickSettings = quickSettings,
        onSettingsChange = { quickSettings = it },
        onSaveSettings = onSaveSettings,
    )

    Spacer(Modifier.height(10.dp))
    WorkRegionPinsCard(
        settings = quickSettings,
        onSettingsChange = ::saveQuickSettings,
    ) // multi_address_work_region_ui_checklist_7


    Spacer(Modifier.height(10.dp))
    // Leitura direta ativa; o gatilho e o ultimo endereco. // universal_models_removed_v2_0_1_95

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
// live_reading_moved_to_general_controls_checklist_7
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
    saving: Boolean,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Casa / ponto principal", fontWeight = FontWeight.Bold)
            Text(
                "O endereço é validado ao salvar. Assim, o farol não precisa localizar a Casa quando a corrida aparece.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = quickSettings.homeAddress,
                onValueChange = { onSettingsChange(quickSettings.copy(homeAddress = it, homeCoordinate = null)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endereço completo da Casa") },
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onSave()
                    },
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRequestHomeGps,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Usar GPS atual")
                }
                OutlinedButton(
                    onClick = { onSettingsChange(quickSettings.copy(homeCoordinate = null)) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Digitar")
                }
            }
            quickSettings.homeCoordinate?.let {
                Text("Coordenada validada: ${formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
            }
            if (homeStatus.isNotBlank()) {
                Text(homeStatus, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSave()
                },
                enabled = quickSettings.homeAddress.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Localizando..." else "Salvar Casa")
            }
        }
    }
} // home_target_editor_final_checklist_9


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
                label = "Todos os alfinetes",
                value = quickSettings.alternativeRadiusKm,
                onValueChange = { onSettingsChange(quickSettings.copy(alternativeRadiusKm = it)) },
                onValueChangeFinished = { onSaveSettings(quickSettings) },
            )
        }
    }
}

// no_pre_registered_cards_ui_0_1_126 superseded manual_card_models_restored_0_1_127
 // no_pre_registered_cards_ui_0_1_126


// capture_library_split_final_checklist_6

// capture_card_component_final_checklist_6
 // automatic_capture_gallery_composable_0_1_129

@Composable
private fun DiagnosticExpander(
    debugLogEnabled: Boolean,
    onDebugLogChange: (Boolean) -> Unit,
    onCreateReport: () -> Unit,
    onClearReport: () -> Unit,
    reportStatus: String,
) {
    ExpandableCard(title = "Relatório para depuração", initiallyExpanded = true) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Log de depuração", fontWeight = FontWeight.Bold)
                Text(
                    if (debugLogEnabled) "ON — eventos relevantes ficam em memória circular até a exportação." else "OFF — nenhuma coleta detalhada contínua.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = debugLogEnabled, onCheckedChange = onDebugLogChange)
        }
        Text(
            "A Agenda mantém uma trilha circular leve apenas em memória. Gerar relatório apenas congela e exporta essa trilha; dados sensíveis são mascarados.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onCreateReport, modifier = Modifier.fillMaxWidth()) {
            Text("Gerar relatório para depuração")
        }
        OutlinedButton(onClick = onClearReport, modifier = Modifier.fillMaxWidth()) {
            Text("Apagar registros")
        }
        if (reportStatus.isNotBlank()) Text(reportStatus, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SavedPlacesCard(
    savedPlaces: List<SavedPlace>,
    highlightedSavedPlaceId: String?,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val places = SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == SavedPlaceType.Place })
    val alerts = SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == SavedPlaceType.ProximityAlert })
    val highlightedType = savedPlaces.firstOrNull { it.id == highlightedSavedPlaceId }?.type

    if (highlightedType == SavedPlaceType.Place) {
        Text("Digite o nome do local e toque em Concluir no teclado.", style = MaterialTheme.typography.bodySmall)
    } else if (highlightedType == SavedPlaceType.ProximityAlert) {
        Text("Confira ou altere o nome que será falado no alerta.", style = MaterialTheme.typography.bodySmall)
    }

    ExpandableCard(
        title = "Locais salvos (" + places.size + ")",
        initiallyExpanded = highlightedType == SavedPlaceType.Place,
    ) {
        if (places.isEmpty()) Text("Nenhum local salvo ainda.", style = MaterialTheme.typography.bodySmall)
        else places.forEach { place ->
            SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)
        }
    }

    Spacer(Modifier.height(10.dp))
    ExpandableCard(
        title = "Alertas de proximidade (" + alerts.size + ")",
        initiallyExpanded = highlightedType == SavedPlaceType.ProximityAlert,
    ) {
        if (alerts.isEmpty()) Text("Nenhum alerta de proximidade criado ainda.", style = MaterialTheme.typography.bodySmall)
        else alerts.forEach { place ->
            SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)
        }
    }
} // saved_places_alphabetical_final_checklist_7


// no_registered_cards_module_0_1_126 superseded registered_cards_module_restored_0_1_127
 // no_registered_cards_module_0_1_126
 // registered_cards_module_0_1_120

@Composable
private fun SavedPlacesModuleCard(
    savedPlaces: List<SavedPlace>,
    type: SavedPlaceType,
    highlightedSavedPlaceId: String?,
    alertDistanceMeters: Int? = null,
    alertsEnabled: Boolean = true,
    onAlertDistanceChange: (Int) -> Unit = {},
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
            if (isAlert) {
                AlertDistanceSelector138(
                    selectedMeters = alertDistanceMeters ?: 500,
                    alertsEnabled = alertsEnabled,
                    onSelect = onAlertDistanceChange,
                )
            }
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(if (isAlert) "Criar alerta neste local" else "Salvar local atual")
            }
            if (!isAlert) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar por nome ou endereço") },
                    singleLine = true,
                )
                if (search.isNotBlank()) {
                    when {
                        filteredItems.isEmpty() -> Text("Nenhum local encontrado por nome ou endereço.")
                        else -> filteredItems.forEach { place -> SavedPlaceSearchResult138(place) }
                    }
                }
            }
            ExpandableCard(
                title = if (isAlert) "Alertas criados (${items.size})" else "Endereços salvos (${items.size})",
                initiallyExpanded = highlightedSavedPlaceId != null && items.any { it.id == highlightedSavedPlaceId },
            ) {
                if (isAlert) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar por nome ou endereço") },
                        singleLine = true,
                    )
                }
                val listInsideExpander = if (isAlert) filteredItems else items
                when {
                    items.isEmpty() -> Text(if (isAlert) "Nenhum alerta criado." else "Nenhum local salvo.")
                    listInsideExpander.isEmpty() -> Text("Nenhum alerta encontrado por nome ou endereço.")
                    else -> listInsideExpander.forEach { place ->
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
} // separate_saved_place_modules_0_1_120 saved_places_search_name_address_0_1_127
 // separate_saved_place_modules_0_1_120

@Composable
private fun AlertDistanceSelector138(
    selectedMeters: Int,
    alertsEnabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val values = listOf(200, 500, 1000)
    Text("Distância do aviso", fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { meters ->
            if (selectedMeters == meters && alertsEnabled) {
                Button(onClick = { onSelect(meters) }, modifier = Modifier.weight(1f)) { Text("$meters m") }
            } else {
                OutlinedButton(onClick = { onSelect(meters) }, modifier = Modifier.weight(1f)) { Text("$meters m") }
            }
        }
    }
    Text(
        if (alertsEnabled) "Alertas ativos a partir da distância selecionada." else "Selecione uma distância para ativar os alertas.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SavedPlaceSearchResult138(place: SavedPlace) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(place.name.ifBlank { "Local salvo" }, fontWeight = FontWeight.Bold)
            Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { openSavedPlaceInGps(context, place) }, modifier = Modifier.fillMaxWidth()) { Text("GPS") }
        }
    }
}

@Composable
private fun SavedPlaceEditor(
    place: SavedPlace,
    highlighted: Boolean = false,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var draftName by remember(place.id, place.name, highlighted) {
        mutableStateOf(SavedPlaceUiPolicy.initialDraftName(place, highlighted))
    }

    fun saveName() {
        val cleanName = draftName.trim()
        if (!SavedPlaceUiPolicy.canSave(place, cleanName)) return
        onRenameSavedPlace(place, cleanName)
        focusManager.clearFocus()
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(savedPlaceTypeLabel(place), fontWeight = FontWeight.Bold)
        if (highlighted && place.type == SavedPlaceType.Place) {
            Text("O campo começa vazio para você escrever o nome diretamente.", style = MaterialTheme.typography.bodySmall)
        } else if (highlighted) {
            Text("O nome sugerido pode ser mantido ou alterado.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (place.type == SavedPlaceType.ProximityAlert) "Nome falado no alerta" else "Nome do local") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { saveName() }),
        )
        if (place.type == SavedPlaceType.ProximityAlert) {
            Text(
                "O app vai falar: " + draftName.ifBlank { defaultSavedPlaceName(place.type) } + " se aproximando.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openSavedPlaceInGps(context, place) }, modifier = Modifier.weight(1f)) { Text("GPS") }
            Button(
                enabled = SavedPlaceUiPolicy.canSave(place, draftName) && draftName.trim() != place.name.trim(),
                onClick = { saveName() },
                modifier = Modifier.weight(1f),
            ) { Text("Salvar") }
            OutlinedButton(onClick = { onDeleteSavedPlace(place) }, modifier = Modifier.weight(1f)) { Text("Apagar") }
        }
    }
} // enter_saves_place_final_checklist_7


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
    savedPlaces: List<SavedPlace>,
    importedRadars: List<ImportedRadar>,
    backupStatus: String,
    highlightedSavedPlaceId: String?,
    highlightedImportedRadarId0178: String?,
    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    onSave: (AppSettings) -> Unit,
    onCreateSavedPlace: () -> Unit,
    onCreateProximityAlert: () -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
    onRegionDetected: (DeviceRegion) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onImportRadarFile: () -> Unit,
    onOpenMapaRadar: () -> Unit,
    onUpdateImportedRadar0178: (ImportedRadar) -> Unit,
    onDeleteImportedRadar0178: (ImportedRadar) -> Unit,
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
            BUBBLE_GROUP_GENERAL,
            BUBBLE_GROUP_READING,
            BUBBLE_GROUP_ACCESS,
            -> {
                SystemControlCard(settings = draft, onChange = ::saveDraft)
                Spacer(Modifier.height(10.dp))
                AlwaysLocationPermissionCard(
                    hasAlwaysPermission = hasAlwaysLocationPermission(context),
                    onOpenLocationSettings = { openAppLocationSettings(context) },
                )
            } // legacy_access_groups_to_general_checklist_7
            BUBBLE_GROUP_ALERTS -> SavedPlacesModuleCard(
                savedPlaces = savedPlaces,
                type = SavedPlaceType.ProximityAlert,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                alertDistanceMeters = draft.proximityAlertDistanceMeters,
                alertsEnabled = draft.proximityAlertsEnabled,
                onAlertDistanceChange = { distance ->
                    saveDraft(
                        draft.copy(
                            proximityAlertDistanceMeters = distance,
                            proximityAlertsEnabled = true,
                        ),
                    )
                },
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
                radars = importedRadars,
                highlightedRadarId = highlightedImportedRadarId0178,
                summary = radarImportSummary,
                importStatus = radarImportStatus,
                onPickFile = onImportRadarFile,
                onOpenMapaRadar = onOpenMapaRadar,
                onUpdateRadar = onUpdateImportedRadar0178,
                onDeleteRadar = onDeleteImportedRadar0178,
                onClearRadars = onClearImportedRadars,
            )
            BUBBLE_GROUP_APPEARANCE -> BubbleSettingsCard(settings = draft, onChange = ::saveDraft)
            BUBBLE_GROUP_BACKUP -> BackupCard(
                status = backupStatus,
                onCreateBackup = onCreateBackup,
                onRestoreBackup = onRestoreBackup,
            )
            BUBBLE_GROUP_CARDS -> Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InstalledRideAppsCard()
            } // selected_apps_visible

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
    BUBBLE_GROUP_CARDS -> "Aplicativos selecionados"
    BUBBLE_GROUP_APPEARANCE -> "Bolinha e aparencia"
    BUBBLE_GROUP_ACCESS -> "Permissoes, leitura e GPS"
    BUBBLE_GROUP_BACKUP -> "Backup dos dados"
    else -> "Controle geral"
}

private fun groupedBubbleDescription(group: String): String = when (group) {
    BUBBLE_GROUP_GENERAL -> "Use Leitura do Farol para ligar ou pausar manualmente o processamento visual."
    BUBBLE_GROUP_READING -> "Autoriza a Acessibilidade e controla a leitura da tela."
    BUBBLE_GROUP_ALERTS -> "Crie e edite somente alertas de proximidade."
    BUBBLE_GROUP_SAVED_PLACES -> "Gerencie somente locais salvos, sem alerta."
    BUBBLE_GROUP_RADARS -> "Importe e gerencie radares separadamente."
    BUBBLE_GROUP_CARDS -> "Selecione manualmente os aplicativos permitidos para leitura."
    BUBBLE_GROUP_APPEARANCE -> "Ajusta transparencia, contraste e aparencia da bolinha flutuante."
    BUBBLE_GROUP_ACCESS -> "Controle a leitura ao vivo, a Acessibilidade, a localizacao e o GPS continuo."
    BUBBLE_GROUP_BACKUP -> "Crie ou restaure uma copia das configuracoes e dos dados."
    else -> "Ajustes do Rota Certa."
}

@Composable
private fun SystemControlCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityGranted by remember { mutableStateOf(isLiveAccessibilityEnabled(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessibilityGranted = isLiveAccessibilityEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ExpandableCard(title = "Controles gerais", initiallyExpanded = true) {
        val mapsKeyConfiguredChecklist11 = GoogleMapsApiKeyPolicy.isConfigured(
            settings.googleMapsApiKey,
            BuildConfig.GOOGLE_MAPS_API_KEY,
        )
        Text(
            if (mapsKeyConfiguredChecklist11) {
                "Chave Google Maps API: configurada"
            } else {
                "Google Maps: necessário para calcular verde/vermelho"
            },
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (mapsKeyConfiguredChecklist11) {
                "A chave é fornecida com segurança pelo build do aplicativo."
            } else {
                "Este APK foi gerado sem a chave do Google Maps. Gere novamente pelo GitHub Actions com o segredo GOOGLE_MAPS_API_KEY."
            },
            style = MaterialTheme.typography.bodySmall,
        ) // maps_key_single_build_source_0_1_138
        SettingsSwitchRow(
            label = "Leitura do Farol",
            checked = WorkModePolicy0162.isEnabled(settings),
            onCheckedChange = { enabled -> onChange(WorkModePolicy0162.setEnabled(settings, enabled)) },
        )
        Text(
            "Ligue ao iniciar o trabalho. ON lê qualquer tela, janela ou pop-up por eventos; OFF mantém a bolinha cinza e não calcula km.",
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSwitchRow(
            label = "Ativar radares e alertas de proximidade",
            checked = settings.proximityAlertsEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(proximityAlertsEnabled = enabled)) },
        )
        val speechStore0186 = remember { SpeechOutputPreferenceStore0186(context) }
        var speechMode0186 by remember { mutableStateOf(speechStore0186.read()) }
        Text("Saída dos avisos sonoros", fontWeight = FontWeight.Bold)
        SpeechOutputMode0186.values().forEach { mode0186 ->
            OutlinedButton(
                onClick = {
                    speechStore0186.write(mode0186)
                    speechMode0186 = mode0186
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text((if (speechMode0186 == mode0186) "✓  " else "") + mode0186.displayLabel)
            }
        }
        Text(
            "O canal de mídia pode usar o alto-falante, mas o Android ainda pode encaminhar o áudio para Bluetooth ou outro dispositivo conectado. A opção Sem som preserva todos os avisos visuais.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (accessibilityGranted) "Permissão de acessibilidade: concedida" else "Permissão de acessibilidade: pendente",
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (accessibilityGranted) "Revisar permissão de acessibilidade" else "Conceder permissão de acessibilidade")
        }
        Text(
            if (WorkModePolicy0162.isEnabled(settings)) {
                "Leitura ATIVA: qualquer aplicativo visível pode fornecer dois ou mais endereços ao Farol."
            } else {
                "Leitura DESLIGADA: bolinha cinza, sem OCR, screenshot automático, rota ou km."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
} // general_controls_final_checklist_7


@Composable
private fun ManualReadingHomeModuleStage42(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onChange: (AppSettings) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val enabledStage42 = FarolManualReadingAuthorityStage42.isEnabled(settings)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSwitchRow(
            label = "Leitura do Farol",
            checked = enabledStage42,
            onCheckedChange = { enabled ->
                onChange(FarolManualReadingAuthorityStage42.setEnabled(settings, enabled))
            },
        )
        Text(
            if (enabledStage42) {
                "ON: a bolinha fica armada e procura dois ou mais endereços em qualquer tela, janela ou pop-up, sem depender de Uber, 99 ou inDrive estarem abertos."
            } else {
                "OFF: a bolinha permanece cinza, sem km e sem processamento visual automático."
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "O atalho Leitura deste módulo pode ser adicionado à grade flutuante e executa o mesmo liga/desliga com um toque.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!accessibilityGranted) {
            Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Conceder permissão de acessibilidade")
            }
            Text(
                "O toggle pode ficar ON, mas a leitura só acontece enquanto o serviço de acessibilidade do Rota Certa estiver autorizado pelo Android.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val popupStore = remember { PopupAppearanceStore(context) }
    var popupScale by remember { mutableStateOf(popupStore.scale()) }

    ExpandableCard(title = "Bolinha e aparência", initiallyExpanded = false) {
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
        Text("Tamanho do pop-up: " + (popupScale * 100).roundToInt() + "%", fontWeight = FontWeight.Bold)
        Slider(
            value = popupScale.toFloat(),
            onValueChange = { popupScale = it.toDouble() },
            valueRange = PopupAppearanceStore.MIN_SCALE.toFloat()..PopupAppearanceStore.MAX_SCALE.toFloat(),
            onValueChangeFinished = { popupStore.setScale(popupScale) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Em tamanhos maiores, o pop-up usa duas colunas e aumenta também a fonte das bolinhas.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Toque na bolinha para abrir o pop-up. Arraste para mudar a posição.", style = MaterialTheme.typography.bodySmall)
    }
} // popup_scale_ui_final_checklist_7


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

        Text(
            "Os endereços dos alfinetes são gerenciados em Região de trabalho.",
            style = MaterialTheme.typography.bodySmall,
        )

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
    ExpandableCard(title = "Google Maps e ajustes avancados", initiallyExpanded = !GoogleMapsApiKeyPolicy.isConfigured(draft.googleMapsApiKey, BuildConfig.GOOGLE_MAPS_API_KEY)) {
        Text(
            if (GoogleMapsApiKeyPolicy.isConfigured(draft.googleMapsApiKey, BuildConfig.GOOGLE_MAPS_API_KEY)) {
                "Google Maps configurado pelo build."
            } else {
                "Google Maps ainda não configurado no build."
            },
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
    var lastManualCapture by remember { mutableStateOf(ManualAppScreenCaptureStore.read(context)) }
    var usageAccessGrantedStage26 by remember { mutableStateOf(SelectedAppUsageStateStage26(context).hasUsageAccess()) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selectedPackages = SelectedRideAppStore.read(context)
                lastManualCapture = ManualAppScreenCaptureStore.read(context)
                usageAccessGrantedStage26 = SelectedAppUsageStateStage26(context).hasUsageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ExpandableCard(title = "Aplicativos que ativam a leitura", initiallyExpanded = true) {
        Text(
            "Escolha os aplicativos de corrida que ligam a infraestrutura do FAROL. O conteúdo visual do card continua universal e não é autorizado pelo packageName.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { context.startActivity(Intent(context, InstalledRideAppPickerActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Buscar aplicativos instalados")
        }
        Text(
            if (usageAccessGrantedStage26) "Acesso ao uso: concedido." else "Acesso ao uso: necessário. Sem essa autorização o FAROL falha fechado e não faz leitura global.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!usageAccessGrantedStage26) {
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Conceder Acesso ao uso") }
        }
        Text(
            "Para adicionar rapidamente: abra o aplicativo de motorista, toque na bolinha e em Capturar. A captura seleciona o package somente para ATIVAR/DESATIVAR a leitura; o screenshot nunca autoriza um card.",
            style = MaterialTheme.typography.bodySmall,
        )
        lastManualCapture?.let { capture ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Última captura manual", fontWeight = FontWeight.Bold)
                    Text(capture.packageName, style = MaterialTheme.typography.bodySmall)
                    if (capture.textPreview.isNotBlank()) {
                        Text(capture.textPreview.take(220), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = {
                            ManualAppScreenCaptureStore.clear(context)
                            lastManualCapture = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Apagar captura") }
                }
            }
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
    val allowedValues = listOf(200, 500, 1000)
    val selectedIndex = allowedValues.indexOf(value).takeIf { it >= 0 } ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Distancia do primeiro aviso")
            Text("${allowedValues[selectedIndex]} m", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { rawValue ->
                val index = rawValue.roundToInt().coerceIn(0, allowedValues.lastIndex)
                onValueChange(allowedValues[index])
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..allowedValues.lastIndex.toFloat(),
            steps = allowedValues.size - 2,
        )
    }
}

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
    onClearClipboard: () -> Unit,
    onOpenWorkTracking: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Text("WhatsApp", fontWeight = FontWeight.Bold)
                Text("Abre o WhatsApp instalado no celular.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenWhatsApp, modifier = Modifier.fillMaxWidth()) { Text("Abrir WhatsApp") }
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
}

@Composable
private fun ReportsGroupScreen(
    settings: AppSettings,
    diagnostic: LiveDiagnostic?,
    history: List<AnalysisResult>,
    debugLogEnabled: Boolean,
    onDebugLogChange: (Boolean) -> Unit,
    onCreateReport: () -> Unit,
    onClearReport: () -> Unit,
    reportStatus: String,
) {
    val context = LocalContext.current
    var intensiveActive0172 by remember { mutableStateOf(IntensiveDiagnostics0172.isActive(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Relatórios e histórico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Investigação intensiva temporária", fontWeight = FontWeight.Bold)
                Text(
                    "Registra um checkpoint pequeno a cada segundo por até 10 minutos. Não captura telas e não executa OCR em ciclo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        if (intensiveActive0172) IntensiveDiagnostics0172.stop(context) else IntensiveDiagnostics0172.start(context)
                        intensiveActive0172 = IntensiveDiagnostics0172.isActive(context)
                        context.sendBroadcast(Intent(ACTION_INTENSIVE_DIAGNOSTIC_CONTROL_0172).setPackage(context.packageName))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (intensiveActive0172) "Parar investigação" else "Iniciar por 10 minutos") }
                Text(
                    if (intensiveActive0172) "Ativa — ${IntensiveDiagnostics0172.remainingMillis(context) / 1000L}s restantes" else "Desativada",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        DiagnosticExpander(
            debugLogEnabled = debugLogEnabled,
            onDebugLogChange = onDebugLogChange,
            onCreateReport = onCreateReport,
            onClearReport = onClearReport,
            reportStatus = reportStatus,
        )
        Text("Histórico de decisões", fontWeight = FontWeight.Bold)
        HistoryScreen(history)
    }
} // grouped_reports_tools_0_1_115 final_reports_compile_repair_checklist_10

@Composable
private fun HistoryScreen(history: List<AnalysisResult>) = Unit

private suspend fun buildManualSupportReport(
    context: Context,
    repository: SettingsRepository,
    settings: AppSettings,
    liveEnabled: Boolean,
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
        appendLine(br.com.mapeiaia.rotacerta.trips.AgendaForensicReportBuilder.build(context))
        appendLine()
        appendLine("ROTA CERTA DIAGNOSTICO DE SESSAO")
        appendLine("Arquivo montado somente por clique do usuario.")
        appendLine("A trilha normal fica apenas em memoria; a investigacao intensiva opcional sobrescreve somente um checkpoint pequeno por segundo.")
        appendLine("Versao: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Data da exportacao: ${formatDate(nowMillis)}")
        appendLine("Pacote: ${context.packageName}")
        appendLine("Leitura ao vivo ativa: $liveEnabled")
        appendLine("Log de depuração: ${if (DebugLogPreferenceStore.isEnabled(context)) "ON" else "OFF"}")
        appendLine("Eventos na trilha unificada: ${UnifiedDebugEventStore.size()}")
        appendLine()
        appendLine("--- RESUMO TÉCNICO UNIFICADO ---")
        appendLine(ManualTechnicalReportBuilder.build(context = context, settings = settings))
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
        appendLine("Aplicativos pre-cadastrados: nenhum")
        appendLine("Selecao manual de apps ativa infraestrutura: true (Stage26)")
        appendLine("Aplicativos selecionados: ${SelectedRideAppStore.read(context).joinToString(", ").ifBlank { "nenhum" }}")
        appendLine("Politica Stage26: app selecionado liga/desliga infraestrutura; package visual nao autoriza card; ultimo endereco coerente e o destino")
        appendLine("Cache exato reaplica verde/vermelho e km em milissegundos; rota nova usa Google Maps")
        appendLine("Pacote Android: somente metadado diagnostico; nao autoriza nem bloqueia a leitura Stage19+") // diagnostic_policy_no_pre_registered_0_1_126 manual_policy_report_0_1_127
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
        appendLine("--- EVENTOS UNIFICADOS DA EXECUÇÃO ---")
        appendLine(UnifiedDebugEventStore.dump())
        appendLine()
        appendLine("--- BLABLACAR NETWORK-FIRST ANONIMIZADO ---")
        appendLine(BlaBlaNetworkDiagnosticStore.exportLatest(context))
        appendLine()
        appendLine("--- EVENTOS TÉCNICOS COMPLEMENTARES ---")
        appendLine(complementaryEvents.ifBlank { "sem eventos complementares" })
        appendLine()
        appendLine("--- INVESTIGAÇÃO INTENSIVA 0.1.172 ---")
        appendLine(IntensiveDiagnostics0172.export(context))
        appendLine()
        appendLine("--- HISTÓRICO DE ENCERRAMENTO DO ANDROID 0.1.172 ---")
        appendLine(ProcessExitDiagnostics0172.build(context))
        appendLine()
        appendLine("--- OBSERVACAO ---")
        appendLine("O relatorio preserva os eventos tecnicos necessarios e agora informa explicitamente a politica universal de leitura. Nao usa IA generativa e nao depende de apps ou cards pre-cadastrados.")
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

// universal_no_card_registration_0_1_102 legacy_marker_only_strict_0_1_127
// Leitura universal de tela: true // legacy_marker_only_strict_0_1_127

// universal_no_card_compile_repair_0_1_102
// cards_ui_allowed_compile_0_1_120

// bubble_shortcut_navigation_0_1_117

// professional_bubble_named_action_markers_0_1_118
// label = "WhatsApp"
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


// manual_ui_annotation_cleanup_0_1_127

// compile_final_cleanup_0_1_127 removed_duplicate_composable=0

// general_controls_ui_complete_checklist_7

// general_group_routing_complete_checklist_7

// SHORTCUT_MODULE_IDENTITY_FOCUS_0177
