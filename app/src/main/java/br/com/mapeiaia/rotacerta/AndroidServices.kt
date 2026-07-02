package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Geocoder
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

class OcrService(private val context: Context) {
    suspend fun extractText(uri: Uri): String = withContext(Dispatchers.Default) {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image).await().text
    }

    suspend fun extractText(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val texts = buildBitmapVariants(bitmap).mapNotNull { candidate ->
            runCatching { recognizer.process(InputImage.fromBitmap(candidate, 0)).await().text }.getOrNull()
        }
        mergeOcrTexts(texts)
    }

    private fun buildBitmapVariants(bitmap: Bitmap): List<Bitmap> {
        val cropTop = (bitmap.height * 0.22f).toInt().coerceIn(0, bitmap.height - 1)
        val lowerScreen = Bitmap.createBitmap(bitmap, 0, cropTop, bitmap.width, bitmap.height - cropTop)
        return listOf(bitmap, lowerScreen, invertBitmap(bitmap), invertBitmap(lowerScreen))
    }

    private fun invertBitmap(source: Bitmap): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val width = output.width
        val height = output.height
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            pixels[index] = Color.argb(
                Color.alpha(color),
                255 - Color.red(color),
                255 - Color.green(color),
                255 - Color.blue(color),
            )
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun mergeOcrTexts(texts: List<String>): String {
        val lines = linkedSetOf<String>()
        texts
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { lines += it }
        return lines.joinToString("\n")
    }
}

class DeviceLocationService(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    suspend fun currentCoordinate(): Coordinate? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return null

        val location = client
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .await()
            ?: client.lastLocation.await()
            ?: return null

        return Coordinate(location.latitude, location.longitude)
    }
}

class GeocodingService(private val context: Context) {
    private val geocoder = Geocoder(context, Locale("pt", "BR"))

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
