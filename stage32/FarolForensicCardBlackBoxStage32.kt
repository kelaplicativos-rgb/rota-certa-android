package br.com.mapeiaia.rotacerta

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Bounded event-driven CASE recorder. Diagnostic only; never a read/paint authority. */
object FarolForensicCardBlackBoxStage32 {
    const val CONTRACT_MARKER = "FAROL_FORENSIC_CARD_BLACK_BOX_STAGE32"
    const val PROVENANCE_MARKER = "FAROL_CASE_PROVENANCE_STAGE32"
    const val USER_MARKER = "FAROL_USER_MARKED_CASE_STAGE32"
    private const val MAX_CASES = 40
    private const val MAX_TIMELINE = 36

    enum class Outcome {
        OPEN,
        READ_SUCCESS,
        READ_SUCCESS_CACHE,
        READ_SUCCESS_OCR,
        UNREAD_ACCESSIBILITY_INSUFFICIENT,
        UNREAD_OCR_SCREENSHOT_FAILED,
        UNREAD_OCR_NO_TWO_ADDRESSES,
        UNREAD_OCR_STALE_REAL_MUTATION,
        UNREAD_ROUTE_FAILED,
        UNREAD_DISAPPEARED_BEFORE_DECISION,
        UNREAD_READING_OFF_BEFORE_DECISION,
        STALE_RESULT_BLOCKED,
        UNKNOWN,
    }

    data class Event(
        val elapsedNs: Long,
        val stage: String,
        val detail: String,
    )

    data class CaseSnapshot(
        val caseId: String,
        val firstSeenElapsedNs: Long,
        val lastSeenElapsedNs: Long,
        val firstWallMs: Long,
        val lastWallMs: Long,
        val triggerPackage: String?,
        val sourcePackage: String?,
        val windowPackage: String?,
        val ownerPackage: String?,
        val ownerConfidence: FarolSemanticCardStage32.OwnerConfidence,
        val ownerEvidence: String,
        val selectedAppsActive: Set<String>,
        val semanticGeneration: Long,
        val semanticFingerprint: Long,
        val rawVisualGeneration: Long,
        val rawVisualHash: Long?,
        val accessibilityBlocks: Int,
        val accessibilityCandidate: Boolean?,
        val ocrRequested: Int,
        val ocrStarted: Int,
        val ocrBlocks: Int,
        val ocrStale: Int,
        val ocrRetries: Int,
        val screenshotStatus: String?,
        val screenshotErrorCode: Int?,
        val screenshotHash: Long?,
        val candidateSource: String?,
        val pickup: String?,
        val destination: String?,
        val addressSignature: String?,
        val routeRequested: Boolean,
        val routeResponse: Boolean,
        val cacheHit: Boolean,
        val paintRequested: Boolean,
        val paintApplied: Boolean,
        val finalColor: String?,
        val finalDistanceKm: Double?,
        val outcome: Outcome,
        val unreadReason: String?,
        val userMarked: Boolean,
        val eventToCandidateNs: Long?,
        val eventToFinalNs: Long?,
        val timeline: List<Event>,
    )

    private data class MutableCase(
        val id: String,
        val firstElapsed: Long,
        val firstWall: Long,
        var lastElapsed: Long,
        var lastWall: Long,
        var triggerPackage: String?,
        var sourcePackage: String?,
        var windowPackage: String?,
        var ownerPackage: String?,
        var ownerConfidence: FarolSemanticCardStage32.OwnerConfidence,
        var ownerEvidence: String,
        var selectedAppsActive: Set<String>,
        var semanticGeneration: Long,
        var semanticFingerprint: Long,
        var rawVisualGeneration: Long = 0L,
        var rawVisualHash: Long? = null,
        var accessibilityBlocks: Int = 0,
        var accessibilityCandidate: Boolean? = null,
        var ocrRequested: Int = 0,
        var ocrStarted: Int = 0,
        var ocrBlocks: Int = 0,
        var ocrStale: Int = 0,
        var ocrRetries: Int = 0,
        var screenshotStatus: String? = null,
        var screenshotErrorCode: Int? = null,
        var screenshotHash: Long? = null,
        var candidateSource: String? = null,
        var pickup: String? = null,
        var destination: String? = null,
        var addressSignature: String? = null,
        var routeRequested: Boolean = false,
        var routeResponse: Boolean = false,
        var cacheHit: Boolean = false,
        var paintRequested: Boolean = false,
        var paintApplied: Boolean = false,
        var finalColor: String? = null,
        var finalDistanceKm: Double? = null,
        var outcome: Outcome = Outcome.OPEN,
        var unreadReason: String? = null,
        var userMarked: Boolean = false,
        var eventToCandidateNs: Long? = null,
        var eventToFinalNs: Long? = null,
        val timeline: ArrayDeque<Event> = ArrayDeque(),
    )

