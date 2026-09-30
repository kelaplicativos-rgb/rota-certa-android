package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 0.1.566 — operational replacement for the old Timeline surface.
 *
 * 0.1.633 — unified canonical trip center.
 * - it shows every canonical trip, whether originated in BlaBlaCar or directly in Rota Certa;
 * - ordering is global by the canonical departure timestamp;
 * - trips stay in the active sequence for the full canonical operational lifecycle
 *   (arrival + grace, or safe retention when arrival is unknown) and only then move
 *   automatically to the collapsible archive while the screen remains open;
 * - 0.1.654: every canonical card exposes the same passenger/shortcut footer used by
 *   Central do Dia, and expands the same canonical compact passenger operator in place;
 * - tapping the card body still opens the original administrative trip URL inside the isolated
 *   WebView profile that owns the confirmed profileUuid;
 * - success is no longer claimed at startActivity(): the browser activity attests the
 *   final main-frame destination before emitting CONFIRMED.
 *
 * Agenda/canonical/collector ownership is not changed here. If strong external
 * identity cannot be proven, the card is visible when appropriate but navigation
 * fails closed. Trips filtered from this surface receive an explicit sanitized reason.
 */
internal enum class OperationalTripSourceFilter0633 {
    ALL,
    ROTA_CERTA,
    BLABLACAR,
}

internal fun operationalTripMatchesSourceFilter0633(
    nativeRotaCerta: Boolean,
    filter: OperationalTripSourceFilter0633,
): Boolean = when (filter) {
    OperationalTripSourceFilter0633.ALL -> true
    OperationalTripSourceFilter0633.ROTA_CERTA -> nativeRotaCerta
    OperationalTripSourceFilter0633.BLABLACAR -> !nativeRotaCerta
}

internal enum class OperationalTripCardRefreshMode0663 {
    BLABLACAR_DIRECT_HTML,
    ROTA_CERTA_LOCAL,
    UNAVAILABLE,
}

internal enum class OperationalTripCardRefreshReason0707 {
    MANUAL,
    CARD_OPEN,
    RETURN_FROM_BLABLACAR,
}

internal fun operationalTripCardRefreshQueuesFollowUp0707(
    reason: OperationalTripCardRefreshReason0707,
): Boolean = reason == OperationalTripCardRefreshReason0707.RETURN_FROM_BLABLACAR

/**
 * One-shot bridge between the exact-card external round-trip and TripsActivity.
 * It suppresses only the generic full local snapshot reload caused by the matching
 * ON_RESUME. The exact-card refresh remains authoritative and event-driven.
 */
internal object OperationalTimelineExternalResume0707 {
    private val suppressNextGenericResume = AtomicBoolean(false)

    fun markExternalNavigationStarted() {
        suppressNextGenericResume.set(true)
    }

    fun cancelExternalNavigation() {
        suppressNextGenericResume.set(false)
    }

    fun consumeGlobalResumeSuppression(): Boolean =
        suppressNextGenericResume.getAndSet(false)
}

internal fun operationalTripCardRefreshMode0663(
    nativeRotaCerta: Boolean,
    canonicalTripPresent: Boolean,
): OperationalTripCardRefreshMode0663 = when {
    !canonicalTripPresent -> OperationalTripCardRefreshMode0663.UNAVAILABLE
    nativeRotaCerta -> OperationalTripCardRefreshMode0663.ROTA_CERTA_LOCAL
    else -> OperationalTripCardRefreshMode0663.BLABLACAR_DIRECT_HTML
}

