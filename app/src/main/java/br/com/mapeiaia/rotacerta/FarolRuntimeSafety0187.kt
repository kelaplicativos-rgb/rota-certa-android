package br.com.mapeiaia.rotacerta

import java.util.Locale

/** Monotonic timing, stale-recovery rejection and bounded external-event admission for the farol. */
object FarolElapsedTimePolicy0187 {
    const val CONTRACT_MARKER = "MONOTONIC_FAROL_TIME_0187"

    fun ageMillis(nowElapsedMillis: Long, startedElapsedMillis: Long): Long? =
        if (startedElapsedMillis <= 0L || nowElapsedMillis < startedElapsedMillis) null
        else nowElapsedMillis - startedElapsedMillis

    fun isWithin(nowElapsedMillis: Long, startedElapsedMillis: Long, maxAgeMillis: Long): Boolean =
        ageMillis(nowElapsedMillis, startedElapsedMillis)?.let { it <= maxAgeMillis.coerceAtLeast(0L) } == true

    fun formatAge(ageMillis: Long?): String = ageMillis?.let { "${it}ms" } ?: "indisponivel"
}

data class FarolRecoveryBinding0187(
    val packageName: String,
    val sessionGeneration: Long,
    val windowId: Int,
    val screenGeneration: Long,
    val windowGeneration: Long,
    val captureSignature: String,
)

object FarolRecoveryBindingPolicy0187 {
    const val CONTRACT_MARKER = "SAME_CARD_RECOVERY_BINDING_0187"

    fun isFresh(
        binding: FarolRecoveryBinding0187,
        currentPackageName: String?,
        currentSessionGeneration: Long,
        currentWindowId: Int,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
        currentCaptureSignature: String,
    ): Boolean = binding.packageName.normalized() == currentPackageName.normalized() &&
        binding.sessionGeneration == currentSessionGeneration &&
        binding.windowId >= 0 && binding.windowId == currentWindowId &&
        binding.screenGeneration == currentScreenGeneration &&
        binding.windowGeneration == currentWindowGeneration &&
        binding.captureSignature.isNotBlank() && binding.captureSignature == currentCaptureSignature

    private fun String?.normalized(): String = this?.trim()?.lowercase(Locale.ROOT).orEmpty()
}


data class FarolRootAdmission0187(
    val accepted: Boolean,
    val reason: String,
)

/**
 * Admits a root only when package and window belong to the same logical read.
 * No text may be traversed before this policy accepts the metadata snapshot.
 */
object FarolRootSnapshotPolicy0187 {
    const val CONTRACT_MARKER = "ATOMIC_ROOT_SNAPSHOT_GATE_0187"
    const val STAGE14_CONTRACT_MARKER = "SELECTED_APP_ACTIVATES_VISIBLE_ROOT_STAGE14"

    fun evaluate(
        eventPackageName: String?,
        selectedPackageName: String,
        rootPackageName: String?,
        eventWindowId: Int,
        rootWindowId: Int?,
        transientOverlayEvent: Boolean,
        activeSessionPackageName: String?,
        activeSessionWindowId: Int?,
    ): FarolRootAdmission0187 {
        val selected = selectedPackageName.normalized0187()
        val event = eventPackageName.normalized0187()
        val root = rootPackageName.normalized0187()
        val session = activeSessionPackageName.normalized0187()
        if (selected.isBlank()) return FarolRootAdmission0187(false, "selected_package_missing")
        if (root.isBlank() || rootWindowId == null || rootWindowId < 0) {
            return FarolRootAdmission0187(false, "root_identity_missing")
        }
        if (root != selected) return FarolRootAdmission0187(false, "root_package_mismatch")

        if (transientOverlayEvent) {
            // Stage 14: the user's selected-app list authorizes observation. A selected
            // app root that is actually visible behind a transient SystemUI/overlay event
            // may start the immutable card session instead of requiring a session first.
            // Package/root coherence is still mandatory above, and the existing 0.1.188
            // card/block gate remains the only route authority.
            val sameSelectedSessionStage14 = session == selected
            if (sameSelectedSessionStage14 && activeSessionWindowId != null && activeSessionWindowId >= 0 && activeSessionWindowId != rootWindowId) {
                return FarolRootAdmission0187(false, "transient_root_window_mismatch")
            }
            return FarolRootAdmission0187(
                accepted = true,
                reason = if (sameSelectedSessionStage14) {
                    "selected_root_behind_transient"
                } else {
                    "selected_root_behind_transient_session_bootstrap_stage14"
                },
            )
        }

        if (event.isBlank()) {
            val sameSelectedSessionStage14 = session == selected &&
                activeSessionWindowId != null && activeSessionWindowId >= 0
            if (sameSelectedSessionStage14 && activeSessionWindowId != rootWindowId) {
                return FarolRootAdmission0187(false, "event_package_missing_root_window_mismatch_stage14")
            }
            return FarolRootAdmission0187(
                accepted = true,
                reason = if (sameSelectedSessionStage14) {
                    "same_session_root_continuation"
                } else {
                    "selected_root_without_event_package_session_bootstrap_stage14"
                },
            )
        }
        if (event != selected) return FarolRootAdmission0187(false, "event_package_mismatch")
        if (eventWindowId >= 0 && eventWindowId != rootWindowId) {
            return FarolRootAdmission0187(false, "event_root_window_mismatch")
        }
        return FarolRootAdmission0187(true, "selected_event_and_root")
    }
}

