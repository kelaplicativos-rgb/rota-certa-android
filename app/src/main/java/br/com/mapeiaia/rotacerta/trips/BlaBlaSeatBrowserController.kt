package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.webkit.WebView
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

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
    private val orchestrator = BlaBlaBrowserOrchestrator()
    private var interactionGeneration = 0L

    fun read(callback: (SeatOptionState?) -> Unit) {
        evaluate(
            request = BlaBlaBrowserRequest.SEAT_OPTIONS,
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
        evaluate<BlaBlaBrowserClickResult>(
            request = BlaBlaBrowserRequest.SEAT_CHANGE,
            arguments = mapOf("DIRECTION" to direction.toString()),
        ) { click ->
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
        evaluate<BlaBlaBrowserClickResult>(BlaBlaBrowserRequest.SEAT_SAVE) { result ->
            if (result?.clicked == true) callback(true, "seat_save_clicked")
            else callback(false, "seat_save_not_clicked")
        }
    }

    private inline fun <reified T> evaluate(
        request: BlaBlaBrowserRequest,
        arguments: Map<String, String> = emptyMap(),
        crossinline callback: (T?) -> Unit,
    ) {
        interactionGeneration += 1L
        val executionContext = context()
        val onResult: (T?) -> Unit = { result -> callback(result) }
        if (request.operation == BlaBlaBrowserOperation.REMOTE_WRITE) {
            orchestrator.executeRemoteWrite(
                androidContext = context,
                webView = webView,
                request = request,
                executionContext = executionContext,
                currentContext = ::context,
                deserializer = serializer<T>(),
                arguments = arguments,
                reason = "seat_controller_write",
                callback = onResult,
            )
        } else {
            orchestrator.executeCollectionStep(
                androidContext = context,
                webView = webView,
                request = request,
                executionContext = executionContext,
                currentContext = ::context,
                deserializer = serializer<T>(),
                arguments = arguments,
                reason = "seat_controller_read",
                callback = onResult,
            )
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
