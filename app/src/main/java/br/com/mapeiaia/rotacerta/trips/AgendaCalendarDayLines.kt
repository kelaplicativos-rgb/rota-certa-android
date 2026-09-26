package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class AgendaCalendarDay<T>(
    val date: LocalDate,
    val items: List<T>,
)

internal fun <T> agendaCalendarDaysForItems(
    items: List<T>,
    dateOf: (T) -> LocalDate?,
): List<AgendaCalendarDay<T>> {
    val byDate = items.mapNotNull { item -> dateOf(item)?.let { it to item } }
        .groupBy({ it.first }, { it.second })
    if (byDate.isEmpty()) return emptyList()

    val firstObserved = byDate.keys.minOrNull() ?: return emptyList()
    val lastObserved = byDate.keys.maxOrNull() ?: return emptyList()
    val start = firstObserved.withDayOfMonth(1)
    val end = lastObserved.withDayOfMonth(lastObserved.lengthOfMonth())
    return inclusiveCalendarDates(start, end).map { date ->
        AgendaCalendarDay(date = date, items = byDate[date].orEmpty())
    }
}

internal fun <T> agendaCalendarDaysForPeriod(
    period: String,
    items: List<T>,
    dateOf: (T) -> LocalDate?,
): List<AgendaCalendarDay<T>> {
    val byDate = items.mapNotNull { item -> dateOf(item)?.let { it to item } }
        .groupBy({ it.first }, { it.second })

    val exactDate = runCatching { LocalDate.parse(period.trim()) }.getOrNull()
    if (exactDate != null) {
        return listOf(AgendaCalendarDay(exactDate, byDate[exactDate].orEmpty()))
    }

    val month = runCatching { YearMonth.parse(period.trim()) }.getOrNull()
    if (month != null) {
        return inclusiveCalendarDates(month.atDay(1), month.atEndOfMonth()).map { date ->
            AgendaCalendarDay(date = date, items = byDate[date].orEmpty())
        }
    }

    return agendaCalendarDaysForItems(items, dateOf)
}

private fun inclusiveCalendarDates(start: LocalDate, end: LocalDate): List<LocalDate> {
    if (start.isAfter(end)) return emptyList()
    return generateSequence(start) { previous ->
        previous.plusDays(1).takeIf { next -> !next.isAfter(end) }
    }.toList()
}

private val agendaCalendarDayFormatter = DateTimeFormatter.ofPattern(
    "EEEE, d 'de' MMMM 'de' yyyy",
    Locale.forLanguageTag("pt-BR"),
)

internal fun agendaCalendarDayLabel(date: LocalDate): String =
    agendaCalendarDayFormatter.format(date)

@Composable
internal fun AgendaCalendarDayLine(date: LocalDate) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = agendaCalendarDayLabel(date),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
