package br.com.mapeiaia.rotacerta

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// Used only after the user taps the GPS button in Config and grants location permission.
data class GpsAddressResult(
    val addressLine: String,
    val region: DeviceRegion,
)

class GpsAddressResolver(private val context: Context) {
    private val geocoder = Geocoder(context, Locale("pt", "BR"))

    suspend fun resolve(coordinate: Coordinate): GpsAddressResult = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val address = runCatching {
            geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)?.firstOrNull()
        }.getOrNull()

        val fallback = listOfNotNull(
            address?.thoroughfare,
            address?.subThoroughfare,
            address?.subLocality,
            address?.locality,
            address?.adminArea,
        ).filter { it.isNotBlank() }.joinToString(", ")

        val line = address?.getAddressLine(0)?.takeIf { it.isNotBlank() }
            ?: fallback.ifBlank { formatCoordinate(coordinate) }

        GpsAddressResult(
            addressLine = line,
            region = DeviceRegion(
                city = address?.locality ?: address?.subAdminArea ?: address?.adminArea.orEmpty(),
                country = address?.countryName ?: address?.countryCode.orEmpty(),
            ),
        )
    }
}
