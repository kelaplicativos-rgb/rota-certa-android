package br.com.mapeiaia.rotacerta.trips

import java.text.Normalizer
import kotlinx.serialization.Serializable

/**
 * Pure helpers for the authoritative HTML passenger-depth pass.
 */
@Serializable
internal data class BlaBlaTripStopLocation0659(
    val label: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

internal fun passengerDeepCaptureTarget0653(
    passenger: BlaBlaCollectorPassenger,
    passengerIndex: Int,
    passengerHrefs: List<String>,
): String? {
    val direct = BlaBlaCollectorUrlModule.absolute(passenger.booking_href)
        .takeIf(BlaBlaCollectorUrlModule::isPassenger)
    if (direct != null) return direct

    val placeholder = "rotacerta-card:$passengerIndex"
    if (passengerHrefs.any { it.trim() == placeholder }) return placeholder

    return null
}

internal fun passengerDeepCaptureComplete0653(
    expectedPassengers: Int,
    resolvedPassengers: Int,
): Boolean =
    expectedPassengers >= 0 &&
        resolvedPassengers >= 0 &&
        expectedPassengers == resolvedPassengers

internal fun passengerPrivateEvidenceNeedsRetry0657(
    phone: String?,
    fareMinorUnits: Long?,
    boardingSegment: String?,
    dropoffSegment: String?,
): Boolean =
    phone.isNullOrBlank() ||
        fareMinorUnits == null ||
        boardingSegment.isNullOrBlank() ||
        dropoffSegment.isNullOrBlank()

internal fun passengerPageBelongsToTrip0653(
    passengerUrl: String?,
    tripId: String,
): Boolean {
    val value = passengerUrl?.trim().orEmpty()
    return BlaBlaCollectorUrlModule.isPassenger(value) &&
        BlaBlaCollectorUrlModule.tripId(value)?.trim() == tripId.trim()
}

internal fun parsePassengerFareMinorUnits0653(values: Iterable<String?>): Long? {
    values.forEach { raw ->
        val value = raw
            ?.replace('\u00A0', ' ')
            ?.replace('\u202F', ' ')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return@forEach
        val match = MONEY_PREFIX_0659.find(value)
            ?: MONEY_SUFFIX_0659.find(value)
            ?: MONEY_PLAIN_0659.matchEntire(value)
            ?: return@forEach
        val integerPart = match.groupValues[1].replace(".", "")
        val whole = integerPart.toLongOrNull() ?: return@forEach
        val centsText = match.groupValues[2]
        val cents = when (centsText.length) {
            0 -> 0L
            1 -> (centsText + "0").toLongOrNull() ?: 0L
            else -> centsText.take(2).toLongOrNull() ?: 0L
        }
        return whole * 100L + cents
    }
    return null
}

internal fun passengerFareCurrency0653(
    explicitCurrency: String?,
    fareValues: Iterable<String?>,
): String {
    val explicit = explicitCurrency?.trim()?.uppercase().orEmpty()
    if (explicit.length == 3) return explicit
    return if (fareValues.any { it?.contains("R$") == true }) "BRL" else ""
}

internal fun passengerTripStopLocation0659(
    segment: String?,
    stopLocations: List<BlaBlaTripStopLocation0659>,
): BlaBlaTripStopLocation0659? {
    val wanted = passengerPlaceKey0659(segment.orEmpty())
    if (wanted.isBlank()) return null

    fun valid(stop: BlaBlaTripStopLocation0659): Boolean {
        val latitude = stop.latitude ?: return false
        val longitude = stop.longitude ?: return false
        return stop.address.isNotBlank() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
    }

    val exact = stopLocations
        .filter(::valid)
        .filter { passengerPlaceKey0659(it.label) == wanted }
        .distinctBy { listOf(it.label, it.address, it.latitude, it.longitude).joinToString("|") }
    if (exact.size == 1) return exact.single()
    if (exact.size > 1) return null

    val compatible = stopLocations
        .filter(::valid)
        .filter { stop ->
            val label = passengerPlaceKey0659(stop.label)
            label.isNotBlank() && (
                label == wanted ||
                    label.startsWith("$wanted ") ||
                    wanted.startsWith("$label ")
                )
        }
        .distinctBy { listOf(it.label, it.address, it.latitude, it.longitude).joinToString("|") }
    return compatible.singleOrNull()
}

internal fun passengerOperationalEvidenceComplete0659(
    phone: String?,
    fareMinorUnits: Long?,
    boardingSegment: String?,
    dropoffSegment: String?,
    boardingStop: BlaBlaTripStopLocation0659?,
    dropoffStop: BlaBlaTripStopLocation0659?,
): Boolean =
    !passengerPrivateEvidenceNeedsRetry0657(
        phone = phone,
        fareMinorUnits = fareMinorUnits,
        boardingSegment = boardingSegment,
        dropoffSegment = dropoffSegment,
    ) &&
        boardingStop != null &&
        dropoffStop != null &&
        passengerPlaceKey0659(boardingStop.label) != passengerPlaceKey0659(dropoffStop.label)

private fun passengerPlaceKey0659(value: String): String = Normalizer
    .normalize(value.substringBefore(',').trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private const val AMOUNT_PATTERN_0659 = "([0-9]{1,3}(?:\\.[0-9]{3})*|[0-9]+)(?:,([0-9]{1,2}))?"
private val MONEY_PREFIX_0659 = Regex("R\\$\\s*$AMOUNT_PATTERN_0659", RegexOption.IGNORE_CASE)
private val MONEY_SUFFIX_0659 = Regex("$AMOUNT_PATTERN_0659\\s*R\\$", RegexOption.IGNORE_CASE)
private val MONEY_PLAIN_0659 = Regex("^\\s*$AMOUNT_PATTERN_0659\\s*$", RegexOption.IGNORE_CASE)
