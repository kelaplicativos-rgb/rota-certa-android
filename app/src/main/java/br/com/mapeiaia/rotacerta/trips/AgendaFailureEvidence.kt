package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.IdentityHashMap

internal data class AgendaFailureTripContext(
    val tripKey: String = "",
    val canonicalIdentity: String = "",
    val publicIdentity: String = "<unresolved>",
    val origin: String = "",
    val route: String = "",
    val date: String = "",
    val time: String = "",
    val blablaQuota: Int? = null,
    val rotaCertaQuota: Int? = null,
    val operationalInventory: Int? = null,
    val confirmedSeats: Int? = null,
    val realAvailableSeats: Int? = null,
    val revision: String = "",
    val signature: String = "",
    val idempotencyKey: String = "",
    val snapshotVersion: String = "",
    val previousState: String = "",
    val intendedState: String = "",
)

internal data class AgendaFailureBackendContext(
    val endpoint: String = "",
    val httpMethod: String = "",
    val httpStatus: Int? = null,
    val backendErrorCode: String = "",
    val sanitizedResponse: String = "",
    val requestId: String = "",
    val correlationId: String = "",
)

internal data class AgendaCauseResolution(
    val chain: List<Throwable>,
    val cycleDetected: Boolean,
    val depthTruncated: Boolean,
)

/**
 * Bounded structured failure envelope for the existing AgendaTrace/UnifiedDebugEventStore path.
 * It does not perform I/O, retry, sync, persistence or mutation. All user-visible/exported text
 * goes through the existing central report sanitizer before it is returned.
 */
