package br.com.mapeiaia.rotacerta.trips

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PublishedTripResponse(
    val tripId: String,
    val publicToken: String,
    val publicUrl: String,
)

@Serializable
data class RemoteBookingResponse(
    val bookingId: String,
    val cancellationToken: String? = null,
    val availableSeats: Int? = null,
)

@Serializable
data class PublicBookingRequest(
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
)

class TripRemoteApi(
    private val settings: TripOnlineSettings,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun publish(trip: Trip): PublishedTripResponse = request(
        method = "POST",
        path = "/v1/driver/trips",
        body = json.encodeToString(trip),
        requireDriverToken = true,
    )

    suspend fun update(trip: Trip): PublishedTripResponse = request(
        method = "PUT",
        path = "/v1/driver/trips/${trip.remoteId ?: trip.id}",
        body = json.encodeToString(trip),
        requireDriverToken = true,
    )

    suspend fun createPublicBooking(
        publicToken: String,
        request: PublicBookingRequest,
    ): RemoteBookingResponse = request(
        method = "POST",
        path = "/v1/public/trips/$publicToken/bookings",
        body = json.encodeToString(request),
        requireDriverToken = false,
    )

    private suspend inline fun <reified T> request(
        method: String,
        path: String,
        body: String? = null,
        requireDriverToken: Boolean,
    ): T = withContext(Dispatchers.IO) {
        check(settings.apiBaseUrl.startsWith("https://")) { "Servidor HTTPS não configurado" }
        if (requireDriverToken) check(settings.driverToken.isNotBlank()) { "Chave do motorista não configurada" }
        val base = settings.apiBaseUrl.trimEnd('/')
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (requireDriverToken) setRequestProperty("X-Rota-Certa-Driver-Token", settings.driverToken)
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Servidor respondeu HTTP $status: ${responseText.take(240)}")
            }
            json.decodeFromString<T>(responseText)
        } finally {
            connection.disconnect()
        }
    }
}
