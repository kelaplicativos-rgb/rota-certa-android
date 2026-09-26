package br.com.mapeiaia.rotacerta.trips

/**
 * 0.1.653 — pure helpers for the authoritative HTML passenger-depth pass.
 *
 * These functions are intentionally side-effect free so target association and completion
 * semantics can be regression-tested without a WebView.
 */
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
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
        val match = MONEY_0653.find(value) ?: return@forEach
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

private val MONEY_0653 = Regex(
    "([0-9]{1,3}(?:\\\\.[0-9]{3})*|[0-9]+)(?:,([0-9]{1,2}))?",
    RegexOption.IGNORE_CASE,
)