    private val lock = Any()
    private val cases = ArrayDeque<MutableCase>()
    private var serial = 0L
    private var current: MutableCase? = null

    fun observeEvent(
        elapsedNs: Long,
        wallMs: Long,
        semanticDecision: FarolSemanticCardStage32.Decision,
        triggerPackage: String?,
        sourcePackage: String?,
        windowPackage: String?,
        provenance: FarolSemanticCardStage32.Provenance,
        selectedAppsActive: Set<String>,
        rawVisualGeneration: Long,
    ): String = synchronized(lock) {
        val existing = current
        if (semanticDecision.mutation && existing != null && existing.outcome == Outcome.OPEN) {
            closeLocked(existing, Outcome.UNREAD_DISAPPEARED_BEFORE_DECISION, "new_semantic_case_before_final", elapsedNs, wallMs)
            current = null
        }
        val c = current ?: newCaseLocked(
            elapsedNs, wallMs, triggerPackage, sourcePackage, windowPackage, provenance,
            selectedAppsActive, semanticDecision.generation, semanticDecision.fingerprint,
        ).also { current = it }
        c.lastElapsed = elapsedNs; c.lastWall = wallMs
        c.triggerPackage = triggerPackage ?: c.triggerPackage
        c.sourcePackage = sourcePackage ?: c.sourcePackage
        c.windowPackage = windowPackage ?: c.windowPackage
        if (provenance.confidence.ordinal < c.ownerConfidence.ordinal || c.ownerPackage == null) {
            c.ownerPackage = provenance.ownerPackage
            c.ownerConfidence = provenance.confidence
            c.ownerEvidence = provenance.evidence
        }
        c.selectedAppsActive = selectedAppsActive
        c.semanticGeneration = semanticDecision.generation
        c.semanticFingerprint = semanticDecision.fingerprint
        c.rawVisualGeneration = rawVisualGeneration
        addLocked(c, elapsedNs, "EVENT", "reason=${semanticDecision.reason}; semanticMutation=${semanticDecision.mutation}; trigger=${triggerPackage.orEmpty()}; owner=${c.ownerPackage.orEmpty()}")
        c.id
    }

    fun recordCollection(elapsedNs: Long, rawHash: Long?, blocks: Int, windows: Int, nodes: Int, reason: String) = update(elapsedNs, "ACCESSIBILITY_COLLECT", "blocks=$blocks; windows=$windows; nodes=$nodes; reason=$reason") {
        rawVisualHash = rawHash; accessibilityBlocks = blocks
    }

    fun recordAccessibilityEvaluation(elapsedNs: Long, candidate: Boolean) = update(elapsedNs, "ACCESSIBILITY_EVALUATE", "candidate=$candidate") {
        accessibilityCandidate = candidate
        if (!candidate) unreadReason = "accessibility_insufficient_waiting_ocr"
    }

    fun recordOcrRequest(elapsedNs: Long, started: Boolean, reason: String) = update(elapsedNs, "OCR_REQUEST", "started=$started; reason=$reason") {
        ocrRequested += 1; if (started) ocrStarted += 1
    }

    fun recordScreenshot(elapsedNs: Long, status: String, errorCode: Int? = null, hash: Long? = null) = update(elapsedNs, "SCREENSHOT_$status", "errorCode=${errorCode ?: -1}; hash=${hash ?: 0L}") {
        screenshotStatus = status; screenshotErrorCode = errorCode; if (hash != null) screenshotHash = hash
    }

