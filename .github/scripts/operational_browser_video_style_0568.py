from pathlib import Path

SOURCE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/trips/OperationalBrowserVideoStyle0568Test.kt')

text = SOURCE.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    text = text.replace(old, new, 1)

replace_once(
    'import androidx.compose.foundation.clickable\n',
    'import androidx.compose.foundation.BorderStroke\n'
    'import androidx.compose.foundation.clickable\n',
    'foundation imports',
)
replace_once(
    'import androidx.compose.foundation.layout.Row\n',
    'import androidx.compose.foundation.layout.Row\n'
    'import androidx.compose.foundation.layout.Spacer\n',
    'spacer import',
)
replace_once(
    'import androidx.compose.foundation.layout.fillMaxWidth\n',
    'import androidx.compose.foundation.layout.fillMaxWidth\n'
    'import androidx.compose.foundation.layout.height\n'
    'import androidx.compose.foundation.layout.width\n'
    'import androidx.compose.foundation.layout.weight\n',
    'layout sizing imports',
)
replace_once(
    'import androidx.compose.material3.Card\n',
    'import androidx.compose.material3.Card\n'
    'import androidx.compose.material3.CardDefaults\n',
    'card defaults import',
)
replace_once(
    'import androidx.compose.ui.platform.LocalContext\n',
    'import androidx.compose.ui.platform.LocalContext\n'
    'import androidx.compose.foundation.shape.RoundedCornerShape\n',
    'rounded shape import',
)

active_old = '''            itemsIndexed(
                items = activeRows,
                key = { _, row -> "active|${operationalTripBrowserKey0563(row.entry)}" },
            ) { index, row ->
                val date = operationalDepartureDate0563(row.entry, zoneId)
                val previousDate = activeRows.getOrNull(index - 1)?.let { previous ->
                    operationalDepartureDate0563(previous.entry, zoneId)
                }
                if (index == 0 || date != previousDate) {
                    Text(
                        text = operationalDateLabel0563(date, today),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp, bottom = 2.dp),
                    )
                }

                OperationalTripBrowserCard0563(
                    row = row,
                    zoneId = zoneId,
                    archived = false,
                    onOpen = { openRow(row) },
                )
            }
'''
active_new = '''            itemsIndexed(
                items = activeRows,
                key = { _, row -> "active|${operationalTripBrowserKey0563(row.entry)}" },
            ) { _, row ->
                OperationalTripBrowserCard0563(
                    row = row,
                    zoneId = zoneId,
                    today = today,
                    archived = false,
                    onOpen = { openRow(row) },
                )
            }
'''
replace_once(active_old, active_new, 'active card list')

archive_toggle_old = '''            item(key = "archived-toggle-0566") {
                TextButton(
                    onClick = { showArchived = !showArchived },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (showArchived) {
                            "Ocultar arquivadas (${archivedRows.size})"
                        } else {
                            "Arquivadas (${archivedRows.size})"
                        },
                    )
                }
            }
'''
archive_toggle_new = '''            item(key = "archived-toggle-0566") {
                TextButton(
                    onClick = { showArchived = !showArchived },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Viagens arquivadas", style = MaterialTheme.typography.titleMedium)
                        Text(if (showArchived) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
'''
replace_once(archive_toggle_old, archive_toggle_new, 'archive toggle')

archived_old = '''                itemsIndexed(
                    items = archivedRows,
                    key = { _, row -> "archived|${operationalTripBrowserKey0563(row.entry)}" },
                ) { index, row ->
                    val date = operationalDepartureDate0563(row.entry, zoneId)
                    val previousDate = archivedRows.getOrNull(index - 1)?.let { previous ->
                        operationalDepartureDate0563(previous.entry, zoneId)
                    }
                    if (index == 0 || date != previousDate) {
                        Text(
                            text = operationalDateLabel0563(date, today),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = if (index == 0) 4.dp else 8.dp, bottom = 2.dp),
                        )
                    }

                    OperationalTripBrowserCard0563(
                        row = row,
                        zoneId = zoneId,
                        archived = true,
                        onOpen = { openRow(row) },
                    )
                }
'''
archived_new = '''                itemsIndexed(
                    items = archivedRows,
                    key = { _, row -> "archived|${operationalTripBrowserKey0563(row.entry)}" },
                ) { _, row ->
                    OperationalTripBrowserCard0563(
                        row = row,
                        zoneId = zoneId,
                        today = today,
                        archived = true,
                        onOpen = { openRow(row) },
                    )
                }
'''
replace_once(archived_old, archived_new, 'archived card list')

card_start = text.index('@Composable\nprivate fun OperationalTripBrowserCard0563(')
helpers_start = text.index('internal fun operationalTripBrowserKey0563', card_start)
old_card = text[card_start:helpers_start]
new_card = '''@Composable
private fun OperationalTripBrowserCard0563(
    row: OperationalTripBrowserRow0563,
    zoneId: ZoneId,
    today: LocalDate,
    archived: Boolean,
    onOpen: () -> Unit,
) {
    val entry = row.entry
    val targetConfirmed = row.target != null && row.account != null
    val date = operationalDepartureDate0563(entry, zoneId)
    val departureTime = operationalDepartureTime0563(entry, zoneId)
    val arrivalTime = operationalArrivalTime0568(entry, zoneId)
    val duration = operationalDurationLabel0568(entry.departureAtMillis, entry.arrivalAtMillis)
    val dateLabel = operationalDateLabel0568(date, today)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = targetConfirmed, onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (entry.status == TripStatus.CANCELLED) {
                Text(
                    text = "⊘ Cancelada",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = row.account?.displayLabel ?: "Conta não confirmada",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.width(60.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(departureTime, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(14.dp))
                    if (duration != null) {
                        Text(
                            duration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(arrivalTime ?: "—", style = MaterialTheme.typography.titleMedium)
                }

                Column(
                    modifier = Modifier.width(34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "●\n│\n│\n●",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.origin, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(if (duration != null) 42.dp else 46.dp))
                    Text(entry.destination, style = MaterialTheme.typography.titleMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🚗", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = operationalPassengerSummary0568(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!targetConfirmed) {
                Text(
                    text = "Identidade externa incompleta — abertura bloqueada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (archived) {
                Text(
                    text = "Viagem arquivada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

'''
text = text[:card_start] + new_card + text[helpers_start:]

