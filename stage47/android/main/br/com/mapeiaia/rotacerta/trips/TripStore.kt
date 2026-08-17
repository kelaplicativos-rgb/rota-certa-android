package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TripStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val secretStore = TripSecretStore(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun trips(): List<Trip> = decode<List<Trip>>(prefs.getString(KEY_TRIPS, null)).orEmpty()
        .sortedByDescending(Trip::departureAtMillis)

    fun bookings(): List<Booking> = decode<List<Booking>>(prefs.getString(KEY_BOOKINGS, null)).orEmpty()

    fun bookingsFor(tripId: String): List<Booking> = bookings().filter { it.tripId == tripId }

    fun getTrip(id: String): Trip? = trips().firstOrNull { it.id == id }

    fun saveTrip(trip: Trip): Trip {
        val normalized = trip.copy(updatedAtMillis = System.currentTimeMillis())
        val current = trips().filterNot { it.id == normalized.id }
        prefs.edit().putString(KEY_TRIPS, json.encodeToString(listOf(normalized) + current)).apply()
        return normalized
    }

    fun deleteTrip(id: String) {
        prefs.edit()
            .putString(KEY_TRIPS, json.encodeToString(trips().filterNot { it.id == id }))
            .putString(KEY_BOOKINGS, json.encodeToString(bookings().filterNot { it.tripId == id }))
            .apply()
    }

    fun saveBooking(booking: Booking): Booking {
        val normalized = booking.copy(updatedAtMillis = System.currentTimeMillis())
        val current = bookings().filterNot { it.id == normalized.id }
        prefs.edit().putString(KEY_BOOKINGS, json.encodeToString(listOf(normalized) + current)).apply()
        refreshTripStatus(normalized.tripId)
        return normalized
    }

    fun deleteBooking(id: String) {
        val booking = bookings().firstOrNull { it.id == id }
        prefs.edit().putString(KEY_BOOKINGS, json.encodeToString(bookings().filterNot { it.id == id })).apply()
        booking?.let { refreshTripStatus(it.tripId) }
    }

    fun onlineSettings(): TripOnlineSettings {
        val publicSettings = decode<TripOnlineSettings>(prefs.getString(KEY_ONLINE, null)) ?: TripOnlineSettings()
        return publicSettings.copy(driverToken = secretStore.driverToken())
    }

    fun saveOnlineSettings(settings: TripOnlineSettings) {
        secretStore.saveDriverToken(settings.driverToken)
        val withoutAdministrativeSecret = settings.copy(driverToken = "")
        prefs.edit().putString(KEY_ONLINE, json.encodeToString(withoutAdministrativeSecret)).apply()
    }

    fun clearOnlineCredentials() {
        secretStore.clear()
        val current = decode<TripOnlineSettings>(prefs.getString(KEY_ONLINE, null)) ?: TripOnlineSettings()
        prefs.edit().putString(KEY_ONLINE, json.encodeToString(current.copy(driverToken = ""))).apply()
    }

    fun nextPublishedTrip(nowMillis: Long = System.currentTimeMillis()): Trip? = trips()
        .asSequence()
        .filter { it.departureAtMillis >= nowMillis }
        .filter { it.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL, TripStatus.STARTING) }
        .minByOrNull(Trip::departureAtMillis)

    private fun refreshTripStatus(tripId: String) {
        val trip = getTrip(tripId) ?: return
        val status = SeatAvailabilityEngine.suggestedStatus(trip, bookingsFor(tripId))
        if (status != trip.status) saveTrip(trip.copy(status = status))
    }

    private inline fun <reified T> decode(value: String?): T? = runCatching {
        if (value.isNullOrBlank()) null else json.decodeFromString<T>(value)
    }.getOrNull()

    companion object {
        private const val PREFS = "rota_certa_trips_stage47"
        private const val KEY_TRIPS = "trips"
        private const val KEY_BOOKINGS = "bookings"
        private const val KEY_ONLINE = "online_settings"
    }
}

@kotlinx.serialization.Serializable
data class TripOnlineSettings(
    val apiBaseUrl: String = "",
    val publicBaseUrl: String = "",
    val driverToken: String = "",
    val publicCalendarToken: String = "",
) {
    val configured: Boolean
        get() = apiBaseUrl.startsWith("https://") && driverToken.isNotBlank()

    val publicCalendarUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }
            ?.trimEnd('/')
            ?.let { base ->
                publicCalendarToken.takeIf { it.length >= 16 }
                    ?.let { token -> "$base/calendar/$token.ics" }
            }
}
