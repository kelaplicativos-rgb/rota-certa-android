package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun BlaBlaPublicSearchPanel(
    trips: List<Trip>,
    currentResponse: BlaBlaPublicSearchResponse?,
    onResult: (BlaBlaPublicSearchResponse?) -> Unit,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { BlaBlaPublicSearchStore(context) }
    val registry = remember(context) { BlaBlaDynamicAccountRegistry(context) }
    val previous = remember { store.lastRequest() }
    val knownNames = remember {
        registry.list().mapNotNull { account ->
            account.profileName?.trim()?.takeIf(String::isNotEmpty)
                ?: account.displayLabel.trim().takeIf(String::isNotEmpty)
        }.distinct()
    }
    val initialPeriod = previous?.period?.takeIf(String::isNotBlank) ?: YearMonth.now().toString()
    val agendaRoute = remember(trips, initialPeriod) {
        BlaBlaCollectorScope.fromAgenda(trips, initialPeriod, maxRoutes = 4).firstOrNull()
    }
    var names by remember {
        mutableStateOf(
            previous?.targetNames?.joinToString(", ")?.takeIf(String::isNotBlank)
                ?: knownNames.joinToString(", "),
        )
    }
    var from by remember { mutableStateOf(previous?.from ?: agendaRoute?.from.orEmpty()) }
    var to by remember { mutableStateOf(previous?.to ?: agendaRoute?.to.orEmpty()) }
    var period by remember { mutableStateOf(initialPeriod) }
    var includeReverse by remember { mutableStateOf(previous?.includeReverse ?: true) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        running = false
        val response = store.lastResponse()
        onResult(response)
        val status = result.data?.getStringExtra(BlaBlaPublicSearchIntents.EXTRA_RESULT_STATUS)
            ?: response?.status
            ?: if (result.resultCode == Activity.RESULT_OK) "validated" else "error"
        val message = when (status) {
            "validated" -> "Consulta pública concluída ✅"
            "partial" -> "Consulta pública parcial ⚠️ • confira as consultas com falha."
            else -> "Consulta pública não pôde ser validada."
        }
        onChanged(message)
        error = if (status == "error") message else null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Consulta pública", style = MaterialTheme.typography.titleMedium)
            Text(
                "Somente leitura. Usa a busca pública da BlaBlaCar em perfil WebView isolado e não altera Agenda, passageiros, vagas ou contas logadas.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = names,
                onValueChange = { names = it },
                label = { Text("Nomes dos motoristas/perfis") },
                supportingText = {
                    Text(
                        if (knownNames.isEmpty()) "Separe vários nomes por vírgula." else
                            "Perfis conhecidos pela Agenda: ${knownNames.joinToString(", ")}",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text("Origem") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("Destino") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = period,
                onValueChange = { period = it },
                label = { Text("Data ou mês") },
                supportingText = { Text("AAAA-MM-DD para um dia ou AAAA-MM para varrer o mês inteiro.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pesquisar também o sentido inverso")
                    Text("No mês inteiro, isso audita ida e volta.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = includeReverse, onCheckedChange = { includeReverse = it })
            }

            val plannedTasks = remember(from, to, period, includeReverse) {
                BlaBlaPublicSearchPlanner.tasks(
                    BlaBlaPublicSearchRequest(emptyList(), from, to, period, includeReverse),
                ).size
            }
            if (plannedTasks > 0) {
                Text("Varredura prevista: $plannedTasks consulta(s) públicas.", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val targets = names.split(',', ';', '\n').map(String::trim).filter(String::isNotBlank).distinct()
                    val req = BlaBlaPublicSearchRequest(
                        targetNames = targets,
                        from = from.trim(),
                        to = to.trim(),
                        period = period.trim(),
                        includeReverse = includeReverse,
                    )
                    error = when {
                        targets.isEmpty() -> "Informe pelo menos um nome de motorista/perfil."
                        req.from.isBlank() || req.to.isBlank() -> "Informe origem e destino."
                        BlaBlaPublicSearchPlanner.tasks(req).isEmpty() -> "Informe uma data AAAA-MM-DD ou um mês AAAA-MM válido."
                        !BlaBlaPublicPlaceDirectory.supported(req.from) -> "Origem ainda não reconhecida pela Consulta Pública."
                        !BlaBlaPublicPlaceDirectory.supported(req.to) -> "Destino ainda não reconhecido pela Consulta Pública."
                        else -> null
                    }
                    if (error == null) {
                        running = true
                        store.saveRequest(req)
                        onChanged("Consulta pública iniciada • ${BlaBlaPublicSearchPlanner.tasks(req).size} busca(s).")
                        launcher.launch(BlaBlaPublicSearchIntents.search(context, req))
                    }
                },
            ) {
                Text(if (running) "Buscando…" else "Buscar")
            }
            if (currentResponse != null) {
                OutlinedButton(
                    onClick = {
                        store.clearResponse()
                        onResult(null)
                        onChanged("Resultado da Consulta Pública limpo. A Agenda foi preservada.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Limpar resultado público") }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun BlaBlaPublicSearchTimelineResults(
    response: BlaBlaPublicSearchResponse,
    trips: List<Trip>,
) {
    val context = LocalContext.current
    val zoneId = ZoneId.systemDefault()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val relevantLocalTrips = remember(response, trips) { relevantAgendaTrips(response.request, trips, zoneId) }
    val cards = remember(response.cards) {
        response.cards.sortedWith(compareBy<BlaBlaPublicSearchCard>({ it.date }, { it.departureTime.orEmpty() }, { it.driverName }))
    }
    val missingLocal = remember(response, relevantLocalTrips) {
        relevantLocalTrips.filter { local -> cards.none { card -> publicCardMatchesAgendaTrip(card, local, zoneId) } }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Timeline • Consulta pública", style = MaterialTheme.typography.titleMedium)
            Text(
                "${response.validatedQueries}/${response.queries.size} consultas validadas • ${cards.size} publicação(ões) dos nomes pesquisados.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (response.failedQueries > 0) {
                Text("⚠️ ${response.failedQueries} consulta(s) não foram validadas; ausência nessas datas não deve ser tratada como confirmação.")
            }
            if (cards.isEmpty()) {
                Text("Nenhuma publicação dos nomes informados foi encontrada nas consultas validadas.")
            }
            cards.forEach { card ->
                val localMatch = relevantLocalTrips.firstOrNull { publicCardMatchesAgendaTrip(card, it, zoneId) }
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        val date = runCatching { LocalDate.parse(card.date).format(dateFormatter) }.getOrDefault(card.date)
                        Text("$date • ${card.departureTime ?: "--:--"} • ${card.driverName}", style = MaterialTheme.typography.titleSmall)
                        Text("${card.searchFrom} → ${card.searchTo}")
                        val realRoute = listOfNotNull(card.actualDeparture, card.actualArrival).joinToString(" → ")
                        if (realRoute.isNotBlank()) Text("Cartão: $realRoute", style = MaterialTheme.typography.bodySmall)
                        card.price?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (card.flags.isNotEmpty()) Text(card.flags.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (localMatch != null) "✅ Corresponde a uma viagem da Agenda" else "⚠️ Publicação encontrada sem viagem correspondente na Agenda",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        card.tripHref?.let { href ->
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(href))) }
                            }) { Text("Abrir publicação pública") }
                        }
                    }
                }
            }
            if (missingLocal.isNotEmpty()) {
                Text("Agenda sem correspondência pública", style = MaterialTheme.typography.titleSmall)
                missingLocal.sortedBy(Trip::departureAtMillis).forEach { trip ->
                    val stops = trip.stops.sortedBy(TripStop::order)
                    val instant = Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId)
                    Text(
                        "❌ ${instant.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))} • ${stops.firstOrNull()?.name.orEmpty()} → ${stops.lastOrNull()?.name.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

internal fun relevantAgendaTrips(
    request: BlaBlaPublicSearchRequest,
    trips: List<Trip>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Trip> {
    val dates = BlaBlaPublicSearchPlanner.datesFor(request.period).toSet()
    val allowedRoutes = buildSet {
        add(BlaBlaPublicSearchPlanner.normalizePlace(request.from) to BlaBlaPublicSearchPlanner.normalizePlace(request.to))
        if (request.includeReverse) {
            add(BlaBlaPublicSearchPlanner.normalizePlace(request.to) to BlaBlaPublicSearchPlanner.normalizePlace(request.from))
        }
    }
    return trips.filter { trip ->
        if (trip.status == TripStatus.CANCELLED) return@filter false
        val stops = trip.stops.sortedBy(TripStop::order)
        if (stops.size < 2) return@filter false
        val date = Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId).toLocalDate()
        val route = BlaBlaPublicSearchPlanner.normalizePlace(stops.first().name) to
            BlaBlaPublicSearchPlanner.normalizePlace(stops.last().name)
        date in dates && route in allowedRoutes
    }
}

internal fun publicCardMatchesAgendaTrip(
    card: BlaBlaPublicSearchCard,
    trip: Trip,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return false
    val date = runCatching { LocalDate.parse(card.date) }.getOrNull() ?: return false
    val time = runCatching { card.departureTime?.trim()?.let(LocalTime::parse) }.getOrNull() ?: return false
    val local = Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId)
    if (local.toLocalDate() != date) return false
    val publicMinutes = time.hour * 60 + time.minute
    val localMinutes = local.hour * 60 + local.minute
    if (abs(publicMinutes - localMinutes) > 45) return false
    return BlaBlaPublicSearchPlanner.normalizePlace(stops.first().name) == BlaBlaPublicSearchPlanner.normalizePlace(card.searchFrom) &&
        BlaBlaPublicSearchPlanner.normalizePlace(stops.last().name) == BlaBlaPublicSearchPlanner.normalizePlace(card.searchTo)
}
