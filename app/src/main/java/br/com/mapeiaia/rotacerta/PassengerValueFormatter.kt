package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

data class PassengerValueData(
    val passengerName: String,
    val seats: Int,
    val origin: String,
    val destination: String,
    val amountCents: Long,
)

object PassengerValueFormatter {
    fun extractAndFormat(rawText: String): String? = extract(rawText)?.let(::format)

    fun extract(rawText: String): PassengerValueData? {
        val lines = rawText
            .replace('\u00A0', ' ')
            .lineSequence()
            .map(::cleanLine)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (lines.isEmpty()) return null

        val route = extractRoute(lines) ?: return null
        val seats = extractSeats(lines) ?: return null
        val amount = extractSingleAmount(lines) ?: return null
        val name = extractPassengerName(lines, route) ?: return null
        return PassengerValueData(
            passengerName = name,
            seats = seats,
            origin = route.first,
            destination = route.second,
            amountCents = amount,
        )
    }

    fun format(data: PassengerValueData): String {
        val seatLabel = if (data.seats == 1) "1 lugar" else "${data.seats} lugares"
        return "Olá, ${data.passengerName}! O valor exibido para sua reserva de $seatLabel, " +
            "de ${data.origin} para ${data.destination}, é ${formatCurrency(data.amountCents)}."
    }

    fun formatCurrency(amountCents: Long): String {
        val absolute = kotlin.math.abs(amountCents)
        val reais = absolute / 100L
        val cents = (absolute % 100L).toString().padStart(2, '0')
        val grouped = reais.toString().reversed().chunked(3).joinToString(".").reversed()
        val sign = if (amountCents < 0L) "-" else ""
        return "${sign}R$ $grouped,$cents"
    }

    fun normalizedIdentity(data: PassengerValueData): String = listOf(
        normalize(data.passengerName),
        normalize(data.origin),
        normalize(data.destination),
        data.seats.toString(),
    ).joinToString("|")