    fun recordOcrExtract(elapsedNs: Long, blocks: Int, durationNs: Long) = update(elapsedNs, "OCR_EXTRACT", "blocks=$blocks; duration_us=${durationNs/1000L}") {
        ocrBlocks = blocks
    }

    fun recordOcrStale(elapsedNs: Long, realSemanticMutation: Boolean, reason: String) = update(elapsedNs, "OCR_STALE", "realSemanticMutation=$realSemanticMutation; reason=$reason") {
        ocrStale += 1
        if (realSemanticMutation) unreadReason = "ocr_stale_real_semantic_mutation"
    }

    fun recordOcrRetry(elapsedNs: Long, reason: String) = update(elapsedNs, "OCR_RETRY", "reason=$reason") { ocrRetries += 1 }

    fun recordCandidate(elapsedNs: Long, source: String, pickup: String, destination: String, signature: String) = update(elapsedNs, "CANDIDATE", "source=$source; destination=${destination.take(180)}") {
        candidateSource = source; this.pickup = pickup; this.destination = destination; addressSignature = signature
        eventToCandidateNs = (elapsedNs - firstElapsed).coerceAtLeast(0L)
        unreadReason = null
    }

    fun recordCacheHit(elapsedNs: Long) = update(elapsedNs, "CACHE_HIT", "exact_current=true") { cacheHit = true }
    fun recordRouteRequested(elapsedNs: Long, destination: String) = update(elapsedNs, "ROUTE_REQUEST", "destination=${destination.take(180)}") { routeRequested = true }
    fun recordRouteResponse(elapsedNs: Long, success: Boolean, durationNs: Long) = update(elapsedNs, "ROUTE_RESPONSE", "success=$success; duration_us=${durationNs/1000L}") { routeResponse = success }
    fun recordPaintRequested(elapsedNs: Long, color: String, km: Double?) = update(elapsedNs, "PAINT_REQUEST", "color=$color; km=${km ?: -1.0}") { paintRequested = true }

    fun recordFinal(elapsedNs: Long, wallMs: Long, color: String, km: Double?, source: String) = synchronized(lock) {
        val c = current ?: return@synchronized
        c.paintApplied = true; c.finalColor = color; c.finalDistanceKm = km
        c.eventToFinalNs = (elapsedNs - c.firstElapsed).coerceAtLeast(0L)
        val outcome = when {
            source.equals("CACHE", true) -> Outcome.READ_SUCCESS_CACHE
            c.candidateSource.equals("Ocr", true) || source.contains("OCR", true) -> Outcome.READ_SUCCESS_OCR
            else -> Outcome.READ_SUCCESS
        }
        closeLocked(c, outcome, null, elapsedNs, wallMs)
        current = null
    }

    fun recordStalePaintBlocked(elapsedNs: Long, reason: String) = update(elapsedNs, "STALE_PAINT_BLOCKED", "reason=$reason") {
        outcome = Outcome.STALE_RESULT_BLOCKED; unreadReason = reason
    }

    fun markOcrNoCandidate(elapsedNs: Long, wallMs: Long) = closeCurrent(elapsedNs, wallMs, Outcome.UNREAD_OCR_NO_TWO_ADDRESSES, "ocr_no_two_addresses")

    fun markScreenshotFailure(elapsedNs: Long, wallMs: Long, errorCode: Int, terminal: Boolean) = synchronized(lock) {
        val c = current ?: return@synchronized
        c.screenshotStatus = "FAILED"; c.screenshotErrorCode = errorCode
        addLocked(c, elapsedNs, "SCREENSHOT_FAILURE", "errorCode=$errorCode; terminal=$terminal")
        if (terminal) {
            closeLocked(c, Outcome.UNREAD_OCR_SCREENSHOT_FAILED, "screenshot_error_$errorCode", elapsedNs, wallMs)
            current = null
        } else {
            c.unreadReason = "screenshot_error_${errorCode}_pending_event_driven_retry"
        }
    }

