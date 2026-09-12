package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Diagnostic-only phases. They do not alter the collector's state machine. */
internal enum class BlaBlaNetworkCapturePhase(val wireName: String) {
    CARD("card"),
    PASSENGERS("passengers"),
    EDIT("edit"),
    OPTIONS("options"),
}

internal data class BlaBlaNetworkCaptureContext(
    val sessionTag: String,
    val accountTag: String,
    val cardTag: String,
    val phase: BlaBlaNetworkCapturePhase,
    val sequence: Int,
)

/**
 * The single fetch/XHR observer used by the collector and its one-card report.
 *
 * An allowlisted trip source stays only in the page's memory for collection.
 * The diagnostic bridge remains first-card-only and receives only anonymized
 * values, which native code validates and anonymizes again before persistence.
 */
internal class BlaBlaNetworkDiagnosticRecorder(
    context: Context,
    private val accountId: String,
    private val appPackageName: String,
) {
    private val appContext = context.applicationContext
    private val store = BlaBlaNetworkDiagnosticStore(appContext, accountId)
    private var scriptHandler: ScriptHandler? = null
    private var installedWebView: WebView? = null
    private var listenerInstalled = false
    private var captureSealed = true
    private var activeCardTag: String? = null
    private var phase = BlaBlaNetworkCapturePhase.CARD
    private var sessionTag = ""
    private var accountTag = ""
    private var sessionSalt = ""
    private var acceptedMessages = 0

    fun install(webView: WebView): Boolean {
        val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        val messageListenerSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        if (!documentStartSupported) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_NETWORK_DIAGNOSTIC_UNAVAILABLE",
                appPackageName,
                "documentStart=false messageListener=$messageListenerSupported networkSource=false collectorChanged=false",
            )
            return false
        }
        installedWebView = webView
        if (messageListenerSupported) {
            listenerInstalled = runCatching {
                WebViewCompat.addWebMessageListener(
                    webView,
                    BRIDGE_NAME,
                    BlaBlaNetworkDiagnosticPolicy.ALLOWED_PAGE_ORIGINS,
                    WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, _ ->
                        onBridgeMessage(message.data, sourceOrigin, isMainFrame)
                    },
                )
                true
            }.getOrDefault(false)
        }
        return runCatching {
            scriptHandler = WebViewCompat.addDocumentStartJavaScript(
                webView,
                BlaBlaNetworkDiagnosticPolicy.DOCUMENT_START_SCRIPT,
                BlaBlaNetworkDiagnosticPolicy.ALLOWED_PAGE_ORIGINS,
            )
            UnifiedDebugEventStore.record(
                "BLABLACAR_NETWORK_DIAGNOSTIC_INSTALLED",
                appPackageName,
                "origin=${BlaBlaNetworkDiagnosticPolicy.PAGE_ORIGIN} fetch=true xhr=true networkSource=true diagnosticBridge=$listenerInstalled domRead=false requestHeaders=false requestBody=false",
            )
            true
        }.getOrElse {
            disableDiagnosticListener()
            installedWebView = null
            UnifiedDebugEventStore.record(
                "BLABLACAR_NETWORK_DIAGNOSTIC_UNAVAILABLE",
                appPackageName,
                "documentStart=true messageListener=$messageListenerSupported installFailed=true networkSource=false collectorChanged=false",
            )
            false
        }
    }

    fun startSync(generation: Long) {
        sessionSalt = UUID.randomUUID().toString()
        sessionTag = UUID.randomUUID().toString().replace("-", "").take(12)
        accountTag = BlaBlaNetworkDiagnosticPolicy.opaqueTag(accountId, sessionSalt)
        acceptedMessages = 0
        activeCardTag = null
        phase = BlaBlaNetworkCapturePhase.CARD
        captureSealed = false
        store.reset(
            buildJsonObject {
                put("schema", BlaBlaNetworkDiagnosticPolicy.SCHEMA_VERSION)
                put("record", "session_start")
                put("session", sessionTag)
                put("account", accountTag)
                put("syncGeneration", generation)
                put("captureScope", "first_card_only")
                put("responseValues", "anonymized_before_bridge_and_native")
                put("atMillis", System.currentTimeMillis())
            }.toString(),
        )
    }

    fun beginFirstCard(rawTripId: String) {
        if (captureSealed || activeCardTag != null || rawTripId.isBlank()) return
        activeCardTag = BlaBlaNetworkDiagnosticPolicy.opaqueTag(rawTripId, sessionSalt)
        phase = BlaBlaNetworkCapturePhase.CARD
        store.append(
            buildJsonObject {
                put("schema", BlaBlaNetworkDiagnosticPolicy.SCHEMA_VERSION)
                put("record", "card_start")
                put("session", sessionTag)
                put("account", accountTag)
                put("card", activeCardTag.orEmpty())
                put("phase", phase.wireName)
                put("atMillis", System.currentTimeMillis())
            }.toString(),
        )
    }

    fun markPhase(next: BlaBlaNetworkCapturePhase) {
        if (captureSealed || activeCardTag == null) return
        phase = next
    }

    fun finishFirstCard(outcome: String) {
        val cardTag = activeCardTag ?: return
        if (captureSealed) return
        captureSealed = true
        store.append(
            buildJsonObject {
                put("schema", BlaBlaNetworkDiagnosticPolicy.SCHEMA_VERSION)
                put("record", "card_end")
                put("session", sessionTag)
                put("account", accountTag)
                put("card", cardTag)
                put("phase", phase.wireName)
                put("outcome", BlaBlaNetworkDiagnosticPolicy.safeOutcome(outcome))
                put("responseCount", acceptedMessages)
                put("atMillis", System.currentTimeMillis())
            }.toString(),
        )
        UnifiedDebugEventStore.record(
            "BLABLACAR_NETWORK_DIAGNOSTIC_COMPLETE",
            appPackageName,
            "session=$sessionTag card=$cardTag outcome=${BlaBlaNetworkDiagnosticPolicy.safeOutcome(outcome)} responses=$acceptedMessages file=${store.relativePath}",
        )
        disableDiagnosticListener()
    }

    fun close() {
        disableBridge()
        store.close()
    }

    private fun onBridgeMessage(raw: String?, sourceOrigin: Uri, isMainFrame: Boolean) {
        if (
            captureSealed ||
            activeCardTag == null ||
            !isMainFrame ||
            raw.isNullOrBlank() ||
            !BlaBlaNetworkDiagnosticPolicy.isAllowedPageOrigin(sourceOrigin.toString()) ||
            acceptedMessages >= MAX_RESPONSES
        ) return
        acceptedMessages++
        val context = BlaBlaNetworkCaptureContext(
            sessionTag = sessionTag,
            accountTag = accountTag,
            cardTag = activeCardTag.orEmpty(),
            phase = phase,
            sequence = acceptedMessages,
        )
        val salt = sessionSalt
        store.sanitizeAndAppend(
            raw = raw,
            salt = salt,
            sourceOrigin = sourceOrigin.toString(),
            capture = context,
            appPackageName = appPackageName,
        )
    }

    private fun disableBridge() {
        scriptHandler?.let { handler -> runCatching { handler.remove() } }
        scriptHandler = null
        disableDiagnosticListener()
        installedWebView = null
    }

    private fun disableDiagnosticListener() {
        if (listenerInstalled) {
            installedWebView?.let { view ->
                runCatching { WebViewCompat.removeWebMessageListener(view, BRIDGE_NAME) }
            }
        }
        listenerInstalled = false
    }

    companion object {
        private const val BRIDGE_NAME = "RotaCertaNetworkDiagnostic"
        private const val MAX_RESPONSES = 160
    }
}