    private fun extractSeats(lines: List<String>): Int? {
        val values = lines.mapNotNull { line ->
            SEATS_REGEX.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..8 }
        }.distinct()
        return values.singleOrNull()
    }

    private fun extractSingleAmount(lines: List<String>): Long? {
        val amounts = linkedSetOf<Long>()
        lines.forEach { line ->
            DIRECT_AMOUNT_REGEX.findAll(line).forEach { match ->
                parseAmount(match.groupValues[1], match.groupValues.getOrNull(2).orEmpty())?.let(amounts::add)
            }
        }
        lines.indices.forEach { index ->
            if (!CURRENCY_ONLY_REGEX.matches(lines[index])) return@forEach
            val next = lines.getOrNull(index + 1).orEmpty()
            val direct = AMOUNT_WITH_OPTIONAL_CENTS_REGEX.matchEntire(next)
            if (direct != null) {
                parseAmount(direct.groupValues[1], direct.groupValues.getOrNull(2).orEmpty())?.let(amounts::add)
            }
            val integerPart = INTEGER_AMOUNT_REGEX.matchEntire(next)?.groupValues?.getOrNull(1)
            val centsPart = CENTS_ONLY_REGEX.matchEntire(lines.getOrNull(index + 2).orEmpty())?.groupValues?.getOrNull(1)
            if (integerPart != null && centsPart != null) {
                parseAmount(integerPart, centsPart)?.let(amounts::add)
            }
        }
        return amounts.singleOrNull()
    }

    private fun parseAmount(integerRaw: String, centsRaw: String): Long? {
        val integerDigits = integerRaw.replace(".", "").filter(Char::isDigit)
        if (integerDigits.isBlank() || integerDigits.length > 7) return null
        val integer = integerDigits.toLongOrNull() ?: return null
        val cents = centsRaw.filter(Char::isDigit).ifBlank { "00" }.toIntOrNull() ?: return null
        if (cents !in 0..99) return null
        val value = integer * 100L + cents
        return value.takeIf { it in 100L..10_000_000L }
    }

    private fun extractRoute(lines: List<String>): Pair<String, String>? {
        val routes = lines.mapNotNull { line ->
            val arrow = ARROW_REGEX.find(line) ?: return@mapNotNull null
            val origin = cleanLocation(line.substring(0, arrow.range.first))
            val destination = cleanLocation(line.substring(arrow.range.last + 1))
            if (origin.isBlank() || destination.isBlank() || origin.equals(destination, ignoreCase = true)) null
            else origin to destination
        }.distinct()
        return routes.singleOrNull()
    }

    private fun extractPassengerName(lines: List<String>, route: Pair<String, String>): String? {
        val searchNames = lines.mapNotNull { line ->
            SEARCH_PASSENGER_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let(::cleanPersonName)
        }.filter(::isPlausibleName).distinct()
        if (searchNames.size == 1) return searchNames.single()
        if (searchNames.size > 1) return null

        val ratingIndex = lines.indexOfFirst { RATING_REGEX.containsMatchIn(it) }
        val seatIndex = lines.indexOfFirst { SEATS_REGEX.containsMatchIn(it) }
        val routeIndex = lines.indexOfFirst { ARROW_REGEX.containsMatchIn(it) }
        val upperBound = listOf(ratingIndex, seatIndex, routeIndex).filter { it >= 0 }.minOrNull() ?: lines.size
        val excluded = setOf(normalize(route.first), normalize(route.second))
        val candidates = lines.take(upperBound).map(::cleanPersonName).filter { candidate ->
            isPlausibleName(candidate) && normalize(candidate) !in excluded
        }.distinct()
        return candidates.lastOrNull()
    }

    private fun isPlausibleName(value: String): Boolean {
        if (value.length !in 2..40 || value.any(Char::isDigit)) return false
        if (!NAME_REGEX.matches(value) || ARROW_REGEX.containsMatchIn(value)) return false
        val normalized = normalize(value)
        return normalized !in NAME_NOISE && NAME_NOISE.none { noise -> normalized.startsWith("$noise ") }
    }

    private fun cleanPersonName(value: String): String = cleanLine(value).trim(',', '.', ':', '-', '•', '·')

    private fun cleanLocation(value: String): String = cleanLine(value)
        .trim('•', '·', '-', '–', '—', '>', '<', ',', ';', ':')
        .take(90)
        .trim()

    private fun cleanLine(value: String): String = value
        .replace(Regex("[\\t\\r]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale("pt", "BR")).trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private val ARROW_REGEX = Regex("(?:→|➜|➡|->|›)")
    private val SEATS_REGEX = Regex("(?i)\\b(\\d{1,2})\\s+lugar(?:es)?\\b")
    private val DIRECT_AMOUNT_REGEX = Regex("(?i)^R\\$\\s*(\\d{1,7}(?:\\.\\d{3})*)(?:\\s*,\\s*(\\d{2}))?\\s*$")
    private val CURRENCY_ONLY_REGEX = Regex("(?i)^R\\$$")
    private val AMOUNT_WITH_OPTIONAL_CENTS_REGEX = Regex("^(\\d{1,7}(?:\\.\\d{3})*)(?:\\s*,\\s*(\\d{2}))?$")
    private val INTEGER_AMOUNT_REGEX = Regex("^(\\d{1,7}(?:\\.\\d{3})*)$")
    private val CENTS_ONLY_REGEX = Regex("^,?\\s*(\\d{2})$")
    private val SEARCH_PASSENGER_REGEX = Regex("(?i)^buscar\\s+([\\p{L} .'’-]{2,40})$")
    private val RATING_REGEX = Regex("(?i)\\b\\d(?:[,.]\\d)?/5\\b|avaliaç(?:ão|ões)")
    private val NAME_REGEX = Regex("^[\\p{L}][\\p{L} .'’_-]{1,39}$")
    private val NAME_NOISE = setOf(
        "blablacar", "enviar mensagem", "ligar", "reportar", "confirmado", "cancelar reserva",
        "resumo da viagem", "obter ajuda", "lugares", "lugar", "avaliacao", "valor", "financeiro",
    )
}
