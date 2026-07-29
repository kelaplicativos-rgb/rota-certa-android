package br.com.mapeiaia.rotacerta

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class BlaBlaCarCollectorActivity : ComponentActivity() {
    private val store by lazy { CollectorStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    CollectorApp(
                        initialTrips = store.load(),
                        onPersist = store::save,
                        onOpen = ::openUri,
                        onExport = ::exportCsv,
                    )
                }
            }
        }
    }

    private fun openUri(uri: String) {
        if (uri.isBlank()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
            .onFailure { Toast.makeText(this, "Não foi possível abrir.", Toast.LENGTH_SHORT).show() }
    }

    private fun exportCsv(trips: List<CollectorTrip>) {
        val file = File(cacheDir, "rota-certa-caixa.csv")
        file.writeText(CollectorReport.csv(trips), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Exportar controle financeiro"))
    }
}

@Serializable
data class CollectorTrip(
    val id: String = UUID.randomUUID().toString(),
    val account: String = "Ezequiel S",
    val title: String = "",
    val dateTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val tripUrl: String = "",
    val passengers: List<CollectorPassenger> = emptyList(),
    val expenses: List<CollectorExpense> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val closed: Boolean = false,
)

@Serializable
data class CollectorPassenger(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val pickup: String = "",
    val dropoff: String = "",
    val seats: Int = 1,
    val fareCents: Long = 0,
    val paymentMethod: String = "BlaBlaCar",
    val paymentStatus: String = "Pendente",
    val bookingStatus: String = "Confirmada",
    val profileUrl: String = "",
    val conversationUrl: String = "",
)

@Serializable
data class CollectorExpense(
    val id: String = UUID.randomUUID().toString(),
    val category: String = "Outros",
    val description: String = "",
    val amountCents: Long = 0,
    val paid: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

private class CollectorStore(context: Context) {
    private val prefs = context.getSharedPreferences("collector_v2", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun load(): List<CollectorTrip> = runCatching {
        json.decodeFromString(prefs.getString("trips", "[]") ?: "[]")
    }.getOrDefault(emptyList())
    fun save(value: List<CollectorTrip>) { prefs.edit().putString("trips", json.encodeToString(value)).apply() }
}

private enum class CollectorScreen { HOME, BROWSER, REVIEW, DETAIL, FINANCE }
private enum class FinancePeriod { TODAY, WEEK, MONTH, YEAR, ALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectorApp(
    initialTrips: List<CollectorTrip>,
    onPersist: (List<CollectorTrip>) -> Unit,
    onOpen: (String) -> Unit,
    onExport: (List<CollectorTrip>) -> Unit,
) {
    var trips by remember { mutableStateOf(initialTrips) }
    var screen by remember { mutableStateOf(CollectorScreen.HOME) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<CollectorTrip?>(null) }
    var comparison by remember { mutableStateOf<List<String>>(emptyList()) }

    fun persist(next: List<CollectorTrip>) { trips = next; onPersist(next) }
    fun upsert(value: CollectorTrip) {
        val next = if (trips.any { it.id == value.id }) trips.map { if (it.id == value.id) value else it } else trips + value
        persist(next)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Coletor de Viagens") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NavButton("Viagens", screen == CollectorScreen.HOME) { screen = CollectorScreen.HOME }
                NavButton("Coletar", screen == CollectorScreen.BROWSER) { draft = null; comparison = emptyList(); screen = CollectorScreen.BROWSER }
                NavButton("Caixa", screen == CollectorScreen.FINANCE) { screen = CollectorScreen.FINANCE }
            }
            when (screen) {
                CollectorScreen.HOME -> TripsHome(
                    trips = trips,
                    onNew = { draft = CollectorTrip(); comparison = emptyList(); screen = CollectorScreen.BROWSER },
                    onOpen = { selectedId = it; screen = CollectorScreen.DETAIL },
                    onUpdate = { id -> selectedId = id; draft = trips.firstOrNull { it.id == id }; comparison = emptyList(); screen = CollectorScreen.BROWSER },
                )
                CollectorScreen.BROWSER -> CollectorBrowser(
                    previous = draft,
                    onCaptured = { captured ->
                        val old = draft
                        comparison = if (old == null) listOf("Nova viagem pronta para revisão") else CollectorSync.diff(old, captured)
                        draft = if (old == null) captured else CollectorSync.merge(old, captured)
                        screen = CollectorScreen.REVIEW
                    },
                )
                CollectorScreen.REVIEW -> draft?.let { value ->
                    ReviewTrip(
                        initial = value,
                        changes = comparison,
                        onSave = { upsert(it.copy(updatedAt = System.currentTimeMillis())); selectedId = it.id; screen = CollectorScreen.DETAIL },
                        onCancel = { screen = CollectorScreen.HOME },
                    )
                }
                CollectorScreen.DETAIL -> trips.firstOrNull { it.id == selectedId }?.let { trip ->
                    TripDetail(
                        trip = trip,
                        onChange = ::upsert,
                        onOpen = onOpen,
                        onUpdate = { draft = trip; comparison = emptyList(); screen = CollectorScreen.BROWSER },
                        onDelete = { persist(trips.filterNot { it.id == trip.id }); screen = CollectorScreen.HOME },
                    )
                } ?: run { screen = CollectorScreen.HOME }
                CollectorScreen.FINANCE -> FinanceDashboard(trips, onExport)
            }
        }
    }
}

@Composable
private fun RowScope.NavButton(label: String, selected: Boolean, action: () -> Unit) {
    if (selected) Button(action, Modifier.weight(1f)) { Text(label) }
    else OutlinedButton(action, Modifier.weight(1f)) { Text(label) }
}

@Composable
private fun TripsHome(trips: List<CollectorTrip>, onNew: () -> Unit, onOpen: (String) -> Unit, onUpdate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Abrir BlaBlaCar e coletar viagem") }
        if (trips.isEmpty()) Text("Nenhuma viagem salva.")
        trips.sortedByDescending { it.createdAt }.forEach { trip ->
            SectionCard(trip.title.ifBlank { "${trip.origin} → ${trip.destination}" }) {
                Text("${trip.account} • ${trip.dateTime.ifBlank { "Data não identificada" }}")
                Text("${trip.passengers.size} passageiro(s) • ${money(CollectorReport.revenue(trip))}")
                Text("Atualizada: ${formatDateTime(trip.updatedAt)}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ onOpen(trip.id) }, Modifier.weight(1f)) { Text("Abrir") }
                    OutlinedButton({ onUpdate(trip.id) }, Modifier.weight(1f)) { Text("Atualizar") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CollectorBrowser(previous: CollectorTrip?, onCaptured: (CollectorTrip) -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("Faça login, abra a viagem e toque em Coletar esta viagem.") }
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (previous != null) Text("Atualizando: ${previous.origin} → ${previous.destination}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ webView?.loadUrl("https://www.blablacar.com.br/") }, Modifier.weight(1f)) { Text("Início") }
            Button({
                status = "Lendo a página e preparando a revisão..."
                webView?.evaluateJavascript("window.RotaCollector.capture(document.documentElement.outerHTML, location.href);", null)
            }, Modifier.weight(2f)) { Text("Coletar esta viagem") }
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(HtmlBridge { html, url ->
                        val parsed = CollectorParser.parse(html, url, previous)
                        status = "Dados encontrados. Revise antes de salvar."
                        onCaptured(parsed)
                    }, "RotaCollector")
                    loadUrl(previous?.tripUrl?.takeIf { it.isNotBlank() } ?: "https://www.blablacar.com.br/")
                    webView = this
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 360.dp),
        )
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

private class HtmlBridge(private val receive: (String, String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    @JavascriptInterface fun capture(html: String, url: String) { handler.post { receive(html, url) } }
}

@Composable
private fun ReviewTrip(initial: CollectorTrip, changes: List<String>, onSave: (CollectorTrip) -> Unit, onCancel: () -> Unit) {
    var trip by remember(initial.id, initial.updatedAt) { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionCard("Revisão antes de salvar") {
            if (changes.isNotEmpty()) changes.forEach { Text("• $it") }
            Field("Conta", trip.account) { trip = trip.copy(account = it) }
            Field("Título", trip.title) { trip = trip.copy(title = it) }
            Field("Data e horário", trip.dateTime) { trip = trip.copy(dateTime = it) }
            Field("Origem", trip.origin) { trip = trip.copy(origin = it) }
            Field("Destino", trip.destination) { trip = trip.copy(destination = it) }
            Field("Link da viagem", trip.tripUrl) { trip = trip.copy(tripUrl = it) }
            Field("Observações", trip.notes, 3) { trip = trip.copy(notes = it) }
        }
        Text("Passageiros encontrados: ${trip.passengers.size}")
        trip.passengers.forEachIndexed { index, p ->
            PassengerEditor(p, onChange = { changed -> trip = trip.copy(passengers = trip.passengers.mapIndexed { i, old -> if (i == index) changed else old }) }, onRemove = { trip = trip.copy(passengers = trip.passengers.filterIndexed { i, _ -> i != index }) })
        }
        Button({ trip = trip.copy(passengers = trip.passengers + CollectorPassenger()) }, Modifier.fillMaxWidth()) { Text("Adicionar passageiro") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onCancel, Modifier.weight(1f)) { Text("Cancelar") }
            Button({ onSave(trip) }, Modifier.weight(1f)) { Text("Salvar viagem") }
        }
    }
}

@Composable
private fun TripDetail(trip: CollectorTrip, onChange: (CollectorTrip) -> Unit, onOpen: (String) -> Unit, onUpdate: () -> Unit, onDelete: () -> Unit) {
    var expenseDescription by remember { mutableStateOf("") }
    var expenseValue by remember { mutableStateOf("") }
    var expenseCategory by remember { mutableStateOf("Combustível") }
    var confirmDelete by remember { mutableStateOf(false) }
    val revenue = CollectorReport.revenue(trip)
    val received = CollectorReport.received(trip)
    val expenses = CollectorReport.expenses(trip)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionCard(trip.title.ifBlank { "${trip.origin} → ${trip.destination}" }) {
            Text("Receita bruta: ${money(revenue)}")
            Text("Recebido: ${money(received)}")
            Text("A receber: ${money((revenue - received).coerceAtLeast(0))}")
            Text("Despesas: ${money(expenses)}")
            Text("Lucro previsto: ${money(revenue - expenses)}")
            Text("Lucro realizado: ${money(received - expenses)}")
            Button(onUpdate, Modifier.fillMaxWidth()) { Text("Verificar e atualizar viagem") }
        }
        SectionCard("Passageiros") {
            trip.passengers.forEachIndexed { index, p ->
                PassengerActions(p, onOpen, onChange = { changed -> onChange(trip.copy(passengers = trip.passengers.mapIndexed { i, old -> if (i == index) changed else old }, updatedAt = System.currentTimeMillis())) })
                if (index < trip.passengers.lastIndex) HorizontalDivider()
            }
        }
        SectionCard("Adicionar despesa") {
            Field("Categoria", expenseCategory) { expenseCategory = it }
            Field("Descrição", expenseDescription) { expenseDescription = it }
            Field("Valor", expenseValue, keyboardType = KeyboardType.Decimal) { expenseValue = it }
            Button({
                val cents = parseMoney(expenseValue)
                if (cents > 0) onChange(trip.copy(expenses = trip.expenses + CollectorExpense(category = expenseCategory, description = expenseDescription, amountCents = cents), updatedAt = System.currentTimeMillis()))
                expenseDescription = ""; expenseValue = ""
            }, Modifier.fillMaxWidth()) { Text("Lançar despesa") }
            trip.expenses.forEach { Text("${it.category}: ${it.description} — ${money(it.amountCents)}") }
        }
        OutlinedButton({ onChange(trip.copy(closed = !trip.closed, updatedAt = System.currentTimeMillis())) }, Modifier.fillMaxWidth()) { Text(if (trip.closed) "Reabrir financeiro" else "Fechar financeiro") }
        TextButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Text("Excluir viagem") }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Excluir viagem?") }, text = { Text("Receitas e despesas desta viagem também serão removidas.") }, confirmButton = { TextButton(onDelete) { Text("Excluir") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar") } })
}

@Composable
private fun PassengerEditor(passenger: CollectorPassenger, onChange: (CollectorPassenger) -> Unit, onRemove: () -> Unit) {
    SectionCard(passenger.name.ifBlank { "Novo passageiro" }) {
        Field("Nome", passenger.name) { onChange(passenger.copy(name = it)) }
        Field("Telefone", passenger.phone, keyboardType = KeyboardType.Phone) { onChange(passenger.copy(phone = normalizePhone(it))) }
        Field("Embarque", passenger.pickup) { onChange(passenger.copy(pickup = it)) }
        Field("Desembarque", passenger.dropoff) { onChange(passenger.copy(dropoff = it)) }
        Field("Lugares", passenger.seats.toString(), keyboardType = KeyboardType.Number) { onChange(passenger.copy(seats = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) }
        Field("Valor", if (passenger.fareCents == 0L) "" else centsInput(passenger.fareCents), keyboardType = KeyboardType.Decimal) { onChange(passenger.copy(fareCents = parseMoney(it))) }
        TextButton(onRemove) { Text("Remover") }
    }
}

@Composable
private fun PassengerActions(passenger: CollectorPassenger, onOpen: (String) -> Unit, onChange: (CollectorPassenger) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Text(passenger.name.ifBlank { "Passageiro" }, style = MaterialTheme.typography.titleMedium)
        Text("${money(passenger.fareCents)} • ${passenger.seats} lugar(es) • ${passenger.paymentStatus}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ onOpen("https://wa.me/${normalizePhone(passenger.phone)}") }, Modifier.weight(1f), enabled = passenger.phone.isNotBlank()) { Text("Contato") }
            Button({ onOpen(mapsUrl(passenger.pickup)) }, Modifier.weight(1f), enabled = passenger.pickup.isNotBlank()) { Text("Embarque") }
            Button({ onOpen(mapsUrl(passenger.dropoff)) }, Modifier.weight(1f), enabled = passenger.dropoff.isNotBlank()) { Text("Destino") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton({ onOpen(passenger.profileUrl) }, Modifier.weight(1f), enabled = passenger.profileUrl.isNotBlank()) { Text("Perfil") }
            OutlinedButton({ onOpen(passenger.conversationUrl) }, Modifier.weight(1f), enabled = passenger.conversationUrl.isNotBlank()) { Text("BlaBlaCar") }
            OutlinedButton({ onChange(passenger.copy(paymentStatus = if (passenger.paymentStatus == "Recebido") "Pendente" else "Recebido")) }, Modifier.weight(1f)) { Text(if (passenger.paymentStatus == "Recebido") "Pendente" else "Recebido") }
        }
    }
}

@Composable
private fun FinanceDashboard(trips: List<CollectorTrip>, onExport: (List<CollectorTrip>) -> Unit) {
    var period by remember { mutableStateOf(FinancePeriod.MONTH) }
    val filtered = trips.filter { inPeriod(it.createdAt, period) }
    val revenue = filtered.sumOf { CollectorReport.revenue(it) }
    val received = filtered.sumOf { CollectorReport.received(it) }
    val expenses = filtered.sumOf { CollectorReport.expenses(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FinancePeriod.entries.forEach { p -> TextButton({ period = p }, Modifier.weight(1f)) { Text(periodLabel(p)) } }
        }
        SectionCard("Controle de caixa") {
            Text("Receitas: ${money(revenue)}")
            Text("Recebido: ${money(received)}")
            Text("A receber: ${money((revenue - received).coerceAtLeast(0))}")
            Text("Despesas: ${money(expenses)}")
            Text("Lucro previsto: ${money(revenue - expenses)}")
            Text("Lucro realizado: ${money(received - expenses)}")
            Text("Viagens: ${filtered.size} • Passageiros: ${filtered.sumOf { it.passengers.size }}")
        }
        filtered.sortedByDescending { it.createdAt }.forEach { Text("${it.dateTime.ifBlank { formatDateTime(it.createdAt) }} — ${it.origin} → ${it.destination}: ${money(CollectorReport.revenue(it) - CollectorReport.expenses(it))}") }
        Button({ onExport(filtered) }, Modifier.fillMaxWidth(), enabled = filtered.isNotEmpty()) { Text("Exportar CSV do período") }
    }
}

private object CollectorParser {
    fun parse(html: String, url: String, previous: CollectorTrip?): CollectorTrip {
        val text = readable(html)
        val lines = text.lines().filter { it.isNotBlank() }
        val route = Regex("(?i)([^\n]{2,50})\s+(?:→|para)\s+([^\n]{2,50})").find(text)
        val moneyMatches = Regex("R\\$\\s*([0-9]{1,4}(?:[.,][0-9]{2})?)").findAll(text).toList()
        val phones = Regex("(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[-\\s]?\\d{4}").findAll(text).map { normalizePhone(it.value) }.filter { it.length in 10..13 }.distinct().toList()
        val names = lines.filter { it.length in 2..35 && it.matches(Regex("[A-Za-zÀ-ÿ' ]+")) }.filterNot { it.contains(Regex("(?i)viagem|carona|reserva|passageiro|embarque|destino|pagamento|blablacar")) }.take(phones.size.coerceAtLeast(1))
        val passengers = if (phones.isNotEmpty()) phones.mapIndexed { index, phone ->
            val old = previous?.passengers?.getOrNull(index)
            old?.copy(phone = phone, fareCents = moneyMatches.getOrNull(index)?.groupValues?.get(1)?.let(::parseMoney) ?: old.fareCents)
                ?: CollectorPassenger(name = names.getOrNull(index).orEmpty(), phone = phone, fareCents = moneyMatches.getOrNull(index)?.groupValues?.get(1)?.let(::parseMoney) ?: 0)
        } else previous?.passengers ?: emptyList()
        return (previous ?: CollectorTrip()).copy(
            title = lines.firstOrNull { it.contains("→") || it.contains(Regex("(?i) para ")) } ?: previous?.title.orEmpty(),
            dateTime = lines.firstOrNull { it.contains(Regex("(?i)(segunda|terça|quarta|quinta|sexta|sábado|domingo|jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez|\\d{1,2}:\\d{2})")) } ?: previous?.dateTime.orEmpty(),
            origin = route?.groupValues?.getOrNull(1)?.trim() ?: previous?.origin.orEmpty(),
            destination = route?.groupValues?.getOrNull(2)?.trim() ?: previous?.destination.orEmpty(),
            tripUrl = url,
            passengers = passengers,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

private object CollectorSync {
    fun diff(old: CollectorTrip, fresh: CollectorTrip): List<String> {
        val result = mutableListOf<String>()
        if (old.origin != fresh.origin && fresh.origin.isNotBlank()) result += "Origem alterada: ${old.origin} → ${fresh.origin}"
        if (old.destination != fresh.destination && fresh.destination.isNotBlank()) result += "Destino alterado: ${old.destination} → ${fresh.destination}"
        val oldPhones = old.passengers.map { normalizePhone(it.phone) }.toSet()
        val newPassengers = fresh.passengers.filter { normalizePhone(it.phone) !in oldPhones }
        newPassengers.forEach { result += "Nova reserva: ${it.name.ifBlank { it.phone }} — ${money(it.fareCents)}" }
        old.passengers.forEach { p ->
            val updated = fresh.passengers.firstOrNull { normalizePhone(it.phone) == normalizePhone(p.phone) && p.phone.isNotBlank() }
            if (updated != null && updated.fareCents != p.fareCents) result += "Valor de ${p.name.ifBlank { p.phone }}: ${money(p.fareCents)} → ${money(updated.fareCents)}"
        }
        if (result.isEmpty()) result += "Nenhuma mudança identificada"
        return result
    }
    fun merge(old: CollectorTrip, fresh: CollectorTrip): CollectorTrip {
        val merged = old.passengers.toMutableList()
        fresh.passengers.forEach { candidate ->
            val index = merged.indexOfFirst { normalizePhone(it.phone) == normalizePhone(candidate.phone) && candidate.phone.isNotBlank() }
            if (index >= 0) merged[index] = merged[index].copy(
                name = candidate.name.ifBlank { merged[index].name }, phone = candidate.phone.ifBlank { merged[index].phone },
                fareCents = candidate.fareCents.takeIf { it > 0 } ?: merged[index].fareCents,
                pickup = candidate.pickup.ifBlank { merged[index].pickup }, dropoff = candidate.dropoff.ifBlank { merged[index].dropoff },
            ) else merged += candidate
        }
        return old.copy(
            title = fresh.title.ifBlank { old.title }, dateTime = fresh.dateTime.ifBlank { old.dateTime },
            origin = fresh.origin.ifBlank { old.origin }, destination = fresh.destination.ifBlank { old.destination },
            tripUrl = fresh.tripUrl.ifBlank { old.tripUrl }, passengers = merged, updatedAt = System.currentTimeMillis(),
        )
    }
}

private object CollectorReport {
    fun revenue(t: CollectorTrip) = t.passengers.filterNot { it.bookingStatus == "Cancelada" }.sumOf { it.fareCents }
    fun received(t: CollectorTrip) = t.passengers.filter { it.paymentStatus == "Recebido" }.sumOf { it.fareCents }
    fun expenses(t: CollectorTrip) = t.expenses.sumOf { it.amountCents }
    fun csv(trips: List<CollectorTrip>): String = buildString {
        appendLine("Conta;Data;Origem;Destino;Passageiros;Receita;Recebido;Despesas;Lucro previsto")
        trips.forEach { t -> appendLine(listOf(t.account, t.dateTime, t.origin, t.destination, t.passengers.size, centsInput(revenue(t)), centsInput(received(t)), centsInput(expenses(t)), centsInput(revenue(t) - expenses(t))).joinToString(";") { it.toString().replace(";", ",") }) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); content() }
    }
}

@Composable
private fun Field(label: String, value: String, minLines: Int = 1, keyboardType: KeyboardType = KeyboardType.Text, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, minLines = minLines, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = Modifier.fillMaxWidth())
}

private fun readable(value: String): String = value
    .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
    .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
    .replace(Regex("(?i)<br\\s*/?>|</(p|div|li|tr|td|section|article|h[1-6])>"), "\n")
    .replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    .lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }.distinct().joinToString("\n")

private fun normalizePhone(value: String) = value.filter(Char::isDigit).let { if (it.length in 10..11) "55$it" else it }
private fun mapsUrl(value: String) = "geo:0,0?q=${Uri.encode(value)}"
private fun parseMoney(value: String): Long {
    val cleaned = value.replace("R$", "").trim().replace(".", "").replace(",", ".")
    return ((cleaned.toDoubleOrNull() ?: 0.0) * 100).toLong()
}
private fun centsInput(cents: Long) = String.format(Locale("pt", "BR"), "%.2f", cents / 100.0)
private fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
private fun formatDateTime(value: Long) = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date(value))
private fun periodLabel(p: FinancePeriod) = when (p) { FinancePeriod.TODAY -> "Hoje"; FinancePeriod.WEEK -> "Semana"; FinancePeriod.MONTH -> "Mês"; FinancePeriod.YEAR -> "Ano"; FinancePeriod.ALL -> "Tudo" }
private fun inPeriod(timestamp: Long, period: FinancePeriod): Boolean {
    if (period == FinancePeriod.ALL) return true
    val now = Calendar.getInstance(); val start = Calendar.getInstance()
    when (period) {
        FinancePeriod.TODAY -> { start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0) }
        FinancePeriod.WEEK -> { start.firstDayOfWeek = Calendar.MONDAY; start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0) }
        FinancePeriod.MONTH -> { start.set(Calendar.DAY_OF_MONTH, 1); start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0) }
        FinancePeriod.YEAR -> { start.set(Calendar.DAY_OF_YEAR, 1); start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0) }
        FinancePeriod.ALL -> Unit
    }
    return timestamp in start.timeInMillis..now.timeInMillis
}
