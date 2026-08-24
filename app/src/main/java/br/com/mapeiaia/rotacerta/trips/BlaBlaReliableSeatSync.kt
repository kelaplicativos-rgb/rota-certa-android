package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/** Exact external publication selected for one manual/private booking. */
@Serializable
data class BlaBlaManualSeatExternalTarget(
    val profileUuid: String,
    val tripId: String,
)

@Serializable
private data class BlaBlaManualSeatBookingBinding(
    val localBookingId: String,
    val profileUuid: String,
    val tripId: String,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

private class BlaBlaManualSeatBookingBindingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun target(localBookingId: String): BlaBlaManualSeatExternalTarget? = list()
        .firstOrNull { it.localBookingId == localBookingId }
        ?.let { BlaBlaManualSeatExternalTarget(it.profileUuid, it.tripId) }

    fun bind(localBookingId: String, target: BlaBlaManualSeatExternalTarget) {
        val next = list().filterNot { it.localBookingId == localBookingId } + BlaBlaManualSeatBookingBinding(
            localBookingId = localBookingId,
            profileUuid = target.profileUuid,
            tripId = target.tripId,
        )
        save(next)
    }

    fun remove(localBookingId: String) {
        save(list().filterNot { it.localBookingId == localBookingId })
    }

    private fun list(): List<BlaBlaManualSeatBookingBinding> = runCatching {
        json.decodeFromString<List<BlaBlaManualSeatBookingBinding>>(prefs.getString(KEY, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun save(value: List<BlaBlaManualSeatBookingBinding>) {
        prefs.edit().putString(KEY, json.encodeToString(value)).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_manual_seat_binding_v1"
        private const val KEY = "bindings"
    }
}

@Serializable
internal data class BlaBlaManualSeatSyncAttempt(
    val requestId: String,
    val localBookingId: String,
    val profileUuid: String,
    val tripId: String,
    val seatDelta: Int,
    val observedSeatsBefore: Int,
    val targetSeats: Int,
    val compensateAfterCancellation: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

internal class BlaBlaManualSeatSyncAttemptStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun get(requestId: String): BlaBlaManualSeatSyncAttempt? = list().firstOrNull { it.requestId == requestId }

    fun save(attempt: BlaBlaManualSeatSyncAttempt) {
        val next = list().filterNot { it.requestId == attempt.requestId } + attempt.copy(updatedAtMillis = System.currentTimeMillis())
        saveAll(next)
    }

    fun markCompensation(requestId: String): BlaBlaManualSeatSyncAttempt? {
        val current = get(requestId) ?: return null
        val next = current.copy(compensateAfterCancellation = true, updatedAtMillis = System.currentTimeMillis())
        save(next)
        return next
    }

    fun clear(requestId: String) {
        saveAll(list().filterNot { it.requestId == requestId })
    }

    private fun list(): List<BlaBlaManualSeatSyncAttempt> = runCatching {
        json.decodeFromString<List<BlaBlaManualSeatSyncAttempt>>(prefs.getString(KEY, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun saveAll(value: List<BlaBlaManualSeatSyncAttempt>) {
        prefs.edit().putString(KEY, json.encodeToString(value)).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_manual_seat_attempt_v1"
        private const val KEY = "attempts"
    }
}

internal enum class BlaBlaReliableSeatSyncAction {
    APPLY_TARGET,
    COMPLETE_ALREADY_APPLIED,
    APPLY_COMPENSATION,
    COMPLETE_COMPENSATION,
    PENDING_CONFLICT,
    PENDING_UNAVAILABLE,
    INVALID,
}

internal data class BlaBlaReliableSeatSyncDecision(
    val action: BlaBlaReliableSeatSyncAction,
    val targetSeats: Int? = null,
)

/** Pure retry/idempotency policy used by the Activity and unit tests. */
internal object BlaBlaReliableSeatSyncPolicy {
    fun decide(
        currentSeats: Int,
        canAdd: Boolean,
        canRemove: Boolean,
        seatDelta: Int,
        attempt: BlaBlaManualSeatSyncAttempt?,
    ): BlaBlaReliableSeatSyncDecision {
        if (currentSeats < 0 || seatDelta == 0) return BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.INVALID)

        if (attempt != null) {
            if (attempt.seatDelta != seatDelta || attempt.observedSeatsBefore < 0 || attempt.targetSeats < 0) {
                return BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.INVALID)
            }
            if (attempt.compensateAfterCancellation) {
                return when (currentSeats) {
                    attempt.observedSeatsBefore -> BlaBlaReliableSeatSyncDecision(
                        BlaBlaReliableSeatSyncAction.COMPLETE_COMPENSATION,
                        attempt.observedSeatsBefore,
                    )
                    attempt.targetSeats -> {
                        if (attempt.observedSeatsBefore > currentSeats && canAdd) {
                            BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.APPLY_COMPENSATION, attempt.observedSeatsBefore)
                        } else if (attempt.observedSeatsBefore < currentSeats && canRemove) {
                            BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.APPLY_COMPENSATION, attempt.observedSeatsBefore)
                        } else {
                            BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE)
                        }
                    }
                    else -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.PENDING_CONFLICT)
                }
            }

            return when (currentSeats) {
                attempt.targetSeats -> BlaBlaReliableSeatSyncDecision(
                    BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED,
                    attempt.targetSeats,
                )
                attempt.observedSeatsBefore -> when {
                    attempt.targetSeats > currentSeats && canAdd -> BlaBlaReliableSeatSyncDecision(
                        BlaBlaReliableSeatSyncAction.APPLY_TARGET,
                        attempt.targetSeats,
                    )
                    attempt.targetSeats < currentSeats && canRemove -> BlaBlaReliableSeatSyncDecision(
                        BlaBlaReliableSeatSyncAction.APPLY_TARGET,
                        attempt.targetSeats,
                    )
                    else -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE)
                }
                else -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.PENDING_CONFLICT)
            }
        }

        val target = currentSeats + seatDelta
        if (target < 0) return BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.INVALID)
        return when {
            target > currentSeats && canAdd -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.APPLY_TARGET, target)
            target < currentSeats && canRemove -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.APPLY_TARGET, target)
            target == currentSeats -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED, target)
            else -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE)
        }
    }
}

