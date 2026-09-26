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
import java.security.MessageDigest
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

    fun clearAll() {
        saveAll(emptyList())
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

@Serializable
internal enum class BlaBlaPublicationSeatSyncVisualState {
    AVAILABLE,
    SYNCING,
    SYNCED,
    PENDING,
    ERROR,
}

@Serializable
internal data class BlaBlaPublicationSeatSyncState(
    val profileUuid: String,
    val tripId: String,
    val desiredPublishedSeats: Int? = null,
    val lastObservedPublishedSeats: Int? = null,
    val state: BlaBlaPublicationSeatSyncVisualState = BlaBlaPublicationSeatSyncVisualState.AVAILABLE,
    val message: String = "Sincronizar somente as vagas deste card",
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

internal class BlaBlaPublicationSeatSyncStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun get(profileUuid: String, tripId: String): BlaBlaPublicationSeatSyncState? = list().firstOrNull {
        it.profileUuid.equals(profileUuid, ignoreCase = true) && it.tripId == tripId
    }

    fun snapshot(): List<BlaBlaPublicationSeatSyncState> = list()

    fun markDesired(profileUuid: String, tripId: String, desired: Int, message: String) = update(
        BlaBlaPublicationSeatSyncState(
            profileUuid = profileUuid,
            tripId = tripId,
            desiredPublishedSeats = desired,
            state = BlaBlaPublicationSeatSyncVisualState.PENDING,
            message = message,
        ),
    )

    fun markSyncing(profileUuid: String, tripId: String, desired: Int) = mutate(profileUuid, tripId) { current ->
        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(
            desiredPublishedSeats = desired,
            state = BlaBlaPublicationSeatSyncVisualState.SYNCING,
            message = "Sincronizando somente as vagas…",
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun markObserved(profileUuid: String, tripId: String, observed: Int) = mutate(profileUuid, tripId) { current ->
        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(
            lastObservedPublishedSeats = observed,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun markSynced(
        profileUuid: String,
        tripId: String,
        value: Int,
        message: String = "Vagas sincronizadas ✅",
    ) = mutate(profileUuid, tripId) { current ->
        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(
            desiredPublishedSeats = current?.desiredPublishedSeats ?: value,
            lastObservedPublishedSeats = value,
            state = BlaBlaPublicationSeatSyncVisualState.SYNCED,
            message = message,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun markPending(profileUuid: String, tripId: String, message: String, desired: Int? = null) = mutate(profileUuid, tripId) { current ->
        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(
            desiredPublishedSeats = desired ?: current?.desiredPublishedSeats,
            state = BlaBlaPublicationSeatSyncVisualState.PENDING,
            message = message,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun markError(profileUuid: String, tripId: String, message: String) = mutate(profileUuid, tripId) { current ->
        (current ?: BlaBlaPublicationSeatSyncState(profileUuid, tripId)).copy(
            state = BlaBlaPublicationSeatSyncVisualState.ERROR,
            message = message,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun clearPendingStates(): Int {
        val current = list()
        val next = current.filterNot {
            it.state in setOf(
                BlaBlaPublicationSeatSyncVisualState.PENDING,
                BlaBlaPublicationSeatSyncVisualState.SYNCING,
                BlaBlaPublicationSeatSyncVisualState.ERROR,
            )
        }
        if (next.size != current.size) {
            prefs.edit().putString(KEY, json.encodeToString(next)).apply()
        }
        return current.size - next.size
    }

    private fun mutate(
        profileUuid: String,
        tripId: String,
        transform: (BlaBlaPublicationSeatSyncState?) -> BlaBlaPublicationSeatSyncState,
    ) {
        update(transform(get(profileUuid, tripId)))
    }

    private fun update(value: BlaBlaPublicationSeatSyncState) {
        val next = list().filterNot {
            it.profileUuid.equals(value.profileUuid, ignoreCase = true) && it.tripId == value.tripId
        } + value
        prefs.edit().putString(KEY, json.encodeToString(next)).apply()
    }

    private fun list(): List<BlaBlaPublicationSeatSyncState> = runCatching {
        json.decodeFromString<List<BlaBlaPublicationSeatSyncState>>(prefs.getString(KEY, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFS = "rota_certa_blablacar_publication_seat_sync_state_v1"
        private const val KEY = "states"
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

internal object BlaBlaReliableSeatQueuePolicy {
    fun select(
        queue: List<BlaBlaManualSeatSyncRequest>,
        hasPersistedAttempt: (String) -> Boolean,
    ): BlaBlaManualSeatSyncRequest? =
        queue.firstOrNull { !hasPersistedAttempt(it.id) } ?: queue.firstOrNull()
}

internal object BlaBlaReliableSeatRequestSelector {
    /**
     * If the launcher supplied an id, only that exact request is valid.
     * The legacy first-item fallback is retained only for callers without an id.
     */
    fun select(
        queue: List<BlaBlaManualSeatSyncRequest>,
        requestId: String?,
    ): BlaBlaManualSeatSyncRequest? {
        val exactId = requestId?.trim()?.takeIf(String::isNotEmpty)
        return if (exactId == null) queue.firstOrNull() else queue.firstOrNull { it.id == exactId }
    }
}

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

    fun decideDesired(
        currentSeats: Int,
        canAdd: Boolean,
        canRemove: Boolean,
        desiredPublishedSeats: Int,
    ): BlaBlaReliableSeatSyncDecision {
        if (currentSeats < 0 || desiredPublishedSeats < 0) {
            return BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.INVALID)
        }
        return when {
            currentSeats == desiredPublishedSeats -> BlaBlaReliableSeatSyncDecision(
                BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED,
                desiredPublishedSeats,
            )
            currentSeats > desiredPublishedSeats && canRemove -> BlaBlaReliableSeatSyncDecision(
                BlaBlaReliableSeatSyncAction.APPLY_TARGET,
                desiredPublishedSeats,
            )
            currentSeats < desiredPublishedSeats && canAdd -> BlaBlaReliableSeatSyncDecision(
                BlaBlaReliableSeatSyncAction.APPLY_TARGET,
                desiredPublishedSeats,
            )
            else -> BlaBlaReliableSeatSyncDecision(BlaBlaReliableSeatSyncAction.PENDING_UNAVAILABLE, desiredPublishedSeats)
        }
    }
}

data class BlaBlaManualSeatCancellationResult(
    val shouldSync: Boolean,
    val message: String,
)

data class BlaBlaDesiredSeatSyncRequestResult(
    val shouldSync: Boolean,
    val message: String,
    val desiredPublishedSeats: Int? = null,
    val noOp: Boolean = false,
    val blockedReason: String? = null,
)

internal enum class BlaBlaPublicBookingQueueMergeKind { DELTA, ABSOLUTE, NO_OP, BLOCKED }

internal data class BlaBlaPublicBookingQueueMerge(
    val kind: BlaBlaPublicBookingQueueMergeKind,
    val seatDelta: Int = 0,
    val desiredPublishedSeats: Int? = null,
)

internal fun mergePublicBookingSeatWork(
    existing: BlaBlaManualSeatSyncRequest?,
    existingAttempt: BlaBlaManualSeatSyncAttempt?,
    incomingDelta: Int,
): BlaBlaPublicBookingQueueMerge {
    if (incomingDelta == 0) return BlaBlaPublicBookingQueueMerge(BlaBlaPublicBookingQueueMergeKind.NO_OP)
    if (existing == null) return BlaBlaPublicBookingQueueMerge(BlaBlaPublicBookingQueueMergeKind.DELTA, seatDelta = incomingDelta)
    val baseAbsolute = existingAttempt?.targetSeats ?: existing.desiredPublishedSeats
    if (baseAbsolute != null) {
        val desired = baseAbsolute + incomingDelta
        return if (desired >= 0) {
            BlaBlaPublicBookingQueueMerge(BlaBlaPublicBookingQueueMergeKind.ABSOLUTE, desiredPublishedSeats = desired)
        } else {
            BlaBlaPublicBookingQueueMerge(BlaBlaPublicBookingQueueMergeKind.BLOCKED)
        }
    }
    if (existing.source != PUBLIC_BOOKING_SEAT_SYNC_SOURCE) {
        return BlaBlaPublicBookingQueueMerge(BlaBlaPublicBookingQueueMergeKind.BLOCKED)
    }
    val mergedDelta = existing.seatDelta + incomingDelta
    return if (mergedDelta == 0) {
        BlaBlaPublicBookingQueueMerge(BlaBlaPublicBookingQueueMergeKind.NO_OP)
    } else {
        BlaBlaPublicBookingQueueMerge(BlaBlaPublicBookingQueueMergeKind.DELTA, seatDelta = mergedDelta)
    }
}

internal fun seatSyncDiagnosticKey(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    .take(12)

internal fun seatSyncAccountMatches(expectedProfileUuid: String, actualProfileUuid: String?): Boolean =
    actualProfileUuid?.trim()?.equals(expectedProfileUuid.trim(), ignoreCase = true) == true

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

    fun enqueueDesiredStateForTimeline(
        context: Context,
        entry: TripTimelineEntry,
        trip: Trip?,
        store: TripStore,
        reason: String,
    ): BlaBlaDesiredSeatSyncRequestResult {
        val target = targetForTimeline(entry) ?: return BlaBlaDesiredSeatSyncRequestResult(
            shouldSync = false,
            message = "Vagas aguardando sincronização ⚠️ • identidade forte da publicação indisponível.",
        )
        val statusStore = BlaBlaPublicationSeatSyncStateStore(context)
        val plan = timelineDesiredSeatSyncPlan(entry, trip, store)
        if (plan == null) {
            val message = if (entry.blablaPassengerRosterComplete != true) {
                "Vagas aguardando sincronização ⚠️ • passageiros externos ainda aguardam leitura completa por trecho."
            } else {
                "Vagas aguardando sincronização ⚠️ • não foi possível calcular o estado físico por trecho."
            }
            statusStore.markPending(target.profileUuid, target.tripId, message)
            return BlaBlaDesiredSeatSyncRequestResult(false, message)
        }

        val requestStore = BlaBlaManualSeatSyncRequestStore(context)
        val attemptStore = BlaBlaManualSeatSyncAttemptStore(context)
        val request = BlaBlaManualSeatSyncRequest(
            profileUuid = target.profileUuid,
            tripId = target.tripId,
            seatDelta = 0,
            desiredPublishedSeats = plan.desiredPublishedSeats,
            desiredStateReason = reason,
            localTripId = plan.localTripId,
            localBookingId = "desired:${target.profileUuid}:${target.tripId}",
            source = "DESIRED_STATE",
        )
        requestStore.replacePublication(request).forEach(attemptStore::clear)
        val tripKey = seatSyncDiagnosticKey("${target.profileUuid}|${target.tripId}")
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_TRIGGER",
            context.packageName,
            "tripKey=$tripKey bookingKey=${seatSyncDiagnosticKey(request.localBookingId)} profileUuidPresent=true blablaTripIdPresent=true seatDelta=0 desired=${plan.desiredPublishedSeats} reason=$reason",
        )
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_TRIP_RESOLUTION",
            context.packageName,
            "tripKey=$tripKey profileUuidPresent=true blablaTripIdPresent=true resolution=timeline_strong",
        )
        val message = "Estado desejado calculado: ${plan.desiredPublishedSeats} vaga(s) • conferindo somente as vagas da publicação correta…"
        statusStore.markDesired(target.profileUuid, target.tripId, plan.desiredPublishedSeats, message)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_DESIRED_STATE_QUEUED",
            context.packageName,
            "reason=$reason desired=${plan.desiredPublishedSeats} segments=${plan.loads.size} profileUuidPresent=true tripIdPresent=true request=${request.id}",
        )
        return BlaBlaDesiredSeatSyncRequestResult(true, message, plan.desiredPublishedSeats)
    }

    fun enqueuePublicBookingDelta(
        context: Context,
        binding: PublicExternalTripBinding,
        seatDelta: Int,
        stateKey: String,
        reason: String,
    ): BlaBlaDesiredSeatSyncRequestResult {
        if (seatDelta == 0) {
            return BlaBlaDesiredSeatSyncRequestResult(false, "Nenhuma alteração externa necessária.", noOp = true)
        }
        val target = normalizeTarget(BlaBlaManualSeatExternalTarget(binding.profileUuid, binding.blablaTripId))
            ?: return BlaBlaDesiredSeatSyncRequestResult(
                false,
                "Vagas aguardando sincronização ⚠️ • binding forte incompleto.",
                blockedReason = "strong_binding_invalid",
            )
        val requestStore = BlaBlaManualSeatSyncRequestStore(context)
        val attemptStore = BlaBlaManualSeatSyncAttemptStore(context)
        val samePublication = requestStore.list().filter { queued ->
            queued.profileUuid.equals(target.profileUuid, ignoreCase = true) && queued.tripId == target.tripId
        }
        if (samePublication.size > 1) {
            UnifiedDebugEventStore.record(
                "SEAT_SYNC_FAILED",
                context.packageName,
                "tripKey=${seatSyncDiagnosticKey("${target.profileUuid}|${target.tripId}")} reason=ambiguous_pending_publication_queue",
            )
            return BlaBlaDesiredSeatSyncRequestResult(
                false,
                "Vagas aguardando sincronização ⚠️ • fila ambígua para a publicação.",
                blockedReason = "ambiguous_pending_publication_queue",
            )
        }
        val existing = samePublication.singleOrNull()
        val existingAttempt = existing?.let { attemptStore.get(it.id) }
        val merged = mergePublicBookingSeatWork(existing, existingAttempt, seatDelta)
        if (merged.kind == BlaBlaPublicBookingQueueMergeKind.BLOCKED) {
            return BlaBlaDesiredSeatSyncRequestResult(
                false,
                "Vagas aguardando sincronização ⚠️ • conflito com uma alteração externa já em curso.",
                blockedReason = "pending_write_conflict",
            )
        }
        if (merged.kind == BlaBlaPublicBookingQueueMergeKind.NO_OP) {
            existing?.let {
                requestStore.remove(it.id)
                attemptStore.clear(it.id)
            }
            UnifiedDebugEventStore.record(
                "BOOKING_SEAT_SYNC_NOOP",
                context.packageName,
                "tripKey=${seatSyncDiagnosticKey("${target.profileUuid}|${target.tripId}")} reason=coalesced_delta_zero",
            )
            return BlaBlaDesiredSeatSyncRequestResult(
                false,
                "Alterações de vaga se compensaram; nenhuma mutação externa é necessária.",
                noOp = true,
            )
        }
        val normalizedStateKey = stateKey.trim().ifBlank {
            seatSyncDiagnosticKey("${binding.bookingTripId}|${System.currentTimeMillis()}")
        }
        val request = BlaBlaManualSeatSyncRequest(
            id = "public-seat-${seatSyncDiagnosticKey("${target.profileUuid}|${target.tripId}|$normalizedStateKey|${merged.seatDelta}|${merged.desiredPublishedSeats ?: -1}")}",
            profileUuid = target.profileUuid,
            tripId = target.tripId,
            seatDelta = merged.seatDelta,
            desiredPublishedSeats = merged.desiredPublishedSeats,
            desiredStateReason = reason,
            localTripId = binding.bookingTripId,
            localBookingId = "public-booking-${seatSyncDiagnosticKey("${binding.bookingTripId}|$normalizedStateKey")}",
            source = PUBLIC_BOOKING_SEAT_SYNC_SOURCE,
        )
        requestStore.replacePublication(request).forEach(attemptStore::clear)
        val tripKey = seatSyncDiagnosticKey("${target.profileUuid}|${target.tripId}")
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_TRIGGER",
            context.packageName,
            "tripKey=$tripKey bookingKey=${seatSyncDiagnosticKey(request.localBookingId)} profileUuidPresent=true blablaTripIdPresent=true seatDelta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} reason=$reason",
        )
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_TRIP_RESOLUTION",
            context.packageName,
            "tripKey=$tripKey profileUuidPresent=true blablaTripIdPresent=true resolution=persisted_strong_binding",
        )
        return BlaBlaDesiredSeatSyncRequestResult(
            true,
            "Vaga da reserva enfileirada para a publicação BlaBlaCar exata.",
            desiredPublishedSeats = request.desiredPublishedSeats,
        )
    }

    fun enqueueForManualBooking(
        context: Context,
        @Suppress("UNUSED_PARAMETER") trip: Trip,
        booking: Booking,
        seatDelta: Int,
        @Suppress("UNUSED_PARAMETER") explicitTarget: BlaBlaManualSeatExternalTarget? = null,
    ): BlaBlaManualSeatSyncRequest? {
        if (booking.source !in setOf(BookingSource.PRIVATE, BookingSource.OTHER)) return null
        if (seatDelta == 0 || kotlin.math.abs(seatDelta) != booking.seats) return null
        BlaBlaManualSeatSyncRequestStore(context).list()
            .filter { it.localBookingId == booking.id }
            .forEach { stale ->
                BlaBlaManualSeatSyncRequestStore(context).remove(stale.id)
                BlaBlaManualSeatSyncAttemptStore(context).clear(stale.id)
            }
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_WRITE_SKIPPED",
            context.packageName,
            "reason=independent_channel_inventory source=${booking.source.name} booking=${booking.id} seats=${booking.seats}",
        )
        return null
    }

    fun onManualBookingCancelled(
        context: Context,
        @Suppress("UNUSED_PARAMETER") trip: Trip,
        booking: Booking,
        @Suppress("UNUSED_PARAMETER") explicitTarget: BlaBlaManualSeatExternalTarget? = null,
    ): BlaBlaManualSeatCancellationResult {
        val requestStore = BlaBlaManualSeatSyncRequestStore(context)
        val attemptStore = BlaBlaManualSeatSyncAttemptStore(context)
        requestStore.list()
            .filter { it.localBookingId == booking.id }
            .forEach { stale ->
                requestStore.remove(stale.id)
                attemptStore.clear(stale.id)
            }
        BlaBlaManualSeatBookingBindingStore(context).remove(booking.id)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_WRITE_SKIPPED",
            context.packageName,
            "reason=independent_channel_inventory action=cancel source=${booking.source.name} booking=${booking.id}",
        )
        return BlaBlaManualSeatCancellationResult(
            shouldSync = false,
            message = "Passageiro manual cancelado. A vaga da viagem foi liberada sem alterar a cota BlaBlaCar.",
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
    const val EXTRA_REQUEST_ID = "blablacar_seat_sync_request_id"

    fun seatSync(
        context: Context,
        account: BlaBlaDynamicAccount,
        requestId: String? = null,
    ): Intent = Intent(context, BlaBlaReliableSeatSyncActivity::class.java)
        .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)
        .apply { requestId?.trim()?.takeIf(String::isNotEmpty)?.let { putExtra(EXTRA_REQUEST_ID, it) } }
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
    private lateinit var publicationSeatStateStore: BlaBlaPublicationSeatSyncStateStore
    private lateinit var request: BlaBlaManualSeatSyncRequest
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var archive: BlaBlaMhtmlArchiveStore
    private lateinit var seatBrowser: BlaBlaSeatBrowserController
    private var phase = Phase.BEFORE
    private var busy = false
    private var expectedSeats = -1
    private var verifyingCompensation = false
    private var beforeArchiveSaved = false
    private var beforeReadAttempts = 0
    private var afterArchiveSaved = false
    private var verifyReadAttempts = 0
    private var verificationReloadScheduled = false
    private var capacityBefore = -1
    private var capacityAfter = -1
    private var mutationAttempted = false
    private var mutationSucceeded = false
    private var readbackSucceeded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        requestStore = BlaBlaManualSeatSyncRequestStore(this)
        attemptStore = BlaBlaManualSeatSyncAttemptStore(this)
        bindingStore = BlaBlaManualSeatBookingBindingStore(this)
        ledger = BlaBlaManualSeatSyncLedger(this)
        publicationSeatStateStore = BlaBlaPublicationSeatSyncStateStore(this)
        account = registry.get(intent?.getStringExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID)) ?: run {
            finishPending("Conta BlaBlaCar não encontrada.", rotate = false)
            return
        }
        val requestedId = intent?.getStringExtra(BlaBlaReliableSeatSyncIntents.EXTRA_REQUEST_ID)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        request = BlaBlaReliableSeatRequestSelector.select(requestStore.list(), requestedId) ?: run {
            finishPending(
                if (requestedId == null) "Nenhuma sincronização manual pendente." else "A sincronização selecionada não está mais pendente.",
                rotate = false,
            )
            return
        }
        if (!seatSyncAccountMatches(request.profileUuid, account.profileUuid)) {
            UnifiedDebugEventStore.record(
                "SEAT_SYNC_ACCOUNT_RESOLUTION",
                packageName,
                "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} profileUuidPresent=true blablaTripIdPresent=true accountMatched=false",
            )
            finishPending("UUID da conta não corresponde à publicação.", rotate = false)
            return
        }
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_ACCOUNT_RESOLUTION",
            packageName,
            "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} profileUuidPresent=true blablaTripIdPresent=true accountMatched=true",
        )
        val desiredPublishedSeats = request.desiredPublishedSeats
        val publicBookingDelta = request.source == PUBLIC_BOOKING_SEAT_SYNC_SOURCE
        if (
            desiredPublishedSeats == null &&
            (request.source !in setOf(BookingSource.PRIVATE.name, BookingSource.OTHER.name, PUBLIC_BOOKING_SEAT_SYNC_SOURCE) || request.seatDelta == 0)
        ) {
            finishInvalid("Operação externa legada inválida; ela foi descartada para não bloquear a fila.")
            return
        }
        if (desiredPublishedSeats != null && desiredPublishedSeats < 0) {
            finishInvalid("Estado desejado de vagas inválido.")
            return
        }
        if (desiredPublishedSeats != null) {
            publicationSeatStateStore.markSyncing(request.profileUuid, request.tripId, desiredPublishedSeats)
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            finishPending(
                "O Android System WebView não oferece o perfil autenticado isolado desta conta; nenhuma vaga foi alterada.",
                rotate = true,
            )
            return
        }

        val manualDeltaRequest = desiredPublishedSeats == null && !publicBookingDelta
        val ledgerEntry = if (manualDeltaRequest) ledger.entry(request.localBookingId) else null
        if (
            manualDeltaRequest &&
            request.seatDelta < 0 &&
            ledgerEntry != null &&
            ledgerEntry.externallyReducedSeats == -request.seatDelta &&
            ledgerEntry.profileUuid.equals(request.profileUuid, ignoreCase = true) &&
            ledgerEntry.tripId == request.tripId
        ) {
            completeNoOp("Redução externa desse passageiro já estava confirmada.")
            return
        }
        if (manualDeltaRequest && request.seatDelta > 0) {
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
        seatBrowser = BlaBlaSeatBrowserController(
            context = this,
            webView = webView,
            accountId = account.id,
            expectedProfileUuid = account.profileUuid.orEmpty(),
            tripId = request.tripId,
        )
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!BlaBlaCollectorUrlModule.isAllowed(url)) return
                if (phase == Phase.SAVING) {
                    scheduleVerificationReload("save_navigation")
                    return
                }
                if (phase == Phase.VERIFY && !BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, url)) return
                if (busy) return
                view.postDelayed({ handlePage() }, 700L)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_RELIABLE_START",
            packageName,
            "request=${request.id} booking=${request.localBookingId} delta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} tripIdPresent=true profileUuidPresent=true",
        )
        UnifiedDebugEventStore.record(
            "BOOKING_SEAT_SYNC_EXECUTING",
            packageName,
            "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} profileUuidPresent=true blablaTripIdPresent=true",
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
            Phase.BEFORE -> {
                if (!beforeArchiveSaved) {
                    archive.save(webView, account, "reliable-options-before", request.tripId) {
                        beforeArchiveSaved = true
                        busy = false
                        handlePage()
                    }
                    return
                }
                seatBrowser.read { state ->
                    if (state == null || state.seats < 0) {
                        if (beforeReadAttempts < RELIABLE_OPTIONS_READ_MAX_RETRIES) {
                            beforeReadAttempts++
                            UnifiedDebugEventStore.record(
                                "EXTERNAL_SEAT_SYNC_RELIABLE_READ_RETRY",
                                packageName,
                                "request=${request.id} attempt=$beforeReadAttempts seats=${state?.seats ?: -1} savePresent=${state?.savePresent ?: false}",
                            )
                            busy = false
                            webView.postDelayed({ handlePage() }, RELIABLE_OPTIONS_READ_RETRY_MS)
                            return@read
                        }
                        finishPending("O editor de vagas não está disponível ou não pôde ser lido após novas leituras.", rotate = true)
                        return@read
                    }
                    beforeReadAttempts = 0
                    capacityBefore = state.seats
                    UnifiedDebugEventStore.record(
                        "SEAT_SYNC_BEFORE",
                        packageName,
                        "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} capacityBefore=$capacityBefore profileUuidPresent=true blablaTripIdPresent=true",
                    )
                    if (!BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, state.pageUrl)) {
                        finishPending("A identidade da página de vagas não foi confirmada.", rotate = true)
                        return@read
                    }
                    val existingAttempt = attemptStore.get(request.id)
                    if (existingAttempt != null && !attemptMatchesRequest(existingAttempt)) {
                        finishPending("A tentativa persistida não corresponde mais a esta operação.", rotate = true)
                        return@read
                    }
                    request.desiredPublishedSeats?.let { desired ->
                        publicationSeatStateStore.markObserved(request.profileUuid, request.tripId, state.seats)
                    }
                    val decision = request.desiredPublishedSeats?.let { desired ->
                        BlaBlaReliableSeatSyncPolicy.decideDesired(
                            currentSeats = state.seats,
                            canAdd = state.canAdd,
                            canRemove = state.canRemove,
                            desiredPublishedSeats = desired,
                        )
                    } ?: BlaBlaReliableSeatSyncPolicy.decide(
                        currentSeats = state.seats,
                        canAdd = state.canAdd,
                        canRemove = state.canRemove,
                        seatDelta = request.seatDelta,
                        attempt = existingAttempt,
                    )
                    UnifiedDebugEventStore.record(
                        "EXTERNAL_SEAT_SYNC_RELIABLE_DECISION",
                        packageName,
                        "request=${request.id} current=${state.seats} delta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} action=${decision.action.name} target=${decision.targetSeats ?: -1} attempt=${existingAttempt != null} compensation=${existingAttempt?.compensateAfterCancellation == true}",
                    )
                    UnifiedDebugEventStore.record(
                        "SEAT_SYNC_DESIRED",
                        packageName,
                        "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} capacityBefore=${state.seats} capacityExpected=${decision.targetSeats ?: -1} reservedSeats=${kotlin.math.abs(request.seatDelta)}",
                    )
                    when (decision.action) {
                        BlaBlaReliableSeatSyncAction.COMPLETE_ALREADY_APPLIED -> completeVerified(decision.targetSeats ?: state.seats, alreadyApplied = true)
                        BlaBlaReliableSeatSyncAction.COMPLETE_COMPENSATION -> completeCompensation(decision.targetSeats ?: state.seats, wrote = false)
                        BlaBlaReliableSeatSyncAction.APPLY_TARGET -> {
                            val target = decision.targetSeats ?: run {
                                finishPending("Alvo de vagas inválido.", rotate = true)
                                return@read
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
                                return@read
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
            Phase.SAVING -> {
                busy = false
                scheduleVerificationReload("handle_page_fallback")
            }
            Phase.VERIFY -> {
                if (!afterArchiveSaved) {
                    archive.save(webView, account, "reliable-options-after", request.tripId) {
                        afterArchiveSaved = true
                        busy = false
                        handlePage()
                    }
                    return
                }
                seatBrowser.read { state ->
                    val exactPage = state != null && BlaBlaHarvestAssociation.optionsPageMatches(request.tripId, state.pageUrl)
                    capacityAfter = state?.seats ?: -1
                    readbackSucceeded = exactPage && state != null && state.seats >= 0
                    UnifiedDebugEventStore.record(
                        "SEAT_SYNC_READBACK_RESULT",
                        packageName,
                        "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} capacityExpected=$expectedSeats capacityAfter=$capacityAfter readbackSucceeded=$readbackSucceeded exactPage=$exactPage",
                    )
                    val verified = exactPage && state?.seats == expectedSeats
                    if (verified) {
                        verifyReadAttempts = 0
                        if (verifyingCompensation) {
                            completeCompensation(expectedSeats, wrote = true)
                        } else {
                            completeVerified(expectedSeats, alreadyApplied = false)
                        }
                        return@read
                    }
                    if (verifyReadAttempts < RELIABLE_OPTIONS_READ_MAX_RETRIES) {
                        verifyReadAttempts++
                        UnifiedDebugEventStore.record(
                            "EXTERNAL_SEAT_SYNC_RELIABLE_VERIFY_RETRY",
                            packageName,
                            "request=${request.id} attempt=$verifyReadAttempts observed=${state?.seats ?: -1} expected=$expectedSeats exactPage=$exactPage savePresent=${state?.savePresent ?: false}",
                        )
                        busy = false
                        webView.postDelayed({ handlePage() }, RELIABLE_OPTIONS_READ_RETRY_MS)
                        return@read
                    }
                    finishPending("Alteração não confirmada após releituras; a tentativa ficou preservada para conferência idempotente.", rotate = true)
                }
            }
        }
    }

    private fun applyTarget(before: Int, target: Int, compensation: Boolean) {
        expectedSeats = target
        verifyingCompensation = compensation
        afterArchiveSaved = false
        verifyReadAttempts = 0
        verificationReloadScheduled = false
        statusView.text = "${account.displayLabel} • $before → $target vagas • salvando…"
        phase = Phase.SAVING
        mutationAttempted = true
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_MUTATION_START",
            packageName,
            "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} capacityBefore=$before capacityExpected=$target mutationAttempted=true",
        )
        seatBrowser.adjustAndSave(before, target) { saved, reason ->
            mutationSucceeded = saved
            UnifiedDebugEventStore.record(
                "SEAT_SYNC_MUTATION_RESULT",
                packageName,
                "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} capacityBefore=$before capacityExpected=$target mutationAttempted=true mutationSucceeded=$saved reasonKey=${seatSyncDiagnosticKey(reason)}",
            )
            if (!saved) {
                finishPending(
                    "A alteração de vagas não foi confirmada pelo orquestrador ($reason).",
                    rotate = true,
                )
                return@adjustAndSave
            }
            UnifiedDebugEventStore.record(
                "BOOKING_SEAT_SYNC_REMOTE_MUTATION_SENT",
                packageName,
                "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} mutationSent=true capacityExpected=$target",
            )
            scheduleVerificationReload("browser_orchestrator")
        }
        webView.postDelayed({
            if (phase == Phase.SAVING) scheduleVerificationReload("save_timeout")
        }, RELIABLE_SAVE_COMPLETION_TIMEOUT_MS)
    }

    private fun scheduleVerificationReload(origin: String) {
        if (phase != Phase.SAVING || verificationReloadScheduled) return
        verificationReloadScheduled = true
        phase = Phase.VERIFY
        statusView.text = "${account.displayLabel} • confirmando $expectedSeats vaga(s) publicadas…"
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_RELIABLE_SAVE_COMPLETED",
            packageName,
            "request=${request.id} origin=$origin expected=$expectedSeats writeRepeated=false",
        )
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_READBACK_START",
            packageName,
            "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} capacityExpected=$expectedSeats remoteReadback=true",
        )
        webView.postDelayed({
            busy = false
            webView.loadUrl(reliableOptionsUrl(request.tripId))
        }, RELIABLE_SAVE_SETTLE_MS)
    }

    private fun completeVerified(afterSeats: Int, alreadyApplied: Boolean) {
        val desiredRequest = request.desiredPublishedSeats != null
        val verifiedMessage = if (desiredRequest && alreadyApplied) {
            "Nenhuma alteração necessária ✅ • $afterSeats vaga(s) já publicadas"
        } else {
            "Vagas sincronizadas ✅ • $afterSeats vaga(s) publicadas"
        }
        val publicBookingDelta = request.source == PUBLIC_BOOKING_SEAT_SYNC_SOURCE
        if (desiredRequest) {
            publicationSeatStateStore.markSynced(request.profileUuid, request.tripId, afterSeats, verifiedMessage)
        } else if (publicBookingDelta) {
            // Public-link booking deltas are isolated from the manual/private compensation ledger.
        } else if (request.seatDelta < 0) {
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
            "request=${request.id} booking=${request.localBookingId} after=$afterSeats delta=${request.seatDelta} desired=${request.desiredPublishedSeats ?: -1} alreadyApplied=$alreadyApplied ledger=${request.desiredPublishedSeats == null && !publicBookingDelta}",
        )
        val readback = readbackSucceeded || alreadyApplied
        val before = capacityBefore.takeIf { it >= 0 } ?: afterSeats
        val mutationSent = mutationSucceeded && !alreadyApplied
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_CONFIRMED",
            packageName,
            "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} profileUuidPresent=true blablaTripIdPresent=true capacityBefore=$before reservedSeats=${kotlin.math.abs(request.seatDelta)} capacityExpected=$afterSeats capacityAfter=$afterSeats mutationAttempted=$mutationAttempted mutationSucceeded=$mutationSucceeded readbackSucceeded=$readback confirmed=true",
        )
        UnifiedDebugEventStore.record(
            "BOOKING_SEAT_SYNC_REMOTE_CONFIRMED",
            packageName,
            "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} capacityAfter=$afterSeats confirmed=true",
        )
        UnifiedDebugEventStore.record(
            "CAPACITY_REMOTE_CONFIRMATION",
            packageName,
            "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} profileUuidPresent=true blablaTripIdPresent=true capacityBefore=$before capacityExpected=$afterSeats capacityAfter=$afterSeats mutationSent=$mutationSent remoteReadback=$readback confirmed=true",
        )
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra("seat_sync_message", verifiedMessage),
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
            if (request.desiredPublishedSeats != null) {
                publicationSeatStateStore.markSynced(request.profileUuid, request.tripId, request.desiredPublishedSeats!!)
            } else if (request.seatDelta > 0) {
                bindingStore.remove(request.localBookingId)
            }
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
        recordSeatSyncFailure("invalid_request")
        if (::request.isInitialized) {
            requestStore.remove(request.id)
            attemptStore.clear(request.id)
            if (request.desiredPublishedSeats != null && ::publicationSeatStateStore.isInitialized) {
                publicationSeatStateStore.markError(request.profileUuid, request.tripId, "Falha ao sincronizar vagas ❌ • $message")
            }
        }
        setResult(RESULT_CANCELED, Intent().putExtra("seat_sync_message", message))
        finish()
    }

    private fun finishPending(message: String, rotate: Boolean) {
        recordSeatSyncFailure(
            when {
                expectedSeats >= 0 && capacityAfter >= 0 && capacityAfter != expectedSeats -> "remote_capacity_mismatch"
                phase == Phase.VERIFY && !readbackSucceeded -> "remote_readback_unavailable"
                capacityBefore < 0 -> "capacity_before_unknown"
                else -> "pending"
            },
        )
        if (::request.isInitialized) {
            if (request.desiredPublishedSeats != null && ::publicationSeatStateStore.isInitialized) {
                publicationSeatStateStore.markPending(
                    request.profileUuid,
                    request.tripId,
                    "Vagas aguardando sincronização ⚠️ • $message",
                    request.desiredPublishedSeats,
                )
            }
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

    private fun tripDiagnosticKey(): String =
        if (::request.isInitialized) seatSyncDiagnosticKey("${request.profileUuid}|${request.tripId}") else "unresolved"

    private fun bookingDiagnosticKey(): String =
        if (::request.isInitialized) seatSyncDiagnosticKey(request.localBookingId) else "unresolved"

    private fun recordSeatSyncFailure(reason: String) {
        if (!::request.isInitialized) return
        val normalizedReason = reason.trim().ifBlank { "unknown" }
        UnifiedDebugEventStore.record(
            "SEAT_SYNC_FAILED",
            packageName,
            "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} profileUuidPresent=true blablaTripIdPresent=true capacityBefore=$capacityBefore reservedSeats=${kotlin.math.abs(request.seatDelta)} capacityExpected=$expectedSeats capacityAfter=$capacityAfter mutationAttempted=$mutationAttempted mutationSucceeded=$mutationSucceeded readbackSucceeded=$readbackSucceeded confirmed=false reason=$normalizedReason",
        )
        if (expectedSeats >= 0 || mutationAttempted) {
            UnifiedDebugEventStore.record(
                "CAPACITY_REMOTE_CONFIRMATION",
                packageName,
                "tripKey=${tripDiagnosticKey()} bookingKey=${bookingDiagnosticKey()} profileUuidPresent=true blablaTripIdPresent=true capacityBefore=$capacityBefore capacityExpected=$expectedSeats capacityAfter=$capacityAfter mutationSent=$mutationSucceeded remoteReadback=$readbackSucceeded confirmed=false reason=$normalizedReason",
            )
        }
    }

    private fun attemptMatchesRequest(attempt: BlaBlaManualSeatSyncAttempt): Boolean =
        attempt.requestId == request.id &&
            attempt.localBookingId == request.localBookingId &&
            attempt.profileUuid.equals(request.profileUuid, ignoreCase = true) &&
            attempt.tripId == request.tripId &&
            attempt.seatDelta == request.seatDelta &&
            (request.desiredPublishedSeats == null || attempt.targetSeats == request.desiredPublishedSeats)

    override fun onDestroy() {
        if (::seatBrowser.isInitialized) seatBrowser.cancel()
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private enum class Phase { BEFORE, SAVING, VERIFY }
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

private const val RELIABLE_OPTIONS_READ_MAX_RETRIES = 4
private const val RELIABLE_OPTIONS_READ_RETRY_MS = 650L
private const val RELIABLE_SAVE_COMPLETION_TIMEOUT_MS = 7_000L
private const val RELIABLE_SAVE_SETTLE_MS = 650L

private fun reliableOptionsUrl(tripId: String): String =
    "${BlaBlaCollectorUrlModule.ORIGIN}/rides/offer/edit/${tripId.trim()}/options"

