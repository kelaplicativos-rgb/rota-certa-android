package br.com.mapeiaia.rotacerta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
}

internal fun rotaCertaInclusiveDates(start: LocalDate, end: LocalDate): List<LocalDate> {
    val first = minOf(start, end)
    val last = maxOf(start, end)
    return generateSequence(first) { current ->
        current.plusDays(1).takeIf { next -> !next.isAfter(last) }
    }.toList()
}

internal fun rotaCertaApplyDateTap(
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

internal fun rotaCertaSelectMonth(
    month: YearMonth,
    minDate: LocalDate? = null,
): RotaCertaDateSelection {
    val start = minDate?.takeIf { !it.isBefore(month.atDay(1)) } ?: month.atDay(1)
    val dates = if (start.isAfter(month.atEndOfMonth())) emptyList() else
        rotaCertaInclusiveDates(start, month.atEndOfMonth())
    return RotaCertaDateSelection(
        mode = RotaCertaDateSelectionMode.MONTH,
        dates = dates,
    )
}

private val shortDateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("pt-BR"))
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

fun rotaCertaDateSelectionSummary(selection: RotaCertaDateSelection): String {
    val dates = selection.normalizedDates
    if (dates.isEmpty()) return "Nenhuma data selecionada • começa hoje"
    if (dates.size == 1) return shortDateFormatter.format(dates.single())

    val first = dates.first()
    val last = dates.last()
    val contiguous = dates.size.toLong() == java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1
    if (
        contiguous &&
        selection.mode in setOf(RotaCertaDateSelectionMode.RANGE, RotaCertaDateSelectionMode.MONTH) &&
        first.year == last.year &&
        first.month == last.month
    ) {
        return "${first.dayOfMonth} a ${last.dayOfMonth} de ${last.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("pt-BR"))}"
    }

    if (dates.size <= 4 && dates.all { it.year == first.year && it.month == first.month }) {
        val days = dates.map { it.dayOfMonth.toString() }
        val joined = when (days.size) {
            2 -> "${days[0]} e ${days[1]}"
            else -> days.dropLast(1).joinToString(", ") + " e " + days.last()
        }
        return "$joined de ${first.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("pt-BR"))}"
    }

    return "${dates.size} datas selecionadas • ${shortDateFormatter.format(first)} a ${shortDateFormatter.format(last)}"
}

@Composable
fun RotaCertaDateSelectionField(
    selection: RotaCertaDateSelection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Datas da consulta",
) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(rotaCertaDateSelectionSummary(selection), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun RotaCertaDatePickerDialog(
    selection: RotaCertaDateSelection,
    onDismiss: () -> Unit,
    onConfirm: (RotaCertaDateSelection) -> Unit,
    minDate: LocalDate = LocalDate.now(),
    allowedModes: Set<RotaCertaDateSelectionMode> = RotaCertaDateSelectionMode.entries.toSet(),
) {
    var draft by remember(selection) { mutableStateOf(selection.copy(dates = selection.normalizedDates)) }
    var visibleMonth by remember(selection, minDate) {
        mutableStateOf(draft.normalizedDates.firstOrNull()?.let(YearMonth::from) ?: YearMonth.from(minDate))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Datas da consulta", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Mesmo calendário da Agenda Pública: dias passados ficam indisponíveis e as datas escolhidas permanecem marcadas.",
                    style = MaterialTheme.typography.bodySmall,
                )

                val modes = allowedModes.toList().sortedBy { it.ordinal }
                modes.chunked(2).forEach { rowModes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowModes.forEach { mode ->
                            val selected = draft.mode == mode
                            val click = {
                                draft = RotaCertaDateSelection(mode = mode)
                            }
                            if (selected) {
                                Button(onClick = click, modifier = Modifier.weight(1f)) {
                                    Text(modeLabel(mode))
                                }
                            } else {
                                OutlinedButton(onClick = click, modifier = Modifier.weight(1f)) {
                                    Text(modeLabel(mode))
                                }
                            }
                        }
                        if (rowModes.size == 1) Box(Modifier.weight(1f))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        enabled = visibleMonth > YearMonth.from(minDate),
                        onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                    ) { Text("‹") }
                    Text(monthFormatter.format(visibleMonth.atDay(1)), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) { Text("›") }
                }

                if (draft.mode == RotaCertaDateSelectionMode.MONTH) {
                    OutlinedButton(
                        onClick = { draft = rotaCertaSelectMonth(visibleMonth, minDate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Selecionar mês inteiro")
                    }
                }

                CalendarMonthGrid(
                    month = visibleMonth,
                    minDate = minDate,
                    selectedDates = draft.normalizedDates.toSet(),
                    onDateClick = { tapped ->
                        draft = if (draft.mode == RotaCertaDateSelectionMode.MONTH) {
                            rotaCertaSelectMonth(YearMonth.from(tapped), minDate)
                        } else {
                            rotaCertaApplyDateTap(draft, tapped)
                        }
                    },
                )

                when {
                    draft.mode == RotaCertaDateSelectionMode.RANGE && draft.normalizedDates.size == 1 ->
                        Text("Agora escolha a data final do período.", style = MaterialTheme.typography.bodySmall)
                    draft.mode == RotaCertaDateSelectionMode.MULTIPLE ->
                        Text("Toque novamente em um dia marcado para desmarcá-lo.", style = MaterialTheme.typography.bodySmall)
                }

                Text(rotaCertaDateSelectionSummary(draft), style = MaterialTheme.typography.bodyMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { draft = draft.copy(dates = emptyList()) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Limpar") }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(onClick = { onConfirm(draft) }, modifier = Modifier.weight(1f)) { Text("Confirmar") }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    minDate: LocalDate,
    selectedDates: Set<LocalDate>,
    onDateClick: (LocalDate) -> Unit,
) {
    val weekLabels = listOf("D", "S", "T", "Q", "Q", "S", "S")
    Row(Modifier.fillMaxWidth()) {
        weekLabels.forEach { label ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val firstOffset = month.atDay(1).dayOfWeek.value % 7
    val usedCells = ((firstOffset + month.lengthOfMonth() + 6) / 7) * 7
    val cells = List<LocalDate?>(usedCells) { index ->
        val day = index - firstOffset + 1
        day.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                if (date == null) {
                    Box(Modifier.weight(1f).aspectRatio(1f))
                } else {
                    val enabled = !date.isBefore(minDate)
                    val selected = date in selectedDates
                    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    val foreground = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        enabled -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(background)
                            .clickable(enabled = enabled) { onDateClick(date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(date.dayOfMonth.toString(), color = foreground, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun modeLabel(mode: RotaCertaDateSelectionMode): String = when (mode) {
    RotaCertaDateSelectionMode.SINGLE -> "Uma data"
    RotaCertaDateSelectionMode.MULTIPLE -> "Várias datas"
    RotaCertaDateSelectionMode.RANGE -> "Período"
    RotaCertaDateSelectionMode.MONTH -> "Mês inteiro"
}
