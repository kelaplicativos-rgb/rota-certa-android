package br.com.mapeiaia.rotacerta.trips

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PublishedTripResponse(
    val tripId: String,
    val publicToken: String,
    val publicUrl: String,
)

@Serializable
data class DriverRegistrationRequest(val displayName: String, val username: String)

@Serializable
data class DriverRegistrationResponse(
    val displayName: String,
    val username: String,
    val driverToken: String,
    val publicAgendaToken: String,
    val publicAgendaUrl: String,
    val calendarUrl: String,
)

@Serializable
data class RemoteBookingResponse(
    val bookingId: String,
    val cancellationToken: String? = null,
    val availableSeats: Int? = null,
)

@Serializable
data class RemoteBooking(
    val id: String,
    val tripId: String = "",
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val status: String = "CONFIRMED",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val source: BookingSource = BookingSource.OTHER,
    val capacityClaimType: CapacityClaimType = CapacityClaimType.PASSENGER,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
    val holdExpiresAtMillis: Long? = null,
)

@Serializable
data class DriverBookingsResponse(
    val bookings: List<RemoteBooking> = emptyList(),
)

@Serializable
data class PublicBookingRequest(
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
)

@Serializable
data class DriverBookingUpsertRequest(
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val status: String = BookingStatus.CONFIRMED.name,
    val holdExpiresAtMillis: Long? = null,
    val source: BookingSource = BookingSource.OTHER,
    val capacityClaimType: CapacityClaimType = CapacityClaimType.PASSENGER,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
)

@Serializable
data class DriverBookingUpsertResponse(
    val booking: RemoteBooking,
    val segmentLoads: List<Int> = emptyList(),
    val availableSeatsMinimum: Int = 0,
    val availableSeatsMaximum: Int = 0,
)

class TripRemoteApi(
    private val settings: TripOnlineSettings,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun registerDriver(displayName: String, username: String): DriverRegistrationResponse = request(
        method = "POST",
        path = "/v1/drivers/register",
        body = json.encodeToString(DriverRegistrationRequest(displayName.trim(), username.trim())),
        requireDriverToken = false,
    )

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

    suspend fun listBookings(remoteTripId: String): DriverBookingsResponse = request(
        method = "GET",
        path = "/v1/driver/trips/$remoteTripId/bookings",
        requireDriverToken = true,
    )

    suspend fun upsertDriverBooking(remoteTripId: String, booking: Booking): DriverBookingUpsertResponse = request(
        method = "PUT",
        path = "/v1/driver/trips/$remoteTripId/bookings/${booking.id}",
        body = json.encodeToString(
            DriverBookingUpsertRequest(
                passengerName = booking.passengerName,
                passengerContact = booking.passengerContact,
                boardingStopId = booking.boardingStopId,
                dropoffStopId = booking.dropoffStopId,
                seats = booking.seats,
                status = booking.status.name,
                holdExpiresAtMillis = booking.holdExpiresAtMillis,
                source = booking.source,
                capacityClaimType = booking.capacityClaimType,
                sourceReference = booking.sourceReference,
                occupancyGroupId = booking.occupancyGroupId,
            ),
        ),
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
            if (settings.publicBaseUrl.startsWith("https://")) {
                setRequestProperty("X-Rota-Certa-Public-Base-Url", settings.publicBaseUrl)
            }
            if (requireDriverToken && settings.driverUsername.isNotBlank()) setRequestProperty("X-Rota-Certa-Driver-Username", settings.driverUsername)
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

/**
 * Server contract does not yet carry passengerId/fare/exact-address fields.
 * Preserve those local-only values when a remote refresh updates the same Booking.
 */
fun RemoteBooking.toLocalBooking(localTripId: String, existingLocal: Booking? = null): Booking = Booking(
    id = id,
    tripId = localTripId,
    passengerName = passengerName,
    passengerContact = passengerContact,
    boardingStopId = boardingStopId,
    dropoffStopId = dropoffStopId,
    seats = seats,
    status = runCatching { BookingStatus.valueOf(status) }.getOrDefault(BookingStatus.CONFIRMED),
    holdExpiresAtMillis = holdExpiresAtMillis,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    source = source,
    capacityClaimType = capacityClaimType,
    sourceReference = sourceReference,
    occupancyGroupId = occupancyGroupId,
    passengerId = existingLocal?.passengerId.orEmpty(),
    fareMinorUnits = existingLocal?.fareMinorUnits,
    fareCurrencyCode = existingLocal?.fareCurrencyCode.orEmpty(),
    boardingAddress = existingLocal?.boardingAddress.orEmpty(),
    dropoffAddress = existingLocal?.dropoffAddress.orEmpty(),
)