internal object AgendaFailureEvidence {
    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_STACK_FRAMES = 6
    private const val MAX_MESSAGE_CHARS = 140
    private const val MAX_RESPONSE_CHARS = 180
    private const val MAX_STACK_CHARS = 220
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun tripContext(
        trip: Trip,
        bookings: List<Booking>,
        tripKey: String,
        publicIdentity: String? = trip.remoteId,
        origin: String = resolvedTripRecordOrigin(trip).name,
        revision: String = "",
        signature: String = "",
        confirmedSeatsOverride: Int? = null,
        realAvailableSeatsOverride: Int? = null,
    ): AgendaFailureTripContext {
        val seats = operationalSeatSummary(trip, bookings)
        val departure = Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault())
        val externalIdentity = canonicalExternalTripIdentityKey(
            trip.blablaProfileUuid,
            trip.blablaTripId,
            trip.blablaManageUrl,
        )
        return AgendaFailureTripContext(
            tripKey = tripKey,
            canonicalIdentity = externalIdentity ?: trip.id,
            publicIdentity = publicIdentity?.takeIf(String::isNotBlank) ?: "<unresolved>",
            origin = origin,
            route = trip.title,
            date = dateFormatter.format(departure),
            time = timeFormatter.format(departure),
            blablaQuota = seats.blablaQuotaSeats,
            rotaCertaQuota = seats.rotaCertaQuotaSeats,
            operationalInventory = seats.operationalInventorySeats,
            confirmedSeats = confirmedSeatsOverride ?: seats.confirmedPassengerSeats,
            realAvailableSeats = realAvailableSeatsOverride ?: seats.availableSeats,
            revision = revision,
            signature = signature,
            snapshotVersion = if (revision.isBlank()) "" else revision.substringBefore(':').take(40),
        )
    }

    fun describe(
        error: Throwable,
        operation: String,
        component: String,
        method: String = "",
        trip: AgendaFailureTripContext? = null,
        backend: AgendaFailureBackendContext? = null,
        timestampMillis: Long = System.currentTimeMillis(),
    ): String {
        val causes = resolveCauseChain(error)
        val root = causes.chain.lastOrNull() ?: error
        val resolvedBackend = backend ?: causes.chain.firstNotNullOfOrNull(::backendContextFrom)
        val sourceFrame = firstRotaCertaFrame(root) ?: firstRotaCertaFrame(error) ?: root.stackTrace.firstOrNull()
        val stack = boundedStack(causes.chain)
        return buildString {
            field("timestampMs", timestampMillis.toString())
            field("operation", operation)
            field("component", component)
            field("method", method.ifBlank { sourceFrame?.methodName.orEmpty() })
            field("exceptionClass", error.javaClass.simpleName.ifBlank { error.javaClass.name })
            nullableField("exceptionMessage", error.message)
            field("rootCauseClass", root.javaClass.simpleName.ifBlank { root.javaClass.name })
            nullableField("rootCauseMessage", root.message)
            field("causes", causeSummary(causes))
            trip?.let { value ->
                field("tripKey", value.tripKey)
                field("canonicalIdentity", value.canonicalIdentity)
                field("publicIdentity", value.publicIdentity.ifBlank { "<unresolved>" })
                field("origin", value.origin)
                field("route", value.route)
                field("date", value.date)
                field("time", value.time)
                intField("blablaQuota", value.blablaQuota)
                intField("rotaCertaQuota", value.rotaCertaQuota)
                intField("operationalInventory", value.operationalInventory)
                intField("confirmedSeats", value.confirmedSeats)
                intField("realAvailableSeats", value.realAvailableSeats)
                field("revision", value.revision)
                field("signature", value.signature)
                field("idempotencyKey", value.idempotencyKey)
                field("snapshotVersion", value.snapshotVersion)
                field("previousState", value.previousState)
                field("intendedState", value.intendedState)
            }
            resolvedBackend?.let { value ->
                field("endpoint", value.endpoint)
                field("httpMethod", value.httpMethod)
                intField("httpStatus", value.httpStatus)
                field("backendErrorCode", value.backendErrorCode)
                field("response", value.sanitizedResponse, MAX_RESPONSE_CHARS)
                field("requestId", value.requestId)
                field("correlationId", value.correlationId)
            }
            sourceFrame?.let { frame ->
                field("source", "${frame.fileName ?: frame.className}:${frame.methodName}:${frame.lineNumber}")
            }
            if (stack.isNotBlank()) field("stackTrace", stack, MAX_STACK_CHARS)
        }.trim()
    }

    internal fun resolveCauseChain(
        error: Throwable,
        causeProvider: (Throwable) -> Throwable? = { it.cause },
    ): AgendaCauseResolution {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val chain = ArrayList<Throwable>(MAX_CAUSE_DEPTH)
        var current: Throwable? = error
        var cycleDetected = false
        var depthTruncated = false
        while (current != null) {
            val currentError = current
            if (!seen.add(currentError)) {
                cycleDetected = true
                break
            }
            chain += currentError
            if (chain.size >= MAX_CAUSE_DEPTH) {
                if (causeProvider(currentError) != null) depthTruncated = true
                break
            }
            val next = runCatching { causeProvider(currentError) }.getOrNull()
            if (next === currentError) {
                cycleDetected = true
                break
            }
            current = next
        }
        return AgendaCauseResolution(chain, cycleDetected, depthTruncated)
    }

    private fun backendContextFrom(error: Throwable): AgendaFailureBackendContext? =
        (error as? TripRemoteApiException)?.let { remote ->
            AgendaFailureBackendContext(
                endpoint = remote.endpoint,
                httpMethod = remote.httpMethod,
                httpStatus = remote.httpStatus,
                backendErrorCode = remote.backendErrorCode,
                sanitizedResponse = remote.sanitizedResponse,
                requestId = remote.requestId,
                correlationId = remote.correlationId,
            )
        }

    private fun causeSummary(resolution: AgendaCauseResolution): String = buildString {
        resolution.chain.forEachIndexed { index, cause ->
            if (index > 0) append(" -> ")
            append(cause.javaClass.simpleName.ifBlank { cause.javaClass.name })
            append('(').append(sanitize(cause.message ?: "<null>", 80)).append(')')
        }
        if (resolution.cycleDetected) append(" -> <cycle>")
        if (resolution.depthTruncated) append(" -> <depth-truncated>")
    }

    private fun boundedStack(chain: List<Throwable>): String {
        val relevant = chain
            .asSequence()
            .flatMap { it.stackTrace.asSequence() }
            .filter { it.className.startsWith("br.com.mapeiaia.rotacerta") }
            .distinctBy { "${it.className}#${it.methodName}:${it.lineNumber}" }
            .take(MAX_STACK_FRAMES)
            .toList()
            .ifEmpty { chain.firstOrNull()?.stackTrace?.take(3).orEmpty() }
        return relevant.joinToString(" > ") { frame ->
            "${frame.className.substringAfterLast('.')}.${frame.methodName}(${frame.fileName ?: "unknown"}:${frame.lineNumber})"
        }
    }

    private fun firstRotaCertaFrame(error: Throwable): StackTraceElement? =
        error.stackTrace.firstOrNull { it.className.startsWith("br.com.mapeiaia.rotacerta") }

    private fun StringBuilder.field(name: String, raw: String, maxChars: Int = MAX_MESSAGE_CHARS) {
        if (raw.isBlank()) return
        if (isNotEmpty()) append(' ')
        append(name).append("=\"").append(sanitize(raw, maxChars)).append('"')
    }

    private fun StringBuilder.nullableField(name: String, raw: String?) {
        if (isNotEmpty()) append(' ')
        append(name).append("=\"")
        append(if (raw == null) "<null>" else sanitize(raw, MAX_MESSAGE_CHARS))
        append('"')
    }

    private fun StringBuilder.intField(name: String, value: Int?) {
        if (value == null) return
        if (isNotEmpty()) append(' ')
        append(name).append('=').append(value)
    }

    private fun sanitize(raw: String, maxChars: Int): String =
        UnifiedDebugEventStore.sanitizeForExport(raw)
            .replace('"', '\'')
            .take(maxChars)
            .ifBlank { "<empty>" }
}
