package br.com.mapeiaia.rotacerta

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinancialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FinancialScreen(onClose = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@Composable
private fun FinancialScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { FinancialRepository(context) }
    var entries by remember { mutableStateOf(repository.todayEntries()) }
    var summary by remember { mutableStateOf(repository.todaySummary()) }
    var closed by remember { mutableStateOf(repository.isDayClosed()) }
    var editing by remember { mutableStateOf<FinanceEntry?>(null) }
    var creatingType by remember { mutableStateOf<FinanceEntryType?>(null) }
    var deleting by remember { mutableStateOf<FinanceEntry?>(null) }
    var status by remember { mutableStateOf("") }

    fun refresh() {
        entries = repository.todayEntries()
        summary = repository.todaySummary()
        closed = repository.isDayClosed()
    }

    val pendingEntries = entries
        .filter { it.type == FinanceEntryType.REVENUE && it.status == FinanceEntryStatus.PENDING }
        .sortedByDescending(FinanceEntry::createdAtMillis)
    val otherEntries = entries
        .filterNot { it.type == FinanceEntryType.REVENUE && it.status == FinanceEntryStatus.PENDING }
        .sortedByDescending(FinanceEntry::createdAtMillis)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Controle financeiro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Text("Dados locais deste aparelho. Nenhuma informação é enviada para servidores.", style = MaterialTheme.typography.bodySmall)
        }

        if (pendingEntries.isNotEmpty()) {
            item {
                Text("Receitas pendentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(pendingEntries, key = FinanceEntry::id) { entry ->
                FinanceEntryCard(
                    entry = entry,
                    enabled = !closed,
                    onCash = { repository.markReceived(entry.id, FinancePaymentMethod.CASH); refresh() },
                    onPix = { repository.markReceived(entry.id, FinancePaymentMethod.PIX); refresh() },
                    onEdit = { editing = entry },
                    onCancel = { repository.cancel(entry.id); refresh() },
                    onDelete = { deleting = entry },
                )
            }
        } else {
            item { Text("Nenhuma receita pendente.", style = MaterialTheme.typography.bodySmall) }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Resumo de hoje", fontWeight = FontWeight.Bold)
                    Text("Previsto: ${PassengerValueFormatter.formatCurrency(summary.expectedRevenueCents)}")
                    Text("Recebido: ${PassengerValueFormatter.formatCurrency(summary.receivedRevenueCents)}")
                    Text("Pendente: ${PassengerValueFormatter.formatCurrency(summary.pendingRevenueCents)}")
                    Text("Em dinheiro: ${PassengerValueFormatter.formatCurrency(summary.cashReceivedCents)}")
                    Text("Por Pix: ${PassengerValueFormatter.formatCurrency(summary.pixReceivedCents)}")
                    Text("Despesas: ${PassengerValueFormatter.formatCurrency(summary.expensesCents)}")
                    Text("Resultado líquido: ${PassengerValueFormatter.formatCurrency(summary.netResultCents)}", fontWeight = FontWeight.Bold)
                    Text("Dinheiro em mãos: ${PassengerValueFormatter.formatCurrency(summary.cashOnHandCents)}", fontWeight = FontWeight.Bold)
                    if (summary.pendingCount > 0) Text("${summary.pendingCount} receita(s) ainda pendente(s).")
                    Text(if (closed) "Caixa conferido" else "Caixa aberto", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { creatingType = FinanceEntryType.REVENUE }, enabled = !closed, modifier = Modifier.weight(1f)) {
                    Text("+ Receita")
                }
                Button(onClick = { creatingType = FinanceEntryType.EXPENSE }, enabled = !closed, modifier = Modifier.weight(1f)) {
                    Text("+ Despesa")
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    repository.setDayClosed(!closed)
                    closed = !closed
                    status = if (closed) "Caixa de hoje marcado como conferido." else "Caixa de hoje reaberto."
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (closed) "Reabrir caixa de hoje" else "Fechar caixa de hoje") }
        }

        if (status.isNotBlank()) item { Text(status, style = MaterialTheme.typography.bodySmall) }

        item {
            Text("Outros lançamentos de hoje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (otherEntries.isEmpty()) {
            item { Text("Nenhum outro lançamento registrado hoje.") }
        } else {
            items(otherEntries, key = FinanceEntry::id) { entry ->
                FinanceEntryCard(
                    entry = entry,
                    enabled = !closed,
                    onCash = { repository.markReceived(entry.id, FinancePaymentMethod.CASH); refresh() },
                    onPix = { repository.markReceived(entry.id, FinancePaymentMethod.PIX); refresh() },
                    onEdit = { editing = entry },
                    onCancel = { repository.cancel(entry.id); refresh() },
                    onDelete = { deleting = entry },
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
        }
    }

    creatingType?.let { type ->
        FinanceEntryEditorDialog(
            initial = null,
            forcedType = type,
            onDismiss = { creatingType = null },
            onSave = { draft ->
                repository.addManual(
                    type = draft.type,
                    description = draft.description,
                    amountCents = draft.amountCents,
                    status = draft.status,
                    paymentMethod = draft.paymentMethod,
                    category = draft.category,
                    note = draft.note,
                )
                creatingType = null
                refresh()
            },
        )
    }

    editing?.let { entry ->
        FinanceEntryEditorDialog(
            initial = entry,
            forcedType = entry.type,
            onDismiss = { editing = null },
            onSave = { draft ->
                repository.update(
                    entry.copy(
                        description = draft.description,
                        amountCents = draft.amountCents,
                        status = draft.status,
                        paymentMethod = draft.paymentMethod,
                        category = draft.category,
                        note = draft.note,
                    ),
                )
                editing = null
                refresh()
            },
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir lançamento?") },
            text = { Text("${entry.description} — ${PassengerValueFormatter.formatCurrency(entry.amountCents)}") },
            confirmButton = {
                TextButton(onClick = { repository.delete(entry.id); deleting = null; refresh() }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FinanceEntryCard(
    entry: FinanceEntry,
    enabled: Boolean,
    onCash: () -> Unit,
    onPix: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val sign = if (entry.type == FinanceEntryType.REVENUE) "+" else "−"
            Text("$sign ${PassengerValueFormatter.formatCurrency(entry.amountCents)} — ${entry.description}", fontWeight = FontWeight.Bold)
            entry.origin?.let { origin -> entry.destination?.let { destination -> Text("$origin → $destination") } }
            entry.seats?.let { Text(if (it == 1) "1 lugar" else "$it lugares") }
            Text("${statusLabel(entry.status)} • ${paymentLabel(entry.paymentMethod)} • ${entry.category}", style = MaterialTheme.typography.bodySmall)
            if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
            Text(SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(entry.occurredAtMillis)), style = MaterialTheme.typography.bodySmall)
            if (entry.type == FinanceEntryType.REVENUE && entry.status == FinanceEntryStatus.PENDING && enabled) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCash, modifier = Modifier.weight(1f)) { Text("Dinheiro") }
                    Button(onClick = onPix, modifier = Modifier.weight(1f)) { Text("Pix") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Editar") }
                OutlinedButton(onClick = onCancel, enabled = enabled && entry.status != FinanceEntryStatus.CANCELLED, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                OutlinedButton(onClick = onDelete, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Excluir") }
            }
        }
    }
}

private data class FinanceEntryDraft(
    val type: FinanceEntryType,
    val description: String,
    val amountCents: Long,
    val status: FinanceEntryStatus,
    val paymentMethod: FinancePaymentMethod,
    val category: String,
    val note: String,
)

@Composable
private fun FinanceEntryEditorDialog(
    initial: FinanceEntry?,
    forcedType: FinanceEntryType,
    onDismiss: () -> Unit,
    onSave: (FinanceEntryDraft) -> Unit,
) {
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.amountCents?.let { cents -> "${cents / 100},${(cents % 100).toString().padStart(2, '0')}" }.orEmpty()) }
    var status by remember(initial?.id) {
        mutableStateOf(initial?.status ?: if (forcedType == FinanceEntryType.EXPENSE) FinanceEntryStatus.CONFIRMED else FinanceEntryStatus.PENDING)
    }
    var payment by remember(initial?.id) { mutableStateOf(initial?.paymentMethod ?: FinancePaymentMethod.UNDEFINED) }
    var category by remember(initial?.id) { mutableStateOf(initial?.category.orEmpty()) }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var error by remember(initial?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) if (forcedType == FinanceEntryType.REVENUE) "Adicionar receita" else "Adicionar despesa" else "Editar lançamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descrição") }, singleLine = true)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Valor em reais") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Observação") })
                Text("Situação", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { status = FinanceEntryStatus.PENDING }) { Text("Pendente") }
                    OutlinedButton(onClick = { status = FinanceEntryStatus.CONFIRMED }) { Text("Confirmado") }
                }
                Text("Pagamento", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { payment = FinancePaymentMethod.UNDEFINED }) { Text("Não informado") }
                    OutlinedButton(onClick = { payment = FinancePaymentMethod.CASH }) { Text("Dinheiro") }
                    OutlinedButton(onClick = { payment = FinancePaymentMethod.PIX }) { Text("Pix") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { payment = FinancePaymentMethod.CARD }) { Text("Cartão") }
                    OutlinedButton(onClick = { payment = FinancePaymentMethod.PLATFORM }) { Text("Plataforma") }
                    OutlinedButton(onClick = { payment = FinancePaymentMethod.OTHER }) { Text("Outro") }
                }
                Text("Selecionado: ${statusLabel(status)} • ${paymentLabel(payment)}", style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = FinancialRepository.parseCurrencyToCents(amount)
                when {
                    description.isBlank() -> error = "Digite uma descrição."
                    cents == null -> error = "Digite um valor válido."
                    else -> onSave(
                        FinanceEntryDraft(
                            type = forcedType,
                            description = description.trim(),
                            amountCents = cents,
                            status = status,
                            paymentMethod = payment,
                            category = category.trim(),
                            note = note.trim(),
                        ),
                    )
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Voltar") } },
    )
}

private fun statusLabel(status: FinanceEntryStatus): String = when (status) {
    FinanceEntryStatus.PENDING -> "Pendente"
    FinanceEntryStatus.CONFIRMED -> "Confirmado"
    FinanceEntryStatus.CANCELLED -> "Cancelado"
}

private fun paymentLabel(method: FinancePaymentMethod): String = when (method) {
    FinancePaymentMethod.UNDEFINED -> "Não informado"
    FinancePaymentMethod.CASH -> "Dinheiro"
    FinancePaymentMethod.PIX -> "Pix"
    FinancePaymentMethod.CARD -> "Cartão"
    FinancePaymentMethod.PLATFORM -> "Plataforma"
    FinancePaymentMethod.OTHER -> "Outro"
}
