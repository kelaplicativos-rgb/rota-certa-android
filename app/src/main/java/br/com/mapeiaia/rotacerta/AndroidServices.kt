package br.com.mapeiaia.rotacerta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
