package br.com.mapeiaia.rotacerta.date

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class RotaCertaDateSelectionMode {
    SINGLE,
    MULTIPLE,
    RANGE,
    MONTH,
}

data class RotaCertaDateSelection(
    val mode: RotaCertaDateSelectionMode = RotaCertaDateSelectionMode.MULTIPLE,
    val dates: List<LocalDate> = emptyList(),
) {
    val normalizedDates: List<LocalDate>
        get() = dates.distinct().sorted()

    val isoDateKeys: List<String>
        get() = normalizedDates.map(LocalDate::toString)
}

fun rotaCertaInclusiveDates(start: LocalDate, end: LocalDate): List<LocalDate> {
    val first = minOf(start, end)
    val last = maxOf(start, end)
    return generateSequence(first) { current ->
        current.plusDays(1).takeIf { next -> !next.isAfter(last) }
    }.toList()
}

fun rotaCertaApplyDateTap(
    current: RotaCertaDateSelection,
    date: LocalDate,
): RotaCertaDateSelection {
    val existing = current.normalizedDates
    val next = when (current.mode) {
        RotaCertaDateSelectionMode.SINGLE -> listOf(date)
        RotaCertaDateSelectionMode.MULTIPLE ->
            if (date in existing) existing - date else (existing + date).sorted()
        RotaCertaDateSelectionMode.RANGE -> when {
            existing.isEmpty() || existing.size > 1 -> listOf(date)
            else -> rotaCertaInclusiveDates(existing.single(), date)
        }
        RotaCertaDateSelectionMode.MONTH -> {
            val month = YearMonth.from(date)
            rotaCertaInclusiveDates(month.atDay(1), month.atEndOfMonth())
        }
    }
    return current.copy(dates = next)
}

fun rotaCertaSelectMonth(
    month: YearMonth,
    minDate: LocalDate? = null,
): RotaCertaDateSelection {
    val firstDay = month.atDay(1)
    val lastDay = month.atEndOfMonth()
    val start = minDate?.takeIf { !it.isBefore(firstDay) } ?: firstDay
    val dates = if (start.isAfter(lastDay)) emptyList() else rotaCertaInclusiveDates(start, lastDay)
    return RotaCertaDateSelection(
        mode = RotaCertaDateSelectionMode.MONTH,
        dates = dates,
    )
}

fun rotaCertaDateSelectionSummary(
    selection: RotaCertaDateSelection,
    emptySummary: String = "Nenhuma data selecionada • começa hoje",
    locale: Locale = Locale.forLanguageTag("pt-BR"),
): String {
    val dates = selection.normalizedDates
    if (dates.isEmpty()) return emptySummary
    val shortDateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", locale)
    if (dates.size == 1) return shortDateFormatter.format(dates.single())

    val first = dates.first()
    val last = dates.last()
    val contiguous = dates.size.toLong() == ChronoUnit.DAYS.between(first, last) + 1
    if (
        contiguous &&
        selection.mode in setOf(RotaCertaDateSelectionMode.RANGE, RotaCertaDateSelectionMode.MONTH) &&
        first.year == last.year &&
        first.month == last.month
    ) {
        return "${first.dayOfMonth} a ${last.dayOfMonth} de ${last.month.getDisplayName(TextStyle.FULL, locale)}"
    }

    if (dates.size <= 4 && dates.all { it.year == first.year && it.month == first.month }) {
        val days = dates.map { it.dayOfMonth.toString() }
        val joined = when (days.size) {
            2 -> "${days[0]} e ${days[1]}"
            else -> days.dropLast(1).joinToString(", ") + " e " + days.last()
        }
        return "$joined de ${first.month.getDisplayName(TextStyle.FULL, locale)}"
    }

    return "${dates.size} datas selecionadas • ${shortDateFormatter.format(first)} a ${shortDateFormatter.format(last)}"
}

fun rotaCertaDateConfirmLabel(
    selection: RotaCertaDateSelection,
    emptyLabel: String = "Continuar a partir de hoje",
): String {
    val count = selection.normalizedDates.size
    return when (count) {
        0 -> emptyLabel
        1 -> "Confirmar 1 data"
        else -> "Confirmar $count datas"
    }
}
