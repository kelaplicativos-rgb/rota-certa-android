package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
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
    val sanitizedRequest: String = "",
    val requestId: String = "",
    val correlationId: String = "",
    val networkCallId: String = "",
    val transportPhase: String = "",
    val requestBytes: Int = 0,
    val responseBytes: Int = 0,
    val requestSha256: String = "",
    val responseSha256: String = "",
    val responseContentType: String = "",
    val elapsedMs: Long = 0L,
)

internal data class AgendaCauseResolution(
    val chain: List<Throwable>,
    val cycleDetected: Boolean,
    val depthTruncated: Boolean,
)

internal data class AgendaByteSanitizationEvidence0458(
    val rawBytes: Int,
    val sanitizedBytes: Int,
    val rawSha256: String,
    val sanitizedSha256: String,
    val utf8RoundTrip: Boolean,
    val sanitizerSucceeded: Boolean,
    val sanitizationChanged: Boolean,
    val changedByteCount: Int,
    val firstSanitizedDiffOffset: Int,
    val sanitizedDiffRanges: List<String>,
    val nulByteCount: Int,
    val controlByteCount: Int,
) {
    fun compactDetails0458(): String = buildString {
        append("rawBytes=").append(rawBytes)
        append(" sanitizedBytes=").append(sanitizedBytes)
        append(" rawSha256=").append(rawSha256)
        append(" sanitizedSha256=").append(sanitizedSha256)
        append(" utf8RoundTrip=").append(utf8RoundTrip)
        append(" sanitizerSucceeded=").append(sanitizerSucceeded)
        append(" sanitizationChanged=").append(sanitizationChanged)
        append(" changedByteCount=").append(changedByteCount)
        append(" firstSanitizedDiffOffset=").append(firstSanitizedDiffOffset)
        append(" sanitizedDiffRanges=").append(sanitizedDiffRanges.joinToString(",").ifBlank { "none" })
        append(" nulByteCount=").append(nulByteCount)
        append(" controlByteCount=").append(controlByteCount)
    }
}

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
    private const val MAX_REQUEST_CHARS = 180
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
        // Evidence generation is observability, never a business precondition.
        // Every derived value is best-effort so malformed trip/booking shape can
        // be described without the diagnostic path throwing before the real guard.
        val seats = runCatching { operationalSeatSummary(trip, bookings) }.getOrNull()
        val departure = runCatching {
            Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault())
        }.getOrNull()
        val externalIdentity = runCatching {
            canonicalExternalTripIdentityKey(
                trip.blablaProfileUuid,
                trip.blablaTripId,
                trip.blablaManageUrl,
            )
        }.getOrNull()
        val fallbackInventory = runCatching { operationalInventoryCapacity(trip, bookings) }.getOrNull()
        return AgendaFailureTripContext(
            tripKey = tripKey,
            canonicalIdentity = externalIdentity ?: trip.tripKey.ifBlank { trip.id },
            publicIdentity = publicIdentity?.takeIf(String::isNotBlank) ?: "<unresolved>",
            origin = origin,
            route = trip.title,
            date = departure?.let(dateFormatter::format).orEmpty(),
            time = departure?.let(timeFormatter::format).orEmpty(),
            blablaQuota = seats?.blablaQuotaSeats ?: trip.publishedSeats?.takeIf { it in 0..999 },
            rotaCertaQuota = seats?.rotaCertaQuotaSeats ?: trip.rotaCertaSeatAllocation?.takeIf { it in 0..999 },
            operationalInventory = seats?.operationalInventorySeats ?: fallbackInventory,
            confirmedSeats = confirmedSeatsOverride ?: seats?.confirmedPassengerSeats,
            realAvailableSeats = realAvailableSeatsOverride ?: seats?.availableSeats,
            revision = revision,
            signature = signature,
            snapshotVersion = if (revision.isBlank()) "" else revision.substringBefore(':').take(40),
        )
    }

    fun byteSanitizationEvidence0458(raw: ByteArray): AgendaByteSanitizationEvidence0458 {
        val decoded = raw.toString(Charsets.UTF_8)
        val utf8RoundTrip = decoded.toByteArray(Charsets.UTF_8).contentEquals(raw)
        var sanitizerSucceeded = true
        val sanitizedText = runCatching {
            UnifiedDebugEventStore.sanitizeForExport(decoded)
        }.getOrElse {
            sanitizerSucceeded = false
            "[evidence sanitization failed]"
        }
        val sanitized = sanitizedText.toByteArray(Charsets.UTF_8)
        val maxLength = maxOf(raw.size, sanitized.size)
        var first = -1
        var changed = 0
        var rangeStart = -1
        val ranges = mutableListOf<String>()
        for (index in 0 until maxLength) {
            val differs = raw.getOrNull(index) != sanitized.getOrNull(index)
            if (differs) {
                changed += 1
                if (first < 0) first = index
                if (rangeStart < 0) rangeStart = index
            } else if (rangeStart >= 0) {
                if (ranges.size < 12) ranges += "$rangeStart-${index - 1}"
                rangeStart = -1
            }
        }
        if (rangeStart >= 0 && ranges.size < 12) ranges += "$rangeStart-${maxLength - 1}"

        fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

        return AgendaByteSanitizationEvidence0458(
            rawBytes = raw.size,
            sanitizedBytes = sanitized.size,
            rawSha256 = sha(raw),
            sanitizedSha256 = sha(sanitized),
            utf8RoundTrip = utf8RoundTrip,
            sanitizerSucceeded = sanitizerSucceeded,
            sanitizationChanged = !raw.contentEquals(sanitized),
            changedByteCount = changed,
            firstSanitizedDiffOffset = first,
            sanitizedDiffRanges = ranges,
            nulByteCount = raw.count { it.toInt() == 0 },
            controlByteCount = raw.count { byte ->
                val unsigned = byte.toInt() and 0xff
                unsigned < 0x20 && unsigned !in setOf(0x09, 0x0a, 0x0d)
            },
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
        val resolvedMethod = method.ifBlank { sourceFrame?.methodName.orEmpty() }
        val fingerprint = failureFingerprint(
            operation = operation,
            component = component,
            method = resolvedMethod,
            root = root,
            backend = resolvedBackend,
            trip = trip,
        )
        return buildString {
            field("timestampMs", timestampMillis.toString())
            field("failureFingerprint", fingerprint)
            field("operation", operation)
            field("component", component)
            field("method", resolvedMethod)
            field("exceptionClass", error.javaClass.simpleName.ifBlank { error.javaClass.name })
            nullableField("exceptionMessage", error.message)
            field("rootCauseClass", root.javaClass.simpleName.ifBlank { root.javaClass.name })
            nullableField("rootCauseMessage", root.message)

            // Backend/transport evidence comes before trip/stack context so the
            // bounded flight-recorder line cannot trim the byte-level facts away.
            resolvedBackend?.let { value ->
                field("networkCallId", value.networkCallId)
                field("transportPhase", value.transportPhase)
                field("httpMethod", value.httpMethod)
                field("endpoint", value.endpoint)
                intField("httpStatus", value.httpStatus)
                field("backendErrorCode", value.backendErrorCode)
                intField("requestBytes", value.requestBytes)
                intField("responseBytes", value.responseBytes)
                field("requestSha256", value.requestSha256)
                field("responseSha256", value.responseSha256)
                longField("networkElapsedMs", value.elapsedMs)
                field("responseContentType", value.responseContentType)
                field("requestId", value.requestId)
                field("correlationId", value.correlationId)
                field("request", value.sanitizedRequest, MAX_REQUEST_CHARS)
                field("response", value.sanitizedResponse, MAX_RESPONSE_CHARS)
            }

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

            field("causes", causeSummary(causes))
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
                httpStatus = remote.httpStatus.takeIf { it > 0 },
                backendErrorCode = remote.backendErrorCode,
                sanitizedResponse = remote.sanitizedResponse,
                sanitizedRequest = remote.sanitizedRequest,
                requestId = remote.requestId,
                correlationId = remote.correlationId,
                networkCallId = remote.networkCallId,
                transportPhase = remote.transportPhase,
                requestBytes = remote.requestBytes,
                responseBytes = remote.responseBytes,
                requestSha256 = remote.requestSha256,
                responseSha256 = remote.responseSha256,
                responseContentType = remote.responseContentType,
                elapsedMs = remote.elapsedMs,
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

    private fun StringBuilder.longField(name: String, value: Long) {
        if (value < 0L) return
        if (isNotEmpty()) append(' ')
        append(name).append('=').append(value)
    }

    private fun failureFingerprint(
        operation: String,
        component: String,
        method: String,
        root: Throwable,
        backend: AgendaFailureBackendContext?,
        trip: AgendaFailureTripContext?,
    ): String {
        val material = buildString {
            append(operation).append('|')
            append(component).append('|')
            append(method).append('|')
            append(root.javaClass.name).append('|')
            append(root.message.orEmpty()).append('|')
            append(backend?.transportPhase.orEmpty()).append('|')
            append(backend?.httpMethod.orEmpty()).append('|')
            append(backend?.endpoint.orEmpty()).append('|')
            append(backend?.httpStatus ?: 0).append('|')
            append(backend?.backendErrorCode.orEmpty()).append('|')
            append(trip?.tripKey.orEmpty()).append('|')
            append(trip?.publicIdentity.orEmpty())
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .take(10)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun sanitize(raw: String, maxChars: Int): String =
        UnifiedDebugEventStore.sanitizeForExport(raw)
            .replace('"', '\'')
            .take(maxChars)
            .ifBlank { "<empty>" }
}
