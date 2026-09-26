package br.com.mapeiaia.rotacerta.trips

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class TargetedTripRefreshCommit0645(
    val revision: Long,
    val tenantId: String,
    val strongIdentityKey: String,
    val canonicalTripId: String,
    val canonicalRevision: Long,
    val changed: Boolean,
    val committedAtMillis: Long,
)

/**
 * Process-local, replayable signal emitted as soon as one exact BlaBlaCar card has been
 * canonicalized in TripStore.
 *
 * The durable source of truth remains TripStore. StateFlow is intentionally used here so a
 * Timeline that was backgrounded while BlaBlaCar was open still observes the latest targeted
 * commit immediately when it resumes/recomposes, without waiting for public-Agenda publication.
 */
internal object TargetedTripRefreshEvents0645 {
    private val counter = AtomicLong(0L)
    private val mutableCommit = MutableStateFlow<TargetedTripRefreshCommit0645?>(null)
    val commit: StateFlow<TargetedTripRefreshCommit0645?> = mutableCommit.asStateFlow()

    fun notifyCanonicalCommitted(
        target: BlaBlaTripTarget0407,
        canonicalTripId: String,
        canonicalRevision: Long,
        changed: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        mutableCommit.value = TargetedTripRefreshCommit0645(
            revision = counter.incrementAndGet(),
            tenantId = target.tenantId,
            strongIdentityKey = target.strongIdentityKey,
            canonicalTripId = canonicalTripId,
            canonicalRevision = canonicalRevision,
            changed = changed,
            committedAtMillis = nowMillis,
        )
    }
}
