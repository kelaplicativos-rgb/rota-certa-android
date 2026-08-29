package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.Window
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Process-start hook for the Agenda forensic trace.
 *
 * The hook remains a ContentProvider so Agenda tracing is installed before a
 * Trips Activity is opened. It is bounded, fail-open and does not create disk,
 * network, OCR, screenshot or polling work.
 */
class AgendaTraceProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        AgendaTrace.install(application)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

internal data class AgendaOperationToken(
    val operation: String,
    val traceId: String,
    val operationId: String,
    val parentOperationId: String?,
    val origin: String,
    val startedNs: Long,
    val startedWallMs: Long,
)

internal object AgendaTrace {
    private const val AGENDA_PACKAGE_PREFIX = "br.com.mapeiaia.rotacerta.trips."
    private const val DETAIL_LIMIT = 760
    private const val EXTRA_TRACE_ID = "br.com.mapeiaia.rotacerta.extra.AGENDA_TRACE_ID"
    private const val EXTRA_TRACE_START_NS = "br.com.mapeiaia.rotacerta.extra.AGENDA_TRACE_START_NS"
    private const val EXTRA_TRACE_START_WALL = "br.com.mapeiaia.rotacerta.extra.AGENDA_TRACE_START_WALL"

    private val installed = AtomicBoolean(false)
    private val sequence = AtomicLong(0L)
    private val operationSequence = AtomicLong(0L)
    private val wrappedCallbacks = WeakHashMap<Activity, Window.Callback>()
    private val frameWatches = WeakHashMap<Activity, AgendaFrameWatch>()
    private val visualStates = WeakHashMap<Activity, AgendaVisualState>()
    private val traceStartNs = ConcurrentHashMap<String, Long>()
    private val traceStartWall = ConcurrentHashMap<String, Long>()
    private val activeOperations = ConcurrentHashMap<String, AgendaOperationToken>()

    @Volatile private var currentTraceId: String = ""

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return

        safeRecord(
            type = "AGENDA_TRACE_READY",
            packageName = application.packageName,
            details = "scope=trips_package lifecycle=true touch=window_action_up semantic=true jank=choreographer",
        )

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (!isAgendaActivity(activity)) return
                recordLifecycle(
                    activity = activity,
                    state = "created",
                    extra = "savedInstanceStatePresent=${savedInstanceState != null} launchAction=${safeKey(activity.intent?.action.orEmpty()).ifBlank { "none" }}",
                )
                wrapWindowCallback(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                if (isAgendaActivity(activity)) recordLifecycle(activity, "started")
            }

