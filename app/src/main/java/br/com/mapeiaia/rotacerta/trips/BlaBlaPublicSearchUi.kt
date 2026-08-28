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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    var from by remember { mutableStateOf(previous?.from.orEmpty()) }
    var to by remember { mutableStateOf(previous?.to.orEmpty()) }
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
                "Somente leitura. A Agenda sugere perfis e rota, mas a busca usa exatamente os nomes, cidades e período informados aqui.",
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
            agendaRoute?.let { suggestion ->
                val differs = BlaBlaPublicSearchPlanner.normalizePlace(from) != BlaBlaPublicSearchPlanner.normalizePlace(suggestion.from) ||
                    BlaBlaPublicSearchPlanner.normalizePlace(to) != BlaBlaPublicSearchPlanner.normalizePlace(suggestion.to)
                if (differs) {
                    Text(
                        "Sugestão da Agenda: ${suggestion.from} → ${suggestion.to}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = {
                            from = suggestion.from
                            to = suggestion.to
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Usar rota sugerida da Agenda") }
                }
            }
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
                    Text("Varre também destino → origem.", style = MaterialTheme.typography.bodySmall)
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

internal fun validatedQueryCoversAgendaTrip(
    response: BlaBlaPublicSearchResponse,
    trip: Trip,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return false
    val date = Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId).toLocalDate().toString()
    val tripFrom = BlaBlaPublicSearchPlanner.normalizePlace(stops.first().name)
    val tripTo = BlaBlaPublicSearchPlanner.normalizePlace(stops.last().name)
    return response.queries.any { query ->
        query.status == "validated" &&
            query.date == date &&
            BlaBlaPublicSearchPlanner.normalizePlace(query.from) == tripFrom &&
            BlaBlaPublicSearchPlanner.normalizePlace(query.to) == tripTo
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

private fun publicProfileColor(slot: Int): Color {
    val palette = listOf(
        Color(0xFF1976D2),
        Color(0xFFFF7A00),
        Color(0xFF7B1FA2),
        Color(0xFF2E7D32),
        Color(0xFFD81B60),
        Color(0xFF00838F),
        Color(0xFF5D4037),
        Color(0xFF455A64),
    )
    return palette[Math.floorMod(slot, palette.size)]
}

private fun publicSearchDateLabel(raw: String): String {
    val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return raw
    val weekday = date.format(DateTimeFormatter.ofPattern("EEEE", Locale("pt", "BR")))
        .lowercase(Locale("pt", "BR"))
        .removeSuffix("-feira")
    return "${date.format(DateTimeFormatter.ofPattern("dd/MM"))} • $weekday"
}

private fun publicCardRoute(card: BlaBlaPublicSearchCard): String {
    val actual = listOfNotNull(
        card.actualDeparture?.takeIf(String::isNotBlank),
        card.actualArrival?.takeIf(String::isNotBlank),
    )
    return if (actual.size == 2) actual.joinToString(" → ") else "${card.searchFrom} → ${card.searchTo}"
}

private fun publicRoutesEquivalent(left: String, right: String): Boolean {
    fun parts(raw: String): Pair<String, String>? {
        val split = raw.split("→", limit = 2).map(String::trim)
        if (split.size != 2) return null
        return BlaBlaPublicSearchPlanner.normalizePlace(split[0]) to
            BlaBlaPublicSearchPlanner.normalizePlace(split[1])
    }
    return parts(left) == parts(right)
}
