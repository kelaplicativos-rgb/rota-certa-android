package br.com.mapeiaia.rotacerta.trips

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import kotlinx.serialization.Serializable

@Serializable
internal data class BlaBlaNetworkWaypointSourceEvidence(
    val label: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
internal data class BlaBlaNetworkBookingSourceEvidence(
    val passengerId: String = "",
    val passengerName: String = "",
    val seats: Int = 0,
    val phone: String = "",
    val fareAmount: String = "",
    val fareCurrencyCode: String = "",
    val fareFormatted: String = "",
    val pickup: BlaBlaNetworkWaypointSourceEvidence = BlaBlaNetworkWaypointSourceEvidence(),
    val dropoff: BlaBlaNetworkWaypointSourceEvidence = BlaBlaNetworkWaypointSourceEvidence(),
)

@Serializable
internal data class BlaBlaNetworkTripSourceEvidence(
    val tripId: String = "",
    val bookingsComplete: Boolean = false,
    val bookings: List<BlaBlaNetworkBookingSourceEvidence> = emptyList(),
    val waypointsComplete: Boolean = false,
    val waypoints: List<BlaBlaNetworkWaypointSourceEvidence> = emptyList(),
)

internal data class BlaBlaNetworkResolvedBooking(
    val passengerId: String,
    val passenger: BlaBlaCollectorPassenger,
    val fareMinorUnits: Long?,
    val fareCurrencyCode: String,
    val boardingAddress: String,
    val dropoffAddress: String,
    val boardingLatitude: Double?,
    val boardingLongitude: Double?,
)

internal data class BlaBlaNetworkTripResolution(
    val tripId: String,
    val bookings: List<BlaBlaNetworkResolvedBooking>,
    val itineraryStops: List<String>,
    val itineraryAuthoritative: Boolean,
) {
    val passengers: List<BlaBlaCollectorPassenger>
        get() = bookings.map(BlaBlaNetworkResolvedBooking::passenger)

    val explicitEmpty: Boolean
        get() = bookings.isEmpty()
}

/**
 * Validates and normalizes the allowlisted network response kept in WebView
 * memory. DOM extraction remains a fallback and never competes as an equal
 * authority once an exact, complete source roster is available.
 */
internal object BlaBlaCollectorNetworkSourceModule {
    private const val MAX_BOOKINGS = 48
    private const val MAX_WAYPOINTS = 48
    private val stableIdRegex = Regex("[A-Za-z0-9_-]{8,160}")

    fun resolve(
        expectedTripId: String?,
        source: BlaBlaNetworkTripSourceEvidence?,
    ): BlaBlaNetworkTripResolution? {
        val expected = expectedTripId?.trim()?.takeIf(stableIdRegex::matches) ?: return null
        val evidence = source ?: return null
        val sourceTripId = evidence.tripId.trim().takeIf(stableIdRegex::matches) ?: return null
        if (!expected.equals(sourceTripId, ignoreCase = true) || !evidence.bookingsComplete) return null
        if (evidence.bookings.size > MAX_BOOKINGS) return null

        val resolved = evidence.bookings.map { booking ->
            resolveBooking(sourceTripId, booking) ?: return null
        }
        if (resolved.map(BlaBlaNetworkResolvedBooking::passengerId).distinct().size != resolved.size) return null

        val itineraryStops = resolveItinerary(evidence)
        return BlaBlaNetworkTripResolution(
            tripId = sourceTripId,
            bookings = resolved,
            itineraryStops = itineraryStops,
            itineraryAuthoritative = evidence.waypointsComplete && itineraryStops.size >= 2,
        )
    }

    fun parseCanonicalMinorUnits(rawAmount: String?, rawCurrencyCode: String?): Long? {
        val amount = rawAmount?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!amount.matches(Regex("[0-9]{1,10}(?:\\.[0-9]{1,3})?"))) return null
        val currencyCode = normalizePassengerFareCurrency(rawCurrencyCode) ?: return null
        val fractionDigits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
            .getOrNull()
            ?.takeIf { it in 0..3 }
            ?: return null
        return runCatching {
            BigDecimal(amount)
                .multiply(BigDecimal.TEN.pow(fractionDigits))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
                .takeIf { it in 1L..1_000_000_000_000L }
        }.getOrNull()
    }

    private fun resolveItinerary(evidence: BlaBlaNetworkTripSourceEvidence): List<String> {
        if (!evidence.waypointsComplete || evidence.waypoints.size !in 2..MAX_WAYPOINTS) return emptyList()
        return evidence.waypoints
            .map { waypoint -> waypoint.routeLabel() }
            .filter(String::isNotBlank)
            .fold(mutableListOf()) { ordered, label ->
                if (ordered.lastOrNull() != label) ordered += label
                ordered
            }
            .toList()
            .takeIf { it.size >= 2 }
            .orEmpty()
    }

    private fun resolveBooking(
        tripId: String,
        evidence: BlaBlaNetworkBookingSourceEvidence,
    ): BlaBlaNetworkResolvedBooking? {
        val passengerId = evidence.passengerId.trim().takeIf(stableIdRegex::matches) ?: return null
        val passengerName = evidence.passengerName.trim().take(120).takeIf(String::isNotEmpty) ?: return null
        val seats = evidence.seats.takeIf { it in 1..20 } ?: return null
        val bookingHref = BlaBlaCollectorUrlModule.passengerPage(passengerId, tripId) ?: return null
        val boarding = evidence.pickup.routeLabel().takeIf(String::isNotEmpty) ?: return null
        val dropoff = evidence.dropoff.routeLabel().takeIf(String::isNotEmpty) ?: return null
        val currencyCode = normalizePassengerFareCurrency(evidence.fareCurrencyCode).orEmpty()
        val fareMinorUnits = parseCanonicalMinorUnits(evidence.fareAmount, currencyCode)

        return BlaBlaNetworkResolvedBooking(
            passengerId = passengerId,
            passenger = BlaBlaCollectorPassenger(
                name = passengerName,
                seats = seats,
                boarding = boarding,
                dropoff = dropoff,
                phone = BlaBlaCollectorPassengerModule.normalizePhone(evidence.phone),
                booking_href = bookingHref,
            ),
            fareMinorUnits = fareMinorUnits,
            fareCurrencyCode = currencyCode.takeIf { fareMinorUnits != null }.orEmpty(),
            boardingAddress = evidence.pickup.address.trim().take(500),
            dropoffAddress = evidence.dropoff.address.trim().take(500),
            boardingLatitude = validLatitude(evidence.pickup.latitude),
            boardingLongitude = validLongitude(evidence.pickup.longitude),
        )
    }

    private fun BlaBlaNetworkWaypointSourceEvidence.routeLabel(): String =
        label.trim().take(240).ifBlank { address.trim().take(240) }
}