enum class FarolRejectedSnapshotEffect0187Phase3 {
    DISCARD_WITHOUT_EFFECT,
    INVALIDATE_READ_KEEP_VISUAL,
}

/**
 * A rejected snapshot proves only that this read is incoherent. It does not prove
 * that the confirmed card disappeared. Visual clearing remains reserved for a
 * coherent selected-app screen without a valid card or a confirmed external app.
 */
object FarolRejectedSnapshotPolicy0187Phase3 {
    const val CONTRACT_MARKER = "REJECTED_SNAPSHOT_HAS_NO_VISUAL_SIDE_EFFECT_0187_PHASE3"

    fun effect(reason: String): FarolRejectedSnapshotEffect0187Phase3 = when (reason) {
        "event_root_window_mismatch" -> FarolRejectedSnapshotEffect0187Phase3.INVALIDATE_READ_KEEP_VISUAL
        else -> FarolRejectedSnapshotEffect0187Phase3.DISCARD_WITHOUT_EFFECT
    }
}

data class FarolReadBinding0187(
    val packageName: String,
    val sessionGeneration: Long,
    val windowId: Int,
    val screenGeneration: Long,
    val windowGeneration: Long,
)

object FarolReadBindingPolicy0187 {
    const val CONTRACT_MARKER = "ACCESSIBILITY_READ_BINDING_0187"

    fun isFresh(
        binding: FarolReadBinding0187,
        currentPackageName: String?,
        currentSessionGeneration: Long,
        currentWindowId: Int?,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
    ): Boolean = binding.packageName.normalized0187() == currentPackageName.normalized0187() &&
        binding.sessionGeneration == currentSessionGeneration &&
        binding.windowId >= 0 && binding.windowId == currentWindowId &&
        binding.screenGeneration == currentScreenGeneration &&
        binding.windowGeneration == currentWindowGeneration
}


data class FarolDecisionBinding0187Phase4(
    val packageName: String,
    val sessionGeneration: Long,
    val windowId: Int,
    val screenGeneration: Long,
    val windowGeneration: Long,
    val screenHash: Int,
    val addressSignature: String,
)

/**
 * A route result may paint only while every identity component captured when the
 * request started is still current. Cancellation is best effort; this binding is
 * the final monotonic barrier when an HTTP/OCR implementation ignores cancellation.
 */
object FarolDecisionBindingPolicy0187Phase4 {
    const val CONTRACT_MARKER = "DECISION_RESULT_MONOTONIC_BINDING_0187_PHASE4"

    fun isFresh(
        binding: FarolDecisionBinding0187Phase4,
        currentPackageName: String?,
        currentSessionGeneration: Long,
        currentWindowId: Int?,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
        currentScreenHash: Int?,
        currentAddressSignature: String?,
    ): Boolean = binding.packageName.normalized0187() == currentPackageName.normalized0187() &&
        binding.sessionGeneration == currentSessionGeneration &&
        binding.windowId >= 0 && binding.windowId == currentWindowId &&
        binding.screenGeneration == currentScreenGeneration &&
        binding.windowGeneration == currentWindowGeneration &&
        binding.screenHash == currentScreenHash &&
        binding.addressSignature == currentAddressSignature
}

private fun String?.normalized0187(): String = this?.trim()?.lowercase(Locale.ROOT).orEmpty()

class FarolExternalPackageEventGate0187(
    private val duplicateWindowMillis: Long = 900L,
) {
    private var lastPackageName = ""
    private var lastAcceptedElapsedMillis = Long.MIN_VALUE

    @Synchronized
    fun shouldHandle(
        packageName: String?,
        windowId: Int,
        eventType: Int,
        alreadyIdle: Boolean,
        nowElapsedMillis: Long,
    ): Boolean {
        @Suppress("UNUSED_VARIABLE") val observedWindowAndType = windowId to eventType
        val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val elapsed = if (lastAcceptedElapsedMillis == Long.MIN_VALUE) Long.MAX_VALUE
            else nowElapsedMillis - lastAcceptedElapsedMillis
        val duplicateIdleBurst = alreadyIdle && normalizedPackage == lastPackageName &&
            elapsed >= 0L && elapsed < duplicateWindowMillis.coerceAtLeast(0L)
        if (duplicateIdleBurst) return false
        lastPackageName = normalizedPackage
        lastAcceptedElapsedMillis = nowElapsedMillis
        return true
    }

    @Synchronized
    fun reset() {
        lastPackageName = ""
        lastAcceptedElapsedMillis = Long.MIN_VALUE
    }
}

object FarolFailureLocation0187 {
    const val CONTRACT_MARKER = "FAILURE_LOCATION_WITHOUT_PII_0187"

    fun describe(error: Throwable, ownPackageName: String): String {
        val ownPrefix = ownPackageName.trim().takeIf(String::isNotBlank)
        val frame = error.stackTrace.firstOrNull { candidate ->
            ownPrefix == null || candidate.className.startsWith(ownPrefix)
        } ?: error.stackTrace.firstOrNull()
        return frame?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            ?: "indisponivel"
    }
}
