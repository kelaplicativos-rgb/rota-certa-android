package br.com.mapeiaia.rotacerta

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Resolve o endereço durante a configuração, nunca durante a pintura do farol. */
class WorkRegionAddressResolver(context: Context) {
    private val appContext = context.applicationContext
    private val mapsService = GoogleMapsService(appContext)

    suspend fun resolve(address: String, apiKey: String): Coordinate? {
        val cleanAddress = address.trim().replace(Regex("\\s+"), " ")
        if (cleanAddress.isBlank()) return null

        mapsService.geocode(
            query = cleanAddress,
            region = DeviceRegion(country = "Brasil"),
            apiKey = apiKey,
        )?.let { return it }

        return resolveWithAndroidGeocoder(cleanAddress)
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveWithAndroidGeocoder(address: String): Coordinate? = withContext(Dispatchers.IO) {
        runCatching {
            Geocoder(appContext, Locale("pt", "BR"))
                .getFromLocationName(address, 1)
                ?.firstOrNull()
                ?.let { result -> Coordinate(result.latitude, result.longitude) }
        }.getOrNull()
    }
}
