package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

data class TripConfirmationData(
    val passengerName: String?,
    val origin: String,
    val destination: String,
    val weekday: String,
    val dayOfMonth: Int,
    val month: String,
    val hour: Int,
    val minute: Int,
)

/**
 * Extrai somente os dados necessários para a confirmação manual da viagem.
 * Ruídos de conversas, duração de áudio, avaliações, preços e horários de
 * mensagens são descartados. Nenhum texto bruto é persistido.
 */
object TripConfirmationFormatter {
    fun extractAndFormat(rawText: String): String? = extract(rawText)?.let(::format)

    fun extract(rawText: String): TripConfirmationData? {
        val lines = rawText
            .replace('\u00A0', ' ')
            .lineSequence()
            .map(::cleanLine)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (lines.isEmpty()) return null

        val route = extractRoute(lines) ?: return null
        val schedule = extractSchedule(lines.joinToString(" ")) ?: return null
        return TripConfirmationData(
            passengerName = extractPassengerName(lines),
            origin = route.first,
            destination = route.second,
            weekday = schedule.weekday,
            dayOfMonth = schedule.dayOfMonth,
            month = schedule.month,
            hour = schedule.hour,
            minute = schedule.minute,
        )
    }

    fun format(data: TripConfirmationData): String {
        val heading = data.passengerName
            ?.takeIf(String::isNotBlank)
            ?.let { name -> "Olá, $name! Confirmando sua viagem:" }
            ?: "Confirmando sua viagem:"
        val time = if (data.minute == 0) {
            "${data.hour}h"
        } else {
            String.format(Locale("pt", "BR"), "%dh%02d", data.hour, data.minute)
        }
        return buildString {
            appendLine(heading)
            appendLine()
            appendLine("${data.origin} → ${data.destination}")
            appendLine("${data.weekday}, ${data.dayOfMonth} de ${data.month}, às $time.")
            appendLine()
            append("Está tudo certo?")
        }
    }

    private fun extractRoute(lines: List<String>): Pair<String, String>? {
        lines.forEachIndexed { index, line ->
            val arrow = ARROW_REGEX.find(line) ?: return@forEachIndexed
            val left = line.substring(0, arrow.range.first).trim()
            val right = line.substring(arrow.range.last + 1).trim()
            val origin = cleanLocation(
                left.ifBlank { lines.getOrNull(index - 1).orEmpty() },
            )
            if (origin.isBlank()) return@forEachIndexed

            val destinationParts = mutableListOf<String>()
            if (right.isNotBlank()) destinationParts += right
            var nextIndex = index + 1
            while (nextIndex < lines.size && destinationParts.size < MAX_ROUTE_LINES) {
                val candidate = lines[nextIndex]
                if (isRouteStop(candidate)) break
                destinationParts += candidate
                nextIndex += 1
            }
            val destination = cleanLocation(cutAtRouteStop(destinationParts.joinToString(" ")))
            if (destination.isNotBlank() && !origin.equals(destination, ignoreCase = true)) {
                return origin to destination
            }
        }
        return null
    }

    private fun extractSchedule(text: String): Schedule? {
        val match = SCHEDULE_REGEX.find(text) ?: return null
        val weekday = WEEKDAYS[normalizeWord(match.groupValues[1])] ?: return null
        val day = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        val month = MONTHS[normalizeWord(match.groupValues[3])] ?: return null
        val hour = match.groupValues[4].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = match.groupValues[5].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return Schedule(weekday, day, month, hour, minute)
    }

    private fun extractPassengerName(lines: List<String>): String? {
        lines.firstNotNullOfOrNull { line ->
            MESSAGE_FOR_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let(::cleanPersonName)
        }?.takeIf(::isPlausibleName)?.let { return it }

        lines.firstNotNullOfOrNull { line ->
            SEARCH_PASSENGER_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let(::cleanPersonName)
        }?.takeIf(::isPlausibleName)?.let { return it }

        val ratingIndex = lines.indexOfFirst { RATING_REGEX.containsMatchIn(it) }
        if (ratingIndex > 0) {
            lines.subList(0, ratingIndex).asReversed().forEach { candidate ->
                cleanPersonName(candidate).takeIf(::isPlausibleName)?.let { return it }
            }
        }
        return null
    }

    private fun isRouteStop(value: String): Boolean {
        val clean = cleanLine(value)
        if (clean.isBlank()) return true
        if (STANDALONE_TIME_REGEX.matches(clean)) return true
        if (clean.length == 1 && !clean[0].isLetterOrDigit()) return true
        return ROUTE_STOP_REGEX.containsMatchIn(clean)
    }

    private fun cutAtRouteStop(value: String): String {
        val match = INLINE_ROUTE_STOP_REGEX.find(value) ?: return value
        return value.substring(0, match.range.first)
    }

