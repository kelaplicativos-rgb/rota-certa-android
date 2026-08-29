package br.com.mapeiaia.rotacerta.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.window.DialogProperties
import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelection
import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelectionMode
import br.com.mapeiaia.rotacerta.date.rotaCertaApplyDateTap
import br.com.mapeiaia.rotacerta.date.rotaCertaDateConfirmLabel
import br.com.mapeiaia.rotacerta.date.rotaCertaDateSelectionSummary
import br.com.mapeiaia.rotacerta.date.rotaCertaSelectMonth
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

@Composable
fun RotaCertaDateSelectionField(
    selection: RotaCertaDateSelection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Datas da consulta",
    emptySummary: String = "Nenhuma data selecionada • começa hoje",
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    rotaCertaDateSelectionSummary(selection, emptySummary = emptySummary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    allowEmptySelection: Boolean = true,
    emptyConfirmLabel: String = "Continuar a partir de hoje",
    title: String = "Datas da consulta",
    description: String = "Escolha uma, várias datas, um período ou o mês inteiro. Dias passados ficam indisponíveis.",
) {
    var draft by remember(selection) { mutableStateOf(selection.copy(dates = selection.normalizedDates)) }
    var visibleMonth by remember(selection, minDate) {
        mutableStateOf(draft.normalizedDates.firstOrNull()?.let(YearMonth::from) ?: YearMonth.from(minDate))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                    TextButton(
                        enabled = draft.normalizedDates.isNotEmpty(),
                        onClick = { draft = draft.copy(dates = emptyList()) },
                    ) {
                        Text("Limpar")
                    }
                }

                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allowedModes.toList().sortedBy { it.ordinal }.forEach { mode ->
                        FilterChip(
                            selected = draft.mode == mode,
                            onClick = { draft = RotaCertaDateSelection(mode = mode) },
                            label = { Text(compactModeLabel(mode), maxLines = 1) },
                        )
                    }
                }

                Button(
                    enabled = allowEmptySelection || draft.normalizedDates.isNotEmpty(),
                    onClick = { onConfirm(draft) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        rotaCertaDateConfirmLabel(draft, emptyLabel = emptyConfirmLabel),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            enabled = visibleMonth > YearMonth.from(minDate),
                            onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                        ) { Text("‹") }
                        Text(
                            monthFormatter.format(visibleMonth.atDay(1)),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) { Text("›") }
                    }

                    if (draft.mode == RotaCertaDateSelectionMode.MONTH) {
                        OutlinedButton(
                            onClick = { draft = rotaCertaSelectMonth(visibleMonth, minDate) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Selecionar mês inteiro", maxLines = 1)
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
                            Text(
                                "Agora escolha a data final do período.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        draft.mode == RotaCertaDateSelectionMode.MULTIPLE ->
                            Text(
                                "Toque novamente em um dia marcado para desmarcá-lo.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }

                    Text(
                        rotaCertaDateSelectionSummary(draft),
                        style = MaterialTheme.typography.bodyLarge,
                    )
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

private fun compactModeLabel(mode: RotaCertaDateSelectionMode): String = when (mode) {
    RotaCertaDateSelectionMode.SINGLE -> "Uma"
    RotaCertaDateSelectionMode.MULTIPLE -> "Várias"
    RotaCertaDateSelectionMode.RANGE -> "Período"
    RotaCertaDateSelectionMode.MONTH -> "Mês"
}