old_helpers = '''private fun operationalDepartureDate0563(entry: TripTimelineEntry, zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(entry.departureAtMillis).atZone(zoneId).toLocalDate()

private fun operationalDepartureTime0563(entry: TripTimelineEntry, zoneId: ZoneId): String =
    Instant.ofEpochMilli(entry.departureAtMillis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun operationalDateLabel0563(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.plusDays(1) -> "Amanhã"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale("pt", "BR")))
        .replaceFirstChar { first -> if (first.isLowerCase()) first.titlecase(Locale("pt", "BR")) else first.toString() }
}
'''
new_helpers = '''private fun operationalDepartureDate0563(entry: TripTimelineEntry, zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(entry.departureAtMillis).atZone(zoneId).toLocalDate()

private fun operationalDepartureTime0563(entry: TripTimelineEntry, zoneId: ZoneId): String =
    Instant.ofEpochMilli(entry.departureAtMillis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun operationalArrivalTime0568(entry: TripTimelineEntry, zoneId: ZoneId): String? =
    entry.arrivalAtMillis?.let { arrival ->
        Instant.ofEpochMilli(arrival)
            .atZone(zoneId)
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }

internal fun operationalDurationLabel0568(departureAtMillis: Long, arrivalAtMillis: Long?): String? {
    val arrival = arrivalAtMillis ?: return null
    val elapsedMillis = arrival - departureAtMillis
    if (elapsedMillis < 0L) return null
    val minutes = elapsedMillis / 60_000L
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return when {
        hours > 0L && remainingMinutes > 0L -> "${hours}h${remainingMinutes.toString().padStart(2, '0')}"
        hours > 0L -> "${hours}h"
        else -> "${remainingMinutes}min"
    }
}

internal fun operationalDateLabel0568(date: LocalDate, today: LocalDate): String {
    if (date == today) return "Hoje"
    if (date == today.minusDays(1)) return "Ontem"
    if (date == today.plusDays(1)) return "Amanhã"

    val weekday = when (date.dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> "Seg."
        java.time.DayOfWeek.TUESDAY -> "Ter."
        java.time.DayOfWeek.WEDNESDAY -> "Qua."
        java.time.DayOfWeek.THURSDAY -> "Qui."
        java.time.DayOfWeek.FRIDAY -> "Sex."
        java.time.DayOfWeek.SATURDAY -> "Sáb."
        java.time.DayOfWeek.SUNDAY -> "Dom."
    }
    val month = when (date.monthValue) {
        1 -> "Jan."
        2 -> "Fev."
        3 -> "Mar."
        4 -> "Abr."
        5 -> "Mai."
        6 -> "Jun."
        7 -> "Jul."
        8 -> "Ago."
        9 -> "Set."
        10 -> "Out."
        11 -> "Nov."
        else -> "Dez."
    }
    val base = "$weekday ${date.dayOfMonth.toString().padStart(2, '0')} $month"
    return if (date.year == today.year) base else "$base ${date.year}"
}

internal fun operationalPassengerSummary0568(entry: TripTimelineEntry): String = when {
    entry.maximumOccupiedSeats > 0 -> {
        val seats = entry.maximumOccupiedSeats
        "$seats ${if (seats == 1) "passageiro" else "passageiros"}"
    }
    entry.blablaPassengerRosterComplete == true -> "Nenhum passageiro nesta viagem"
    else -> "Viagem BlaBlaCar"
}
'''
replace_once(old_helpers, new_helpers, 'date/time helpers')

# Locale is no longer used after deterministic BlaBlaCar-like Brazilian abbreviations.
text = text.replace('import java.util.Locale\n', '')

SOURCE.write_text(text)

TEST.parent.mkdir(parents=True, exist_ok=True)
TEST.write_text('''package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperationalBrowserVideoStyle0568Test {
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun dateLabelsMatchReferenceVideoForRelativeCurrentAndNextYearDates() {
        assertEquals("Hoje", operationalDateLabel0568(today, today))
        assertEquals("Ontem", operationalDateLabel0568(today.minusDays(1), today))
        assertEquals("Amanhã", operationalDateLabel0568(today.plusDays(1), today))
        assertEquals("Sex. 18 Set.", operationalDateLabel0568(LocalDate.of(2026, 9, 18), today))
        assertEquals("Sáb. 07 Ago. 2027", operationalDateLabel0568(LocalDate.of(2027, 8, 7), today))
        assertEquals("Qui. 23 Set. 2027", operationalDateLabel0568(LocalDate.of(2027, 9, 23), today))
    }

    @Test
    fun durationUsesCompactBlaBlaCarStyle() {
        val start = 1_000_000L
        assertEquals("5h40", operationalDurationLabel0568(start, start + (5 * 60 + 40) * 60_000L))
        assertEquals("2h", operationalDurationLabel0568(start, start + 2 * 60 * 60_000L))
        assertEquals("45min", operationalDurationLabel0568(start, start + 45 * 60_000L))
        assertNull(operationalDurationLabel0568(start, null))
        assertNull(operationalDurationLabel0568(start, start - 1L))
    }
}
''')

print('Operational browser video-style patch 0.1.568 applied successfully.')
