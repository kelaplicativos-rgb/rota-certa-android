package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay

/**
 * 0.1.566 — operational replacement for the old Timeline surface.
 *
 * This screen remains an INDEX, not a second BlaBlaCar implementation:
 * - it shows trips from every currently connected/verified BlaBlaCar account;
 * - ordering is global by the canonical departure timestamp;
 * - trips stay in the active sequence for the full canonical operational lifecycle
 *   (arrival + grace, or safe retention when arrival is unknown) and only then move
 *   automatically to the collapsible archive while the screen remains open;
 * - the card carries no Rota Certa operational shortcuts;
 * - tapping a card opens the original administrative trip URL inside the isolated
 *   WebView profile that owns the confirmed profileUuid;
 * - success is no longer claimed at startActivity(): the browser activity attests the
 *   final main-frame destination before emitting CONFIRMED.
 *
 * Agenda/canonical/collector ownership is not changed here. If strong external
 * identity cannot be proven, the card is visible when appropriate but navigation
 * fails closed. Trips filtered from this surface receive an explicit sanitized reason.
 */
@Composable
internal fun OperationalAllTripsBrowserScreen0563(
    trips: List<Trip>,
    bookings: List<Booking>,
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {},
    onFirstUsableFrame: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val accounts = remember(trips, bookings) {
        BlaBlaDynamicAccountRegistry(context.applicationContext).list()
    }
    val projectedEntries = remember(trips, bookings) {
        localAgendaTimelineProjection0515(
            trips = trips,
            bookings = bookings,
            localProfileLabel = "Agenda",
        ).entries
    }
    val selection = remember(projectedEntries, accounts) {
        operationalConnectedSelection0564(
            entries = projectedEntries,
            accounts = accounts,
        )
    }
    val entries = selection.includedEntries
    val decisionByEntry = remember(selection.decisions) {
        selection.decisions.associateBy(OperationalTripDecision0564::entry)
    }
    val rows = remember(entries, accounts, decisionByEntry) {
        entries.map { entry ->
            val target = resolveBlaBlaTripTarget0407(
                context = context,
                entry = entry,
                accounts = accounts,
            )
            val account = target?.let { resolved ->
                accounts.singleOrNull { candidate -> candidate.id == resolved.accountId }
            } ?: entry.blablaProfileUuid
                ?.trim()
                ?.lowercase()
                ?.let { profileUuid ->
                    accounts.singleOrNull { candidate ->
                        candidate.profileUuid?.trim()?.lowercase() == profileUuid
                    }
                }
            val reason = decisionByEntry[entry]?.reason
                ?: if (target == null) OperationalTripDecisionReason0564.TARGET_UNRESOLVED else null
            OperationalTripBrowserRow0563(entry, account, target, reason)
        }
    }

    LaunchedEffect(selection.decisions) {
        selection.decisions
            .filter { decision -> decision.reason != null }
            .forEach { decision ->
                UnifiedDebugEventStore.recordAlways(
                    "OPERATIONAL_BROWSER_ENTRY_DECISION_0564",
                    context.packageName,
                    "tripKey=${operationalTripDecisionDiagnosticKey0564(decision.entry)} " +
                        "included=${decision.included} reason=${decision.reason?.name.orEmpty()} " +
                        "profilePresent=${!decision.entry.blablaProfileUuid.isNullOrBlank()} " +
                        "tripIdPresent=${!decision.entry.blablaTripId.isNullOrBlank()} " +
                        "hrefPresent=${!decision.entry.blablaTripHref.isNullOrBlank()} piiLogged=false",
                )
            }
    }
    LaunchedEffect(rows.map { row -> operationalTripBrowserKey0563(row.entry) to row.decisionReason }) {
        rows
            .filter { row ->
                row.decisionReason == OperationalTripDecisionReason0564.TARGET_UNRESOLVED
            }
            .forEach { row ->
                UnifiedDebugEventStore.recordAlways(
                    "OPERATIONAL_BROWSER_ENTRY_DECISION_0564",
                    context.packageName,
                    "tripKey=${operationalTripDecisionDiagnosticKey0564(row.entry)} " +
                        "included=true reason=${OperationalTripDecisionReason0564.TARGET_UNRESOLVED.name} " +
                        "profilePresent=${!row.entry.blablaProfileUuid.isNullOrBlank()} " +
                        "tripIdPresent=${!row.entry.blablaTripId.isNullOrBlank()} " +
                        "hrefPresent=${!row.entry.blablaTripHref.isNullOrBlank()} piiLogged=false",
                )
            }
    }

    LaunchedEffect(rows.size) {
        onFirstUsableFrame(rows.size)
    }

    if (accounts.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nenhuma conta BlaBlaCar conectada.")
        }
        return
    }

    if (rows.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nenhuma viagem das contas conectadas disponível no estado canônico.")
        }
        return
    }

    var nowMillis by remember(rows) { mutableStateOf(System.currentTimeMillis()) }
    var showArchived by remember { mutableStateOf(false) }

    LaunchedEffect(rows.map { it.entry.departureAtMillis }) {
        while (true) {
            val current = System.currentTimeMillis()
            nowMillis = current
            val nextBoundary = rows
                .asSequence()
                .map { row -> row.entry.departureAtMillis }
                .filter { departure -> departure >= current }
                .minOrNull()
            val waitMillis = nextBoundary
                ?.let { departure -> (departure - current + 50L).coerceIn(50L, 60_000L) }
                ?: 60_000L
            delay(waitMillis)
        }
    }

    val archiveSelection = remember(rows, nowMillis) {
        operationalArchiveSelection0566(
            items = rows,
            nowMillis = nowMillis,
            departureAtMillis = { row -> row.entry.departureAtMillis },
            arrivalAtMillis = { row -> row.entry.arrivalAtMillis },
        )
    }
    val activeRows = archiveSelection.active
    val archivedRows = archiveSelection.archived

    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember(zoneId, nowMillis) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    }

    val openRow: (OperationalTripBrowserRow0563) -> Unit = openRow@{ row ->
        val target = row.target
        val account = row.account
        if (target == null || account == null) {
            UnifiedDebugEventStore.recordAlways(
                "OPERATIONAL_BROWSER_TARGET_REJECTED_0564",
                context.packageName,
                "tripKey=${operationalTripDecisionDiagnosticKey0564(row.entry)} " +
                    "reason=${row.decisionReason?.name ?: OperationalTripDecisionReason0564.TARGET_UNRESOLVED.name} " +
                    "failClosed=true piiLogged=false",
            )
            onMessage("Não foi possível confirmar a conta e a viagem original na BlaBlaCar.")
            return@openRow
        }
        val liveAccount = BlaBlaDynamicAccountRegistry(context.applicationContext).get(account.id)
        val expectedProfile = target.profileUuid.trim().lowercase()
        val liveProfile = liveAccount?.profileUuid?.trim()?.lowercase().orEmpty()
        if (liveAccount == null || expectedProfile.isBlank() || liveProfile != expectedProfile) {
            UnifiedDebugEventStore.recordAlways(
                "OPERATIONAL_BROWSER_ACCOUNT_CONTEXT_MISMATCH_0564",
                context.packageName,
                "accountKey=${sha256TripPublication0387(account.id).take(16)} " +
                    "tripKey=${operationalTripDecisionDiagnosticKey0564(row.entry)} " +
                    "expectedProfilePresent=${expectedProfile.isNotBlank()} liveProfileMatches=false " +
                    "failClosed=true piiLogged=false",
            )
            onMessage("A identidade da conta mudou. Reconfirme o login desta conta antes de abrir a viagem.")
            return@openRow
        }

        val operationId = UUID.randomUUID().toString()
        runCatching {
            context.startActivity(
                OperationalTripBrowserIntents0564.open(
                    context = context,
                    account = liveAccount,
                    target = target,
                    operationId = operationId,
                ),
            )
        }.onSuccess {
            UnifiedDebugEventStore.recordAlways(
                "OPERATIONAL_BROWSER_TARGET_NAVIGATION_REQUESTED_0564",
                context.packageName,
                "operationId=$operationId " +
                    "accountKey=${sha256TripPublication0387(liveAccount.id).take(16)} " +
                    "tripKey=${sha256TripPublication0387(target.tripId).take(16)} " +
                    "profileVerified=true targetUrlTripMatches=${BlaBlaCollectorUrlModule.tripId(target.tripHref) == target.tripId} " +
                    "confirmed=false piiLogged=false cookiesLogged=false",
            )
        }.onFailure { error ->
            UnifiedDebugEventStore.record(
                "OPERATIONAL_BROWSER_TARGET_NAVIGATION_DISPATCH_FAILED_0564",
                context.packageName,
                "operationId=$operationId " +
                    "accountKey=${sha256TripPublication0387(liveAccount.id).take(16)} " +
                    "tripKey=${sha256TripPublication0387(target.tripId).take(16)} " +
                    "error=${error.javaClass.simpleName.take(80)} confirmed=false",
            )
            onMessage("Não foi possível abrir a viagem original na BlaBlaCar.")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (activeRows.isEmpty()) {
            item(key = "no-active-trips-0566") {
                Text(
                    text = "Nenhuma viagem atual ou futura.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            itemsIndexed(
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
        }

        if (archivedRows.isNotEmpty()) {
            item(key = "archived-toggle-0566") {
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

            if (showArchived) {
                itemsIndexed(
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
            }
        }
    }
}

internal data class OperationalTripBrowserRow0563(
    val entry: TripTimelineEntry,
    val account: BlaBlaDynamicAccount?,
    val target: BlaBlaTripTarget0407?,
    val decisionReason: OperationalTripDecisionReason0564? = null,
)

/** Compatibility entry point retained for existing unit tests/callers. */
internal fun operationalConnectedEntries0563(
    entries: List<TripTimelineEntry>,
    accounts: List<BlaBlaDynamicAccount>,
): List<TripTimelineEntry> = operationalConnectedSelection0564(entries, accounts).includedEntries

@Composable
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

internal fun operationalTripBrowserKey0563(entry: TripTimelineEntry): String =
    listOf(
        entry.blablaProfileUuid.orEmpty().trim().lowercase(),
        entry.blablaTripId.orEmpty().trim(),
        entry.tripId,
    ).joinToString("|")

private fun operationalDepartureDate0563(entry: TripTimelineEntry, zoneId: ZoneId): LocalDate =
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