internal data class BlaBlaManualSeatCancellationResult(
    val shouldSync: Boolean,
    val message: String,
)

/**
 * Single bridge for manual/private booking -> exact external publication.
 * It binds the booking to profile UUID + trip id, deduplicates queued deltas and
 * safely compensates a decrease that became uncertain before a cancellation.
 */
object BlaBlaReliableSeatSyncBridge {
    fun targetForTimeline(entry: TripTimelineEntry): BlaBlaManualSeatExternalTarget? {
        val profile = entry.blablaProfileUuid
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(::isCanonicalUuid)
            ?: return null
        val tripId = entry.blablaTripId?.trim()?.takeIf(String::isNotEmpty)
            ?: BlaBlaTripIdentity.externalTripIdFromHref(entry.blablaTripHref)
            ?: return null
        return BlaBlaManualSeatExternalTarget(profile, tripId)
    }

    fun enqueueForManualBooking(
        context: Context,
        trip: Trip,
        booking: Booking,
        seatDelta: Int,
        explicitTarget: BlaBlaManualSeatExternalTarget? = null,
    ): BlaBlaManualSeatSyncRequest? {
        if (booking.source !in setOf(BookingSource.PRIVATE, BookingSource.OTHER)) return null
        if (seatDelta == 0 || kotlin.math.abs(seatDelta) != booking.seats) return null

        val requestStore = BlaBlaManualSeatSyncRequestStore(context)
        val ledger = BlaBlaManualSeatSyncLedger(context)
        val bindingStore = BlaBlaManualSeatBookingBindingStore(context)
        val normalizedExplicit = explicitTarget?.let(::normalizeTarget)
        val bound = bindingStore.target(booking.id)?.let(::normalizeTarget)
        val resolved = normalizedExplicit ?: bound ?: resolveFallbackTarget(context, trip)

        if (resolved == null) {
            recordPending(context, booking, "external_target_unresolved")
            return null
        }

        if (seatDelta < 0) {
            val verified = ledger.entry(booking.id)
            if (
                verified != null &&
                verified.externallyReducedSeats == booking.seats &&
                verified.profileUuid.equals(resolved.profileUuid, ignoreCase = true) &&
                verified.tripId == resolved.tripId
            ) {
                UnifiedDebugEventStore.record(
                    "EXTERNAL_SEAT_SYNC_DUPLICATE_SKIPPED",
                    context.packageName,
                    "booking=${booking.id} direction=decrease verified=true",
                )
                return null
            }
        } else {
            val verified = ledger.entry(booking.id)
            if (
                verified == null ||
                verified.externallyReducedSeats != booking.seats ||
                !verified.profileUuid.equals(resolved.profileUuid, ignoreCase = true) ||
                verified.tripId != resolved.tripId
            ) {
                recordPending(context, booking, "reverse_without_matching_verified_decrease")
                return null
            }
        }

        bindingStore.bind(booking.id, resolved)
        requestStore.list().firstOrNull { queued ->
            queued.localBookingId == booking.id &&
                queued.profileUuid.equals(resolved.profileUuid, ignoreCase = true) &&
                queued.tripId == resolved.tripId &&
                queued.seatDelta == seatDelta
        }?.let { existing ->
            UnifiedDebugEventStore.record(
                "EXTERNAL_SEAT_SYNC_DUPLICATE_SKIPPED",
                context.packageName,
                "booking=${booking.id} direction=${if (seatDelta < 0) "decrease" else "reverse"} queued=true request=${existing.id}",
            )
            return existing
        }

        val request = BlaBlaManualSeatSyncRequest(
            profileUuid = resolved.profileUuid,
            tripId = resolved.tripId,
            seatDelta = seatDelta,
            localTripId = trip.id,
            localBookingId = booking.id,
            source = booking.source.name,
        )
        requestStore.enqueue(request)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_QUEUED_RELIABLE",
            context.packageName,
            "manual=true booking=${booking.id} profileUuidPresent=true tripIdPresent=true delta=$seatDelta request=${request.id} strongBinding=${normalizedExplicit != null || bound != null}",
        )
        return request
    }

    fun onManualBookingCancelled(
        context: Context,
        trip: Trip,
        booking: Booking,
        explicitTarget: BlaBlaManualSeatExternalTarget? = null,
    ): BlaBlaManualSeatCancellationResult {
        val requestStore = BlaBlaManualSeatSyncRequestStore(context)
        val attemptStore = BlaBlaManualSeatSyncAttemptStore(context)
        val ledger = BlaBlaManualSeatSyncLedger(context)
        val bindingStore = BlaBlaManualSeatBookingBindingStore(context)
        val verified = ledger.entry(booking.id)

        if (verified != null && verified.externallyReducedSeats == booking.seats) {
            requestStore.list()
                .filter { it.localBookingId == booking.id && it.seatDelta < 0 }
                .forEach { stale ->
                    requestStore.remove(stale.id)
                    attemptStore.clear(stale.id)
                }
            val reverseTarget = BlaBlaManualSeatExternalTarget(verified.profileUuid, verified.tripId)
            bindingStore.bind(booking.id, reverseTarget)
            val reverse = enqueueForManualBooking(
                context = context,
                trip = trip,
                booking = booking,
                seatDelta = booking.seats,
                explicitTarget = reverseTarget,
            )
            return if (reverse != null) {
                BlaBlaManualSeatCancellationResult(
                    shouldSync = true,
                    message = "Passageiro manual cancelado. Vaga interna liberada • devolvendo ${booking.seats} vaga(s) à mesma publicação BlaBlaCar…",
                )
            } else {
                BlaBlaManualSeatCancellationResult(
                    shouldSync = false,
                    message = "Passageiro manual cancelado. Vaga interna liberada • devolução externa pendente ⚠️",
                )
            }
        }

        val pending = requestStore.list().filter { it.localBookingId == booking.id && it.seatDelta < 0 }
        if (pending.isNotEmpty()) {
            val attempted = pending.firstOrNull { attemptStore.get(it.id) != null }
            pending.filterNot { it.id == attempted?.id }.forEach { duplicate ->
                requestStore.remove(duplicate.id)
                attemptStore.clear(duplicate.id)
            }
            if (attempted != null) {
                attemptStore.markCompensation(attempted.id)
                return BlaBlaManualSeatCancellationResult(
                    shouldSync = true,
                    message = "Passageiro manual cancelado. Vaga interna liberada • conferindo se a redução externa chegou a ser aplicada antes de devolver a vaga…",
                )
            }
            pending.forEach { neverStarted ->
                requestStore.remove(neverStarted.id)
                attemptStore.clear(neverStarted.id)
            }
            bindingStore.remove(booking.id)
            return BlaBlaManualSeatCancellationResult(
                shouldSync = false,
                message = "Passageiro manual cancelado. Vaga interna liberada • a redução externa ainda não tinha começado e foi cancelada com segurança.",
            )
        }

        bindingStore.remove(booking.id)
        return BlaBlaManualSeatCancellationResult(
            shouldSync = false,
            message = "Passageiro manual cancelado. Vaga interna liberada • nenhuma vaga externa foi adicionada porque não existe redução externa confirmada.",
        )
    }

    private fun resolveFallbackTarget(context: Context, trip: Trip): BlaBlaManualSeatExternalTarget? {
        val response = BlaBlaCollectorStateStore(context).lastResponse() ?: return null
        val match = BlaBlaManualSeatTripResolver.resolveExact(trip, response) ?: return null
        val profile = match.profile_uuid.trim().lowercase(Locale.ROOT).takeIf(::isCanonicalUuid) ?: return null
        val tripId = match.trip_id?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return BlaBlaManualSeatExternalTarget(profile, tripId)
    }

    private fun normalizeTarget(target: BlaBlaManualSeatExternalTarget): BlaBlaManualSeatExternalTarget? {
        val profile = target.profileUuid.trim().lowercase(Locale.ROOT).takeIf(::isCanonicalUuid) ?: return null
        val tripId = target.tripId.trim().takeIf(String::isNotEmpty) ?: return null
        return BlaBlaManualSeatExternalTarget(profile, tripId)
    }

    private fun recordPending(context: Context, booking: Booking, reason: String) {
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_PENDING_RELIABLE",
            context.packageName,
            "reason=$reason booking=${booking.id} seats=${booking.seats} source=${booking.source.name}",
        )
    }

    private fun isCanonicalUuid(value: String): Boolean = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    ).matches(value)
}