@Composable
internal fun OperationalAllTripsBrowserScreen0563(
    trips: List<Trip>,
    bookings: List<Booking>,
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {},
    onFirstUsableFrame: (Int) -> Unit = {},
    onCreateTrip: () -> Unit = {},
    onManageCanonicalTrip: (String) -> Unit = {},
    onRefreshLocal: () -> Unit = {},
    onOpenTripIntegrity: (String) -> Unit = {},
    downloadTriggerToken0616: Int = 0,
) {
    val context = LocalContext.current
    val fallbackRefreshScope0663 = rememberCoroutineScope()
    val activity0707 = remember(context) { context.findComponentActivity0663() }
    val cardRefreshScope0663 = remember(activity0707, fallbackRefreshScope0663) {
        activity0707?.lifecycleScope ?: fallbackRefreshScope0663
    }
    val refreshingTripIds0663 = remember { mutableStateMapOf<String, Boolean>() }
    val pendingFollowUpTripIds0707 = remember { mutableStateMapOf<String, Boolean>() }
    var pendingExternalCanonicalTripId0707 by remember { mutableStateOf<String?>(null) }
    var externalRoundTripObservedAway0707 by remember { mutableStateOf(false) }
    val store0654 = remember(context) { TripStore(context) }
    val accounts = remember(trips, bookings) {
        BlaBlaDynamicAccountRegistry(context.applicationContext).list()
    }
    val projectedTimeline0602 = remember(trips, bookings) {
        localAgendaTimelineProjection0515(
            trips = trips,
            bookings = bookings,
            localProfileLabel = "Agenda",
        )
    }
    val projectedEntries = projectedTimeline0602.entries
    val projectedTripsById0602 = remember(projectedTimeline0602.trips) {
        projectedTimeline0602.trips.associateBy(Trip::id)
    }
    val selection = remember(projectedEntries, accounts) {
        operationalConnectedSelection0564(
            entries = projectedEntries,
            accounts = accounts,
        )
    }
    // 0.1.633: visibility belongs to the canonical trip domain, not to the
    // availability of a BlaBlaCar account. External navigation can still fail closed,
    // but native Rota Certa trips must remain fully manageable.
    val entries = remember(projectedEntries) {
        projectedEntries.sortedWith(
            compareBy<TripTimelineEntry> { it.departureAtMillis }
                .thenBy { it.tripId },
        )
    }

    AgendaTimelineDownloadAction0399(
        entries = entries,
        canonicalResponse0494 = localAgendaTimelineDownloadResponse0516(projectedTimeline0602),
        canonicalBookings0494 = projectedTimeline0602.bookings,
        triggerToken = downloadTriggerToken0616,
        onChanged = onMessage,
    )

    val decisionByEntry = remember(selection.decisions) {
        selection.decisions.associateBy(OperationalTripDecision0564::entry)
    }
    val rows = remember(
        entries,
        accounts,
        decisionByEntry,
        projectedTripsById0602,
    ) {
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
            val canonicalTrip0602 = entry.localTripId
                ?.let(projectedTripsById0602::get)
                ?: projectedTripsById0602[entry.tripId]
            val nativeRotaCerta0633 = canonicalTrip0602?.isNativeRotaCertaTrip0633() == true
            val reason = if (nativeRotaCerta0633) {
                null
            } else {
                decisionByEntry[entry]?.reason
                    ?: if (target == null) OperationalTripDecisionReason0564.TARGET_UNRESOLVED else null
            }
            val liveSegmentLoads0602 = operationalTimelineSegmentLoads0602(
                entry = entry,
                trip = canonicalTrip0602,
            )
            OperationalTripBrowserRow0563(
                entry = entry,
                account = account,
                target = target,
                canonicalTrip0633 = canonicalTrip0602,
                nativeRotaCerta0633 = nativeRotaCerta0633,
                decisionReason = reason,
                segmentLoads0602 = liveSegmentLoads0602,
            )
        }
    }

    var sourceFilter0633 by remember { mutableStateOf(OperationalTripSourceFilter0633.ALL) }
    val filteredRows0633 = remember(rows, sourceFilter0633) {
        rows.filter { row ->
            operationalTripMatchesSourceFilter0633(
                nativeRotaCerta = row.nativeRotaCerta0633,
                filter = sourceFilter0633,
            )
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

    LaunchedEffect(filteredRows0633.size) {
        onFirstUsableFrame(filteredRows0633.size)
    }

    if (rows.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nenhuma viagem disponível no estado canônico.")
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

    val archiveSelection = remember(filteredRows0633, nowMillis) {
        operationalArchiveSelection0566(
            items = filteredRows0633,
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

    fun refreshRow0663(
        row: OperationalTripBrowserRow0563,
        reason0707: OperationalTripCardRefreshReason0707 = OperationalTripCardRefreshReason0707.MANUAL,
    ) {
        val canonicalTripId0663 = operationalCanonicalTripId0654(row)
        val canonicalTrip0663 = row.canonicalTrip0633
        val mode0663 = operationalTripCardRefreshMode0663(
            nativeRotaCerta = row.nativeRotaCerta0633,
            canonicalTripPresent = canonicalTrip0663 != null && canonicalTripId0663.isNotBlank(),
        )
        if (refreshingTripIds0663[canonicalTripId0663] == true) {
            if (operationalTripCardRefreshQueuesFollowUp0707(reason0707)) {
                pendingFollowUpTripIds0707[canonicalTripId0663] = true
                UnifiedDebugEventStore.recordAlways(
                    "TIMELINE_CARD_RETURN_REFRESH_COALESCED_0707",
                    context.packageName,
                    "tripKey=${sha256TripPublication0387(canonicalTripId0663).take(16)} " +
                        "scope=TRIP_ONLY reason=${reason0707.name} followUpCount=1 latestWins=true noPolling=true",
                )
            }
            return
        }

        when (mode0663) {
            OperationalTripCardRefreshMode0663.UNAVAILABLE -> {
                UnifiedDebugEventStore.recordAlways(
                    "TRIPS_CARD_REFRESH_REJECTED_0663",
                    context.packageName,
                    "scope=CARD_ONLY reason=canonical_trip_unavailable noTraversal=true",
                )
                onMessage("Atualização não iniciada: viagem canônica não encontrada.")
            }

            OperationalTripCardRefreshMode0663.ROTA_CERTA_LOCAL -> {
                refreshingTripIds0663[canonicalTripId0663] = true
                try {
                    UnifiedDebugEventStore.recordAlways(
                        "TRIPS_CARD_LOCAL_REFRESH_0663",
                        context.packageName,
                        "tripKey=${sha256TripPublication0387(canonicalTripId0663).take(16)} " +
                            "scope=CARD_ONLY source=ROTA_CERTA_LOCAL noExternalTraversal=true",
                    )
                    onRefreshLocal()
                    onMessage("✅ Card Rota Certa atualizado.")
                } finally {
                    refreshingTripIds0663.remove(canonicalTripId0663)
                }
            }

            OperationalTripCardRefreshMode0663.BLABLACAR_DIRECT_HTML -> {
                val trip0663 = canonicalTrip0663 ?: return
                refreshingTripIds0663[canonicalTripId0663] = true
                UnifiedDebugEventStore.recordAlways(
                    if (reason0707 == OperationalTripCardRefreshReason0707.RETURN_FROM_BLABLACAR) {
                        "TIMELINE_CARD_RETURN_REFRESH_0707"
                    } else {
                        "TIMELINE_CARD_OPEN_REFRESH_0707"
                    },
                    context.packageName,
                    "tripKey=${sha256TripPublication0387(canonicalTripId0663).take(16)} " +
                        "scope=TRIP_ONLY reason=${reason0707.name} exactTargetOnly=true noPolling=true fullTraversal=false",
                )
                onMessage(
                    if (reason0707 == OperationalTripCardRefreshReason0707.RETURN_FROM_BLABLACAR) {
                        "📥 Confirmando somente este card após voltar da BlaBlaCar…"
                    } else {
                        "📥 Atualizando somente este card pelo HTML…"
                    },
                )
                cardRefreshScope0663.launch {
                    try {
                        val result0663 = CentralDayCommandBridge0552.refreshTripDirect0662(
                            context = context.applicationContext,
                            trip = trip0663,
                        )
                        onRefreshLocal()
                        UnifiedDebugEventStore.recordAlways(
                            "TRIPS_CARD_HTML_DIRECT_0663",
                            context.packageName,
                            "tripKey=${sha256TripPublication0387(canonicalTripId0663).take(16)} " +
                                "scope=TRIP_ONLY singleCard=true fullTraversal=false " +
                                "status=${result0663?.status?.name ?: "NOT_STARTED"} authority=HTML_DIRECT_0607",
                        )
                        onMessage(
                            when (result0663?.status) {
                                BlaBlaCommandStatus0407.VERIFIED_SUCCESS ->
                                    "✅ Card atualizado pelo HTML • nenhuma outra viagem foi percorrida."
                                BlaBlaCommandStatus0407.AUTH_REQUIRED ->
                                    "⚠ Sessão BlaBlaCar necessária para atualizar este card."
                                null ->
                                    "Atualização não iniciada: identidade forte indisponível ou atualização já em andamento."
                                else ->
                                    "⚠ Atualização deste card terminou com pendência: " +
                                        result0663.errorCode.ifBlank { result0663.status.name }
                            },
                        )
                    } finally {
                        refreshingTripIds0663.remove(canonicalTripId0663)
                        if (pendingFollowUpTripIds0707.remove(canonicalTripId0663) == true) {
                            UnifiedDebugEventStore.recordAlways(
                                "TIMELINE_CARD_RETURN_REFRESH_FOLLOW_UP_0707",
                                context.packageName,
                                "tripKey=${sha256TripPublication0387(canonicalTripId0663).take(16)} " +
                                    "scope=TRIP_ONLY latestWins=true noPolling=true",
                            )
                            refreshRow0663(
                                row = row,
                                reason0707 = OperationalTripCardRefreshReason0707.RETURN_FROM_BLABLACAR,
                            )
                        }
                    }
                }
            }
        }
    }

    val currentRows0707 by rememberUpdatedState(rows)
    val currentRefresh0707 by rememberUpdatedState<(OperationalTripBrowserRow0563, OperationalTripCardRefreshReason0707) -> Unit>(
        newValue = { row0707, reason0707 -> refreshRow0663(row0707, reason0707) },
    )
    DisposableEffect(activity0707) {
        val host0707 = activity0707
        if (host0707 == null) {
            onDispose { }
        } else {
            val observer0707 = LifecycleEventObserver { _, event0707 ->
                when (event0707) {
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    -> {
                        if (pendingExternalCanonicalTripId0707 != null) {
                            externalRoundTripObservedAway0707 = true
                        }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        val pendingId0707 = pendingExternalCanonicalTripId0707
                        if (externalRoundTripObservedAway0707 && pendingId0707 != null) {
                            pendingExternalCanonicalTripId0707 = null
                            externalRoundTripObservedAway0707 = false
                            val row0707 = currentRows0707.singleOrNull { candidate0707 ->
                                !candidate0707.nativeRotaCerta0633 &&
                                    operationalCanonicalTripId0654(candidate0707) == pendingId0707
                            }
                            if (row0707 != null) {
                                currentRefresh0707(
                                    row0707,
                                    OperationalTripCardRefreshReason0707.RETURN_FROM_BLABLACAR,
                                )
                            } else {
                                UnifiedDebugEventStore.recordAlways(
                                    "TIMELINE_CARD_RETURN_REFRESH_TARGET_MISSING_0707",
                                    context.packageName,
                                    "tripKey=${sha256TripPublication0387(pendingId0707).take(16)} " +
                                        "scope=TRIP_ONLY failClosed=true noFallbackTraversal=true noPolling=true",
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
            host0707.lifecycle.addObserver(observer0707)
            onDispose { host0707.lifecycle.removeObserver(observer0707) }
        }
    }

    val openRow: (OperationalTripBrowserRow0563) -> Unit = openRow@{ row ->
        if (row.nativeRotaCerta0633) {
            val canonicalId = row.canonicalTrip0633?.id
                ?: row.entry.localTripId
                ?: row.entry.tripId
            if (canonicalId.isBlank()) {
                onMessage("Não foi possível identificar a viagem do Rota Certa.")
            } else {
                onManageCanonicalTrip(canonicalId)
            }
            return@openRow
        }
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

        val canonicalTripId0707 = operationalCanonicalTripId0654(row)
        if (canonicalTripId0707.isBlank()) {
            onMessage("Não foi possível identificar de forma única a viagem para atualizar.")
            return@openRow
        }
        pendingExternalCanonicalTripId0707 = canonicalTripId0707
        externalRoundTripObservedAway0707 = false
        OperationalTimelineExternalResume0707.markExternalNavigationStarted()
        refreshRow0663(
            row = row,
            reason0707 = OperationalTripCardRefreshReason0707.CARD_OPEN,
        )

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
            if (pendingExternalCanonicalTripId0707 == canonicalTripId0707) {
                pendingExternalCanonicalTripId0707 = null
            }
            externalRoundTripObservedAway0707 = false
            OperationalTimelineExternalResume0707.cancelExternalNavigation()
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
        item(key = "create-native-trip-0633") {
            Button(
                onClick = onCreateTrip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ Nova viagem")
            }
        }

        item(key = "source-filter-0633") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    OperationalTripSourceFilter0633.ALL to "Todas",
                    OperationalTripSourceFilter0633.ROTA_CERTA to "Rota Certa",
                    OperationalTripSourceFilter0633.BLABLACAR to "BlaBlaCar",
                ).forEach { (filter0633, label0633) ->
                    if (sourceFilter0633 == filter0633) {
                        Button(
                            onClick = { sourceFilter0633 = filter0633 },
                            modifier = Modifier.weight(1f),
                        ) { Text(label0633) }
                    } else {
                        TextButton(
                            onClick = { sourceFilter0633 = filter0633 },
                            modifier = Modifier.weight(1f),
                        ) { Text(label0633) }
                    }
                }
            }
        }

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
                val canonicalTripId0654 = operationalCanonicalTripId0654(row)
                OperationalTripBrowserCard0563(
                    row = row,
                    zoneId = zoneId,
                    today = today,
                    archived = false,
                    store0654 = store0654,
                    bookings0654 = projectedTimeline0602.bookings.filter { it.tripId == canonicalTripId0654 },
                    onOperationsChanged0654 = { text0654 ->
                        onMessage(text0654)
                        onRefreshLocal()
                    },
                    refreshRunning0663 = refreshingTripIds0663[canonicalTripId0654] == true,
                    onRefreshCard0663 = {
                        refreshRow0663(row, OperationalTripCardRefreshReason0707.MANUAL)
                    },
                    onOpenIntegrity0654 = { onOpenTripIntegrity(canonicalTripId0654) },
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
                    val canonicalTripId0654 = operationalCanonicalTripId0654(row)
                    OperationalTripBrowserCard0563(
                        row = row,
                        zoneId = zoneId,
                        today = today,
                        archived = true,
                        store0654 = store0654,
                        bookings0654 = projectedTimeline0602.bookings.filter { it.tripId == canonicalTripId0654 },
                        onOperationsChanged0654 = { text0654 ->
                            onMessage(text0654)
                            onRefreshLocal()
                        },
                        refreshRunning0663 = refreshingTripIds0663[canonicalTripId0654] == true,
                        onRefreshCard0663 = {
                            refreshRow0663(row, OperationalTripCardRefreshReason0707.MANUAL)
                        },
                        onOpenIntegrity0654 = { onOpenTripIntegrity(canonicalTripId0654) },
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
    val canonicalTrip0633: Trip? = null,
    val nativeRotaCerta0633: Boolean = false,
    val decisionReason: OperationalTripDecisionReason0564? = null,
    val segmentLoads0602: List<SegmentLoad> = emptyList(),
)

internal fun operationalTimelineSegmentLoads0602(
    entry: TripTimelineEntry,
    trip: Trip?,
): List<SegmentLoad> {
    if (!entry.canonicalBackendAuthoritative0494) return emptyList()
    if (entry.canonicalCapacityReliable0494 != true || entry.capacity <= 0) return emptyList()
    return canonicalTimelineSegmentLoads0494(entry, trip)
}

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
    store0654: TripStore,
    bookings0654: List<Booking>,
    onOperationsChanged0654: (String) -> Unit,
    refreshRunning0663: Boolean,
    onRefreshCard0663: () -> Unit,
    onOpenIntegrity0654: () -> Unit,
    onOpen: () -> Unit,
) {
    val entry = row.entry
    val targetConfirmed = row.target != null && row.account != null
    val manageable0633 = row.nativeRotaCerta0633 || targetConfirmed
    val date = operationalDepartureDate0563(entry, zoneId)
    val departureTime = operationalDepartureTime0563(entry, zoneId)
    val arrivalTime = operationalArrivalTime0568(entry, zoneId)
    val duration = operationalDurationLabel0568(entry.departureAtMillis, entry.arrivalAtMillis)
    val dateLabel = operationalDateLabel0568(date, today)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = manageable0633, onClick = onOpen),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (row.nativeRotaCerta0633) {
                            "Rota Certa"
                        } else {
                            row.account?.displayLabel ?: "BlaBlaCar • conta não confirmada"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        enabled = !refreshRunning0663,
                        onClick = onRefreshCard0663,
                    ) {
                        Text(
                            text = if (refreshRunning0663) "…" else "↻",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            val canonicalTrip0667 = row.canonicalTrip0633
            if (canonicalTrip0667 != null) {
                EnhancedPassengerTimelineSection(
                    entry = entry,
                    trip = canonicalTrip0667,
                    store = store0654,
                    currentCoordinate = null,
                    onChanged = onOperationsChanged0654,
                    canonicalBookings0494 = bookings0654,
                    showTripActions0549 = false,
                    compactEmbeddedControls0593 = true,
                    segmentLoads0671 = row.segmentLoads0602,
                )
            } else {
                // Fail-closed fallback: without the canonical Trip there is no safe stop/passenger
                // association. Keep only the proven origin/destination summary.
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
            }

            // 0.1.671 — horários, vagas e PAX reais são renderizados pela
            // mesma seção de passageiros acima. Não existe uma segunda linha de ocupação.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenIntegrity0654) {
                    Text("Integridade", maxLines = 1)
                }
            }

            if (row.nativeRotaCerta0633) {
                Text(
                    text = if (archived) "Rota Certa • viagem arquivada" else "Rota Certa • toque para gerenciar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!targetConfirmed) {
                Text(
                    text = "BlaBlaCar • identidade externa incompleta — abertura bloqueada",
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

internal fun operationalCanonicalTripId0654(row: OperationalTripBrowserRow0563): String =
    row.canonicalTrip0633?.id
        ?: row.entry.localTripId
        ?: row.entry.tripId

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


private fun Context.findComponentActivity0663(): ComponentActivity? {
    var current0663: Context = this
    while (current0663 is ContextWrapper) {
        if (current0663 is ComponentActivity) return current0663
        val next0663 = current0663.baseContext
        if (next0663 === current0663) break
        current0663 = next0663
    }
    return current0663 as? ComponentActivity
}