    fun markReadingOff(elapsedNs: Long, wallMs: Long) = closeCurrent(elapsedNs, wallMs, Outcome.UNREAD_READING_OFF_BEFORE_DECISION, "reading_off")

    fun userMark(elapsedNs: Long, wallMs: Long, packageHint: String?, semantic: FarolSemanticCardStage32.Snapshot): String = synchronized(lock) {
        val c = current ?: newCaseLocked(
            elapsedNs, wallMs, packageHint, packageHint, packageHint,
            FarolSemanticCardStage32.Provenance(null, FarolSemanticCardStage32.OwnerConfidence.UNKNOWN, "manual_mark_without_provenance"),
            emptySet(), semantic.generation, semantic.fingerprint,
        ).also { current = it }
        c.userMarked = true
        addLocked(c, elapsedNs, "USER_MARKED_CASE", "packageHint=${packageHint.orEmpty()}")
        c.id
    }

    fun currentCaseId(): String? = synchronized(lock) { current?.id }
    fun currentOwnerToken(): String? = synchronized(lock) { current?.ownerPackage?.let(::shortOwner) }
    fun snapshots(): List<CaseSnapshot> = synchronized(lock) { cases.map(::snapshotLocked) }
    fun currentSnapshot(): CaseSnapshot? = synchronized(lock) { current?.let(::snapshotLocked) }

    fun resetForTests() = synchronized(lock) { cases.clear(); current = null; serial = 0L }

    fun exportReport(): String = synchronized(lock) {
        buildString {
            appendLine("ROTA CERTA — STAGE32 FORENSIC CARD BLACK BOX")
            appendLine("marker=$CONTRACT_MARKER")
            appendLine("provenance=$PROVENANCE_MARKER")
            appendLine("userMarker=$USER_MARKER")
            appendLine("cases=${cases.size}; current=${current?.id ?: "none"}")
            if (cases.isEmpty()) appendLine("(nenhum CASE registrado)")
            cases.forEach { c ->
                appendLine()
                appendLine("${c.id} | outcome=${c.outcome} | owner=${c.ownerPackage ?: "UNKNOWN"} | confidence=${c.ownerConfidence} | userMarked=${c.userMarked}")
                appendLine("time=${fmt(c.firstWall)}..${fmt(c.lastWall)} | semanticGeneration=${c.semanticGeneration} | semanticFingerprint=${c.semanticFingerprint} | rawVisualGeneration=${c.rawVisualGeneration} | rawHash=${c.rawVisualHash ?: 0L}")
                appendLine("packages trigger=${c.triggerPackage ?: "none"}; source=${c.sourcePackage ?: "none"}; window=${c.windowPackage ?: "none"}; selectedActive=${c.selectedAppsActive.sorted().joinToString(",")}")
                appendLine("ownerEvidence=${c.ownerEvidence}")
                appendLine("accessibility blocks=${c.accessibilityBlocks}; candidate=${c.accessibilityCandidate ?: false}")
                appendLine("ocr requests=${c.ocrRequested}; starts=${c.ocrStarted}; blocks=${c.ocrBlocks}; stale=${c.ocrStale}; retries=${c.ocrRetries}; screenshot=${c.screenshotStatus ?: "none"}; errorCode=${c.screenshotErrorCode ?: -1}; screenshotHash=${c.screenshotHash ?: 0L}")
                appendLine("candidate source=${c.candidateSource ?: "none"}; pickup=${c.pickup ?: "none"}; destination=${c.destination ?: "none"}; signature=${c.addressSignature ?: "none"}")
                appendLine("route requested=${c.routeRequested}; response=${c.routeResponse}; cache=${c.cacheHit}; paintRequested=${c.paintRequested}; paintApplied=${c.paintApplied}; final=${c.finalColor ?: "none"}/${c.finalDistanceKm ?: -1.0}")
                appendLine("unreadReason=${c.unreadReason ?: "none"}; eventToCandidate_us=${c.eventToCandidateNs?.div(1000L) ?: -1L}; eventToFinal_us=${c.eventToFinalNs?.div(1000L) ?: -1L}")
                c.timeline.forEach { e -> appendLine("  t=${e.elapsedNs} | ${e.stage} | ${e.detail}") }
            }
        }.trimEnd()
    }