object BlaBlaReliableSeatSyncIntents {
    fun seatSync(context: Context, account: BlaBlaDynamicAccount): Intent =
        Intent(context, BlaBlaReliableSeatSyncActivity::class.java)
            .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)
}

/**
 * Retry-safe external seat writer. A persisted before/target pair prevents
 * applying the same -N/+N twice when the save succeeds but verification fails.
 */
class BlaBlaReliableSeatSyncActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var account: BlaBlaDynamicAccount
    private lateinit var requestStore: BlaBlaManualSeatSyncRequestStore
    private lateinit var attemptStore: BlaBlaManualSeatSyncAttemptStore
    private lateinit var bindingStore: BlaBlaManualSeatBookingBindingStore
    private lateinit var ledger: BlaBlaManualSeatSyncLedger
    private lateinit var request: BlaBlaManualSeatSyncRequest
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var archive: BlaBlaMhtmlArchiveStore
    private var phase = Phase.BEFORE
    private var busy = false
    private var expectedSeats = -1
    private var verifyingCompensation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        requestStore = BlaBlaManualSeatSyncRequestStore(this)
        attemptStore = BlaBlaManualSeatSyncAttemptStore(this)
        bindingStore = BlaBlaManualSeatBookingBindingStore(this)
        ledger = BlaBlaManualSeatSyncLedger(this)
        account = registry.get(intent?.getStringExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID)) ?: run {
            finishPending("Conta BlaBlaCar não encontrada.", rotate = false)
            return
        }
        request = requestStore.peek() ?: run {
            finishPending("Nenhuma sincronização manual pendente.", rotate = false)
            return
        }
        if (!account.profileUuid.equals(request.profileUuid, ignoreCase = true)) {
            finishPending("UUID da conta não corresponde à publicação.", rotate = false)
            return
        }
        if (request.source !in setOf(BookingSource.PRIVATE.name, BookingSource.OTHER.name) || request.seatDelta == 0) {
            finishInvalid("Operação externa inválida; ela foi descartada para não bloquear a fila.")
            return
        }

        val ledgerEntry = ledger.entry(request.localBookingId)
        if (
            request.seatDelta < 0 &&
            ledgerEntry != null &&
            ledgerEntry.externallyReducedSeats == -request.seatDelta &&
            ledgerEntry.profileUuid.equals(request.profileUuid, ignoreCase = true) &&
            ledgerEntry.tripId == request.tripId
        ) {
            completeNoOp("Redução externa desse passageiro já estava confirmada.")
            return
        }
        if (request.seatDelta > 0) {
            if (ledgerEntry == null) {
                completeNoOp("A vaga já foi devolvida ou não havia redução externa comprovada.")
                return
            }
            if (
                ledgerEntry.externallyReducedSeats != request.seatDelta ||
                !ledgerEntry.profileUuid.equals(request.profileUuid, ignoreCase = true) ||
                ledgerEntry.tripId != request.tripId
            ) {
                finishPending("A prova da redução anterior não corresponde a esta publicação.", rotate = true)
                return
            }
        }

        archive = BlaBlaMhtmlArchiveStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusView = TextView(this).apply {
            text = "${account.displayLabel} • conferindo vagas da publicação exata…"
            setPadding(18, 18, 18, 18)
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        webView = WebView(this)
        configureReliableProfiledWebView(webView, account)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!BlaBlaCollectorUrlModule.isAllowed(url) || busy) return
                view.postDelayed({ handlePage() }, 700L)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_RELIABLE_START",
            packageName,
            "request=${request.id} booking=${request.localBookingId} delta=${request.seatDelta} tripIdPresent=true profileUuidPresent=true",
        )
        webView.loadUrl(reliableOptionsUrl(request.tripId))
    }

    private fun handlePage() {
        if (busy) return
        if (!BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, webView.url.orEmpty())) {
            finishPending("A página aberta não corresponde ao editor de vagas dessa publicação.", rotate = true)
            return
        }
        busy = true
        when (phase) {
            Phase.BEFORE -> archive.save(webView, account, "reliable-options-before", request.tripId) {
                evaluate<SeatOptionState>(RELIABLE_SEAT_OPTIONS_READ_JS) { state ->
                    if (state == null || state.seats < 0 || !state.savePresent) {
                        finishPending("O editor de vagas não está disponível ou não pôde ser lido.", rotate = true)
                        return@evaluate
                    }
                    if (!BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, state.pageUrl)) {
                        finishPending("A identidade da página de vagas não foi confirmada.", rotate = true)
                        return@evaluate
                    }
                    val existingAttempt = attemptStore.get(request.id)
                    if (existingAttempt != null && !attemptMatchesRequest(existingAttempt)) {
                        finishPending("A tentativa persistida não corresponde mais a esta operação.", rotate = true)
                        return@evaluate
                    }
                    val decision = BlaBlaReliableSeatSyncPolicy.decide(
                        currentSeats = state.seats,
                        canAdd = state.canAdd,
                        canRemove = state.canRemove,
                        seatDelta = request.seatDelta,
                        attempt = existingAttempt,
                    )
                    UnifiedDebugEventStore.record(
                        "EXTERNAL_SEAT_SYNC_RELIABLE_DECISION",
                        packageName,
                        "request=${request.id} current=${state.seats} delta=${request.seatDelta} action=${decision.action.name} target=${decision.targetSeats ?: -1} attempt=${existingAttempt != null} compensation=${existingAttempt?.compensateAfterCancellation == true}",
                    )
                    when (decision.action) {
                        BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED -> completeVerified(decision.targetSeats ?: state.seats, alreadyApplied = true)
                        BlaBlaReliableSeatSyncAction.COMPLETE_COMPENSATION -> completeCompensation(decision.targetSeats ?: state.seats, wrote = false)
                        BlaBlaReliableSeatSyncAction.APPLY_TARGET -> {
                            val target = decision.targetSeats ?: run {
                                finishPending("Alvo de vagas inválido.", rotate = true)
                                return@evaluate
                            }
                            if (existingAttempt == null) {
                                attemptStore.save(
                                    BlaBlaManualSeatSyncAttempt(
                                        requestId = request.id,
                                        localBookingId = request.localBookingId,
                                        profileUuid = request.profileUuid,
                                        tripId = request.tripId,
                                        seatDelta = request.seatDelta,
                                        observedSeatsBefore = state.seats,
                                        targetSeats = target,
                                    ),
                                )
                            }
                            applyTarget(state.seats, target, compensation = false)
                        }
                        BlaBlaReliableSeatSyncAction.APPLY_COMPENSATION -> {
                            val target = decision.targetSeats ?: run {
                                finishPending("Alvo de compensação inválido.", rotate = true)
                                return@evaluate
                            }
                            applyTarget(state.seats, target, compensation = true)
                        }
                        BlaBlaReliableSeatSyncAction.PENDING_CONFLICT -> finishPending(
                            "A quantidade externa mudou desde a tentativa anterior; não apliquei outra alteração automaticamente.",
                            rotate = true,
                        )
                        BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE -> finishPending(
                            "A BlaBlaCar não permite essa alteração de vagas neste momento.",
                            rotate = true,
                        )
                        BlaBlaReliableSeatSyncAction.INVALID -> finishPending(
                            "A alteração calculada ficou inválida; nenhuma vaga foi modificada.",
                            rotate = true,
                        )
                    }
                }
            }
            Phase.VERIFY -> archive.save(webView, account, "reliable-options-after", request.tripId) {
                evaluate<SeatOptionState>(RELIABLE_SEAT_OPTIONS_READ_JS) { state ->
                    if (state != null && state.seats == expectedSeats && BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, state.pageUrl)) {
                        if (verifyingCompensation) {
                            completeCompensation(expectedSeats, wrote = true)
                        } else {
                            completeVerified(expectedSeats, alreadyApplied = false)
                        }
                    } else {
                        finishPending("Alteração não confirmada após releitura; a tentativa ficou preservada para conferência idempotente.", rotate = true)
                    }
                }
            }
        }
    }

    private fun applyTarget(before: Int, target: Int, compensation: Boolean) {
        expectedSeats = target
        verifyingCompensation = compensation
        statusView.text = "${account.displayLabel} • $before → $target vagas • salvando…"
        phase = Phase.VERIFY
        webView.evaluateJavascript(applyReliableSeatsJs(target), null)
        val distance = kotlin.math.abs(target - before).coerceAtMost(20)
        webView.postDelayed({
            busy = false
            webView.loadUrl(reliableOptionsUrl(request.tripId))
        }, 1_700L + distance * 320L)
    }

    private fun completeVerified(afterSeats: Int, alreadyApplied: Boolean) {
        if (request.seatDelta < 0) {
            ledger.markVerifiedDecrease(request)
        } else {
            ledger.clearAfterVerifiedReverse(request.localBookingId)
            bindingStore.remove(request.localBookingId)
        }
        requestStore.remove(request.id)
        attemptStore.clear(request.id)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_RELIABLE_VERIFIED",
            packageName,
            "request=${request.id} booking=${request.localBookingId} after=$afterSeats delta=${request.seatDelta} alreadyApplied=$alreadyApplied ledger=true",
        )
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra("seat_sync_message", "Sincronizado externamente ✅ • $afterSeats vaga(s) publicadas"),
        )
        finish()
    }

    private fun completeCompensation(afterSeats: Int, wrote: Boolean) {
        requestStore.remove(request.id)
        attemptStore.clear(request.id)
        bindingStore.remove(request.localBookingId)
        ledger.clearAfterVerifiedReverse(request.localBookingId)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_RELIABLE_COMPENSATED",
            packageName,
            "request=${request.id} booking=${request.localBookingId} after=$afterSeats wrote=$wrote cancellation=true",
        )
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra("seat_sync_message", "Cancelamento reconciliado ✅ • $afterSeats vaga(s) publicadas"),
        )
        finish()
    }

    private fun completeNoOp(message: String) {
        if (::request.isInitialized) {
            requestStore.remove(request.id)
            attemptStore.clear(request.id)
            if (request.seatDelta > 0) bindingStore.remove(request.localBookingId)
        }
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, if (::account.isInitialized) account.id else "")
                .putExtra("seat_sync_message", "$message ✅"),
        )
        finish()
    }

    private fun finishInvalid(message: String) {
        if (::request.isInitialized) {
            requestStore.remove(request.id)
            attemptStore.clear(request.id)
        }
        setResult(RESULT_CANCELED, Intent().putExtra("seat_sync_message", message))
        finish()
    }

    private fun finishPending(message: String, rotate: Boolean) {
        if (::request.isInitialized) {
            UnifiedDebugEventStore.record(
                "EXTERNAL_SEAT_SYNC_RELIABLE_PENDING",
                packageName,
                "request=${request.id} booking=${request.localBookingId} delta=${request.seatDelta} reason=${message.replace(' ', '_').take(120)} retained=true",
            )
            if (rotate) {
                requestStore.remove(request.id)
                requestStore.enqueue(request)
            }
        }
        setResult(
            RESULT_CANCELED,
            Intent()
                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, if (::account.isInitialized) account.id else "")
                .putExtra("seat_sync_message", "Sincronização externa pendente ⚠️ • $message")
                .putExtra("seat_sync_request_retained", ::request.isInitialized),
        )
        finish()
    }

    private fun attemptMatchesRequest(attempt: BlaBlaManualSeatSyncAttempt): Boolean =
        attempt.requestId == request.id &&
            attempt.localBookingId == request.localBookingId &&
            attempt.profileUuid.equals(request.profileUuid, ignoreCase = true) &&
            attempt.tripId == request.tripId &&
            attempt.seatDelta == request.seatDelta

    private inline fun <reified T> evaluate(script: String, crossinline callback: (T?) -> Unit) {
        webView.evaluateJavascript(script) { encoded ->
            val decoded = runCatching {
                if (encoded.isNullOrBlank() || encoded == "null") return@runCatching null
                val raw = json.parseToJsonElement(encoded).jsonPrimitive.content
                json.decodeFromString<T>(raw)
            }.getOrNull()
            callback(decoded)
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private enum class Phase { BEFORE, VERIFY }
}

private fun configureReliableProfiledWebView(webView: WebView, account: BlaBlaDynamicAccount) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        WebViewCompat.setProfile(webView, account.webProfileName)
        WebViewCompat.getProfile(webView).cookieManager.apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setAcceptThirdPartyCookies(webView, true)
        }
    }
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.allowFileAccess = false
    webView.settings.allowContentAccess = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
}

