package br.com.mapeiaia.rotacerta.trips

import java.text.Normalizer
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A verified external account identity used only after the real profile UUID is known. */
data class BlaBlaAccountDefinition(
    val slot: String,
    val label: String,
    val uuid: String,
    val dataDirectorySuffix: String,
)

@Serializable
data class BlaBlaDomRideCandidate(
    val href: String = "",
    val text: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val dateText: String = "",
    val passengers: List<BlaBlaCollectorPassenger> = emptyList(),
    @SerialName("passengerRosterComplete")
    val passengerRosterReportedComplete: Boolean = false,
) {
    val passengerRosterComplete: Boolean
        get() = passengerRosterReportedComplete
}

@Serializable
data class BlaBlaDomTripDetail(
    val url: String = "",
    val bodyText: String = "",
    val dateText: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val driverName: String = "",
    val profileLinks: List<String> = emptyList(),
    val passengers: List<BlaBlaCollectorPassenger> = emptyList(),
    val passengerRosterComplete: Boolean = false,
)

object BlaBlaDomNormalizer {
    private val uuidRegex = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val timeRegex = Regex("(?<!\\d)([01]?\\d|2[0-3]):[0-5]\\d(?!\\d)")
    private val isoDateRegex = Regex("(?<!\\d)(20\\d{2})-(0?[1-9]|1[0-2])-([0-2]?\\d|3[01])(?!\\d)")
    private val numericDateRegex = Regex("(?<!\\d)([0-2]?\\d|3[01])[/.-](0?[1-9]|1[0-2])(?:[/.-](20\\d{2}|\\d{2}))?(?!\\d)")
    private val monthDateRegex = Regex("(?i)([0-2]?\\d|3[01])\\s*(?:de\\s+)?([a-zçãáâéêíóôõú]{3,12})(?:\\s*(?:de\\s+)?(20\\d{2}))?")
    private val months = mapOf(
        "jan" to 1, "janeiro" to 1,
        "fev" to 2, "fevereiro" to 2,
        "mar" to 3, "marco" to 3,
        "abr" to 4, "abril" to 4,
        "mai" to 5, "maio" to 5,
        "jun" to 6, "junho" to 6,
        "jul" to 7, "julho" to 7,
        "ago" to 8, "agosto" to 8,
        "set" to 9, "setembro" to 9,
        "out" to 10, "outubro" to 10,
        "nov" to 11, "novembro" to 11,
        "dez" to 12, "dezembro" to 12,
    )

    fun profileUuids(detail: BlaBlaDomTripDetail): Set<String> = detail.profileLinks
        .flatMap { link -> uuidRegex.findAll(link).map { it.value.lowercase() }.toList() }
        .toSet()

    fun isExpectedProfile(account: BlaBlaAccountDefinition, detail: BlaBlaDomTripDetail): Boolean =
        account.uuid.lowercase() in profileUuids(detail)

    fun toTrip(
        account: BlaBlaAccountDefinition,
        candidate: BlaBlaDomRideCandidate,
        detail: BlaBlaDomTripDetail,
        today: LocalDate = LocalDate.now(),
        authenticatedProfileSessionVerified: Boolean = false,
    ): BlaBlaCollectorTrip? {
        val detailUuids = profileUuids(detail)
        val expectedUuid = account.uuid.lowercase()
        val uuidValidation = when {
            expectedUuid in detailUuids -> "verified_from_trip_detail_profile_link"
            detailUuids.isNotEmpty() -> return null
            authenticatedProfileSessionVerified -> "verified_from_authenticated_profile_session"
            else -> return null
        }
        val date = parseDate(
            listOf(detail.dateText, candidate.dateText, candidate.text, detail.bodyText.take(6000)).joinToString(" | "),
            today,
        ) ?: return null
        val departureTime = normalizeTime(detail.departureTime.ifBlank { candidate.departureTime })
            ?: timeRegex.find(candidate.text)?.value?.let(::normalizeTime)
            ?: return null
        val timeValues = timeRegex.findAll(candidate.text).map { it.value }.toList()
        val arrivalTime = normalizeTime(detail.arrivalTime.ifBlank { candidate.arrivalTime })
            ?: timeValues.drop(1).firstOrNull()?.let(::normalizeTime)
        val origin = detail.origin.trim().ifBlank { candidate.origin.trim() }
        val destination = detail.destination.trim().ifBlank { candidate.destination.trim() }
        if (origin.isBlank() || destination.isBlank()) return null
        val allText = "${candidate.text} ${detail.bodyText}"
        val full = listOf("cheio", "esgotad", "sem vagas", "indisponível", "indisponivel").any { token ->
            normalize(allText).contains(token)
        }
        val href = detail.url.takeIf(String::isNotBlank) ?: candidate.href
        val passengers = mergePassengerEvidence(candidate.passengers, detail.passengers)
        return BlaBlaCollectorTrip(
            profile_uuid = account.uuid,
            profile_name = account.label,
            date = date.toString(),
            departure_time = departureTime,
            arrival_time = arrivalTime,
            actual_departure = origin,
            actual_arrival = destination,
            price = detail.price.trim().ifBlank { candidate.price.trim() }.takeIf(String::isNotBlank),
            flags = if (full) listOf("Cheio") else emptyList(),
            availability = if (full) "full" else "unknown",
            trip_href = href.takeIf(String::isNotBlank),
            trip_id = tripId(href),
            uuid_validation = uuidValidation,
            passengers = passengers,
            booked_seats = passengers.sumOf(BlaBlaCollectorPassenger::seats),
            passenger_roster_complete = candidate.passengerRosterComplete || detail.passengerRosterComplete,
        )
    }