    private fun update(elapsedNs: Long, stage: String, detail: String, block: MutableCase.() -> Unit) = synchronized(lock) {
        val c = current ?: return@synchronized
        c.lastElapsed = elapsedNs; c.lastWall = System.currentTimeMillis(); c.block(); addLocked(c, elapsedNs, stage, detail)
    }

    private fun closeCurrent(elapsedNs: Long, wallMs: Long, outcome: Outcome, reason: String) = synchronized(lock) {
        val c = current ?: return@synchronized
        if (c.outcome == Outcome.OPEN) closeLocked(c, outcome, reason, elapsedNs, wallMs)
        current = null
    }

    private fun newCaseLocked(
        elapsedNs: Long, wallMs: Long, triggerPackage: String?, sourcePackage: String?, windowPackage: String?,
        provenance: FarolSemanticCardStage32.Provenance, selected: Set<String>, semanticGen: Long, semanticFingerprint: Long,
    ): MutableCase {
        serial += 1L
        val c = MutableCase(
            id = "CASE-${serial.toString().padStart(6, '0')}", firstElapsed = elapsedNs, firstWall = wallMs,
            lastElapsed = elapsedNs, lastWall = wallMs, triggerPackage = triggerPackage, sourcePackage = sourcePackage,
            windowPackage = windowPackage, ownerPackage = provenance.ownerPackage, ownerConfidence = provenance.confidence,
            ownerEvidence = provenance.evidence, selectedAppsActive = selected, semanticGeneration = semanticGen,
            semanticFingerprint = semanticFingerprint,
        )
        if (cases.size >= MAX_CASES) cases.removeFirst()
        cases.addLast(c)
        addLocked(c, elapsedNs, "CASE_OPEN", "owner=${provenance.ownerPackage ?: "UNKNOWN"}; confidence=${provenance.confidence}")
        return c
    }

    private fun closeLocked(c: MutableCase, outcome: Outcome, reason: String?, elapsedNs: Long, wallMs: Long) {
        c.lastElapsed = elapsedNs; c.lastWall = wallMs; c.outcome = outcome; c.unreadReason = reason
        addLocked(c, elapsedNs, "CASE_CLOSE", "outcome=$outcome; reason=${reason ?: "none"}")
    }

    private fun addLocked(c: MutableCase, elapsedNs: Long, stage: String, detail: String) {
        if (c.timeline.size >= MAX_TIMELINE) c.timeline.removeFirst()
        c.timeline.addLast(Event(elapsedNs, stage, detail.take(520)))
    }

    private fun snapshotLocked(c: MutableCase) = CaseSnapshot(
        c.id,c.firstElapsed,c.lastElapsed,c.firstWall,c.lastWall,c.triggerPackage,c.sourcePackage,c.windowPackage,
        c.ownerPackage,c.ownerConfidence,c.ownerEvidence,c.selectedAppsActive,c.semanticGeneration,c.semanticFingerprint,
        c.rawVisualGeneration,c.rawVisualHash,c.accessibilityBlocks,c.accessibilityCandidate,c.ocrRequested,c.ocrStarted,
        c.ocrBlocks,c.ocrStale,c.ocrRetries,c.screenshotStatus,c.screenshotErrorCode,c.screenshotHash,c.candidateSource,
        c.pickup,c.destination,c.addressSignature,c.routeRequested,c.routeResponse,c.cacheHit,c.paintRequested,c.paintApplied,
        c.finalColor,c.finalDistanceKm,c.outcome,c.unreadReason,c.userMarked,c.eventToCandidateNs,c.eventToFinalNs,c.timeline.toList(),
    )

    private fun shortOwner(pkg: String): String = when (pkg.lowercase(Locale.ROOT)) {
        "com.app99.driver" -> "99"
        "com.ubercab.driver" -> "Uber"
        "sinet.startup.indriver" -> "inDrive"
        else -> pkg.substringAfterLast('.').take(18).ifBlank { "UNKNOWN" }
    }
    private fun fmt(ms: Long) = SimpleDateFormat("dd/MM HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(ms))
}