private fun reliableOptionsUrl(tripId: String): String =
    "${BlaBlaCollectorUrlModule.ORIGIN}/rides/offer/edit/${tripId.trim()}/options"

private val RELIABLE_SEAT_OPTIONS_READ_JS = """
    (function() {
      const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
      const marker = (node) => clean(
        ((node && node.getAttribute && node.getAttribute('data-testid')) || '') + ' ' +
        ((node && node.getAttribute && node.getAttribute('aria-label')) || '') + ' ' +
        ((node && node.getAttribute && node.getAttribute('title')) || '') + ' ' +
        ((node && node.innerText) || '')
      ).toLowerCase();
      const buttons = Array.from(document.querySelectorAll('button, [role="button"]'));
      let remove = buttons.find((node) => /decrement|decrease|remove|minus|remover/.test(marker(node)) || /^[−–-]$/.test(clean(node.innerText)));
      let add = buttons.find((node) => /increment|increase|add|plus|adicionar/.test(marker(node)) || /^\+$/.test(clean(node.innerText)));
      let root = (remove && remove.parentElement) || (add && add.parentElement) || null;
      while (root && root !== document.body && root.querySelectorAll('button, [role="button"]').length < 2) root = root.parentElement;
      const grouped = root ? Array.from(root.querySelectorAll('button, [role="button"]')) : [];
      if (!remove && grouped.length >= 2) remove = grouped[0];
      if (!add && grouped.length >= 2) add = grouped[grouped.length - 1];
      root = root || document.querySelector('[data-testid*="seat"], [data-testid*="capacity"], [role="spinbutton"]') || document.body;
      const numeric = root.querySelector('input[type="number"], [role="spinbutton"], select');
      const controlled = numeric && clean(numeric.value || numeric.getAttribute('aria-valuenow') || numeric.getAttribute('value') || '');
      const leaves = Array.from(root.querySelectorAll('span, p, div'))
        .filter((node) => node.children.length === 0)
        .map((node) => clean(node.innerText))
        .filter((text) => /^\d{1,3}$/.test(text));
      let seats = /^\d{1,3}$/.test(controlled || '') ? parseInt(controlled, 10) : (leaves.length ? parseInt(leaves[0], 10) : -1);
      if (seats < 0) {
        const all = clean(root.innerText).match(/(?:^|\s)(\d{1,3})(?:\s|$)/);
        seats = all ? parseInt(all[1], 10) : -1;
      }
      const save = buttons.find((node) => /^(salvar|save)$/i.test(clean(node.innerText)))
        || document.querySelector('button[type="submit"], [data-testid*="save"], [data-testid*="submit"]');
      return JSON.stringify({
        seats: Number.isFinite(seats) ? seats : -1,
        canAdd: !!add && !add.disabled,
        canRemove: !!remove && !remove.disabled,
        savePresent: !!save,
        pageUrl: location.href || '',
        domHtml: ''
      });
    })();
""".trimIndent()

