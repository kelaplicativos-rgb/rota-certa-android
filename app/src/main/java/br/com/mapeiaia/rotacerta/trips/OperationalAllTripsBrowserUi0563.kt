package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * 0.1.564 — operational replacement for the old Timeline surface.
 *
 * This screen remains an INDEX, not a second BlaBlaCar implementation:
 * - it shows trips from every currently connected/verified BlaBlaCar account;
 * - ordering is global by the canonical departure timestamp;
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

    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row -> operationalTripBrowserKey0563(row.entry) },
        ) { index, row ->
            val date = operationalDepartureDate0563(row.entry, zoneId)
            val previousDate = rows.getOrNull(index - 1)?.let { previous ->
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
                onOpen = {
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
                        return@OperationalTripBrowserCard0563
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
                        return@OperationalTripBrowserCard0563
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
                },
            )
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
    onOpen: () -> Unit,
) {
    val targetConfirmed = row.target != null && row.account != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = targetConfirmed, onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = operationalDepartureTime0563(row.entry, zoneId),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = row.account?.displayLabel ?: "Conta não confirmada",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = row.entry.origin,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "↓",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = row.entry.destination,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (targetConfirmed) {
                    "Toque para abrir esta viagem no site original da BlaBlaCar"
                } else {
                    "Identidade externa incompleta — abertura bloqueada"
                },
                style = MaterialTheme.typography.bodySmall,
            )
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

private fun operationalDateLabel0563(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.plusDays(1) -> "Amanhã"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale("pt", "BR")))
        .replaceFirstChar { first -> if (first.isLowerCase()) first.titlecase(Locale("pt", "BR")) else first.toString() }
}
