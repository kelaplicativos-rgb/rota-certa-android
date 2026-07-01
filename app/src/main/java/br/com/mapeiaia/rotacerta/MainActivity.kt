package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val repository = remember { SettingsRepository(context) }
    val settings by repository.settings.collectAsState(initial = AppSettings())
    val history by repository.analyses.collectAsState(initial = emptyList())
    val ocrService = remember { OcrService(context) }
    val locationService = remember { DeviceLocationService(context) }
    val geocodingService = remember { GeocodingService(context) }
    val googleMapsService = remember { GoogleMapsService() }
    val parser = remember { RideTextParser() }
    val decisionEngine = remember { DecisionEngine() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf("analise") }
    var lastResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var region by remember { mutableStateOf(DeviceRegion()) }
    var status by remember { mutableStateOf("Pronto para analisar um print.") }
    var radarEnabled by remember { mutableStateOf(false) }
    var radarStatus by remember { mutableStateOf("Radar por print desligado.") }

    val radarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasAllRadarPermissions(context)) {
            startRadarService(context)
            radarEnabled = true
            radarStatus = "Radar por print ativo: print novo mostra bolinha amarela, depois verde ou vermelha."
        } else {
            radarEnabled = false
            radarStatus = "Permita acesso as imagens e notificacoes para usar o radar por print."
        }
    }

    fun enableRadar() {
        if (!Settings.canDrawOverlays(context)) {
            radarStatus = "Libere 'aparecer sobre outros apps' e toque em Ativar radar por print novamente."
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }

        val missingPermissions = radarPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            radarPermissionLauncher.launch(missingPermissions)
            return
        }

        startRadarService(context)
        radarEnabled = true
        radarStatus = "Radar por print ativo: aguardando novo print da galeria."
    }

    fun disableRadar() {
        stopRadarService(context)
        radarEnabled = false
        radarStatus = "Radar por print desligado."
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

    suspend fun geocodeBest(query: String): Coordinate? =
        googleMapsService.geocode(query, region, settings.googleMapsApiKey)
            ?: geocodingService.geocode(query, region)

    suspend fun routeDistanceKm(origin: Coordinate?, destination: Coordinate?): Double? =
        if (origin != null && destination != null && settings.googleMapsApiKey.isNotBlank()) {
            googleMapsService.drivingDistanceKm(origin, destination, settings.googleMapsApiKey)
        } else {
            null
        }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Lendo print com OCR..."
            val extractedText = ocrService.extractText(uri)
            val fields = parser.parse(extractedText)
            val destinationCoordinate = fields.destination?.let { geocodeBest(it) }
            val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress)
            val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress)
            status = "Calculando distancia..."
            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate)
            val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate)
            val result = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = destinationCoordinate,
                homeCoordinate = homeCoordinate,
                alternativeCoordinate = alternativeCoordinate,
                fullText = extractedText,
                homeDistanceKm = homeDistanceKm,
                alternativeDistanceKm = alternativeDistanceKm,
            )
            repository.addAnalysis(result)
            lastResult = result
            status = "Analise concluida."
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
                    status = status,
                    result = lastResult,
                    settings = settings,
                    radarEnabled = radarEnabled,
                    radarStatus = radarStatus,
                    onPickImage = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onToggleRadar = {
                        if (radarEnabled) disableRadar() else enableRadar()
                    },
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
    status: String,
    result: AnalysisResult?,
    settings: AppSettings,
    radarEnabled: Boolean,
    radarStatus: String,
    onPickImage: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleRadar: () -> Unit,
) {
    Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
        Text("Ativar leitura ao vivo")
    }
    Text(
        "Nas configuracoes, ative 'Rota Certa - leitura ao vivo'. Ela le a tela da corrida e mostra a bolinha automaticamente.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onToggleRadar, modifier = Modifier.fillMaxWidth()) {
        Text(if (radarEnabled) "Parar radar por print" else "Ativar radar por print")
    }
    Text(radarStatus, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
        Text("Selecionar print manualmente")
    }
    Spacer(Modifier.height(12.dp))
    Text(status)
    result?.let { ResultCard(it, settings) }
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

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = draft.homeRadiusKm.toString(),
                onValueChange = { draft = draft.copy(homeRadiusKm = it.toDoubleOrNull() ?: draft.homeRadiusKm) },
                modifier = Modifier.weight(1f),
                label = { Text("Raio casa km") },
            )
            OutlinedTextField(
                value = draft.alternativeRadiusKm.toString(),
                onValueChange = { draft = draft.copy(alternativeRadiusKm = it.toDoubleOrNull() ?: draft.alternativeRadiusKm) },
                modifier = Modifier.weight(1f),
                label = { Text("Raio alfinete km") },
            )
        }
        OutlinedTextField(
            value = draft.googleMapsApiKey,
            onValueChange = { draft = draft.copy(googleMapsApiKey = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Chave Google Maps API") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Text(
            "Opcional, mas recomendado: usa Google Maps para localizar o destino e calcular rota real.",
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

private fun startRadarService(context: Context) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, RadarService::class.java).setAction(RadarService.ACTION_START),
    )
}

private fun stopRadarService(context: Context) {
    context.startService(Intent(context, RadarService::class.java).setAction(RadarService.ACTION_STOP))
}

private fun radarPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}.toTypedArray()

private fun hasAllRadarPermissions(context: Context): Boolean = radarPermissions().all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
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
