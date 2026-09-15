package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.webkit.WebView
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.json.JSONObject

@Serializable
internal data class BlaBlaTripBoostBrowserState0562(
    val screen: String = "UNKNOWN",
    val tripIdBound: Boolean = false,
    val controlFound: Boolean = false,
    val controlAmbiguous: Boolean = false,
    val state: String = "UNKNOWN",
    val savePresent: Boolean = false,
    val authRequired: Boolean = false,
    val error: Boolean = false,
    val pageUrl: String = "",
) {
    fun observed(): BlaBlaTripBoostObservedState0562 = when (state.uppercase()) {
        "ENABLED" -> BlaBlaTripBoostObservedState0562.ENABLED
        "DISABLED" -> BlaBlaTripBoostObservedState0562.DISABLED
        else -> BlaBlaTripBoostObservedState0562.UNKNOWN
    }
}

@Serializable
internal data class BlaBlaTripBoostSessionIdentity0562(
    val observedUuids: List<String> = emptyList(),
    val profileLinks: List<String> = emptyList(),
)

@Serializable
internal data class BlaBlaTripBoostBrowserActionResult0562(
    val found: Boolean = false,
    val clicked: Boolean = false,
    val reason: String = "",
    val before: Boolean? = null,
    val after: Boolean? = null,
)

/**
 * Browser-only actuator for SET_TRIP_BOOST.
 * Input JSON never enters this class as code. All executable scripts are registered assets.
 */
internal class BlaBlaTripBoostBrowserController0562(
    context: Context,
    private val webView: WebView,
    private val accountId: String,
    private val expectedProfileUuid: String,
    private val tripId: String,
    private val correlationId: String,
) {
    private val app = context.applicationContext
    private val orchestrator = BlaBlaBrowserOrchestrator()
    private var interactionGeneration = 0L

    fun verifySession(callback: (Boolean) -> Unit) {
        evaluate<BlaBlaTripBoostSessionIdentity0562>(BlaBlaBrowserRequest.SESSION_IDENTITY) { result ->
            val matches = result?.observedUuids.orEmpty().count { it.equals(expectedProfileUuid, ignoreCase = true) }
            callback(matches == 1)
        }
    }

    fun read(callback: (BlaBlaTripBoostBrowserState0562?) -> Unit) =
        evaluate(
            request = BlaBlaBrowserRequest.BOOST_STATE,
            arguments = identityArguments(),
            callback = callback,
        )

    fun openEdit(callback: (BlaBlaTripBoostBrowserActionResult0562?) -> Unit) =
        evaluate(
            request = BlaBlaBrowserRequest.BOOST_OPEN_EDIT,
            arguments = identityArguments(),
            callback = callback,
        )

    fun openBoostSection(callback: (BlaBlaTripBoostBrowserActionResult0562?) -> Unit) =
        evaluate(
            request = BlaBlaBrowserRequest.BOOST_OPEN_SECTION,
            arguments = identityArguments(),
            callback = callback,
        )

    fun setDesired(
        desired: BlaBlaTripBoostDesiredState0562,
        callback: (BlaBlaTripBoostBrowserActionResult0562?) -> Unit,
    ) = evaluate(
        request = BlaBlaBrowserRequest.BOOST_SET_STATE,
        arguments = identityArguments() + ("DESIRED_ENABLED" to (desired == BlaBlaTripBoostDesiredState0562.ENABLED).toString()),
        callback = callback,
    )

    fun save(
        desired: BlaBlaTripBoostDesiredState0562,
        callback: (BlaBlaTripBoostBrowserActionResult0562?) -> Unit,
    ) = evaluate(
        request = BlaBlaBrowserRequest.BOOST_SAVE,
        arguments = identityArguments() + ("DESIRED_ENABLED" to (desired == BlaBlaTripBoostDesiredState0562.ENABLED).toString()),
        callback = callback,
    )

    fun cancel() {
        interactionGeneration += 1
        orchestrator.cancel()
    }

    private fun identityArguments(): Map<String, String> =
        mapOf("EXPECTED_TRIP_ID" to JSONObject.quote(tripId))

    private inline fun <reified T> evaluate(
        request: BlaBlaBrowserRequest,
        arguments: Map<String, String> = emptyMap(),
        crossinline callback: (T?) -> Unit,
    ) {
        interactionGeneration += 1L
        val execution = browserContext()
        val current = ::browserContext
        val result: (T?) -> Unit = { callback(it) }
        if (request.operation == BlaBlaBrowserOperation.REMOTE_WRITE) {
            orchestrator.executeRemoteWrite(
                androidContext = app,
                webView = webView,
                request = request,
                executionContext = execution,
                currentContext = current,
                deserializer = serializer<T>(),
                arguments = arguments,
                reason = "trip_boost_remote_write_0562",
                timeoutMs = REQUEST_TIMEOUT_MS,
                callback = result,
            )
        } else {
            orchestrator.executeCollectionStep(
                androidContext = app,
                webView = webView,
                request = request,
                executionContext = execution,
                currentContext = current,
                deserializer = serializer<T>(),
                arguments = arguments,
                reason = "trip_boost_read_or_navigation_0562",
                timeoutMs = REQUEST_TIMEOUT_MS,
                callback = result,
            )
        }
    }

    private fun browserContext(): BlaBlaBrowserExecutionContext = BlaBlaBrowserExecutionContext(
        accountId = accountId,
        expectedProfileUuid = expectedProfileUuid,
        syncGeneration = interactionGeneration,
        navigationGeneration = 0L,
        tripId = tripId,
        url = webView.url.orEmpty(),
        diagnosticCorrelationId = correlationId,
    )

    private companion object { const val REQUEST_TIMEOUT_MS = 12_000L }
}