            override fun onActivityResumed(activity: Activity) {
                if (!isAgendaActivity(activity)) return
                wrapWindowCallback(activity)
                recordLifecycle(activity, "resumed")
                startFrameWatch(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (!isAgendaActivity(activity)) return
                stopFrameWatch(activity)
                recordLifecycle(activity, "paused")
            }

            override fun onActivityStopped(activity: Activity) {
                if (isAgendaActivity(activity)) recordLifecycle(activity, "stopped")
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                if (isAgendaActivity(activity)) recordLifecycle(activity, "state_saved")
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (!isAgendaActivity(activity)) return
                stopFrameWatch(activity)
                recordLifecycle(activity, "destroyed")
                synchronized(wrappedCallbacks) { wrappedCallbacks.remove(activity) }
                synchronized(visualStates) { visualStates.remove(activity) }
            }
        })
    }

    fun beginAgendaOpen(context: Context, source: String): String {
        val trace = newTraceId()
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val nowWall = System.currentTimeMillis()
        currentTraceId = trace
        traceStartNs[trace] = nowNs
        traceStartWall[trace] = nowWall
        event(context, "USER_OPEN_AGENDA", "source=${safeKey(source)}", trace, null, nowWall, nowNs)
        event(context, "AGENDA_OPEN_REQUESTED", "source=${safeKey(source)}", trace, null, nowWall, nowNs)
        event(context, "AGENDA_SHORTCUT_DISPATCH", "source=${safeKey(source)}", trace)
        event(context, "MAIN_ACTIVITY_RECEIVED_AGENDA_REQUEST", "source=${safeKey(source)}", trace)
        return trace
    }

    fun attachTrace(intent: Intent, traceId: String): Intent {
        val startNs = traceStartNs[traceId] ?: SystemClock.elapsedRealtimeNanos()
        val startWall = traceStartWall[traceId] ?: System.currentTimeMillis()
        traceStartNs.putIfAbsent(traceId, startNs)
        traceStartWall.putIfAbsent(traceId, startWall)
        return intent
            .putExtra(EXTRA_TRACE_ID, traceId)
            .putExtra(EXTRA_TRACE_START_NS, startNs)
            .putExtra(EXTRA_TRACE_START_WALL, startWall)
    }

    fun adoptTrace(intent: Intent?): String {
        val supplied = intent?.getStringExtra(EXTRA_TRACE_ID)
            ?.let(::safeKey)
            ?.takeIf(String::isNotBlank)
        val trace = supplied ?: newTraceId()
        currentTraceId = trace
        val startNs = intent?.getLongExtra(EXTRA_TRACE_START_NS, 0L)?.takeIf { it > 0L }
            ?: traceStartNs[trace]
            ?: SystemClock.elapsedRealtimeNanos()
        val startWall = intent?.getLongExtra(EXTRA_TRACE_START_WALL, 0L)?.takeIf { it > 0L }
            ?: traceStartWall[trace]
            ?: System.currentTimeMillis()
        traceStartNs[trace] = startNs
        traceStartWall[trace] = startWall
        return trace
    }

    fun openStartNs(intent: Intent?, traceId: String): Long =
        intent?.getLongExtra(EXTRA_TRACE_START_NS, 0L)?.takeIf { it > 0L }
            ?: traceStartNs[traceId]
            ?: SystemClock.elapsedRealtimeNanos()

    fun currentTraceId(): String = currentTraceId.takeIf(String::isNotBlank) ?: "trace_unavailable"

    fun event(
        context: Context,
        stage: String,
        details: String = "",
        traceId: String = currentTraceId(),
        operationId: String? = null,
        wallMs: Long = System.currentTimeMillis(),
        monotonicNs: Long = SystemClock.elapsedRealtimeNanos(),
    ) {
        val causal = buildString {
            append("traceId=").append(safeKey(traceId).ifBlank { "trace_unavailable" })
            operationId?.takeIf(String::isNotBlank)?.let { append(" operationId=").append(safeKey(it)) }
            if (details.isNotBlank()) append(' ').append(details)
        }
        safeRecord(
            type = safeStage(stage),
            packageName = context.packageName,
            details = causal,
            wallMs = wallMs,
            monotonicNs = monotonicNs,
        )
    }

    fun action(context: Context, traceKey: String?, rawLabel: String) {
        val key = traceKey
            ?.let(::safeKey)
            ?.takeIf(String::isNotBlank)
            ?: "label_hash_${hashLabel(rawLabel)}"
        val trace = currentTraceId()
        event(
            context,
            "AGENDA_ACTION",
            "seq=${nextSequence()} source=responsive_actions key=$key",
            trace,
        )
        event(
            context,
            semanticEvent(key, rawLabel),
            "source=semantic_control key=$key",
            trace,
        )
    }

    fun operationStart(
        context: Context,
        operation: String,
        origin: String,
        traceId: String = currentTraceId(),
        parentOperationId: String? = null,
    ): AgendaOperationToken {
        val safeOperation = safeStage(operation).removeSuffix("_START").removeSuffix("_END")
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val startedWall = System.currentTimeMillis()
        val operationId = "op-${operationSequence.incrementAndGet().toString(36)}-${startedNs.toString(36).takeLast(8)}"
        val token = AgendaOperationToken(
            operation = safeOperation,
            traceId = safeKey(traceId).ifBlank { "trace_unavailable" },
            operationId = operationId,
            parentOperationId = parentOperationId?.let(::safeKey)?.takeIf(String::isNotBlank),
            origin = safeKey(origin).ifBlank { "unknown" },
            startedNs = startedNs,
            startedWallMs = startedWall,
        )
        activeOperations[token.operationId] = token
        val details = operationDetails(token)
        event(context, "OPERATION_START", details, token.traceId, token.operationId, startedWall, startedNs)
        event(context, "${token.operation}_START", details, token.traceId, token.operationId, startedWall, startedNs)
        return token
    }

    fun operationEnd(
        context: Context,
        token: AgendaOperationToken,
        result: String = "ok",
        processedCount: Int? = null,
    ) = terminal(context, token, "END", result, processedCount, null)

    fun operationError(
        context: Context,
        token: AgendaOperationToken,
        error: Throwable,
        processedCount: Int? = null,
    ) = terminal(context, token, "ERROR", "error", processedCount, error.javaClass.simpleName)

    fun operationCancelled(
        context: Context,
        token: AgendaOperationToken,
        result: String = "cancelled",
        processedCount: Int? = null,
    ) = terminal(context, token, "CANCELLED", result, processedCount, null)

    fun activeOperationSummary(): String {
        val active = activeOperations.values.maxByOrNull(AgendaOperationToken::startedNs)
            ?: return "traceId=${currentTraceId()} operationId=none stage=none"
        return "traceId=${safeKey(active.traceId)} operationId=${safeKey(active.operationId)} stage=${safeStage(active.operation)}"
    }

    fun installFirstRenderObservers(activity: Activity, traceId: String, openStartedNs: Long) {
        synchronized(visualStates) {
            visualStates[activity] = AgendaVisualState(
                traceId = traceId,
                firstFrameSeen = false,
                contentMounted = false,
                loadingIntentional = false,
                emptyStartedNs = 0L,
            )
        }

        val decor = activity.window.decorView
        val observer = decor.viewTreeObserver
        val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnGlobalLayoutListener(this)
                event(activity, "AGENDA_FIRST_LAYOUT", "screen=${screenKey(activity)}", traceId)
            }
        }
        observer.addOnGlobalLayoutListener(layoutListener)

        Choreographer.getInstance().postFrameCallback {
            event(activity, "AGENDA_FIRST_FRAME_DRAWN", "screen=${screenKey(activity)}", traceId)
            val state = synchronized(visualStates) {
                visualStates[activity]?.also { it.firstFrameSeen = true }
            }
            if (state != null && !state.contentMounted && !state.loadingIntentional) {
                state.emptyStartedNs = SystemClock.elapsedRealtimeNanos()
                event(
                    activity,
                    "AGENDA_EMPTY_VISUAL_STATE",
                    "screen=${screenKey(activity)} loading=false",
                    traceId,
                )
            }
            decor.post {
                val totalMs = ((SystemClock.elapsedRealtimeNanos() - openStartedNs).coerceAtLeast(0L)) / 1_000_000L
                event(
                    activity,
                    "AGENDA_FIRST_INTERACTIVE_FRAME",
                    "screen=${screenKey(activity)} totalMs=$totalMs",
                    traceId,
                )
                event(
                    activity,
                    "AGENDA_OPEN_TOTAL_MS",
                    "value=$totalMs unit=ms",
                    traceId,
                )
            }
        }
    }

    fun markContentMounted(context: Activity, loading: Boolean = false) {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val state = synchronized(visualStates) {
            visualStates[context]?.also {
                it.contentMounted = true
                it.loadingIntentional = loading
            }
        } ?: return
        val emptyStarted = state.emptyStartedNs
        if (emptyStarted > 0L) {
            val durationMs = ((nowNs - emptyStarted).coerceAtLeast(0L)) / 1_000_000L
            event(
                context,
                "AGENDA_EMPTY_VISUAL_STATE_END",
                "durationMs=$durationMs loading=$loading",
                state.traceId,
            )
            if (durationMs >= 500L) {
                event(
                    context,
                    "AGENDA_EMPTY_VISUAL_STATE_LONG",
                    "durationMs=$durationMs loading=$loading",
                    state.traceId,
                )
            }
            state.emptyStartedNs = 0L
        }
    }

    private fun terminal(
        context: Context,
        token: AgendaOperationToken,
        terminal: String,
        result: String,
        processedCount: Int?,
        exceptionClass: String?,
    ) {
        val endNs = SystemClock.elapsedRealtimeNanos()
        val durationMs = ((endNs - token.startedNs).coerceAtLeast(0L)) / 1_000_000L
        val details = buildString {
            append(operationDetails(token))
            append(" durationMs=").append(durationMs)
            append(" result=").append(safeKey(result).ifBlank { "unknown" })
            processedCount?.let { append(" processed=").append(it.coerceAtLeast(0)) }
            exceptionClass?.let { append(" exceptionClass=").append(safeKey(it)) }
        }
        activeOperations.remove(token.operationId)
        event(context, "OPERATION_$terminal", details, token.traceId, token.operationId)
        event(context, "${token.operation}_$terminal", details, token.traceId, token.operationId)
        if (durationMs >= 1_000L) {
            event(
                context,
                "SLOW_OPERATION",
                "operation=${token.operation} durationMs=$durationMs",
                token.traceId,
                token.operationId,
            )
        }
    }

    private fun operationDetails(token: AgendaOperationToken): String = buildString {
        append("operation=").append(token.operation)
        append(" origin=").append(token.origin)
        token.parentOperationId?.let { append(" parentOperationId=").append(it) }
    }

    private fun startFrameWatch(activity: Activity) {
        synchronized(frameWatches) {
            val existing = frameWatches[activity]
            if (existing != null) {
                existing.start()
                return
            }
            AgendaFrameWatch(activity).also {
                frameWatches[activity] = it
                it.start()
            }
        }
    }

    private fun stopFrameWatch(activity: Activity) {
        synchronized(frameWatches) {
            frameWatches[activity]?.stop()
        }
    }

    private fun wrapWindowCallback(activity: Activity) {
        val window = activity.window
        val current = window.callback ?: return
        synchronized(wrappedCallbacks) {
            if (wrappedCallbacks[activity] === current) return

            val proxy = Proxy.newProxyInstance(
                activity.javaClass.classLoader,
                arrayOf(Window.Callback::class.java),
            ) { _, method, args ->
                if (method.name == "dispatchTouchEvent") {
                    val motion = args?.firstOrNull() as? MotionEvent
                    if (motion?.actionMasked == MotionEvent.ACTION_UP) {
                        recordTouch(activity, motion)
                    }
                }

                try {
                    if (args == null) method.invoke(current) else method.invoke(current, *args)
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            } as Window.Callback

            window.callback = proxy
            wrappedCallbacks[activity] = proxy
        }
    }

    private fun recordTouch(activity: Activity, motion: MotionEvent) {
        val decor = activity.window.decorView
        val width = decor.width.coerceAtLeast(1)
        val height = decor.height.coerceAtLeast(1)
        val xPercent = ((motion.x / width.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        val yPercent = ((motion.y / height.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        val xBucket = (xPercent / 5) * 5
        val yBucket = (yPercent / 5) * 5
        event(
            activity,
            "AGENDA_INTERACTION",
            "seq=${nextSequence()} screen=${screenKey(activity)} kind=touch_up xPct5=$xBucket yPct5=$yBucket pointers=${motion.pointerCount.coerceIn(1, 10)}",
        )
    }

    private fun recordLifecycle(activity: Activity, state: String, extra: String = "") {
        event(
            activity,
            "AGENDA_SCREEN",
            buildString {
                append("seq=").append(nextSequence())
                append(" screen=").append(screenKey(activity))
                append(" state=").append(safeKey(state))
                if (extra.isNotBlank()) append(' ').append(extra)
            },
        )
    }

    private fun semanticEvent(key: String, rawLabel: String): String {
        val probe = "${safeKey(key)}_${safeKey(rawLabel)}"
        return when {
            "capacidade" in probe || "capacity" in probe -> "USER_OPEN_CAPACITY"
            "passage" in probe -> "USER_OPEN_PASSENGERS"
            ("sync" in probe || "sincron" in probe) && ("all" in probe || "todas" in probe || "tudo" in probe) -> "USER_SYNC_ALL"
            ("sync" in probe || "sincron" in probe) && "hoje" in probe -> "USER_SYNC_TODAY"
            "limpar" in probe || "clear" in probe -> "USER_CLEAR_TIMELINE"
            "public_search" in probe || "consulta_publica" in probe || "pesquisa" in probe || "search" in probe -> "USER_SEARCH"
            "config" in probe || "settings" in probe -> "USER_OPEN_SETTINGS"
            "nova_viagem" in probe || "create_trip" in probe || "new_trip" in probe -> "USER_OPEN_TRIP"
            "date" in probe || "data" in probe || "calendar" in probe -> "USER_OPEN_DATE_PICKER"
            "voltar" in probe || "back" in probe -> "USER_BACK"
            else -> "USER_ACTION"
        }
    }

    private fun isAgendaActivity(activity: Activity): Boolean =
        activity.javaClass.name.startsWith(AGENDA_PACKAGE_PREFIX)

    private fun screenKey(activity: Activity): String =
        activity.javaClass.simpleName
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .take(80)
            .ifBlank { "AgendaActivity" }

    private fun safeStage(raw: String): String =
        raw.uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9_]+"), "_")
            .trim('_')
            .take(140)
            .ifBlank { "AGENDA_EVENT" }

    private fun safeKey(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_.-]+"), "_")
            .trim('_')
            .take(96)

    private fun hashLabel(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(6).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun newTraceId(): String {
        val seq = sequence.incrementAndGet().toString(36)
        val clock = SystemClock.elapsedRealtimeNanos().toString(36).takeLast(10)
        return "ag-$clock-$seq"
    }

    private fun nextSequence(): Long = sequence.incrementAndGet()

    private fun safeRecord(
        type: String,
        packageName: String,
        details: String,
        wallMs: Long = System.currentTimeMillis(),
        monotonicNs: Long = SystemClock.elapsedRealtimeNanos(),
    ) {
        runCatching {
            UnifiedDebugEventStore.recordAlways(
                stage = type,
                packageName = packageName,
                details = details.take(DETAIL_LIMIT),
                nowMillis = wallMs,
                monotonicNs = monotonicNs,
            )
        }
    }

    private class AgendaFrameWatch(private val activity: Activity) : Choreographer.FrameCallback {
        private var running = false
        private var lastFrameNs = 0L
        private var slowCount = 0L

        fun start() {
            if (running) return
            running = true
            lastFrameNs = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun stop() {
            running = false
            lastFrameNs = 0L
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrameNs > 0L) {
                val durationMs = ((frameTimeNanos - lastFrameNs).coerceAtLeast(0L)) / 1_000_000L
                if (durationMs >= 100L) {
                    slowCount++
                    val stage = when {
                        durationMs >= 1_000L -> "AGENDA_JANK_FREEZE"
                        durationMs >= 500L -> "AGENDA_JANK_FRAME_500MS"
                        durationMs >= 250L -> "AGENDA_JANK_FRAME_250MS"
                        else -> "AGENDA_JANK_FRAME_100MS"
                    }
                    val active = activeOperations.values.maxByOrNull(AgendaOperationToken::startedNs)
                    event(
                        activity,
                        stage,
                        "durationMs=$durationMs activity=${screenKey(activity)} mainThread=true activeOperation=${active?.operation ?: "none"} accumulated=$slowCount",
                        active?.traceId ?: currentTraceId(),
                        active?.operationId,
                    )
                }
            }
            lastFrameNs = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private data class AgendaVisualState(
        val traceId: String,
        var firstFrameSeen: Boolean,
        var contentMounted: Boolean,
        var loadingIntentional: Boolean,
        var emptyStartedNs: Long,
    )
}
