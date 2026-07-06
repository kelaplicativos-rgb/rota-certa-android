package br.com.mapeiaia.rotacerta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DarkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BlaBlaCarCollectorActivity : ComponentActivity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlaBlaCarCollectorScreen(
                        initialRecord = loadRecord(),
                        onRecordChanged = ::saveRecord,
                        onCopySummary = ::copySummary,
                        onOpenUrl = ::openUrl,
                        onPasteText = ::pasteText,
                    )
                }
            }
        }
    }

    private fun loadRecord(): BlaBlaCarTripRecord {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_RECORD, null) ?: return BlaBlaCarTripRecord()
        return runCatching { json.decodeFromString<BlaBlaCarTripRecord>(raw) }.getOrDefault(BlaBlaCarTripRecord())
    }

    private fun saveRecord(record: BlaBlaCarTripRecord) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORD, json.encodeToString(record))
            .apply()
    }

    private fun copySummary(record: BlaBlaCarTripRecord) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Resumo BlaBlaCar", BlaBlaCarCollectorParser.summary(record)))
        Toast.makeText(this, "Resumo copiado.", Toast.LENGTH_SHORT).show()
    }

    private fun pasteText(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "Informe um destino primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "Nao foi possivel abrir o link.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val PREFS_NAME = "blablacar_collector"
        private const val KEY_RECORD = "record"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlaBlaCarCollectorScreen(
    initialRecord: BlaBlaCarTripRecord,
    onRecordChanged: (BlaBlaCarTripRecord) -> Unit,
    onCopySummary: (BlaBlaCarTripRecord) -> Unit,
    onOpenUrl: (String) -> Unit,
    onPasteText: () -> String,
) {
    var record by remember { mutableStateOf(initialRecord) }
    var rawText by remember { mutableStateOf("") }
    var passengerName by remember { mutableStateOf("") }
    var passengerPhone by remember { mutableStateOf("") }
    var passengerPickup by remember { mutableStateOf("") }
    var passengerDropoff by remember { mutableStateOf("") }
    var passengerSeats by remember { mutableStateOf("1") }
    var passengerFare by remember { mutableStateOf("") }
    var passengerStatus by remember { mutableStateOf("Confirmado") }
    var expenseLabel by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }

    fun update(next: BlaBlaCarTripRecord) {
        record = next
        onRecordChanged(next)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Coletor BlaBlaCar") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FinanceSummary(record = record)

            SectionCard(title = "Dados da viagem") {
                TextFieldLine("Titulo", record.title) { update(record.copy(title = it)) }
                TextFieldLine("Data e horario", record.dateTime) { update(record.copy(dateTime = it)) }
                TextFieldLine("Origem", record.origin) { update(record.copy(origin = it)) }
                TextFieldLine("Destino", record.destination) { update(record.copy(destination = it)) }
                TextFieldLine("Distancia em km", record.distanceKm, KeyboardType.Decimal) { update(record.copy(distanceKm = it)) }
                TextFieldLine("Link da viagem", record.tripUrl) { update(record.copy(tripUrl = it)) }
                TextFieldLine("Observacoes", record.notes, minLines = 3) { update(record.copy(notes = it)) }
                Button(onClick = { onOpenUrl(BlaBlaCarCollectorParser.mapsDirectionsUrl(record)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir rota no Google Maps")
                }
                if (record.tripUrl.isNotBlank()) {
                    TextButton(onClick = { onOpenUrl(record.tripUrl) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir link da viagem")
                    }
                }
            }

            SectionCard(title = "Extracao manual") {
                Text("Cole aqui o texto copiado da tela logada do BlaBlaCar. A extracao e manual e local.", style = MaterialTheme.typography.bodySmall)
                TextFieldLine("Texto copiado", rawText, minLines = 6) { rawText = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { rawText = onPasteText() }, modifier = Modifier.weight(1f)) { Text("Colar") }
                    Button(
                        onClick = {
                            val parsed = BlaBlaCarCollectorParser.parsePassengers(rawText)
                            val existingPhones = record.passengers.map { it.phone }.toSet()
                            update(record.copy(passengers = record.passengers + parsed.filterNot { it.phone in existingPhones }))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Extrair") }
                }
            }

            SectionCard(title = "Adicionar passageiro") {
                TextFieldLine("Nome", passengerName) { passengerName = it }
                TextFieldLine("Telefone", passengerPhone, KeyboardType.Phone) { passengerPhone = it }
                TextFieldLine("Embarque", passengerPickup) { passengerPickup = it }
                TextFieldLine("Desembarque", passengerDropoff) { passengerDropoff = it }
                TextFieldLine("Vagas", passengerSeats, KeyboardType.Number) { passengerSeats = it }
                TextFieldLine("Valor", passengerFare, KeyboardType.Decimal) { passengerFare = it }
                TextFieldLine("Status", passengerStatus) { passengerStatus = it }
                Button(
                    onClick = {
                        val phone = BlaBlaCarCollectorParser.normalizePhone(passengerPhone)
                        update(
                            record.copy(
                                passengers = record.passengers + BlaBlaCarPassenger(
                                    name = passengerName.trim(),
                                    phone = phone,
                                    pickup = passengerPickup.trim(),
                                    dropoff = passengerDropoff.trim(),
                                    seats = passengerSeats.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                    fareText = passengerFare.trim(),
                                    status = passengerStatus.trim().ifBlank { "Confirmado" },
                                ),
                            ),
                        )
                        passengerName = ""
                        passengerPhone = ""
                        passengerPickup = ""
                        passengerDropoff = ""
                        passengerSeats = "1"
                        passengerFare = ""
                        passengerStatus = "Confirmado"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salvar passageiro") }
            }

            SectionCard(title = "Passageiros (${record.passengers.size})") {
                if (record.passengers.isEmpty()) {
                    Text("Nenhum passageiro registrado.", style = MaterialTheme.typography.bodySmall)
                } else {
                    record.passengers.forEachIndexed { index, passenger ->
                        PassengerRow(
                            passenger = passenger,
                            onWhatsApp = {
                                val msg = "Ola ${passenger.name.ifBlank { "" }}. Sou o motorista da viagem BlaBlaCar ${record.origin} -> ${record.destination}."
                                onOpenUrl(BlaBlaCarCollectorParser.whatsappUrl(passenger.phone, msg))
                            },
                            onPickup = { onOpenUrl(BlaBlaCarCollectorParser.mapsSearchUrl(passenger.pickup)) },
                            onDropoff = { onOpenUrl(BlaBlaCarCollectorParser.mapsSearchUrl(passenger.dropoff)) },
                            onRemove = { update(record.copy(passengers = record.passengers.filterIndexed { i, _ -> i != index })) },
                        )
                        if (index != record.passengers.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }

            SectionCard(title = "Despesas") {
                TextFieldLine("Descricao", expenseLabel) { expenseLabel = it }
                TextFieldLine("Valor", expenseAmount, KeyboardType.Decimal) { expenseAmount = it }
                Button(
                    onClick = {
                        update(record.copy(expenses = record.expenses + BlaBlaCarExpense(expenseLabel.trim(), expenseAmount.trim())))
                        expenseLabel = ""
                        expenseAmount = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Adicionar despesa") }
                record.expenses.forEachIndexed { index, expense ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("${expense.label.ifBlank { "Despesa" }}: ${expense.amountText}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { update(record.copy(expenses = record.expenses.filterIndexed { i, _ -> i != index })) }) {
                            Text("Remover")
                        }
                    }
                }
            }

            Button(onClick = { onCopySummary(record) }, modifier = Modifier.fillMaxWidth()) {
                Text("Copiar resumo completo")
            }
            TextButton(onClick = { update(BlaBlaCarTripRecord()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar registro local")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FinanceSummary(record: BlaBlaCarTripRecord) {
    val revenue = BlaBlaCarCollectorCalculator.totalRevenue(record)
    val expenses = BlaBlaCarCollectorCalculator.totalExpenses(record)
    val profit = BlaBlaCarCollectorCalculator.profit(record)
    val profitPerKm = BlaBlaCarCollectorCalculator.profitPerKm(record)
    SectionCard(title = "Resumo financeiro") {
        Text("Faturamento: ${BlaBlaCarCollectorCalculator.formatMoney(revenue)}")
        Text("Despesas: ${BlaBlaCarCollectorCalculator.formatMoney(expenses)}")
        Text("Lucro total: ${BlaBlaCarCollectorCalculator.formatMoney(profit)}")
        Text("Lucro por km: ${profitPerKm?.let { BlaBlaCarCollectorCalculator.formatMoney(it) } ?: "sem distancia"}")
    }
}

@Composable
private fun PassengerRow(
    passenger: BlaBlaCarPassenger,
    onWhatsApp: () -> Unit,
    onPickup: () -> Unit,
    onDropoff: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(passenger.name.ifBlank { "Passageiro" }, style = MaterialTheme.typography.titleMedium)
        Text("Telefone: ${passenger.phone.ifBlank { "sem telefone" }}")
        Text("Valor: ${passenger.fareText.ifBlank { "sem valor" }} | Vagas: ${passenger.seats} | ${passenger.status}")
        if (passenger.pickup.isNotBlank()) Text("Embarque: ${passenger.pickup}")
        if (passenger.dropoff.isNotBlank()) Text("Desembarque: ${passenger.dropoff}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onWhatsApp, modifier = Modifier.weight(1f), enabled = passenger.phone.isNotBlank()) { Text("WhatsApp") }
            Button(onClick = onPickup, modifier = Modifier.weight(1f), enabled = passenger.pickup.isNotBlank()) { Text("GPS embarque") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDropoff, modifier = Modifier.weight(1f), enabled = passenger.dropoff.isNotBlank()) { Text("GPS destino") }
            TextButton(onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Remover") }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun TextFieldLine(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}