private fun applyReliableSeatsJs(target: Int): String = """
    (function() {
      const target = $target;
      const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
      const marker = (node) => clean(
        ((node && node.getAttribute && node.getAttribute('data-testid')) || '') + ' ' +
        ((node && node.getAttribute && node.getAttribute('aria-label')) || '') + ' ' +
        ((node && node.getAttribute && node.getAttribute('title')) || '') + ' ' +
        ((node && node.innerText) || '')
      ).toLowerCase();
      const controls = () => {
        const buttons = Array.from(document.querySelectorAll('button, [role="button"]'));
        let remove = buttons.find((node) => /decrement|decrease|remove|minus|remover/.test(marker(node)) || /^[−–-]$/.test(clean(node.innerText)));
        let add = buttons.find((node) => /increment|increase|add|plus|adicionar/.test(marker(node)) || /^\+$/.test(clean(node.innerText)));
        let root = (remove && remove.parentElement) || (add && add.parentElement) || null;
        while (root && root !== document.body && root.querySelectorAll('button, [role="button"]').length < 2) root = root.parentElement;
        const grouped = root ? Array.from(root.querySelectorAll('button, [role="button"]')) : [];
        if (!remove && grouped.length >= 2) remove = grouped[0];
        if (!add && grouped.length >= 2) add = grouped[grouped.length - 1];
        root = root || document.querySelector('[data-testid*="seat"], [data-testid*="capacity"], [role="spinbutton"]') || document.body;
        return {buttons, remove, add, root};
      };
      const read = () => {
        const c = controls();
        const numeric = c.root.querySelector('input[type="number"], [role="spinbutton"], select');
        const controlled = numeric && clean(numeric.value || numeric.getAttribute('aria-valuenow') || numeric.getAttribute('value') || '');
        if (/^\d{1,3}$/.test(controlled || '')) return parseInt(controlled, 10);
        const leaves = Array.from(c.root.querySelectorAll('span, p, div'))
          .filter((node) => node.children.length === 0)
          .map((node) => clean(node.innerText))
          .filter((text) => /^\d{1,3}$/.test(text));
        return leaves.length ? parseInt(leaves[0], 10) : -1;
      };
      let attempts = 0;
      const step = () => {
        attempts++;
        const current = read();
        if (current === target) {
          const c = controls();
          const save = c.buttons.find((node) => /^(salvar|save)$/i.test(clean(node.innerText)))
            || document.querySelector('button[type="submit"], [data-testid*="save"], [data-testid*="submit"]');
          if (save && !save.disabled) save.click();
          return;
        }
        if (attempts > 30 || current < 0) return;
        const c = controls();
        const button = current > target ? c.remove : c.add;
        if (!button || button.disabled) return;
        button.click();
        setTimeout(step, 280);
      };
      step();
      return JSON.stringify({scheduled:true,target:target});
    })();
""".trimIndent()
