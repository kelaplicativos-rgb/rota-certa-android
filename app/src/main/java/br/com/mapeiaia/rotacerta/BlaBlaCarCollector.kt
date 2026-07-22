package br.com.mapeiaia.rotacerta

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class BlaBlaCarTripRecord(
    val title: String = "",
    val dateTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val distanceKm: String = "",
    val tripUrl: String = "",
    val notes: String = "",
    val passengers: List<BlaBlaCarPassenger> = emptyList(),
    val expenses: List<BlaBlaCarExpense> = emptyList(),
)

@Serializable
data class BlaBlaCarPassenger(
    val name: String = "",
    val phone: String = "",
    val pickup: String = "",
    val dropoff: String = "",
    val seats: Int = 1,
    val fareText: String = "",
    val status: String = "Confirmado",
)

@Serializable
data class BlaBlaCarExpense(
    val label: String = "",
    val amountText: String = "",
)

object BlaBlaCarCollectorCalculator {
    fun totalRevenue(record: BlaBlaCarTripRecord): Double =
        record.passengers.sumOf { passenger ->
            parseMoney(passenger.fareText) * passenger.seats.coerceAtLeast(1)
        }

    fun totalExpenses(record: BlaBlaCarTripRecord): Double =
        record.expenses.sumOf { expense -> parseMoney(expense.amountText) }

    fun profit(record: BlaBlaCarTripRecord): Double = totalRevenue(record) - totalExpenses(record)

    fun profitPerKm(record: BlaBlaCarTripRecord): Double? {
        val km = parseDistanceKm(record.distanceKm).takeIf { it > 0.0 } ?: return null
        return profit(record) / km
    }

    fun parseMoney(value: String): Double {
        val cleaned = value
            .replace("R$", "", ignoreCase = true)
            .replace(Regex("[^0-9,.-]"), "")
            .trim()
        if (cleaned.isBlank()) return 0.0
        val normalized = if (cleaned.contains(',')) {
            cleaned.replace(".", "").replace(',', '.')
        } else {
            cleaned
        }
        return normalized.toDoubleOrNull() ?: 0.0
    }

    fun parseDistanceKm(value: String): Double =
        value
            .replace("km", "", ignoreCase = true)
            .replace(Regex("[^0-9,.-]"), "")
            .replace(".", "")
            .replace(',', '.')
            .toDoubleOrNull() ?: 0.0

    fun formatMoney(value: Double): String =
        String.format(Locale("pt", "BR"), "R$ %.2f", value)
}

object BlaBlaCarCollectorParser {
    private val phoneRegex = Regex("""(?:\+?55\s*)?(?:\(?\d{2}\)?\s*)?\d{4,5}[-\s]?\d{4}""")
    private val moneyRegex = Regex("""R\$\s*\d+(?:[.,]\d{2})?""", RegexOption.IGNORE_CASE)

    fun parsePassengers(rawText: String): List<BlaBlaCarPassenger> {
        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        return lines.mapIndexedNotNull { index, line ->
            val phone = phoneRegex.find(line)?.value ?: return@mapIndexedNotNull null
            val sameLineFare = moneyRegex.find(line)?.value.orEmpty()
            val nearbyFare = sameLineFare.ifBlank {
                lines.drop(index + 1).take(3).firstNotNullOfOrNull { moneyRegex.find(it)?.value }.orEmpty()
            }
            val name = previousName(lines, index)
            BlaBlaCarPassenger(
                name = name,
                phone = normalizePhone(phone),
                fareText = nearbyFare,
            )
        }.distinctBy { it.phone }
    }

    fun normalizePhone(value: String): String = value.filter { it.isDigit() }.let { digits ->
        when {
            digits.startsWith("55") -> digits
            digits.length in 10..11 -> "55$digits"
            else -> digits
        }
    }

    fun whatsappUrl(phone: String, message: String): String {
        val normalizedPhone = normalizePhone(phone)
        val encoded = java.net.URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        return "https://wa.me/$normalizedPhone?text=$encoded"
    }

    fun mapsSearchUrl(value: String): String {
        val encoded = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return "https://www.google.com/maps/search/?api=1&query=$encoded"
    }

    fun mapsDirectionsUrl(record: BlaBlaCarTripRecord): String {
        val origin = java.net.URLEncoder.encode(record.origin, "UTF-8").replace("+", "%20")
        val destination = java.net.URLEncoder.encode(record.destination, "UTF-8").replace("+", "%20")
        return "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&travelmode=driving"
    }

    fun summary(record: BlaBlaCarTripRecord): String = buildString {
        appendLine("BlaBlaCar - resumo da viagem")
        appendLine("Data: ${record.dateTime.ifBlank { "nao informada" }}")
        appendLine("Origem: ${record.origin.ifBlank { "nao informada" }}")
        appendLine("Destino: ${record.destination.ifBlank { "nao informado" }}")
        if (record.distanceKm.isNotBlank()) appendLine("Distancia: ${record.distanceKm}")
        appendLine("Passageiros: ${record.passengers.size}")
        record.passengers.forEach { passenger ->
            appendLine("- ${passenger.name.ifBlank { "Passageiro" }} | ${passenger.phone.ifBlank { "sem telefone" }} | ${passenger.fareText.ifBlank { "sem valor" }} | ${passenger.status}")
        }
        val revenue = BlaBlaCarCollectorCalculator.totalRevenue(record)
        val expenses = BlaBlaCarCollectorCalculator.totalExpenses(record)
        val profit = BlaBlaCarCollectorCalculator.profit(record)
        appendLine("Faturamento: ${BlaBlaCarCollectorCalculator.formatMoney(revenue)}")
        appendLine("Despesas: ${BlaBlaCarCollectorCalculator.formatMoney(expenses)}")
        appendLine("Lucro: ${BlaBlaCarCollectorCalculator.formatMoney(profit)}")
        record.notes.takeIf { it.isNotBlank() }?.let { appendLine("Obs: $it") }
    }

    private fun previousName(lines: List<String>, phoneLineIndex: Int): String {
        return lines
            .take(phoneLineIndex)
            .asReversed()
            .firstOrNull { candidate ->
                phoneRegex.find(candidate) == null &&
                    moneyRegex.find(candidate) == null &&
                    candidate.length in 2..60 &&
                    !candidate.contains("http", ignoreCase = true)
            }.orEmpty()
    }
}
