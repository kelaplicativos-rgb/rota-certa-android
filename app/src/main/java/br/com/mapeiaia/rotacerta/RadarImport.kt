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
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

const val MAPA_RADAR_URL = "https://maparadar.com/"

private enum class RadarCoordinateOrder { LongitudeLatitude, LatitudeLongitude }

fun parseMapaRadarFile(bytes: ByteArray, importedAtMillis: Long = System.currentTimeMillis()): List<ImportedRadar> =
    parseMapaRadarCsv(decodeMapaRadarText(bytes), importedAtMillis)

fun parseMapaRadarCsv(content: String, importedAtMillis: Long = System.currentTimeMillis()): List<ImportedRadar> {
    val lines = content
        .removePrefix("\uFEFF")
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()
    val coordinateOrder = detectRadarCoordinateOrder(lines)
    return lines
        .mapIndexedNotNull { index, rawLine ->
            parseMapaRadarLine(index, rawLine, importedAtMillis, coordinateOrder)
        }
        .distinctBy { radar ->
            "${radar.coordinate.latitude}:${radar.coordinate.longitude}:${radar.type}:${radar.speedKmh}"
        }
}

private fun decodeMapaRadarText(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
    }
    val utf8 = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
    return utf8 ?: bytes.toString(Charset.forName("windows-1252"))
}

private fun detectRadarCoordinateOrder(lines: List<String>): RadarCoordinateOrder {
    lines.take(12).forEach { line ->
        val columns = splitRadarColumns(line)
        if (columns.size < 2) return@forEach
        val first = normalizeRadarText(columns[0])
        val second = normalizeRadarText(columns[1])
        if (first.contains("lat") && (second.contains("lon") || second == "x")) {
            return RadarCoordinateOrder.LatitudeLongitude
        }
        if ((first.contains("lon") || first == "x") && (second.contains("lat") || second == "y")) {
            return RadarCoordinateOrder.LongitudeLatitude
        }
    }
    lines.take(100).forEach { line ->
        val columns = splitRadarColumns(line)
        val first = columns.getOrNull(0)?.toRadarDoubleOrNull() ?: return@forEach
        val second = columns.getOrNull(1)?.toRadarDoubleOrNull() ?: return@forEach
        if (kotlin.math.abs(first) > 90.0 && kotlin.math.abs(second) <= 90.0) {
            return RadarCoordinateOrder.LongitudeLatitude
        }
        if (kotlin.math.abs(second) > 90.0 && kotlin.math.abs(first) <= 90.0) {
            return RadarCoordinateOrder.LatitudeLongitude
        }
    }
    // O formato oficial X,Y do MapaRadar usa longitude primeiro.
    return RadarCoordinateOrder.LongitudeLatitude
}

private fun parseMapaRadarLine(
    index: Int,
    rawLine: String,
    importedAtMillis: Long,
    coordinateOrder: RadarCoordinateOrder,
): ImportedRadar? {
    val line = rawLine.trim().removePrefix("\uFEFF")
    if (line.isBlank() || isRadarHeader(line)) return null

    val columns = splitRadarColumns(line)
    if (columns.size < 3) return null

    val firstCoordinate = columns.getOrNull(0)?.toRadarDoubleOrNull() ?: return null
    val secondCoordinate = columns.getOrNull(1)?.toRadarDoubleOrNull() ?: return null
    val (longitude, latitude) = when (coordinateOrder) {
        RadarCoordinateOrder.LongitudeLatitude -> firstCoordinate to secondCoordinate
        RadarCoordinateOrder.LatitudeLongitude -> secondCoordinate to firstCoordinate
    }
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

    val numericType = columns.getOrNull(2)?.toIntOrNull()
    val descriptor = if (numericType == null) columns.drop(2).joinToString(" ") else ""
    val type = numericType ?: inferRadarType(descriptor)
    val speed = if (numericType != null) {
        columns.getOrNull(3)?.radarPositiveIntOrNull()
    } else {
        parseRadarSpeed(descriptor)
    }
    val directionType = if (numericType != null) columns.getOrNull(4)?.toIntOrNull() else null
    val direction = if (numericType != null) columns.getOrNull(5)?.toIntOrNull() else null

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

private fun splitRadarColumns(line: String): List<String> {
    val delimiter = when {
        line.count { it == '\t' } >= 2 -> '\t'
        line.count { it == ';' } >= 2 -> ';'
        else -> ','
    }
    return line.split(delimiter).map { it.trim().trim('"') }
}

private fun isRadarHeader(line: String): Boolean {
    val columns = splitRadarColumns(line)
    if (columns.size < 2) return false
    val first = normalizeRadarText(columns[0])
    val second = normalizeRadarText(columns[1])
    return (first == "x" && second == "y") ||
        first.contains("longitude") || first.contains("latitude") ||
        second.contains("longitude") || second.contains("latitude")
}

private fun String.toRadarDoubleOrNull(): Double? {
    val safe = trim().trim('"')
    return safe.toDoubleOrNull() ?: safe.replace(',', '.').toDoubleOrNull()
}

private fun String.radarPositiveIntOrNull(): Int? =
    trim().filter { it.isDigit() || it == '-' }.toIntOrNull()?.takeIf { it > 0 }

private fun parseRadarSpeed(descriptor: String): Int? {
    val atSpeed = Regex("""@(\d{1,3})(?:\D|$)""").find(descriptor)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (atSpeed != null && atSpeed > 0) return atSpeed
    return Regex("""(?i)(\d{1,3})\s*(?:km/?h|kmh|km\s+por\s+hora)""")
        .find(descriptor)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
}

private fun inferRadarType(descriptor: String): Int {
    val normalized = normalizeRadarText(descriptor)
    return when {
        "semaforo com radar" in normalized -> 2
        "semaforo com camera" in normalized -> 3
        "radar movel" in normalized -> 4
        "policia" in normalized -> 5
        "lombada" in normalized -> 6
        "pedagio" in normalized -> 7
        "radar fixo" in normalized -> 1
        else -> 0
    }
}

private fun normalizeRadarText(value: String): String = Normalizer
    .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .trim()

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
                "Importe TXT, CSV ou arquivo CSV salvo com extensao .xls. O Rota Certa reconhece automaticamente os formatos do MapaRadar.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Radares carregados: ${summary.count}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                    Text("Importar arquivo")
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
