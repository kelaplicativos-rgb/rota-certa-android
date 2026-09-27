package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkTrackingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                WorkTrackingScreen(onClose = ::finish)
            }
        }
    }
}

private enum class TrackingPermissionPurpose0668 { PRIVATE, FAMILY }

@Composable
private fun WorkTrackingScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { WorkTrackingRepository(context) }
    val shareManager0668 = remember { LiveTrackingShareManager0668(context) }
    val scope0668 = rememberCoroutineScope()
    var summary by remember { mutableStateOf(repository.todaySummary()) }
    var active by remember { mutableStateOf(repository.isTrackingActive()) }
    var familyActive0668 by remember { mutableStateOf(shareManager0668.familyShareUrl() != null) }
    var permissionPurpose0668 by remember { mutableStateOf(TrackingPermissionPurpose0668.PRIVATE) }
    var status by remember { mutableStateOf("") }

    fun refresh() {
        summary = repository.todaySummary()
        active = repository.isTrackingActive()
        familyActive0668 = shareManager0668.familyShareUrl() != null
    }

    fun startTracking() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, WorkTrackingService::class.java).setAction(WorkTrackingService.ACTION_START),
        )
        active = true
        status = "Rastreamento iniciado. A notificação permanece visível enquanto estiver ativo."
    }

    fun createFamilyShare0668() {
        startTracking()
        status = "Criando link familiar seguro…"
        scope0668.launch {
            runCatching {
                withContext(Dispatchers.IO) { shareManager0668.createFamilyLink() }
            }.onSuccess { outcome0668 ->
                familyActive0668 = true
                status = if (outcome0668.reused) {
                    "Link familiar ativo. Você pode compartilhá-lo novamente."
                } else {
                    "Acompanhamento familiar ativado."
                }
                shareTrackingLink0668(
                    context = context,
                    title = "Compartilhar acompanhamento com a família",
                    message = "🛡 Acompanhe meu percurso em tempo real pelo Rota Certa:",
                    url = outcome0668.url,
                )
            }.onFailure { error0668 ->
                status = "O percurso local foi iniciado, mas o link não pôde ser criado: ${error0668.message ?: "falha no servidor"}"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (locationGranted) {
            if (permissionPurpose0668 == TrackingPermissionPurpose0668.FAMILY) {
                createFamilyShare0668()
            } else {
                startTracking()
            }
        } else {
            status = "Autorize a localização para registrar o percurso."
        }
    }

    fun requestLocation0668(purpose0668: TrackingPermissionPurpose0668) {
        permissionPurpose0668 = purpose0668
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            if (purpose0668 == TrackingPermissionPurpose0668.FAMILY) createFamilyShare0668() else startTracking()
        } else {
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(2_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Rastreamento de trabalho", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Privado por padrão: o percurso fica somente neste aparelho. A localização só é enviada ao servidor quando você ativa explicitamente um link de acompanhamento.",
            style = MaterialTheme.typography.bodySmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (active) "ATIVO" else "PARADO", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { requestLocation0668(TrackingPermissionPurpose0668.PRIVATE) },
                        enabled = !active,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Iniciar")
                    }
                    OutlinedButton(
                        onClick = {
                            context.startService(
                                Intent(context, WorkTrackingService::class.java).setAction(WorkTrackingService.ACTION_STOP),
                            )
                            active = false
                            status = if (shareManager0668.hasActiveShares()) {
                                "Rastreamento encerrado. O Rota Certa também encerrará os links ativos no servidor."
                            } else {
                                "Rastreamento encerrado."
                            }
                            refresh()
                        },
                        enabled = active,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Parar")
                    }
                }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Compartilhamento", fontWeight = FontWeight.Bold)
                Text(
                    "Família acompanha toda esta sessão. Passageiros recebem links temporários individualmente nos cards das viagens.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { requestLocation0668(TrackingPermissionPurpose0668.FAMILY) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (familyActive0668) "🛡 Família" else "🛡 Família")
                    }
                    OutlinedButton(
                        onClick = {
                            status = "Encerrando link familiar…"
                            scope0668.launch {
                                runCatching { withContext(Dispatchers.IO) { shareManager0668.closeFamilyShare() } }
                                    .onSuccess {
                                        familyActive0668 = false
                                        status = "Link familiar encerrado."
                                    }
                                    .onFailure { error0668 ->
                                        status = "Não foi possível confirmar o encerramento no servidor: ${error0668.message ?: "falha de conexão"}"
                                    }
                            }
                        },
                        enabled = familyActive0668,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Encerrar link")
                    }
                }
                Text(
                    if (familyActive0668) "🟢 Link familiar ativo" else "⚫ Nenhum link familiar ativo",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Resumo de hoje", fontWeight = FontWeight.Bold)
                Text("Distância registrada: ${formatTrackingDistance(summary.distanceMeters)}")
                Text("Tempo registrado: ${formatTrackingDuration(summary.durationMillis)}")
                Text("Pontos de GPS: ${summary.points.size}")
                summary.startedAtMillis?.let { Text("Início: ${formatTrackingTime(it)}") }
                summary.endedAtMillis?.let { Text("Última posição: ${formatTrackingTime(it)}") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Traçado do percurso", fontWeight = FontWeight.Bold)
                Text(
                    "A linha abaixo usa os pontos reais gravados pelo GPS. O link compartilhado usa o mesmo motor de localização.",
                    style = MaterialTheme.typography.bodySmall,
                )
                WorkRouteCanvas(summary.points)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("Atualizar") }
            OutlinedButton(
                onClick = {
                    if (!active) {
                        repository.clearToday()
                        refresh()
                        status = "Histórico de hoje apagado."
                    } else {
                        status = "Pare o rastreamento antes de apagar o percurso de hoje."
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Apagar hoje") }
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}

@Composable
private fun WorkRouteCanvas(points: List<WorkTrackPoint>) {
    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxWidth().height(250.dp)) {
        if (points.size < 2) return@Canvas
        val latitudes = points.map { it.coordinate.latitude }
        val longitudes = points.map { it.coordinate.longitude }
        val minLat = latitudes.minOrNull() ?: return@Canvas
        val maxLat = latitudes.maxOrNull() ?: return@Canvas
        val minLon = longitudes.minOrNull() ?: return@Canvas
        val maxLon = longitudes.maxOrNull() ?: return@Canvas
        val latSpan = (maxLat - minLat).takeIf { it > 0.000001 } ?: 0.000001
        val lonSpan = (maxLon - minLon).takeIf { it > 0.000001 } ?: 0.000001
        val padding = 14.dp.toPx()
        val usableWidth = (size.width - padding * 2).coerceAtLeast(1f)
        val usableHeight = (size.height - padding * 2).coerceAtLeast(1f)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = padding + (((point.coordinate.longitude - minLon) / lonSpan).toFloat() * usableWidth)
            val y = padding + ((1f - ((point.coordinate.latitude - minLat) / latSpan).toFloat()) * usableHeight)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = routeColor, style = Stroke(width = 4.dp.toPx()))
        val first = points.first().coordinate
        val last = points.last().coordinate
        fun offset(coordinate: Coordinate): Offset = Offset(
            x = padding + (((coordinate.longitude - minLon) / lonSpan).toFloat() * usableWidth),
            y = padding + ((1f - ((coordinate.latitude - minLat) / latSpan).toFloat()) * usableHeight),
        )
        drawCircle(startColor, radius = 6.dp.toPx(), center = offset(first))
        drawCircle(endColor, radius = 6.dp.toPx(), center = offset(last))
    }
}

private fun formatTrackingDistance(meters: Double): String =
    if (meters < 1000.0) "${meters.roundToInt()} m" else String.format(Locale("pt", "BR"), "%.1f km", meters / 1000.0)

private fun formatTrackingDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}min" else "${minutes} min"
}

private fun formatTrackingTime(timeMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(Date(timeMillis))