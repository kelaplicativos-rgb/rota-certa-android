package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

data class OcrTextBlock0188(
    val id: String,
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class OcrStructuredText0188(
    val text: String,
    val blocks: List<OcrTextBlock0188>,
)

data class DeviceLocationFix(
    val coordinate: Coordinate,
    val accuracyMeters: Float?,
    val capturedAtMillis: Long,
    val fromCachedLocation: Boolean,
)

class OcrService(private val context: Context) {
    suspend fun extractText(uri: Uri): String = withContext(Dispatchers.Default) {
        recognize0188(InputImage.fromFilePath(context, uri)).text
    }

    suspend fun extractText(bitmap: Bitmap): String = extractStructuredText(bitmap).text

    suspend fun extractStructuredText(bitmap: Bitmap): OcrStructuredText0188 = withContext(Dispatchers.Default) {
        recognize0188(InputImage.fromBitmap(bitmap, 0))
    }

    private suspend fun recognize0188(image: InputImage): OcrStructuredText0188 {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(image).await()
            OcrStructuredText0188(
                text = FarolUnifiedVisual0168.fromVisionText(result),
                blocks = result.textBlocks.mapIndexedNotNull { index, block ->
                    val value = block.text.trim()
                    if (value.isBlank()) return@mapIndexedNotNull null
                    val bounds = block.boundingBox
                    OcrTextBlock0188(
                        id = "ocr-block-$index",
                        text = value,
                        left = bounds?.left ?: 0,
                        top = bounds?.top ?: 0,
                        right = bounds?.right ?: 0,
                        bottom = bounds?.bottom ?: 0,
                    )
                }.take(80),
            )
        } finally {
            recognizer.close()
        }
    }
}

class DeviceLocationService(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Existing operational position lookup. A cached fix is acceptable for passive timeline progress. */
    suspend fun currentCoordinate(): Coordinate? {
        if (!hasLocationPermission()) return null
        val location = client
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .await()
            ?: client.lastLocation.await()
            ?: return null
        return location.toCoordinate()
    }

    /**
     * Captures a reference point that may persist for days. A stale or very imprecise
     * cached fix is rejected instead of silently redefining the driver's origin.
     */
    suspend fun freshReferenceFix(
        maxAgeMillis: Long = 2L * 60L * 1_000L,
        maxAccuracyMeters: Float = 1_500f,
    ): DeviceLocationFix? {
        if (!hasLocationPermission()) return null
        val now = System.currentTimeMillis()
        val current = client
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .await()
        current?.takeIf { usableReferenceFix(it, now, maxAgeMillis, maxAccuracyMeters) }?.let {
            return it.toFix(fromCachedLocation = false)
        }
        val cached = client.lastLocation.await()
        return cached
            ?.takeIf { usableReferenceFix(it, now, maxAgeMillis, maxAccuracyMeters) }
            ?.toFix(fromCachedLocation = true)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun usableReferenceFix(
        location: Location,
        nowMillis: Long,
        maxAgeMillis: Long,
        maxAccuracyMeters: Float,
    ): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        val age = (nowMillis - location.time).coerceAtLeast(0L)
        if (location.time <= 0L || age > maxAgeMillis) return false
        if (location.hasAccuracy() && (!location.accuracy.isFinite() || location.accuracy > maxAccuracyMeters)) return false
        return true
    }

    private fun Location.toCoordinate(): Coordinate = Coordinate(latitude, longitude)

    private fun Location.toFix(fromCachedLocation: Boolean): DeviceLocationFix = DeviceLocationFix(
        coordinate = toCoordinate(),
        accuracyMeters = accuracy.takeIf { hasAccuracy() && it.isFinite() },
        capturedAtMillis = time,
        fromCachedLocation = fromCachedLocation,
    )
}

class GeocodingService(private val context: Context) {
    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun geocode(query: String, region: DeviceRegion): Coordinate? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        val scopedQuery = listOf(query, region.city, region.country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        @Suppress("DEPRECATION")
        val address = runCatching { geocoder.getFromLocationName(scopedQuery, 1)?.firstOrNull() }
            .getOrNull()
            ?: return@withContext null
        Coordinate(address.latitude, address.longitude)
    }

    suspend fun reverseGeocode(coordinate: Coordinate): DeviceRegion = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val address = runCatching {
            geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)?.firstOrNull()
        }.getOrNull()

        DeviceRegion(
            city = address?.locality ?: address?.subAdminArea.orEmpty(),
            country = address?.countryName.orEmpty(),
        )
    }
}
