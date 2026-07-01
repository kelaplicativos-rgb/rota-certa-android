package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
fun RotaCertaApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { SettingsRepository(context) }
    val settings by repository.settings.collectAsState(initial = AppSettings())
    val history by repository.analyses.collectAsState(initial = emptyList())
    val locationService = remember { DeviceLocationService(context) }
    val geocodingService = remember { GeocodingService(context) }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf("analise") }
    var region by remember { mutableStateOf(DeviceRegion()) }
    var liveEnabled by remember { mutableStateOf(isLiveAccessibilityEnabled(context)) }

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
                NavigationBarItem(selected = tab == "analise", onClick = { tab = "analise" }, label = { Text("Analise") }, icon = {})
                NavigationBarItem(selected = tab == "config", onClick = { tab = "config" }, label = { Text("Config") }, icon = {})
                NavigationBarItem(selected = tab == "historico", onClick = { tab = "historico" }, label = { Text("Historico") }, icon = {})
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
            Text("Regra principal: destino final dentro ou fora do raio.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            when (tab) {
                "analise" -> AnalysisScreen(
                    settings = settings,
                    latestResult = history.firstOrNull(),
                    liveEnabled = liveEnabled,
                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                )
                "config" -> SettingsScreen(
                    settings = settings,
                    onSave = { scope.launch { repository.saveSettings(it) } },
                    onRegionDetected = { detectedRegion -> region = detectedRegion },
                )
                "historico" -> HistoryScreen(history)
            }
        }
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
            homeStatus = "GPS casa salvo: ${formatCoordinate(coordinate)}"
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
            homeStatus = "Localizacao negada. Autorize o GPS para salvar a casa atual."
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
                    "Operando. Quando a corrida tocar, a bolinha informa verde, vermelho ou amarelo."
                } else {
                    "Acesso negado. Ative 'Rota Certa - leitura ao vivo' nas configuracoes de Acessibilidade."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Home", fontWeight = FontWeight.Bold)
            Text("Configure o ponto que o destino final precisa ficar perto.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = quickSettings.homeAddress,
                onValueChange = { quickSettings = quickSettings.copy(homeAddress = it, homeCoordinate = null) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Casa / ponto principal") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { requestHomeGps() }, modifier = Modifier.weight(1f)) {
                    Text("Usar GPS atual")
                }
                OutlinedButton(
                    onClick = {
                        quickSettings = quickSettings.copy(homeCoordinate = null)
                        homeStatus = "Digite o endereco e toque em Salvar home."
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Digitar")
                }
            }
            quickSettings.homeCoordinate?.let {
                Text("GPS casa salvo: ${formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
            }
            if (homeStatus.isNotBlank()) {
                Text(homeStatus, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { saveQuickSettings(quickSettings) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar home")
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Raio rapido", fontWeight = FontWeight.Bold)
            RadiusSlider(
                label = "Casa",
                value = quickSettings.homeRadiusKm,
                onValueChange = { quickSettings = quickSettings.copy(homeRadiusKm = it) },
                onValueChangeFinished = { onSaveSettings(quickSettings) },
            )
            RadiusSlider(
                label = "Alfinete",
                value = quickSettings.alternativeRadiusKm,
                onValueChange = { quickSettings = quickSettings.copy(alternativeRadiusKm = it) },
                onValueChangeFinished = { onSaveSettings(quickSettings) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bolinha", fontWeight = FontWeight.Bold)
            BubbleOpacitySlider(
                value = quickSettings.bubbleOpacity,
                onValueChange = { quickSettings = quickSettings.copy(bubbleOpacity = it) },
                onValueChangeFinished = { onSaveSettings(quickSettings) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Cor mais escura")
                Switch(
                    checked = quickSettings.bubbleDarkMode,
                    onCheckedChange = {
                        quickSettings = quickSettings.copy(bubbleDarkMode = it)
                        onSaveSettings(quickSettings.copy(bubbleDarkMode = it))
                    },
                )
            }
            Text("Toque na bolinha para abrir o Rota Certa. Arraste para mudar a posicao.", style = MaterialTheme.typography.bodySmall)
        }
    }

    latestResult?.let {
        Spacer(Modifier.height(12.dp))
        ResultCard(it, settings)
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
    onSave: (AppSettings) -> Unit,
    onRegionDetected: (DeviceRegion) -> Unit,
) {
    val context = LocalContext.current
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()

    var draft by remember(settings) { mutableStateOf(settings) }
    var gpsStatus by remember { mutableStateOf("") }
    var pendingLocationTarget by remember { mutableStateOf<LocationTarget?>(null) }

    fun captureGps(target: LocationTarget) {
        scope.launch {
            gpsStatus = "Buscando sinal de GPS..."
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                gpsStatus = "Nao consegui captar o GPS. Autorize a localizacao e tente novamente."
                return@launch
            }

            val resolved = gpsAddressResolver.resolve(coordinate)
            if (resolved.region.city.isNotBlank() || resolved.region.country.isNotBlank()) {
                onRegionDetected(resolved.region)
            }

            val address = resolved.addressLine.ifBlank { formatCoordinate(coordinate) }
            draft = when (target) {
                LocationTarget.Home -> draft.copy(
                    homeAddress = address,
                    homeCoordinate = coordinate,
                )
                LocationTarget.Alternative -> draft.copy(
                    alternativeAddress = address,
                    alternativeCoordinate = coordinate,
                )
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
        gpsPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Configure o ponto que o destino final precisa ficar perto.", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = draft.homeAddress,
            onValueChange = { draft = draft.copy(homeAddress = it, homeCoordinate = null) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Casa / ponto principal") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { requestGps(LocationTarget.Home) }, modifier = Modifier.weight(1f)) {
                Text("Usar GPS atual")
            }
            OutlinedButton(
                onClick = { draft = draft.copy(homeCoordinate = null) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Digitar")
            }
        }
        draft.homeCoordinate?.let {
            Text("GPS casa salvo: ${formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = draft.alternativeAddress,
            onValueChange = { draft = draft.copy(alternativeAddress = it, alternativeCoordinate = null) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Alfinete / localidade alternativa") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { requestGps(LocationTarget.Alternative) }, modifier = Modifier.weight(1f)) {
                Text("Usar GPS")
            }
            OutlinedButton(
                onClick = { draft = draft.copy(alternativeCoordinate = null) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Digitar")
            }
        }
        draft.alternativeCoordinate?.let {
            Text("GPS alfinete salvo: ${formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
        }

        RadiusSlider(
            label = "Raio casa",
            value = draft.homeRadiusKm,
            onValueChange = { draft = draft.copy(homeRadiusKm = it) },
            onValueChangeFinished = { onSave(draft) },
        )
        RadiusSlider(
            label = "Raio alfinete",
            value = draft.alternativeRadiusKm,
            onValueChange = { draft = draft.copy(alternativeRadiusKm = it) },
            onValueChangeFinished = { onSave(draft) },
        )
        OutlinedTextField(
            value = draft.googleMapsApiKey,
            onValueChange = { draft = draft.copy(googleMapsApiKey = it) },
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
            onValueChange = { draft = draft.copy(desiredKeywords = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bairros/palavras desejados") },
        )
        OutlinedTextField(
            value = draft.avoidedKeywords,
            onValueChange = { draft = draft.copy(avoidedKeywords = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bairros/palavras evitados") },
        )
        if (gpsStatus.isNotBlank()) {
            Text(gpsStatus, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) {
            Text("Salvar configuracoes")
        }
    }
}

@Composable
private fun RadiusSlider(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val safeValue = value.coerceIn(1.0, 30.0)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label)
            Text(formatKm(safeValue), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = safeValue.toFloat(),
            onValueChange = { rawValue ->
                val rounded = (rawValue * 2f).roundToInt() / 2.0
                onValueChange(rounded.coerceIn(1.0, 30.0))
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 1f..30f,
            steps = 57,
        )
    }
}

@Composable
private fun BubbleOpacitySlider(
    value: Double,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val safeValue = value.coerceIn(0.25, 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Transparencia")
            Text("${(safeValue * 100).roundToInt()}%", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = safeValue.toFloat(),
            onValueChange = { rawValue ->
                val rounded = (rawValue * 20f).roundToInt() / 20.0
                onValueChange(rounded.coerceIn(0.25, 1.0))
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0.25f..1f,
            steps = 14,
        )
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

private enum class LocationTarget {
    Home,
    Alternative,
}

private data class RadiusInfo(
    val label: String,
    val distanceKm: Double,
    val radiusKm: Double,
)

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
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()

    return enabledServices
        .split(':')
        .any { service -> expectedServices.any { it.equals(service, ignoreCase = true) } }
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
    listOf(region.city, region.country).filter { it.isNotBlank() }.joinToString(" - ").ifBlank {
        "Cidade e pais serao detectados pela localizacao."
    }

private fun formatKm(value: Double): String = String.format(Locale("pt", "BR"), "%.1f km", value)

private fun formatCoordinate(coordinate: Coordinate): String =
    String.format(Locale("pt", "BR"), "%.5f, %.5f", coordinate.latitude, coordinate.longitude)

private fun formatDate(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(value))