    fun parseDate(textRaw: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val text = normalize(textRaw)
        isoDateRegex.find(text)?.let { match ->
            explicitDate(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())?.let { return it }
        }
        numericDateRegex.findAll(text)
            .firstOrNull { it.groupValues[3].isNotBlank() }
            ?.let { match ->
                val yearText = match.groupValues[3]
                val year = if (yearText.length == 2) 2000 + yearText.toInt() else yearText.toInt()
                explicitDate(year, match.groupValues[2].toInt(), match.groupValues[1].toInt())?.let { return it }
            }
        monthDateRegex.findAll(text)
            .firstOrNull { it.groupValues[3].isNotBlank() }
            ?.let { match ->
                val day = match.groupValues[1].toIntOrNull() ?: return@let
                val month = monthNumber(match.groupValues[2]) ?: return@let
                val year = match.groupValues[3].toIntOrNull() ?: return@let
                explicitDate(year, month, day)?.let { return it }
            }

        if (Regex("\\bhoje\\b").containsMatchIn(text)) return today
        if (Regex("\\bamanha\\b").containsMatchIn(text)) return today.plusDays(1)

        numericDateRegex.findAll(text)
            .firstOrNull { it.groupValues[3].isBlank() }
            ?.let { match ->
                inferredDate(today, match.groupValues[2].toInt(), match.groupValues[1].toInt())?.let { return it }
            }
        monthDateRegex.findAll(text)
            .firstOrNull { it.groupValues[3].isBlank() }
            ?.let { match ->
                val day = match.groupValues[1].toIntOrNull() ?: return@let
                val month = monthNumber(match.groupValues[2]) ?: return@let
                inferredDate(today, month, day)?.let { return it }
            }
        return null
    }

    private fun explicitDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun inferredDate(today: LocalDate, month: Int, day: Int): LocalDate? = runCatching {
        var value = LocalDate.of(today.year, month, day)
        if (value.isBefore(today.minusMonths(3))) value = value.plusYears(1)
        value
    }.getOrNull()

    private fun monthNumber(raw: String): Int? {
        val key = normalize(raw).takeWhile(Char::isLetter)
        return months[key] ?: months.entries.firstOrNull { key.startsWith(it.key) }?.value
    }

    private fun mergePassengerEvidence(
        batch: List<BlaBlaCollectorPassenger>,
        detail: List<BlaBlaCollectorPassenger>,
    ): List<BlaBlaCollectorPassenger> {
        if (batch.isEmpty()) return detail
        if (detail.isEmpty()) return batch
        val merged = batch.toMutableList()
        detail.forEach { incoming ->
            val index = merged.indexOfFirst { existing -> passengerEvidenceMatches(existing, incoming) }
            if (index < 0) {
                merged += incoming
            } else {
                val existing = merged[index]
                merged[index] = existing.copy(
                    name = incoming.name.ifBlank { existing.name },
                    seats = maxOf(existing.seats, incoming.seats),
                    boarding = incoming.boarding?.takeIf(String::isNotBlank) ?: existing.boarding,
                    dropoff = incoming.dropoff?.takeIf(String::isNotBlank) ?: existing.dropoff,
                    phone = incoming.phone?.takeIf(String::isNotBlank) ?: existing.phone,
                    booking_href = incoming.booking_href?.takeIf(String::isNotBlank) ?: existing.booking_href,
                )
            }
        }
        return merged
    }

    private fun passengerEvidenceMatches(left: BlaBlaCollectorPassenger, right: BlaBlaCollectorPassenger): Boolean {
        val leftHref = left.booking_href?.trim().orEmpty()
        val rightHref = right.booking_href?.trim().orEmpty()
        if (leftHref.isNotBlank() && rightHref.isNotBlank()) return leftHref == rightHref
        val leftPhone = left.phone?.filter(Char::isDigit).orEmpty()
        val rightPhone = right.phone?.filter(Char::isDigit).orEmpty()
        if (leftPhone.length >= 8 && rightPhone.length >= 8) return leftPhone == rightPhone
        return normalize(left.name) == normalize(right.name) &&
            left.seats == right.seats &&
            normalize(left.boarding.orEmpty()) == normalize(right.boarding.orEmpty()) &&
            normalize(left.dropoff.orEmpty()) == normalize(right.dropoff.orEmpty())
    }

    private fun normalizeTime(value: String): String? {
        val match = timeRegex.find(value.trim()) ?: return null
        val parts = match.value.split(':')
        return "%02d:%02d".format(parts[0].toInt(), parts[1].toInt())
    }

    private fun tripId(href: String): String? = Regex("[?&]id=([^&#]+)").find(href)?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
}