    private fun cleanLocation(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('•', '·', '-', '–', '—', '>', '<', ',', ';', ':')
        .replace(Regex("\\s+"), " ")
        .take(MAX_LOCATION_LENGTH)
        .trim()

    private fun cleanPersonName(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim(',', '.', ':', '-', '•', '·')

    private fun isPlausibleName(value: String): Boolean {
        if (value.length !in 2..40) return false
        if (value.any(Char::isDigit)) return false
        if (ARROW_REGEX.containsMatchIn(value)) return false
        if (!NAME_REGEX.matches(value)) return false
        val normalized = normalizeWord(value)
        return normalized !in NAME_NOISE && !ROUTE_STOP_REGEX.containsMatchIn(value)
    }

    private fun cleanLine(value: String): String = value
        .replace(Regex("[\\t\\r]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeWord(value: String): String = Normalizer
        .normalize(value.trim().lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(".", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private data class Schedule(
        val weekday: String,
        val dayOfMonth: Int,
        val month: String,
        val hour: Int,
        val minute: Int,
    )

    private const val MAX_ROUTE_LINES = 4
    private const val MAX_LOCATION_LENGTH = 90

    private val ARROW_REGEX = Regex("(?:→|➜|➡|->|›)")
    private val STANDALONE_TIME_REGEX = Regex("^\\d{1,2}:\\d{2}$")
    private val NAME_REGEX = Regex("^[\\p{L}][\\p{L} .'’_-]{1,39}$")
    private val RATING_REGEX = Regex("(?i)(?:\\b\\d(?:[,.]\\d)?/5\\b|avaliaç(?:ão|ões))")
    private val MESSAGE_FOR_REGEX = Regex("(?i)^sua mensagem para\\s+([\\p{L} .'’-]{2,40})$")
    private val SEARCH_PASSENGER_REGEX = Regex("(?i)^buscar\\s+([\\p{L} .'’-]{2,40})$")
    private val ROUTE_STOP_REGEX = Regex(
        "(?i)^(?:confirmad[oa]\\??|cancelar.*|cancelad[oa].*|reservad[oa].*|" +
            "seg\\.?|ter\\.?|qua\\.?|qui\\.?|sex\\.?|s[áa]b\\.?|dom\\.?|" +
            "r\\$.*|\\d+\\s+lugar.*|enviar mensagem.*|ligar.*|buscar\\s+.*|" +
            "sua mensagem.*|mensagem não lida.*|obter ajuda.*|resumo da viagem.*)$",
    )
    private val INLINE_ROUTE_STOP_REGEX = Regex(
        "(?i)\\s+(?=(?:confirmad[oa]\\b|cancelar\\b|reservad[oa]\\b|" +
            "seg\\.?[, ]|ter\\.?[, ]|qua\\.?[, ]|qui\\.?[, ]|sex\\.?[, ]|" +
            "s[áa]b\\.?[, ]|dom\\.?[, ]|r\\$|\\d+\\s+lugar\\b|" +
            "enviar mensagem\\b|ligar\\b|buscar\\b|\\d{1,2}:\\d{2}\\b))",
    )
    private val SCHEDULE_REGEX = Regex(
        "(?i)\\b(seg(?:unda(?:-feira)?)?|ter(?:ça(?:-feira)?)?|" +
            "qua(?:rta(?:-feira)?)?|qui(?:nta(?:-feira)?)?|" +
            "sex(?:ta(?:-feira)?)?|s[áa]b(?:ado)?|dom(?:ingo)?)\\.?" +
            "\\s*[,·•-]?\\s*(\\d{1,2})\\s*(?:de\\s+)?" +
            "(jan(?:eiro)?|fev(?:ereiro)?|mar(?:ço|co)?|abr(?:il)?|" +
            "mai(?:o)?|jun(?:ho)?|jul(?:ho)?|ago(?:sto)?|set(?:embro)?|" +
            "out(?:ubro)?|nov(?:embro)?|dez(?:embro)?)\\.?" +
            "\\s*[,·•-]?\\s*(?:às?\\s*)?(\\d{1,2})\\s*[:h]\\s*(\\d{2})\\b",
    )

    private val WEEKDAYS = mapOf(
        "seg" to "Segunda-feira",
        "segunda" to "Segunda-feira",
        "segunda-feira" to "Segunda-feira",
        "ter" to "Terça-feira",
        "terca" to "Terça-feira",
        "terca-feira" to "Terça-feira",
        "qua" to "Quarta-feira",
        "quarta" to "Quarta-feira",
        "quarta-feira" to "Quarta-feira",
        "qui" to "Quinta-feira",
        "quinta" to "Quinta-feira",
        "quinta-feira" to "Quinta-feira",
        "sex" to "Sexta-feira",
        "sexta" to "Sexta-feira",
        "sexta-feira" to "Sexta-feira",
        "sab" to "Sábado",
        "sabado" to "Sábado",
        "dom" to "Domingo",
        "domingo" to "Domingo",
    )
    private val MONTHS = mapOf(
        "jan" to "janeiro", "janeiro" to "janeiro",
        "fev" to "fevereiro", "fevereiro" to "fevereiro",
        "mar" to "março", "marco" to "março", "março" to "março",
        "abr" to "abril", "abril" to "abril",
        "mai" to "maio", "maio" to "maio",
        "jun" to "junho", "junho" to "junho",
        "jul" to "julho", "julho" to "julho",
        "ago" to "agosto", "agosto" to "agosto",
        "set" to "setembro", "setembro" to "setembro",
        "out" to "outubro", "outubro" to "outubro",
        "nov" to "novembro", "novembro" to "novembro",
        "dez" to "dezembro", "dezembro" to "dezembro",
    )
    private val NAME_NOISE = setOf(
        "obter ajuda",
        "resumo da viagem",
        "confirmado",
        "cancelar reserva",
        "enviar mensagem via blablacar",
        "ligar",
    )
}