internal class BlaBlaNetworkDiagnosticStore(
    context: Context,
    accountId: String,
) {
    private val outputFile = File(
        File(context.filesDir, "blablacar-network-diagnostic/${stableDirectoryTag(accountId)}"),
        "first-card-latest.jsonl",
    )
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "blablacar-network-diagnostic-io").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    val relativePath: String
        get() = "files/blablacar-network-diagnostic/${outputFile.parentFile?.name}/first-card-latest.jsonl"

    fun reset(header: String) {
        executor.execute {
            runCatching {
                outputFile.parentFile?.mkdirs()
                outputFile.writeText(header + "\n", Charsets.UTF_8)
            }
        }
    }

    fun append(line: String) {
        executor.execute { appendBounded(line) }
    }

    fun sanitizeAndAppend(
        raw: String,
        salt: String,
        sourceOrigin: String,
        capture: BlaBlaNetworkCaptureContext,
        appPackageName: String,
    ) {
        executor.execute {
            val response = BlaBlaNetworkDiagnosticPolicy.anonymizeBridgePayload(raw, salt) ?: return@execute
            val responseElement = runCatching { Json.parseToJsonElement(response) }.getOrNull() ?: return@execute
            val line = buildJsonObject {
                put("schema", BlaBlaNetworkDiagnosticPolicy.SCHEMA_VERSION)
                put("record", "response")
                put("session", capture.sessionTag)
                put("account", capture.accountTag)
                put("card", capture.cardTag)
                put("phase", capture.phase.wireName)
                put("sequence", capture.sequence)
                put("sourceOrigin", BlaBlaNetworkDiagnosticPolicy.normalizedPageOrigin(sourceOrigin).orEmpty())
                put("mainFrame", true)
                put("atMillis", System.currentTimeMillis())
                put("response", responseElement)
            }.toString()
            appendBounded(line)
            val summary = BlaBlaNetworkDiagnosticPolicy.safeSummary(response)
            UnifiedDebugEventStore.record(
                "BLABLACAR_NETWORK_RESPONSE_CAPTURED",
                appPackageName,
                "session=${capture.sessionTag} card=${capture.cardTag} phase=${capture.phase.wireName} sequence=${capture.sequence} $summary bodyPersisted=anonymized",
            )
        }
    }

    fun close() {
        executor.shutdown()
    }

    private fun appendBounded(line: String) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_LINE_BYTES || outputFile.length() + bytes.size > MAX_FILE_BYTES) return
            FileOutputStream(outputFile, true).use { stream -> stream.write(bytes) }
        }
    }

    companion object {
        private const val MAX_LINE_BYTES = 96_000
        private const val MAX_FILE_BYTES = 2_000_000L
        private const val MAX_REPORT_CAPTURES = 4
        private const val MAX_REPORT_RESPONSES_PER_PHASE = 10

        fun exportLatest(context: Context): String {
            val root = File(context.filesDir, "blablacar-network-diagnostic")
            val captures = root.listFiles()
                .orEmpty()
                .map { directory -> File(directory, "first-card-latest.jsonl") }
                .filter(File::isFile)
                .sortedByDescending(File::lastModified)
                .take(MAX_REPORT_CAPTURES)
            if (captures.isEmpty()) return "sem captura network-first"
            return captures.mapIndexed { captureIndex, file ->
                val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
                    .filter { line -> line.startsWith('{') && line.endsWith('}') }
                val selectedIndexes = linkedSetOf<Int>()
                lines.indices.filterTo(selectedIndexes) { index -> !lines[index].contains("\"record\":\"response\"") }
                BlaBlaNetworkCapturePhase.entries.forEach { capturePhase ->
                    lines.indices.asSequence()
                        .filter { index ->
                            lines[index].contains("\"record\":\"response\"") &&
                                lines[index].contains("\"phase\":\"${capturePhase.wireName}\"")
                        }
                        .take(MAX_REPORT_RESPONSES_PER_PHASE)
                        .forEach(selectedIndexes::add)
                }
                buildString {
                    appendLine("captura=${captureIndex + 1}/${captures.size} linhas=${lines.size} respostasPorFaseMax=$MAX_REPORT_RESPONSES_PER_PHASE")
                    selectedIndexes.sorted().forEach { index -> appendLine(lines[index]) }
                }.trimEnd()
            }.joinToString("\n")
        }

        private fun stableDirectoryTag(accountId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(accountId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
    }
}

internal object BlaBlaNetworkDiagnosticPolicy {
    const val SCHEMA_VERSION = 1
    const val PAGE_ORIGIN = "https://www.blablacar.com.br"
    val ALLOWED_PAGE_ORIGINS: Set<String> = setOf(PAGE_ORIGIN)

    private const val MAX_BRIDGE_CHARS = 80_000
    private const val MAX_DEPTH = 9
    private const val MAX_OBJECT_FIELDS = 64
    private const val MAX_ARRAY_ITEMS = 24
    private val json = Json { ignoreUnknownKeys = true }
    private val safeKeyRegex = Regex("[A-Za-z_][A-Za-z0-9_.-]{0,79}")
    private val secretKeyRegex = Regex(
        "(^|[_.-])(auth|authorization|cookie|cookies|token|secret|password|passwd|session|csrf|jwt|bearer|credential|api.?key)([_.-]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val personalKeyRegex = Regex(
        "(^|[_.-])(name|phone|mobile|email|address|street|latitude|longitude|coordinates?|geolocation)([_.-]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val exactNumberKeys = setOf(
        "schema", "status", "durationbucketms", "length", "fieldcount", "arraylength", "sequence",
    )
    private val seatKeyRegex = Regex("(^|[_.-])(seat|seats|places?|vacancies|capacity|quantity|count)([_.-]|$)", RegexOption.IGNORE_CASE)
    private val safeRouteSegments = setOf(
        "api", "graphql", "rides", "ride", "offer", "offers", "trip", "trips", "booking", "bookings",
        "passenger", "passengers", "reservation", "reservations", "edit", "options", "seats", "seat",
        "dashboard", "driver", "drivers", "profile", "profiles", "management", "manage", "web", "search",
    )
    private val safeKinds = setOf(
        "object", "array", "text", "email", "phone", "url", "uuid", "empty", "redacted", "number",
        "depth_limit", "node_limit", "unsupported", "json", "non_json", "invalid_json", "too_large",
    )
    private val safeLengthBuckets = setOf("0", "1-4", "5-8", "9-16", "17-32", "33-64", "65-128", "129+")
    private val safeMagnitudes = setOf("zero", "lt1", "lt10", "lt100", "lt1000", "lt1000000", "large")

    fun isAllowedPageOrigin(raw: String): Boolean = normalizedPageOrigin(raw) != null

    fun normalizedPageOrigin(raw: String): String? = runCatching {
        val uri = URI(raw)
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("www.blablacar.com.br", ignoreCase = true) ||
            uri.userInfo != null ||
            uri.port !in setOf(-1, 443)
        ) return@runCatching null
        PAGE_ORIGIN
    }.getOrNull()

    fun isAllowedNetworkHost(rawHost: String?): Boolean {
        val host = rawHost?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT).orEmpty()
        return host == "blablacar.com.br" ||
            host.endsWith(".blablacar.com.br") ||
            host == "blablacar.com" ||
            host.endsWith(".blablacar.com")
    }

    fun opaqueTag(value: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((salt + "|" + value).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun safeOutcome(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
        "complete" -> "complete"
        "quarantined" -> "quarantined"
        "sync_blocked" -> "sync_blocked"
        "activity_closed" -> "activity_closed"
        else -> "stopped"
    }

    fun anonymizeBridgePayload(raw: String, salt: String): String? {
        if (raw.isBlank() || raw.length > MAX_BRIDGE_CHARS || salt.isBlank()) return null
        val source = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        if ((source["schema"] as? JsonPrimitive)?.intOrNull != SCHEMA_VERSION) return null
        val transport = source.string("transport")?.lowercase(Locale.ROOT)?.takeIf { it == "fetch" || it == "xhr" } ?: return null
        val method = source.string("method")?.uppercase(Locale.ROOT)?.takeIf {
            it in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
        } ?: "OTHER"
        val endpoint = sanitizeEndpoint(source.string("endpoint").orEmpty(), salt) ?: return null
        val page = sanitizePageUrl(source.string("page").orEmpty(), salt) ?: return null
        val status = (source["status"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..599 } ?: 0
        val durationBucket = (source["durationBucketMs"] as? JsonPrimitive)?.intOrNull
            ?.takeIf { it in setOf(0, 50, 100, 250, 500, 1_000, 2_000, 5_000, 10_000) }
            ?: 10_000
        val contentKind = source.string("contentKind")
            ?.takeIf { it in setOf("json", "non_json", "invalid_json", "empty", "too_large") }
            ?: "non_json"
        val body = source["body"]?.let { sanitizeElement(it, "body", 0, salt) }
            ?: buildJsonObject { put("kind", "empty") }
        return buildJsonObject {
            put("schema", SCHEMA_VERSION)
            put("transport", transport)
            put("method", method)
            put("endpoint", endpoint)
            put("page", page)
            put("status", status)
            put("durationBucketMs", durationBucket)
            put("contentKind", contentKind)
            put("body", body)
        }.toString()
    }

    fun safeSummary(sanitizedPayload: String): String = runCatching {
        val value = json.parseToJsonElement(sanitizedPayload).jsonObject
        val transport = value.string("transport").orEmpty()
        val method = value.string("method").orEmpty()
        val endpoint = value.string("endpoint").orEmpty()
        val status = value["status"]?.jsonPrimitive?.intOrNull ?: 0
        "transport=$transport method=$method status=$status endpoint=$endpoint"
    }.getOrDefault("payloadValidated=true")

    private fun sanitizePageUrl(raw: String, salt: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.host.equals("www.blablacar.com.br", ignoreCase = true)) return null
        return sanitizeEndpoint(raw, salt)
    }

    private fun sanitizeEndpoint(raw: String, salt: String): String? = runCatching {
        val uri = URI(raw)
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            !isAllowedNetworkHost(uri.host) ||
            uri.userInfo != null ||
            uri.port !in setOf(-1, 443)
        ) return@runCatching null
        val host = uri.host.lowercase(Locale.ROOT)
        val path = uri.rawPath.orEmpty().split('/').filter(String::isNotBlank).joinToString("/") { rawSegment ->
            val segment = rawSegment.lowercase(Locale.ROOT)
            when {
                segment in safeRouteSegments -> segment
                segment.matches(Regex("v[0-9]{1,2}")) -> segment
                else -> ":id_${opaqueTag(rawSegment, salt).take(8)}"
            }
        }
        "https://$host/${path}".trimEnd('/').ifBlank { "https://$host" }
    }.getOrNull()

    private fun sanitizeElement(element: JsonElement, key: String?, depth: Int, salt: String): JsonElement {
        if (depth > MAX_DEPTH) return marker("depth_limit")
        if (key != null && secretKeyRegex.containsMatchIn(key)) return marker("redacted")
        if (key != null && personalKeyRegex.containsMatchIn(key) && element is JsonPrimitive) return marker("redacted")
        return when (element) {
            JsonNull -> JsonNull
            is JsonObject -> {
                val fields = element.entries.take(MAX_OBJECT_FIELDS).mapIndexed { index, (rawKey, value) ->
                    val safeKey = sanitizeKey(rawKey, index, salt)
                    safeKey to sanitizeElement(value, rawKey, depth + 1, salt)
                }.toMap(LinkedHashMap())
                JsonObject(
                    if (element.size > MAX_OBJECT_FIELDS) fields + ("_truncatedFields" to JsonPrimitive(true)) else fields,
                )
            }
            is JsonArray -> JsonArray(element.take(MAX_ARRAY_ITEMS).map { sanitizeElement(it, key, depth + 1, salt) })
            is JsonPrimitive -> sanitizePrimitive(element, key, salt)
        }
    }

    private fun sanitizePrimitive(value: JsonPrimitive, key: String?, salt: String): JsonElement {
        value.booleanOrNull?.let { return JsonPrimitive(it) }
        value.longOrNull?.let { return sanitizeNumber(it.toDouble(), key) }
        value.doubleOrNull?.let { return sanitizeNumber(it, key) }
        if (!value.isString) return marker("unsupported")
        val raw = value.content
        val normalizedKey = key?.lowercase(Locale.ROOT).orEmpty()
        return when (normalizedKey) {
            "kind" -> JsonPrimitive(raw.takeIf { it in safeKinds } ?: "unsupported")
            "lengthbucket" -> JsonPrimitive(raw.takeIf { it in safeLengthBuckets } ?: lengthBucket(raw.length))
            "magnitude" -> JsonPrimitive(raw.takeIf { it in safeMagnitudes } ?: "large")
            "sign" -> JsonPrimitive(raw.takeIf { it == "negative" || it == "zero" || it == "positive" } ?: "zero")
            else -> buildJsonObject {
                put("kind", classifyString(raw))
                put("lengthBucket", lengthBucket(raw.length))
                if (raw.isNotEmpty()) put("tag", opaqueTag(raw, salt).take(8))
            }
        }
    }

    private fun sanitizeNumber(value: Double, key: String?): JsonElement {
        val normalizedKey = key?.lowercase(Locale.ROOT).orEmpty()
        if (normalizedKey in exactNumberKeys && value.isFinite()) return JsonPrimitive(value.toLong())
        if (seatKeyRegex.containsMatchIn(normalizedKey) && value.isFinite() && value % 1.0 == 0.0 && value in 0.0..20.0) {
            return JsonPrimitive(value.toInt())
        }
        return buildJsonObject {
            put("kind", "number")
            put("integer", value.isFinite() && value % 1.0 == 0.0)
            put("sign", when { value < 0 -> "negative"; value > 0 -> "positive"; else -> "zero" })
            put("magnitude", numberMagnitude(value))
        }
    }

    private fun sanitizeKey(raw: String, index: Int, salt: String): String = when {
        raw in setOf("__proto__", "prototype", "constructor") -> "field_${index}_${opaqueTag(raw, salt).take(6)}"
        safeKeyRegex.matches(raw) -> raw
        else -> "field_${index}_${opaqueTag(raw, salt).take(6)}"
    }

    private fun marker(kind: String): JsonObject = buildJsonObject { put("kind", kind) }

    private fun classifyString(raw: String): String = when {
        raw.isEmpty() -> "empty"
        raw.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}")) -> "uuid"
        raw.contains('@') && raw.contains('.') -> "email"
        raw.count(Char::isDigit) in 8..15 && raw.filterNot { it.isDigit() || it in "+-() " }.isEmpty() -> "phone"
        raw.startsWith("http://") || raw.startsWith("https://") -> "url"
        else -> "text"
    }

    private fun lengthBucket(length: Int): String = when (length) {
        0 -> "0"
        in 1..4 -> "1-4"
        in 5..8 -> "5-8"
        in 9..16 -> "9-16"
        in 17..32 -> "17-32"
        in 33..64 -> "33-64"
        in 65..128 -> "65-128"
        else -> "129+"
    }

    private fun numberMagnitude(value: Double): String {
        val absolute = kotlin.math.abs(value)
        return when {
            absolute == 0.0 -> "zero"
            absolute < 1.0 -> "lt1"
            absolute < 10.0 -> "lt10"
            absolute < 100.0 -> "lt100"
            absolute < 1_000.0 -> "lt1000"
            absolute < 1_000_000.0 -> "lt1000000"
            else -> "large"
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    val DOCUMENT_START_SCRIPT: String = """
        (function() {
          'use strict';
          if (window.__rotaCertaNetworkDiagnosticInstalled === true) return;
          if (window !== window.top) return;
          if (location.protocol !== 'https:' || location.hostname.toLowerCase() !== 'www.blablacar.com.br') return;
          const bridge = window.RotaCertaNetworkDiagnostic;
          Object.defineProperty(window, '__rotaCertaNetworkDiagnosticInstalled', { value: true });

          const MAX_BODY_CHARS = 600000;
          const MAX_MESSAGE_CHARS = 60000;
          const MAX_DEPTH = 8;
          const MAX_FIELDS = 48;
          const MAX_ARRAY = 18;
          const MAX_NODES = 420;
          const MAX_SOURCE_BOOKINGS = 48;
          const MAX_SOURCE_TRIPS = 24;
          const networkTripSources = new Map();
          Object.defineProperty(window, '__rotaCertaNetworkTripSource', {
            value: function(rawTripId) {
              const tripId = String(rawTripId || '').trim().toLowerCase();
              const source = networkTripSources.get(tripId) || null;
              if (!source) return null;
              try { return JSON.parse(JSON.stringify(source)); } catch (_) { return null; }
            }
          });
          const safeSegments = new Set([
            'api', 'graphql', 'rides', 'ride', 'offer', 'offers', 'trip', 'trips', 'booking', 'bookings',
            'passenger', 'passengers', 'reservation', 'reservations', 'edit', 'options', 'seats', 'seat',
            'dashboard', 'driver', 'drivers', 'profile', 'profiles', 'management', 'manage', 'web', 'search'
          ]);
          const secretKey = /(^|[_.-])(auth|authorization|cookie|cookies|token|secret|password|passwd|session|csrf|jwt|bearer|credential|api.?key)([_.-]|$)/i;
          const personalKey = /(^|[_.-])(name|phone|mobile|email|address|street|latitude|longitude|coordinates?|geolocation)([_.-]|$)/i;
          const seatKey = /(^|[_.-])(seat|seats|places?|vacancies|capacity|quantity|count)([_.-]|$)/i;

          function allowedHost(host) {
            const value = String(host || '').toLowerCase().replace(/\.$/, '');
            return value === 'blablacar.com.br' || value.endsWith('.blablacar.com.br') ||
              value === 'blablacar.com' || value.endsWith('.blablacar.com');
          }

          function capturePage() {
            const path = String(location.pathname || '').toLowerCase();
            return path.includes('/rides/offer') || path.includes('/trip') ||
              path.includes('/passenger') || path.includes('/booking');
          }

          function safeEndpoint(raw) {
            try {
              const url = new URL(String(raw || ''), location.href);
              if (url.protocol !== 'https:' || !allowedHost(url.hostname)) return '';
              const parts = url.pathname.split('/').filter(Boolean).map(function(part) {
                const lower = part.toLowerCase();
                if (safeSegments.has(lower) || /^v[0-9]{1,2}$/.test(lower)) return lower;
                return ':id';
              });
              return 'https://' + url.hostname.toLowerCase() + (parts.length ? '/' + parts.join('/') : '');
            } catch (_) {
              return '';
            }
          }

          function sourceText(value, maxLength) {
            if (typeof value !== 'string' && typeof value !== 'number') return '';
            return String(value).trim().slice(0, maxLength);
          }

          function sourceNumber(value, minimum, maximum) {
            const number = typeof value === 'number' ? value : Number(value);
            return Number.isFinite(number) && number >= minimum && number <= maximum ? number : null;
          }

          function sourceObject(value) {
            return value && typeof value === 'object' && !Array.isArray(value) ? value : Object.create(null);
          }

          function sourceWaypoint(rawValue) {
            const waypoint = sourceObject(rawValue);
            const place = sourceObject(waypoint.place);
            const address = sourceText(place.address || waypoint.address || waypoint.secondary_text, 500);
            return {
              label: sourceText(waypoint.main_text || place.city || waypoint.secondary_text || address, 240),
              address: address,
              latitude: sourceNumber(place.latitude !== undefined ? place.latitude : waypoint.latitude, -90, 90),
              longitude: sourceNumber(place.longitude !== undefined ? place.longitude : waypoint.longitude, -180, 180)
            };
          }

          function sourcePhone(rawModes) {
            if (!Array.isArray(rawModes)) return '';
            for (const rawMode of rawModes.slice(0, 12)) {
              const mode = sourceObject(rawMode);
              const phone = sourceText(mode.phone_number || mode.phoneNumber, 40);
              if (phone) return phone;
            }
            return '';
          }

          function rememberTripRoot(rawRoot) {
            const root = sourceObject(rawRoot);
            if (!Array.isArray(root.bookings)) return;
            const tripId = sourceText(root.trip_offer_encrypted_id, 160).toLowerCase();
            if (!/^[A-Za-z0-9_-]{8,160}$/.test(tripId)) return;
            const bookings = root.bookings.slice(0, MAX_SOURCE_BOOKINGS).map(function(rawBooking) {
              const booking = sourceObject(rawBooking);
              const passenger = sourceObject(booking.passenger);
              const price = sourceObject(booking.price);
              return {
                passengerId: sourceText(passenger.id, 160),
                passengerName: sourceText(passenger.display_name, 120),
                seats: sourceNumber(booking.seats_reserved, 1, 20) || 0,
                phone: sourcePhone(booking.contact_modes),
                fareAmount: sourceText(price.amount, 40),
                fareCurrencyCode: sourceText(price.currency, 3).toUpperCase(),
                fareFormatted: sourceText(price.formatted_price, 80),
                pickup: sourceWaypoint(booking.pickup_waypoint),
                dropoff: sourceWaypoint(booking.dropoff_waypoint)
              };
            });
            if (!networkTripSources.has(tripId) && networkTripSources.size >= MAX_SOURCE_TRIPS) {
              const oldest = networkTripSources.keys().next();
              if (!oldest.done) networkTripSources.delete(oldest.value);
            }
            networkTripSources.set(tripId, {
              tripId: tripId,
              bookingsComplete: root.bookings.length <= MAX_SOURCE_BOOKINGS,
              bookings: bookings
            });
          }

          function rememberNetworkTripSources(parsed) {
            if (!parsed || typeof parsed !== 'object') return;
            const pending = [{ value: parsed, depth: 0 }];
            const seen = new WeakSet();
            let visited = 0;
            while (pending.length && visited < 160) {
              const current = pending.shift();
              const value = current.value;
              if (!value || typeof value !== 'object' || seen.has(value)) continue;
              seen.add(value);
              visited += 1;
              if (!Array.isArray(value) && Array.isArray(value.bookings) && value.trip_offer_encrypted_id) {
                rememberTripRoot(value);
              }
              if (current.depth >= 5) continue;
              if (Array.isArray(value)) {
                value.slice(0, 32).forEach(function(item) {
                  if (item && typeof item === 'object') pending.push({ value: item, depth: current.depth + 1 });
                });
              } else {
                Object.keys(value).slice(0, 64).forEach(function(key) {
                  let child;
                  try { child = value[key]; } catch (_) { child = null; }
                  if (child && typeof child === 'object') pending.push({ value: child, depth: current.depth + 1 });
                });
              }
            }
          }

          function lengthBucket(length) {
            if (length <= 0) return '0';
            if (length <= 4) return '1-4';
            if (length <= 8) return '5-8';
            if (length <= 16) return '9-16';
            if (length <= 32) return '17-32';
            if (length <= 64) return '33-64';
            if (length <= 128) return '65-128';
            return '129+';
          }

          function magnitude(value) {
            const absolute = Math.abs(value);
            if (absolute === 0) return 'zero';
            if (absolute < 1) return 'lt1';
            if (absolute < 10) return 'lt10';
            if (absolute < 100) return 'lt100';
            if (absolute < 1000) return 'lt1000';
            if (absolute < 1000000) return 'lt1000000';
            return 'large';
          }

          function classifyString(value) {
            if (!value) return 'empty';
            if (/^[0-9a-f]{8}-[0-9a-f-]{27,}$/i.test(value)) return 'uuid';
            if (/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) return 'email';
            if (/^\+?[0-9()\-\s]{8,20}$/.test(value)) return 'phone';
            if (/^https?:\/\//i.test(value)) return 'url';
            return 'text';
          }

          function safeKey(raw, index) {
            const value = String(raw || '');
            if (value === '__proto__' || value === 'prototype' || value === 'constructor') return 'field_' + index;
            return /^[A-Za-z_][A-Za-z0-9_.-]{0,79}$/.test(value) ? value : 'field_' + index;
          }

          function anonymize(value, key, depth, state, seen) {
            state.nodes += 1;
            if (state.nodes > MAX_NODES) return { kind: 'node_limit' };
            if (depth > MAX_DEPTH) return { kind: 'depth_limit' };
            if (key && secretKey.test(key)) return { kind: 'redacted' };
            if (value === null || typeof value === 'boolean') return value;
            if (typeof value === 'string') {
              if (key && personalKey.test(key)) return { kind: 'redacted' };
              return { kind: classifyString(value), lengthBucket: lengthBucket(value.length) };
            }
            if (typeof value === 'number') {
              if (!Number.isFinite(value)) return { kind: 'number', magnitude: 'large' };
              if (key && seatKey.test(key) && Number.isInteger(value) && value >= 0 && value <= 20) return value;
              return {
                kind: 'number',
                integer: Number.isInteger(value),
                sign: value < 0 ? 'negative' : (value > 0 ? 'positive' : 'zero'),
                magnitude: magnitude(value)
              };
            }
            if (typeof value !== 'object') return { kind: 'unsupported' };
            if (seen.has(value)) return { kind: 'unsupported' };
            seen.add(value);
            if (Array.isArray(value)) {
              const sample = value.slice(0, MAX_ARRAY).map(function(item) {
                return anonymize(item, key, depth + 1, state, seen);
              });
              return { kind: 'array', arrayLength: value.length, sample: sample };
            }
            const fields = Object.create(null);
            const keys = Object.keys(value);
            keys.slice(0, MAX_FIELDS).forEach(function(rawKey, index) {
              let fieldValue;
              try { fieldValue = value[rawKey]; } catch (_) { fieldValue = undefined; }
              fields[safeKey(rawKey, index)] = anonymize(fieldValue, rawKey, depth + 1, state, seen);
            });
            return {
              kind: 'object',
              fieldCount: keys.length,
              truncated: keys.length > MAX_FIELDS,
              fields: fields
            };
          }

          function durationBucket(started) {
            const elapsed = Math.max(0, performance.now() - started);
            if (elapsed < 50) return 0;
            if (elapsed < 100) return 50;
            if (elapsed < 250) return 100;
            if (elapsed < 500) return 250;
            if (elapsed < 1000) return 500;
            if (elapsed < 2000) return 1000;
            if (elapsed < 5000) return 2000;
            if (elapsed < 10000) return 5000;
            return 10000;
          }

          function post(envelope) {
            if (!bridge || typeof bridge.postMessage !== 'function') return;
            try {
              let serialized = JSON.stringify(envelope);
              if (serialized.length > MAX_MESSAGE_CHARS) {
                envelope.body = { kind: 'too_large' };
                envelope.contentKind = 'too_large';
                serialized = JSON.stringify(envelope);
              }
              bridge.postMessage(serialized);
            } catch (_) {}
          }

          function baseEnvelope(transport, method, endpoint, status, started, contentKind, body) {
            return {
              schema: 1,
              transport: transport,
              method: String(method || 'GET').toUpperCase(),
              endpoint: endpoint,
              page: safeEndpoint(location.href),
              status: Number.isInteger(status) ? status : 0,
              durationBucketMs: durationBucket(started),
              contentKind: contentKind,
              body: body
            };
          }

          async function observeFetch(response, rawUrl, method, started) {
            if (!capturePage()) return;
            const endpoint = safeEndpoint(response && response.url ? response.url : rawUrl);
            if (!endpoint) return;
            let contentType = '';
            let contentLength = 0;
            try {
              contentType = String(response.headers.get('content-type') || '').toLowerCase();
              contentLength = Number(response.headers.get('content-length') || 0);
            } catch (_) {}
            if (!contentType.includes('json')) {
              post(baseEnvelope('fetch', method, endpoint, response.status, started, 'non_json', {
                kind: 'non_json', lengthBucket: lengthBucket(contentLength)
              }));
              return;
            }
            if (contentLength > MAX_BODY_CHARS) {
              post(baseEnvelope('fetch', method, endpoint, response.status, started, 'too_large', { kind: 'too_large' }));
              return;
            }
            try {
              const text = await response.clone().text();
              if (text.length > MAX_BODY_CHARS) {
                post(baseEnvelope('fetch', method, endpoint, response.status, started, 'too_large', { kind: 'too_large' }));
                return;
              }
              if (!text) {
                post(baseEnvelope('fetch', method, endpoint, response.status, started, 'empty', { kind: 'empty' }));
                return;
              }
              const parsed = JSON.parse(text);
              if (response.status >= 200 && response.status < 300) rememberNetworkTripSources(parsed);
              const body = anonymize(parsed, 'body', 0, { nodes: 0 }, new WeakSet());
              post(baseEnvelope('fetch', method, endpoint, response.status, started, 'json', body));
            } catch (_) {
              post(baseEnvelope('fetch', method, endpoint, response.status, started, 'invalid_json', { kind: 'invalid_json' }));
            }
          }

          const originalFetch = window.fetch;
          if (typeof originalFetch === 'function') {
            window.fetch = function() {
              const args = arguments;
              const started = performance.now();
              let rawUrl = '';
              let method = 'GET';
              try {
                rawUrl = args[0] instanceof Request ? args[0].url : String(args[0] || '');
                method = args[0] instanceof Request ? args[0].method : (args[1] && args[1].method ? args[1].method : 'GET');
              } catch (_) {}
              return originalFetch.apply(this, args).then(function(response) {
                observeFetch(response, rawUrl, method, started);
                return response;
              });
            };
          }

          const xhrMeta = new WeakMap();
          const originalOpen = XMLHttpRequest.prototype.open;
          const originalSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function(method, url) {
            xhrMeta.set(this, { method: String(method || 'GET'), url: String(url || ''), started: 0 });
            return originalOpen.apply(this, arguments);
          };
          XMLHttpRequest.prototype.send = function() {
            const xhr = this;
            const meta = xhrMeta.get(xhr) || { method: 'GET', url: '', started: 0 };
            meta.started = performance.now();
            xhr.addEventListener('loadend', function() {
              if (!capturePage()) return;
              const endpoint = safeEndpoint(xhr.responseURL || meta.url);
              if (!endpoint) return;
              let contentType = '';
              try { contentType = String(xhr.getResponseHeader('content-type') || '').toLowerCase(); } catch (_) {}
              if (!contentType.includes('json')) {
                post(baseEnvelope('xhr', meta.method, endpoint, xhr.status, meta.started, 'non_json', { kind: 'non_json' }));
                return;
              }
              try {
                let parsed;
                if (xhr.responseType === 'json') {
                  parsed = xhr.response;
                } else {
                  const text = String(xhr.responseText || '');
                  if (text.length > MAX_BODY_CHARS) {
                    post(baseEnvelope('xhr', meta.method, endpoint, xhr.status, meta.started, 'too_large', { kind: 'too_large' }));
                    return;
                  }
                  if (!text) {
                    post(baseEnvelope('xhr', meta.method, endpoint, xhr.status, meta.started, 'empty', { kind: 'empty' }));
                    return;
                  }
                  parsed = JSON.parse(text);
                }
                if (xhr.status >= 200 && xhr.status < 300) rememberNetworkTripSources(parsed);
                const body = anonymize(parsed, 'body', 0, { nodes: 0 }, new WeakSet());
                post(baseEnvelope('xhr', meta.method, endpoint, xhr.status, meta.started, 'json', body));
              } catch (_) {
                post(baseEnvelope('xhr', meta.method, endpoint, xhr.status, meta.started, 'invalid_json', { kind: 'invalid_json' }));
              }
            }, { once: true });
            return originalSend.apply(this, arguments);
          };
        })();
    """.trimIndent()
}
