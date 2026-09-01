package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.webkit.WebView
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * The single browser execution authority.
 *
 * Scripts never invoke another script. Navigation/capture code asks this
 * orchestrator to start exactly one request and every asynchronous callback is
 * checked against the token that was current when the request started.
 */
internal class BlaBlaBrowserOrchestrator {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var scriptRegistry: BlaBlaBrowserScriptRegistry? = null
    private var generation = 0L
    private var active: BlaBlaBrowserRequestToken? = null

    fun start(
        request: BlaBlaBrowserRequest,
        context: BlaBlaBrowserExecutionContext,
        reason: String = "",
    ): BlaBlaBrowserRequestToken {
        generation += 1L
        return BlaBlaBrowserRequestToken(
            generation = generation,
            request = request,
            context = context,
            reason = reason,
        ).also { active = it }
    }

    fun startOrReuse(
        request: BlaBlaBrowserRequest,
        context: BlaBlaBrowserExecutionContext,
        reason: String = "",
    ): BlaBlaBrowserRequestToken {
        val current = active
        if (current != null && current.request == request && contextsCompatible(current.context, context)) {
            return current
        }
        return start(request, context, reason)
    }

    fun current(): BlaBlaBrowserRequestToken? = active

    fun isCurrent(
        token: BlaBlaBrowserRequestToken,
        currentContext: BlaBlaBrowserExecutionContext,
    ): Boolean {
        val current = active ?: return false
        return current.generation == token.generation &&
            current.request == token.request &&
            contextsCompatible(token.context, currentContext)
    }


    /**
     * Installs the network-first observer through the same capture authority.
     * Response interpretation remains in the dedicated normalization module.
     */
    fun installNetworkEvidenceCapture(
        androidContext: Context,
        webView: WebView,
        accountId: String,
    ): BlaBlaNetworkDiagnosticRecorder =
        BlaBlaNetworkDiagnosticRecorder(
            context = androidContext,
            accountId = accountId,
            appPackageName = androidContext.packageName,
        ).also { recorder -> recorder.install(webView) }

    /**
     * Canonical path for BlaBlaCar data collection. CAPTURE and browser-only
     * NAVIGATION are allowed; REMOTE_WRITE is rejected fail-closed.
     */
    fun <T> executeCollectionStep(
        androidContext: Context,
        webView: WebView,
        request: BlaBlaBrowserRequest,
        executionContext: BlaBlaBrowserExecutionContext,
        currentContext: () -> BlaBlaBrowserExecutionContext,
        deserializer: DeserializationStrategy<T>,
        arguments: Map<String, String> = emptyMap(),
        reason: String = "collection",
        timeoutMs: Long = 0L,
        callback: (T?) -> Unit,
    ): BlaBlaBrowserRequestToken? {
        if (!request.isCollectionStep) {
            recordRejected(androidContext, request, executionContext, "collection_cannot_remote_write")
            callback(null)
            return null
        }
        val script = runCatching { registry(androidContext).script(request, arguments) }.getOrElse { error ->
            recordScriptError(androidContext, request, executionContext, error)
            callback(null)
            return null
        }
        return executeCollectionScript(
            androidContext = androidContext,
            webView = webView,
            request = request,
            script = script,
            executionContext = executionContext,
            currentContext = currentContext,
            reason = reason,
            timeoutMs = timeoutMs,
        ) { encoded ->
            callback(decode(encoded, deserializer, androidContext, request, executionContext))
        }
    }

    /**
     * Bridge for the proven legacy HTML/MHTML selectors. New collection
     * capabilities should use registered asset requests above.
     */
    fun executeCollectionScript(
        androidContext: Context,
        webView: WebView,
        request: BlaBlaBrowserRequest,
        script: String,
        executionContext: BlaBlaBrowserExecutionContext,
        currentContext: () -> BlaBlaBrowserExecutionContext,
        reason: String = "inline_collection",
        timeoutMs: Long = 0L,
        callback: (String?) -> Unit,
    ): BlaBlaBrowserRequestToken? {
        if (!request.isCollectionStep) {
            recordRejected(androidContext, request, executionContext, "inline_collection_cannot_remote_write")
            callback(null)
            return null
        }
        return executeScript(
            androidContext,
            webView,
            request,
            script,
            executionContext,
            currentContext,
            reason,
            timeoutMs,
            callback,
        )
    }

    /**
     * Remote mutation is deliberately separate from collection.
     */
    fun <T> executeRemoteWrite(
        androidContext: Context,
        webView: WebView,
        request: BlaBlaBrowserRequest,
        executionContext: BlaBlaBrowserExecutionContext,
        currentContext: () -> BlaBlaBrowserExecutionContext,
        deserializer: DeserializationStrategy<T>,
        arguments: Map<String, String> = emptyMap(),
        reason: String = "remote_write",
        timeoutMs: Long = 0L,
        callback: (T?) -> Unit,
    ): BlaBlaBrowserRequestToken? {
        if (request.operation != BlaBlaBrowserOperation.REMOTE_WRITE) {
            recordRejected(androidContext, request, executionContext, "remote_write_requires_explicit_write_request")
            callback(null)
            return null
        }
        val script = runCatching { registry(androidContext).script(request, arguments) }.getOrElse { error ->
            recordScriptError(androidContext, request, executionContext, error)
            callback(null)
            return null
        }
        return executeScript(
            androidContext,
            webView,
            request,
            script,
            executionContext,
            currentContext,
            reason,
            timeoutMs,
        ) { encoded ->
            callback(decode(encoded, deserializer, androidContext, request, executionContext))
        }
    }

