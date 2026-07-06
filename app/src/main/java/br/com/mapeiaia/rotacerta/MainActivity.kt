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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { SettingsRepository(context) }
    val settings by repository.settings.collectAsState(initial = AppSettings())
    val history by repository.analyses.collectAsState(initial = emptyList())
    val diagnostic by repository.diagnostic.collectAsState(initial = null)
    val cardTemplates by repository.cardTemplates.collectAsState(initial = emptyList())
    val savedPlaces by repository.savedPlaces.collectAsState(initial = emptyList())
    val radarImportSummary by repository.radarImportSummary.collectAsState(initial = RadarImportSummary())
    val ocrService = remember { OcrService(context) }
    val locationService = remember { DeviceLocationService(context) }
    val geocodingService = remember { GeocodingService(context) }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(TAB_ANALYSIS) }
    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }
    var region by remember { mutableStateOf(DeviceRegion()) }
    var liveEnabled by remember { mutableStateOf(isLiveAccessibilityEnabled(context)) }
    var templateStatus by remember { mutableStateOf("Modelos cadastrados: ${cardTemplates.size}") }
    var unreadTemplatePrints by remember { mutableStateOf(0) }
    var backupStatus by remember { mutableStateOf("") }
    var radarImportStatus by remember { mutableStateOf("") }

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
                val packageName = RideCardTemplateMatcher.inferPackageName(extractedText)
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
                imported == 0 -> "Nenhum modelo importado. Confira se os prints sao cards de corrida."
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

    LaunchedEffect(launchIntent) {
        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            tab = requestedTab
        }
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == TAB_ANALYSIS, onClick = { tab = TAB_ANALYSIS }, label = { Text("Analise") }, icon = {})
                NavigationBarItem(selected = tab == TAB_CONFIG, onClick = { tab = TAB_CONFIG }, label = { Text("Config") }, icon = {})
                NavigationBarItem(selected = tab == TAB_TOOLS, onClick = { tab = TAB_TOOLS }, label = { Text("Ferramentas") }, icon = {})
                NavigationBarItem(selected = tab == TAB_HISTORY, onClick = { tab = TAB_HISTORY }, label = { Text("Historico") }, icon = {})
            }
        },
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
            Text("Aceite corridas cujo destino final fique dentro do raio definido por voce.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            when (tab) {
                TAB_ANALYSIS -> AnalysisScreen(
                    settings = settings,
                    latestResult = history.firstOrNull(),
                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    liveEnabled = liveEnabled,
                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
                    onDeleteCardModel = ::deleteCardModel,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                )
                TAB_CONFIG -> SettingsScreen(
                    settings = settings,
                    diagnostic = diagnostic,
                    cardTemplates = cardTemplates,
                    savedPlaces = savedPlaces,
                    backupStatus = backupStatus,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    radarImportSummary = radarImportSummary,
                    radarImportStatus = radarImportStatus,
                    onSave = { scope.launch { repository.saveSettings(it) } },
                    onRegisterRideCard = ::registerRideCard,
                    onRenameSavedPlace = ::renameSavedPlace,
                    onDeleteSavedPlace = { place -> scope.launch { repository.removeSavedPlace(place.id) } },
                    onRegionDetected = { detectedRegion -> region = detectedRegion },
                    onCreateBackup = { backupFileCreator.launch("rota-certa-backup.json") },
                    onRestoreBackup = { backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onImportRadarFile = { radarFilePicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream", "*/*")) },
                    onOpenMapaRadar = { openMapaRadarSite(context) },
                    onClearImportedRadars = {
                        scope.launch {
                            repository.clearImportedRadars()
                            radarImportStatus = "Radares importados removidos."
                        }
                    },
                )
                TAB_TOOLS -> ToolsScreen(
                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                )
                TAB_HISTORY -> HistoryScreen(history)
            }
        }
    }
}

@Composable
private fun AnalysisScreen(
    settings: AppSettings,
    latestResult: AnalysisResult?,
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    liveEnabled: Boolean,
    onSaveSettings: (AppSettings) -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
    onPickCardModels: () -> Unit,
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

    LiveReadingCard(
        liveEnabled = liveEnabled,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onRefreshLiveState = onRefreshLiveState,
    )

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
    CardModelsCard(
        cardTemplates = cardTemplates,
        templateStatus = templateStatus,
        unreadTemplatePrints = unreadTemplatePrints,
        onPickCardModels = onPickCardModels,
        onDeleteCardModel = onDeleteCardModel,
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
                    "Operando. Verde/vermelho aparecem quando o app reconhece um card de corrida cadastrado."
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
            Text("Endereco para aceitar corridas", fontWeight = FontWeight.Bold)
            Text(
                "Configure o ponto que o destino final precisa ficar perto. O Rota Certa usa este endereco e o raio em km para decidir aceitar ou recusar.",
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
                    Text("Usar GPS atual")
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
                Text("Salvar endereco")
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
            Text("Raio de aceite", fontWeight = FontWeight.Bold)
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
            Text("Modelos de cards", fontWeight = FontWeight.Bold)
            Text("Modelos cadastrados: ${cardTemplates.size}")
            Button(onClick = onPickCardModels, modifier = Modifier.fillMaxWidth()) {
                Text("Anexar modelos de cards (prints)")
            }
            Text(templateStatus, style = MaterialTheme.typography.bodySmall)
            if (unreadTemplatePrints > 0) {
                Text("Prints sem leitura: $unreadTemplatePrints", style = MaterialTheme.typography.bodySmall)
            }
            if (cardTemplates.isEmpty()) {
                Text("Nenhum modelo cadastrado ainda.", style = MaterialTheme.typography.bodySmall)
            } else {
                cardTemplates.forEach { template ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, fontWeight = FontWeight.Bold)
                            Text(template.packageName ?: "app nao identificado", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onDeleteCardModel(template) }) {
                            Text("Apagar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticExpander(
    diagnostic: LiveDiagnostic?,
    cardTemplates: List<RideCardTemplate>,
    onRegisterRideCard: (String?, String) -> Unit,
) {
    val context = LocalContext.current
    ExpandableCard(title = "Diagnostico tecnico", initiallyExpanded = false) {
        Text("Cards cadastrados: ${cardTemplates.size}", style = MaterialTheme.typography.bodySmall)
        if (diagnostic == null) {
            Text("Nenhum diagnostico registrado ainda. Ative a leitura e abra um card de corrida.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Cor: ${diagnostic.bubbleColor}")
            Text("Etapa: ${diagnostic.stage}")
            Text("Pacote: ${diagnostic.packageName ?: "nao informado"}")
            Text("Card reconhecido: ${diagnostic.registeredCardMatched ?: "nenhum"}", style = MaterialTheme.typography.bodySmall)
            Text("Motivo: ${diagnostic.reason}", style = MaterialTheme.typography.bodySmall)
            diagnostic.destination?.takeIf { it.isNotBlank() }?.let {
                Text("Destino: $it", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Rota Certa diagnostico", diagnostic.toShareText()))
                    Toast.makeText(context, "Diagnostico copiado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copiar diagnostico")
            }
            OutlinedButton(
                enabled = diagnostic.textPreview.isNotBlank(),
                onClick = { onRegisterRideCard(diagnostic.packageName, diagnostic.textPreview) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cadastrar texto lido como modelo")
            }
        }
    }
}

@Composable
private fun SavedPlacesCard(
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

    ExpandableCard(title = "Locais salvos (${places.size})", initiallyExpanded = highlightedType == SavedPlaceType.Place) {
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
            Text("Informe o nome deste item agora. Esse nome aparece na lista e no alerta de voz.", style = MaterialTheme.typography.bodySmall)
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
                enabled = draftName.trim() != place.name,
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
    settings: AppSettings,
    diagnostic: LiveDiagnostic?,
    cardTemplates: List<RideCardTemplate>,
    savedPlaces: List<SavedPlace>,
    backupStatus: String,
    highlightedSavedPlaceId: String?,
    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    onSave: (AppSettings) -> Unit,
    onRegisterRideCard: (String?, String) -> Unit,
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
            gpsStatus = "GPS preenchido. Confira e toque em Salvar configuracoes."
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
        Text("Configuracoes", fontWeight = FontWeight.Bold)
        AlwaysLocationPermissionCard(
            hasAlwaysPermission = hasAlwaysLocationPermission(context),
            onOpenLocationSettings = { openAppLocationSettings(context) },
        )
        DiagnosticExpander(
            diagnostic = diagnostic,
            cardTemplates = cardTemplates,
            onRegisterRideCard = onRegisterRideCard,
        )
        BubbleSettingsCard(settings = draft, onChange = ::saveDraft)
        SavedPlacesCard(
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
        )
        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)
        SettingsLocationCard(
            draft = draft,
            gpsStatus = gpsStatus,
            onDraftChange = { draft = it },
            onRequestGps = ::requestGps,
            onSave = { onSave(draft) },
        )
        MapsAndAdvancedCard(draft = draft, onDraftChange = { draft = it }, onSave = { onSave(draft) })
        BackupCard(
            status = backupStatus,
            onCreateBackup = onCreateBackup,
            onRestoreBackup = onRestoreBackup,
        )
        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar configuracoes") }
    }
}

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
            Button(onClick = { onRequestGps(LocationTarget.Home) }, modifier = Modifier.weight(1f)) { Text("Usar GPS atual") }
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
            "Salva configuracoes, modelos de cards, locais e alertas de proximidade em um arquivo do celular.",
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
    ExpandableCard(title = "Apps monitorados", initiallyExpanded = false) {
        Text(
            "Farol ao vivo: verde/vermelho somente quando a tela bater com um card cadastrado manualmente. Telas desconhecidas ficam amarelas e viram amostra.",
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSwitchRow(
            label = "Ler somente apps selecionados",
            checked = settings.restrictToSelectedRideApps,
            onCheckedChange = { onChange(settings.copy(restrictToSelectedRideApps = it)) },
        )
        Text(
            if (settings.restrictToSelectedRideApps) {
                "Modo restrito: a bolinha so analisa os apps marcados abaixo. Outros apps voltam para amarelo."
            } else {
                "Modo livre: a bolinha analisa somente cards cadastrados e ignora telas passivas."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSwitchRow("99 Motorista", settings.monitor99) { onChange(settings.copy(monitor99 = it)) }
        SettingsSwitchRow("Uber Driver", settings.monitorUber) { onChange(settings.copy(monitorUber = it)) }
        SettingsSwitchRow("inDrive", settings.monitorInDrive) { onChange(settings.copy(monitorInDrive = it)) }
        OutlinedTextField(
            value = settings.extraMonitoredPackages,
            onValueChange = { onChange(settings.copy(extraMonitoredPackages = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pacote extra permitido") },
        )
        Text("Use este campo se outro app de motorista nao estiver na lista. Separe varios pacotes por virgula.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExpandableCard(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) expanded = true
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Fechar" else "Abrir")
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

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
    val selectedIndex = allowedValues.indexOf(value).takeIf { it >= 0 } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Distancia do alerta de proximidade")
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
private fun ToolsScreen(onOpenBlaBlaCarCollector: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ferramentas", fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletor BlaBlaCar", fontWeight = FontWeight.Bold)
                Text(
                    "Registro manual de viagem logada: passageiros, telefones, WhatsApp, rotas, faturamento, despesas e lucro.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir coletor")
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<AnalysisResult>) {
    if (history.isEmpty()) {
        Text("Nenhuma analise salva ainda.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        history.forEach { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(recommendationLabel(result.recommendation), fontWeight = FontWeight.Bold)
                    Text(formatDate(result.createdAtMillis))
                    Text(result.fields.destination ?: "Destino final nao identificado")
                    Text(result.reason)
                }
            }
        }
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

private fun LiveDiagnostic.toShareText(): String = buildString {
    appendLine("ROTA CERTA DIAGNOSTICO")
    appendLine("Versao: $appVersionName ($appVersionCode)")
    appendLine("Data: ${formatDate(createdAtMillis)}")
    appendLine("Pacote: ${packageName ?: "nao informado"}")
    appendLine("Etapa: $stage")
    appendLine("Cor: $bubbleColor")
    appendLine("Motivo: $reason")
    appendLine("Modo restrito: $restrictToSelectedRideApps")
    appendLine("Card cadastrado obrigatorio: $registeredCardRequired")
    appendLine("Card reconhecido: ${registeredCardMatched ?: "nenhum"}")
    appendLine("Pacotes selecionados: ${selectedPackages.joinToString(", ").ifBlank { "nenhum" }}")
    appendLine("Destino: ${destination ?: "nao identificado"}")
    appendLine("Embarque: ${pickup ?: "nao identificado"}")
    appendLine("Recomendacao: ${recommendation ?: "sem decisao"}")
    appendLine("Distancia casa: ${homeDistanceKm?.let(::formatKm) ?: "nao calculada"}")
    appendLine("Distancia alfinete: ${alternativeDistanceKm?.let(::formatKm) ?: "nao calculada"}")
    appendLine("Texto tamanho: $textLength")
    appendLine("Texto hash: ${textHash ?: "sem hash"}")
    appendLine("Erro: ${error ?: "nenhum"}")
    appendLine("--- TEXTO LIDO ---")
    appendLine(textPreview.ifBlank { "sem texto" })
}

private fun savedPlaceTypeLabel(place: SavedPlace): String = when (place.type) {
    SavedPlaceType.Place -> "Local salvo"
    SavedPlaceType.ProximityAlert -> "Alerta de proximidade: ${place.alertDistanceMeters ?: 200} m"
}

private fun defaultSavedPlaceName(type: SavedPlaceType): String = when (type) {
    SavedPlaceType.Place -> "Local salvo"
    SavedPlaceType.ProximityAlert -> "Alerta de proximidade"
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
    listOf(region.city, region.country).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Cidade e pais serao detectados pela localizacao." }

private fun formatKm(value: Double): String = String.format(Locale("pt", "BR"), "%.1f km", value)

private fun formatCoordinate(coordinate: Coordinate): String =
    String.format(Locale("pt", "BR"), "%.5f, %.5f", coordinate.latitude, coordinate.longitude)

private fun formatDate(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(value))
