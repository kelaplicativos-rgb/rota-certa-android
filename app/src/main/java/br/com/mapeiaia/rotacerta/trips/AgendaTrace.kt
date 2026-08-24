package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.Window
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Process-start hook for the Agenda forensic trace.
 *
 * It is deliberately a ContentProvider so every current and future Activity in
 * br.com.mapeiaia.rotacerta.trips is covered without requiring a developer to
 * remember to install tracing in each screen.
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

/**
 * Global, privacy-safe Agenda trace layered on top of the existing unified
 * debug report. It never consumes input and never changes Agenda behavior.
 *
 * Layer 1: lifecycle of every Activity under the trips package.
 * Layer 2: every ACTION_UP delivered through the Activity Window callback.
 * Layer 3: semantic actions from shared renderers such as ResponsiveTripActions.
 * Layer 4: existing collector/network/passenger/seat events remain authoritative.
 */
internal object AgendaTrace {
    private const val AGENDA_PACKAGE_PREFIX = "br.com.mapeiaia.rotacerta.trips."
    private const val DETAIL_LIMIT = 320
    private val installed = AtomicBoolean(false)
    private val sequence = AtomicLong(0L)
    private val wrappedCallbacks = WeakHashMap<Activity, Window.Callback>()

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return

        safeRecord(
            type = "AGENDA_TRACE_READY",
            packageName = application.packageName,
            details = "scope=trips_package lifecycle=true touch=window_action_up semantic=responsive_actions",
        )

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (!isAgendaActivity(activity)) return
                recordLifecycle(activity, "created")
                wrapWindowCallback(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                if (isAgendaActivity(activity)) recordLifecycle(activity, "started")
            }

            override fun onActivityResumed(activity: Activity) {
                if (!isAgendaActivity(activity)) return
                wrapWindowCallback(activity)
                recordLifecycle(activity, "resumed")
            }

            override fun onActivityPaused(activity: Activity) {
                if (isAgendaActivity(activity)) recordLifecycle(activity, "paused")
            }

            override fun onActivityStopped(activity: Activity) {
                if (isAgendaActivity(activity)) recordLifecycle(activity, "stopped")
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                if (isAgendaActivity(activity)) recordLifecycle(activity, "state_saved")
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (!isAgendaActivity(activity)) return
                recordLifecycle(activity, "destroyed")
                synchronized(wrappedCallbacks) {
                    wrappedCallbacks.remove(activity)
                }
            }
        })
    }

    fun action(context: Context, traceKey: String?, rawLabel: String) {
        val key = traceKey
            ?.let(::safeKey)
            ?.takeIf(String::isNotBlank)
            ?: "label_hash_${hashLabel(rawLabel)}"
        safeRecord(
            type = "AGENDA_ACTION",
            packageName = context.packageName,
            details = "seq=${nextSequence()} source=responsive_actions key=$key",
        )
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
                    val event = args?.firstOrNull() as? MotionEvent
                    if (event?.actionMasked == MotionEvent.ACTION_UP) {
                        recordTouch(activity, event)
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

    private fun recordTouch(activity: Activity, event: MotionEvent) {
        val decor = activity.window.decorView
        val width = decor.width.coerceAtLeast(1)
        val height = decor.height.coerceAtLeast(1)
        val xPercent = ((event.x / width.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        val yPercent = ((event.y / height.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        val xBucket = (xPercent / 5) * 5
        val yBucket = (yPercent / 5) * 5
        safeRecord(
            type = "AGENDA_INTERACTION",
            packageName = activity.packageName,
            details = "seq=${nextSequence()} screen=${screenKey(activity)} kind=touch_up xPct5=$xBucket yPct5=$yBucket pointers=${event.pointerCount.coerceIn(1, 10)}",
        )
    }

    private fun recordLifecycle(activity: Activity, state: String) {
        safeRecord(
            type = "AGENDA_SCREEN",
            packageName = activity.packageName,
            details = "seq=${nextSequence()} screen=${screenKey(activity)} state=$state",
        )
    }

    private fun isAgendaActivity(activity: Activity): Boolean =
        activity.javaClass.name.startsWith(AGENDA_PACKAGE_PREFIX)

    private fun screenKey(activity: Activity): String =
        activity.javaClass.simpleName
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .take(80)
            .ifBlank { "AgendaActivity" }

    private fun safeKey(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_.-]+"), "_")
            .trim('_')
            .take(64)

    private fun hashLabel(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(6).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun nextSequence(): Long = sequence.incrementAndGet()

    private fun safeRecord(type: String, packageName: String, details: String) {
        runCatching {
            UnifiedDebugEventStore.record(type, packageName, details.take(DETAIL_LIMIT))
        }
    }
}
