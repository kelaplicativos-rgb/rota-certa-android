package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

data class OfflineCoordinate0709(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }

    fun wireValue(): String = String.format(Locale.US, "%.7f,%.7f", latitude, longitude)
}

data class OrganicMapsLaunchResult0709(
    val launched: Boolean,
    val packageName: String? = null,
    val reason: String,
)

object OrganicMapsOfflineBridge0709 {
    const val CONTRACT_MARKER = "ORGANIC_MAPS_OFFLINE_BRIDGE_0709"
    const val V2_NAV_MARKER = "ORGANIC_MAPS_V2_NAV_CURRENT_LOCATION_0709"
    const val PICK_POINT_MARKER = "ORGANIC_MAPS_PICK_POINT_ROUNDTRIP_0709"
    const val FAROL_ISOLATION_MARKER = "ORGANIC_MAPS_BRIDGE_DOES_NOT_CHANGE_FAROL_AUTHORITY_0709"

    const val EXTRA_PICK_POINT = "app.organicmaps.api.extra.PICK_POINT"
    const val EXTRA_POINT_NAME = "app.organicmaps.api.extra.POINT_NAME"
    const val EXTRA_POINT_LAT = "app.organicmaps.api.extra.POINT_LAT"
    const val EXTRA_POINT_LON = "app.organicmaps.api.extra.POINT_LON"

    private val allowedPackages = listOf(
        "app.organicmaps",
        "app.organicmaps.web",
        "app.organicmaps.beta",
        "app.organicmaps.debug",
    )

    fun parseCoordinate(raw: String): OfflineCoordinate0709? {
        val value = raw.trim()
            .removePrefix("(")
            .removeSuffix(")")
            .trim()

        val semicolonParts = value.split(';').map(String::trim)
        if (semicolonParts.size == 2) {
            return coordinateOrNull(
                semicolonParts[0].replace(',', '.').toDoubleOrNull(),
                semicolonParts[1].replace(',', '.').toDoubleOrNull(),
            )
        }

        val comma = Regex("""^\s*([+-]?\d{1,2}(?:\.\d+)?)\s*,\s*([+-]?\d{1,3}(?:\.\d+)?)\s*$""")
            .matchEntire(value)
        if (comma != null) {
            return coordinateOrNull(
                comma.groupValues[1].toDoubleOrNull(),
                comma.groupValues[2].toDoubleOrNull(),
            )
        }

        val whitespace = Regex("""^\s*([+-]?\d{1,2}(?:[.,]\d+)?)\s+([+-]?\d{1,3}(?:[.,]\d+)?)\s*$""")
            .matchEntire(value)
        if (whitespace != null) {
            return coordinateOrNull(
                whitespace.groupValues[1].replace(',', '.').toDoubleOrNull(),
                whitespace.groupValues[2].replace(',', '.').toDoubleOrNull(),
            )
        }
        return null
    }

    fun buildSearchUri(query: String, center: OfflineCoordinate0709? = null): Uri {
        val builder = Uri.Builder()
            .scheme("om")
            .authority("search")
            .appendQueryParameter("locale", "pt-BR")
            .appendQueryParameter("query", query.trim())
        center?.let { builder.appendQueryParameter("cll", it.wireValue()) }
        return builder.build()
    }

    fun buildPickPointIntent(appName: String = "Rota Certa"): Intent {
        val uri = Uri.Builder()
            .scheme("om")
            .authority("crosshair")
            .appendQueryParameter("appname", appName)
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
            .putExtra(EXTRA_PICK_POINT, true)
    }

    fun extractPickedCoordinate(intent: Intent?): Pair<OfflineCoordinate0709, String?>? {
        val extras = intent?.extras ?: return null
        if (!extras.containsKey(EXTRA_POINT_LAT) || !extras.containsKey(EXTRA_POINT_LON)) return null
        val lat = extras.getDouble(EXTRA_POINT_LAT, Double.NaN)
        val lon = extras.getDouble(EXTRA_POINT_LON, Double.NaN)
        val coordinate = coordinateOrNull(lat, lon) ?: return null
        return coordinate to extras.getString(EXTRA_POINT_NAME)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun buildNavigationUri(
        destination: OfflineCoordinate0709,
        destinationName: String,
    ): Uri = Uri.Builder()
        .scheme("om")
        .authority("v2")
        .appendPath("nav")
        .appendQueryParameter("origin", "currentLocation")
        .appendQueryParameter("destination", destination.wireValue())
        .appendQueryParameter("destination_name", destinationName.trim().ifBlank { "Destino Rota Certa" })
        .appendQueryParameter("mode", "drive")
        .build()

    fun buildRouteUri(
        origin: OfflineCoordinate0709,
        destination: OfflineCoordinate0709,
        originName: String = "Origem",
        destinationName: String = "Destino Rota Certa",
    ): Uri = Uri.Builder()
        .scheme("om")
        .authority("v2")
        .appendPath("dir")
        .appendQueryParameter("origin", origin.wireValue())
        .appendQueryParameter("origin_name", originName)
        .appendQueryParameter("destination", destination.wireValue())
        .appendQueryParameter("destination_name", destinationName)
        .appendQueryParameter("mode", "drive")
        .build()

    fun isAvailable(context: Context): Boolean =
        resolveAllowedPackage(context, Intent(Intent.ACTION_VIEW, Uri.parse("om://map?v=1"))) != null

    fun prepareIntent(context: Context, source: Intent): Intent? {
        val packageName = resolveAllowedPackage(context, source) ?: return null
        return Intent(source).setPackage(packageName)
    }

    fun launch(context: Context, uri: Uri): OrganicMapsLaunchResult0709 =
        launchIntent(context, Intent(Intent.ACTION_VIEW, uri))

    fun launchIntent(context: Context, source: Intent): OrganicMapsLaunchResult0709 {
        val packageName = resolveAllowedPackage(context, source)
            ?: return OrganicMapsLaunchResult0709(
                launched = false,
                reason = "Organic Maps não está disponível para esta ação.",
            )

        return runCatching {
            context.startActivity(
                Intent(source)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            OrganicMapsLaunchResult0709(
                launched = true,
                packageName = packageName,
                reason = "Organic Maps aberto.",
            )
        }.getOrElse { error ->
            OrganicMapsLaunchResult0709(
                launched = false,
                packageName = packageName,
                reason = error.message?.takeIf { it.isNotBlank() } ?: "Falha ao abrir o Organic Maps.",
            )
        }
    }

    fun openMain(context: Context): OrganicMapsLaunchResult0709 {
        val packageName = allowedPackages.firstOrNull { pkg ->
            runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
        } ?: return OrganicMapsLaunchResult0709(false, reason = "Organic Maps não instalado.")

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return OrganicMapsLaunchResult0709(false, packageName, "Organic Maps instalado, mas sem tela inicial acessível.")
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            OrganicMapsLaunchResult0709(true, packageName, "Organic Maps aberto.")
        }.getOrElse { error ->
            OrganicMapsLaunchResult0709(false, packageName, error.message ?: "Falha ao abrir Organic Maps.")
        }
    }

    fun downloadPageIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://omaps.app/get?api"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun resolveAllowedPackage(context: Context, source: Intent): String? =
        allowedPackages.firstOrNull { packageName ->
            val candidate = Intent(source).setPackage(packageName)
            candidate.resolveActivity(context.packageManager) != null
        }

    private fun coordinateOrNull(lat: Double?, lon: Double?): OfflineCoordinate0709? {
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return OfflineCoordinate0709(lat, lon)
    }
}
