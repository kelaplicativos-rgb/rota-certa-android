package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.core.content.ContextCompat
import java.util.Locale

const val MAPA_RADAR_URL = "https://maparadar.com/"

fun parseMapaRadarCsv(content: String, importedAtMillis: Long = System.currentTimeMillis()): List<ImportedRadar> =
    content
        .lineSequence()
        .mapIndexedNotNull { index, rawLine -> parseMapaRadarLine(index, rawLine, importedAtMillis) }
        .distinctBy { radar -> "${radar.coordinate.latitude}:${radar.coordinate.longitude}:${radar.type}:${radar.speedKmh}" }
        .toList()

private fun parseMapaRadarLine(index: Int, rawLine: String, importedAtMillis: Long): ImportedRadar? {
    val line = rawLine.trim()
    if (line.isBlank() || line.startsWith("X,Y", ignoreCase = true)) return null

    val columns = line.split(',', ';', '\t').map { it.trim() }
    if (columns.size < 4) return null

    val longitude = columns.getOrNull(0)?.toDoubleOrNull() ?: return null
    val latitude = columns.getOrNull(1)?.toDoubleOrNull() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

    val type = columns.getOrNull(2)?.toIntOrNull() ?: 0
    val speed = columns.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 }
    val directionType = columns.getOrNull(4)?.toIntOrNull()
    val direction = columns.getOrNull(5)?.toIntOrNull()

    return ImportedRadar(
        id = "maparadar-$index-$latitude-$longitude-$type-${speed ?: 0}",
        coordinate = Coordinate(latitude = latitude, longitude = longitude),
        type = type,
        speedKmh = speed,
        directionType = directionType,
        direction = direction,
        createdAtMillis = importedAtMillis,
    )
}

fun importedRadarTypeLabel(type: Int): String = when (type) {
    1 -> "Radar fixo"
    2 -> "Semaforo com radar"
    3 -> "Semaforo com camera"
    4 -> "Radar movel"
    5 -> "Policia rodoviaria"
    6 -> "Lombada"
    7 -> "Pedagio"
    else -> "Radar"
}

fun importedRadarSpeech(radar: ImportedRadar, distanceMeters: Double): String {
    val distance = distanceMeters.toInt().coerceAtLeast(0)
    val speed = radar.speedKmh?.let { " de $it km por hora" }.orEmpty()
    return "Atenção: ${importedRadarTypeLabel(radar.type).lowercase(Locale("pt", "BR"))}$speed a $distance metros."
}

fun openMapaRadarSite(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MAPA_RADAR_URL)))
    }
}

fun openAppLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

fun hasAlwaysLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun AlwaysLocationPermissionCard(
    hasAlwaysPermission: Boolean,
    onOpenLocationSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GPS continuo", fontWeight = FontWeight.Bold)
            Text(
                if (hasAlwaysPermission) {
                    "Localizacao configurada para funcionar continuamente."
                } else {
                    "Para alertas em movimento, abra Permissoes > Localizacao e selecione Permitir o tempo todo."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenLocationSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir permissao de localizacao")
            }
        }
    }
}

@Composable
fun RadarImportCard(
    summary: RadarImportSummary,
    importStatus: String,
    onPickFile: () -> Unit,
    onOpenMapaRadar: () -> Unit,
    onClearRadars: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Radares importados", fontWeight = FontWeight.Bold)
            Text(
                "Importe o TXT do MapaRadar. O Rota Certa usa esses pontos como alertas de proximidade durante o trajeto.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Radares carregados: ${summary.count}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                    Text("Importar TXT")
                }
                OutlinedButton(onClick = onOpenMapaRadar, modifier = Modifier.weight(1f)) {
                    Text("MapaRadar")
                }
            }
            OutlinedButton(onClick = onClearRadars, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar radares importados")
            }
            if (importStatus.isNotBlank()) {
                Text(importStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
