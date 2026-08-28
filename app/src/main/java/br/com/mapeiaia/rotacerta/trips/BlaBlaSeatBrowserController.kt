package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.webkit.WebView
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class BlaBlaBrowserClickResult(
    val found: Boolean = false,
    val clicked: Boolean = false,
)

/**
 * Shared documented seat interaction path.
 *
 * Business rules stay in the caller. This class is the only WebView executor for
 * the documented SEAT_OPTIONS -> SEAT_CHANGE -> SEAT_SAVE sequence.
 */
internal class BlaBlaSeatBrowserController(
    context: Context,
    private val webView: WebView,
    private val accountId: String,
    private val expectedProfileUuid: String,
    private val tripId: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scripts = BlaBlaBrowserScriptRegistry(context)
    private val orchestrator = BlaBlaBrowserOrchestrator()
    private var interactionGeneration = 0L

    fun read(callback: (SeatOptionState?) -> Unit) {
        evaluate(
            request = BlaBlaBrowserRequest.SEAT_OPTIONS,
            script = scripts.script(BlaBlaBrowserRequest.SEAT_OPTIONS),
            callback = callback,
        )
    }

    fun adjustAndSave(
        currentSeats: Int,
        targetSeats: Int,
        callback: (Boolean, String) -> Unit,
    ) {
        if (currentSeats < 0 || targetSeats < 0) {
            callback(false, "invalid_seat_target")
            return
        }
        if (currentSeats == targetSeats) {
            save(callback)
            return
        }
        changeVerified(currentSeats, targetSeats, callback)
    }

    fun cancel() {
        interactionGeneration += 1L
        orchestrator.cancel()
    }

    private fun changeVerified(
        currentSeats: Int,
        targetSeats: Int,
        callback: (Boolean, String) -> Unit,
    ) {
        if (currentSeats == targetSeats) {
            save(callback)
            return
        }
        val direction = if (targetSeats > currentSeats) 1 else -1
        val expectedNext = currentSeats + direction
        val script = scripts.script(
            BlaBlaBrowserRequest.SEAT_CHANGE,
            mapOf("DIRECTION" to direction.toString()),
        )
        evaluate<BlaBlaBrowserClickResult>(BlaBlaBrowserRequest.SEAT_CHANGE, script) { click ->
            if (click?.clicked != true) {
                callback(false, "seat_change_not_clicked")
                return@evaluate
            }
            webView.postDelayed({
                read { observed ->
                    if (observed?.seats != expectedNext) {
                        callback(false, "seat_change_not_confirmed")
                        return@read
                    }
                    changeVerified(expectedNext, targetSeats, callback)
                }
            }, CHANGE_SETTLE_MS)
        }
    }

    private fun save(callback: (Boolean, String) -> Unit) {
        val script = scripts.script(BlaBlaBrowserRequest.SEAT_SAVE)
        evaluate<BlaBlaBrowserClickResult>(BlaBlaBrowserRequest.SEAT_SAVE, script) { result ->
            if (result?.clicked == true) callback(true, "seat_save_clicked")
            else callback(false, "seat_save_not_clicked")
        }
    }

    private inline fun <reified T> evaluate(
        request: BlaBlaBrowserRequest,
        script: String,
        crossinline callback: (T?) -> Unit,
    ) {
        val localGeneration = ++interactionGeneration
        val token = orchestrator.start(request, context(), "seat_controller")
        webView.evaluateJavascript(script) { encoded ->
            if (localGeneration != interactionGeneration || !orchestrator.isCurrent(token, context())) {
                return@evaluateJavascript
            }
            val decoded = runCatching {
                if (encoded.isNullOrBlank() || encoded == "null") return@runCatching null
                val raw = json.parseToJsonElement(encoded).jsonPrimitive.content
                json.decodeFromString<T>(raw)
            }.getOrNull()
            callback(decoded)
        }
    }

    private fun context(): BlaBlaBrowserExecutionContext = BlaBlaBrowserExecutionContext(
        accountId = accountId,
        expectedProfileUuid = expectedProfileUuid,
        syncGeneration = interactionGeneration,
        navigationGeneration = 0L,
        tripId = tripId,
        url = webView.url.orEmpty(),
    )

    private companion object {
        const val CHANGE_SETTLE_MS = 360L
    }
}