    private fun executeScript(
        androidContext: Context,
        webView: WebView,
        request: BlaBlaBrowserRequest,
        script: String,
        executionContext: BlaBlaBrowserExecutionContext,
        currentContext: () -> BlaBlaBrowserExecutionContext,
        reason: String,
        timeoutMs: Long,
        callback: (String?) -> Unit,
    ): BlaBlaBrowserRequestToken {
        val previous = current()
        val token = startOrReuse(request, executionContext, reason)
        if (previous?.generation != token.generation) {
            UnifiedDebugEventStore.record(
                "BROWSER_REQUEST_STARTED",
                androidContext.packageName,
                "accountId=${executionContext.accountId.take(80)} request=${request.name} operation=${request.operation.name} token=${token.generation} sync=${executionContext.syncGeneration} nav=${executionContext.navigationGeneration} tripIdPresent=${executionContext.tripId.isNotBlank()} passengerKeyPresent=${executionContext.passengerKey.isNotBlank()}",
            )
        }
        var completed = false
        if (timeoutMs > 0L) {
            webView.postDelayed({
                if (completed || !isCurrent(token, currentContext())) return@postDelayed
                completed = true
                finish(token)
                UnifiedDebugEventStore.record(
                    "BROWSER_REQUEST_TIMEOUT",
                    androidContext.packageName,
                    "accountId=${executionContext.accountId.take(80)} request=${request.name} operation=${request.operation.name} timeoutMs=$timeoutMs failClosed=true",
                )
                callback(null)
            }, timeoutMs)
        }
        webView.evaluateJavascript(script) { encoded ->
            if (completed) return@evaluateJavascript
            val liveContext = currentContext()
            if (!isCurrent(token, liveContext)) {
                completed = true
                UnifiedDebugEventStore.record(
                    "BROWSER_STALE_CALLBACK_IGNORED",
                    androidContext.packageName,
                    "accountId=${executionContext.accountId.take(80)} request=${request.name} token=${token.generation} current=${current()?.generation ?: -1L}",
                )
                return@evaluateJavascript
            }
            completed = true
            callback(encoded)
        }
        return token
    }

    private fun <T> decode(
        encoded: String?,
        deserializer: DeserializationStrategy<T>,
        androidContext: Context,
        request: BlaBlaBrowserRequest,
        executionContext: BlaBlaBrowserExecutionContext,
    ): T? {
        if (encoded.isNullOrBlank() || encoded == "null") return null
        return runCatching {
            val raw = json.parseToJsonElement(encoded).jsonPrimitive.content
            json.decodeFromString(deserializer, raw)
        }.getOrElse { error ->
            UnifiedDebugEventStore.record(
                "BROWSER_REQUEST_DECODE_ERROR",
                androidContext.packageName,
                "accountId=${executionContext.accountId.take(80)} request=${request.name} " +
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "BROWSER_REQUEST_DECODE",
                        component = "BlaBlaBrowserOrchestrator",
                        method = "decodeResult",
                    ),
            )
            null
        }
    }

    private fun registry(context: Context): BlaBlaBrowserScriptRegistry =
        scriptRegistry ?: BlaBlaBrowserScriptRegistry(context).also { scriptRegistry = it }

    private fun recordRejected(
        context: Context,
        request: BlaBlaBrowserRequest,
        executionContext: BlaBlaBrowserExecutionContext,
        reason: String,
    ) {
        UnifiedDebugEventStore.record(
            "BROWSER_REQUEST_REJECTED",
            context.packageName,
            "accountId=${executionContext.accountId.take(80)} request=${request.name} operation=${request.operation.name} reason=$reason",
        )
    }

    private fun recordScriptError(
        context: Context,
        request: BlaBlaBrowserRequest,
        executionContext: BlaBlaBrowserExecutionContext,
        error: Throwable,
    ) {
        UnifiedDebugEventStore.record(
            "BROWSER_REQUEST_SCRIPT_ERROR",
            context.packageName,
            "accountId=${executionContext.accountId.take(80)} request=${request.name} " +
                AgendaFailureEvidence.describe(
                    error = error,
                    operation = "BROWSER_REQUEST_SCRIPT",
                    component = "BlaBlaBrowserOrchestrator",
                    method = "recordScriptError",
                ),
        )
    }

    fun finish(token: BlaBlaBrowserRequestToken): Boolean {
        val current = active ?: return false
        if (current.generation != token.generation || current.request != token.request) return false
        active = null
        return true
    }

    fun cancel() {
        generation += 1L
        active = null
    }

    private fun contextsCompatible(
        expected: BlaBlaBrowserExecutionContext,
        actual: BlaBlaBrowserExecutionContext,
    ): Boolean {
        if (expected.accountId != actual.accountId) return false
        if (
            expected.expectedProfileUuid.isNotBlank() &&
            actual.expectedProfileUuid.isNotBlank() &&
            !expected.expectedProfileUuid.equals(actual.expectedProfileUuid, ignoreCase = true)
        ) return false
        if (expected.syncGeneration != actual.syncGeneration) return false
        if (expected.navigationGeneration != actual.navigationGeneration) return false
        if (expected.cardKey.isNotBlank() && actual.cardKey.isNotBlank() && expected.cardKey != actual.cardKey) return false
        if (expected.tripId.isNotBlank() && actual.tripId.isNotBlank() && !expected.tripId.equals(actual.tripId, true)) return false
        if (expected.passengerKey.isNotBlank() && actual.passengerKey.isNotBlank() && expected.passengerKey != actual.passengerKey) return false
        return true
    }
}
