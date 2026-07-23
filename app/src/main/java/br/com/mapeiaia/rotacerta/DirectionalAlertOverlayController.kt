package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/** Painel único e atualizável para radar/alerta, sem recriar a janela a cada GPS. */
class DirectionalAlertOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var container: LinearLayout? = null
    private var titleView: TextView? = null
    private var distanceView: TextView? = null
    private var statusView: TextView? = null
    private var detailsView: TextView? = null
    private var actionsView: LinearLayout? = null
    private var activeTargetId: String? = null
    private var pendingClose: Runnable? = null

    val isVisible: Boolean
        get() = container != null

    fun showOrUpdate(
        visual: DirectionalAlertVisual,
        actions: DirectionalAlertOverlayActions = DirectionalAlertOverlayActions(),
    ) {
        cancelPendingClose()
        ensureView()
        activeTargetId = visual.targetId

        titleView?.text = when (visual.kind) {
            DirectionalAlertKind.ImportedRadar -> "📡 ${visual.title}"
            DirectionalAlertKind.SavedPlace -> "⚠️ ${visual.title}"
        }
        distanceView?.text = formatDistance(visual.distanceMeters)
        statusView?.text = visual.status
        statusView?.setTextColor(if (visual.gpsReliable) Color.rgb(146, 227, 169) else Color.rgb(255, 214, 102))
        detailsView?.text = buildString {
            append("GPS ±")
            append(visual.accuracyMeters.roundToInt())
            append(" m")
            append("  •  ")
            append(visual.speedKilometersPerHour.roundToInt())
            append(" km/h")
            append("  •  ")
            append(headingLabel(visual.headingSource))
            visual.speedLimitKmh?.let { limit ->
                append("\nLimite do radar: ")
                append(limit)
                append(" km/h")
            }
        }

        configureActions(visual, actions)

        if (visual.shouldClose) {
            val closeTarget = visual.targetId
            val close = Runnable {
                if (activeTargetId == closeTarget) hide()
            }
            pendingClose = close
            handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)
        }
    }

    fun hide() {
        cancelPendingClose()
        val view = container ?: return
        runCatching { windowManager.removeView(view) }
        container = null
        titleView = null
        distanceView = null
        statusView = null
        detailsView = null
        actionsView = null
        activeTargetId = null
    }

    private fun ensureView() {
        if (container != null) return
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(247, 25, 28, 32))
                setStroke(dp(3), Color.rgb(255, 193, 7))
            }
        }
        titleView = TextView(context).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
        }.also(root::addView)
        distanceView = TextView(context).apply {
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }.also(root::addView)
        statusView = TextView(context).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(2))
        }.also(root::addView)
        detailsView = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }.also(root::addView)
        actionsView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }.also(root::addView)

        val params = WindowManager.LayoutParams(
            dp(330).coerceAtMost(context.resources.displayMetrics.widthPixels - dp(20)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(68)
        }
        if (runCatching { windowManager.addView(root, params) }.isSuccess) {
            container = root
        }
    }

    private fun configureActions(
        visual: DirectionalAlertVisual,
        actions: DirectionalAlertOverlayActions,
    ) {
        val row = actionsView ?: return
        row.removeAllViews()
        row.addView(actionButton("Fechar") {
            hide()
            actions.onDismiss()
        })
        if (visual.kind == DirectionalAlertKind.SavedPlace && visual.savedPlaceId != null) {
            if (actions.onEdit != null) {
                row.addView(actionButton("Editar") { actions.onEdit.invoke(visual.savedPlaceId) })
            }
            if (actions.onDelete != null) {
                row.addView(actionButton("Excluir") {
                    hide()
                    actions.onDelete.invoke(visual.savedPlaceId)
                })
            }
        }
    }

    private fun actionButton(label: String, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            setColor(Color.rgb(73, 75, 82))
        }
        setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }
        setOnClickListener { action() }
    }

    private fun cancelPendingClose() {
        pendingClose?.let(handler::removeCallbacks)
        pendingClose = null
    }

    private fun formatDistance(distanceMeters: Double): String = when {
        distanceMeters >= 995.0 -> String.format(Locale("pt", "BR"), "%.1f km", distanceMeters / 1000.0)
        else -> "${distanceMeters.roundToInt().coerceAtLeast(0)} m"
    }

    private fun headingLabel(source: NavigationHeadingSource): String = when (source) {
        NavigationHeadingSource.GpsAndCompass -> "GPS + bússola"
        NavigationHeadingSource.GpsBearing -> "rumo do GPS"
        NavigationHeadingSource.Movement -> "rumo do deslocamento"
        NavigationHeadingSource.Compass -> "bússola"
        NavigationHeadingSource.Unavailable -> "sem direção"
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PASSED_CLOSE_DELAY_MILLIS = 750L
    }
}

data class DirectionalAlertOverlayActions(
    val onDismiss: () -> Unit = {},
    val onEdit: ((String) -> Unit)? = null,
    val onDelete: ((String) -> Unit)? = null,
)
