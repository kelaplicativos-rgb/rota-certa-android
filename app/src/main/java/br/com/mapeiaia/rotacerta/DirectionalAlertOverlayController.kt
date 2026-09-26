package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Painel único e atualizável para radar/alerta, sem recriar a janela a cada GPS.
 *
 * 0.1.647:
 * - cada targetId recebe um único prazo de 20 s contado desde a primeira exibição;
 * - atualizações de GPS/estado não reiniciam esse prazo;
 * - ultrapassar o ponto ou o motor ficar ocioso não fecha antes do prazo;
 * - somente Fechar/Editar/Excluir encerram antecipadamente;
 * - toques fora dos botões são ignorados;
 * - timeout reconhece o alerta e aplica o mesmo dismissUntilExit do botão Fechar.
 */
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
    private var activeDismissAction0647: (() -> Unit)? = null
    private var activeTimeout0647: Runnable? = null
    private var activeTimeoutStartedAtNanos0647: Long? = null
    private var passedLoggedTargetId0647: String? = null

    val isVisible: Boolean
        get() = container != null

    fun showOrUpdate(
        visual: DirectionalAlertVisual,
        actions: DirectionalAlertOverlayActions = DirectionalAlertOverlayActions(),
    ) {
        ensureView()
        if (container == null) return

        val targetChanged0647 = activeTargetId != visual.targetId
        if (targetChanged0647) {
            cancelActiveTimeout0647(reason = "TARGET_REPLACED")
            activeTargetId = visual.targetId
            passedLoggedTargetId0647 = null
        }
        activeDismissAction0647 = actions.onDismiss

        titleView?.text = when (visual.kind) {
            DirectionalAlertKind.ImportedRadar -> "📡 ${visual.title}"
            DirectionalAlertKind.SavedPlace -> "⚠️ ${visual.title}"
        }
        distanceView?.text = formatDistance(visual.distanceMeters)
        statusView?.text = visual.status
        statusView?.setTextColor(
            if (visual.gpsReliable) Color.rgb(146, 227, 169)
            else Color.rgb(255, 214, 102),
        )
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

        if (targetChanged0647 || activeTimeout0647 == null) {
            scheduleActiveTimeout0647(visual.targetId)
        }

        if (visual.shouldClose && passedLoggedTargetId0647 != visual.targetId) {
            passedLoggedTargetId0647 = visual.targetId
            FarolFlightRecorder0163.record(
                stage = "ALERT_OVERLAY_PASSED_PINNED_0647",
                packageName = null,
                details = "target_hash=${visual.targetId.hashCode()}; timeout_not_restarted=true; auto_close_on_pass=false",
            )
        }
    }

    /**
     * O motor pode deixar de produzir visual depois de ultrapassar o ponto ou durante
     * oscilações de GPS. Enquanto o popup estiver visível, o relógio de 20 s é a única
     * autoridade automática de fechamento.
     */
    fun hideFromEngineIdle() {
        if (container != null) {
            FarolFlightRecorder0163.record(
                stage = "ALERT_OVERLAY_ENGINE_IDLE_PINNED_0647",
                packageName = null,
                details = "target_hash=${activeTargetId?.hashCode()}; visible=true; timeout_active=${activeTimeout0647 != null}",
            )
            return
        }
    }

    /**
     * Fechamento explícito externo (recurso desligado, serviço encerrado etc.).
     * Não equivale a reconhecimento humano e por isso não chama dismissUntilExit.
     */
    fun hide() {
        cancelActiveTimeout0647(reason = "EXPLICIT_HIDE")
        removeView0647()
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
            isClickable = true
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    FarolFlightRecorder0163.record(
                        stage = "ALERT_OVERLAY_NON_ACTION_TOUCH_IGNORED_0647",
                        packageName = null,
                        details = "target_hash=${activeTargetId?.hashCode()}; timer_restarted=false; dismissed=false",
                    )
                }
                false
            }
        }
        val newTitle = TextView(context).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
        }.also(root::addView)
        val newDistance = TextView(context).apply {
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }.also(root::addView)
        val newStatus = TextView(context).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(2))
        }.also(root::addView)
        val newDetails = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }.also(root::addView)
        val newActions = LinearLayout(context).apply {
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
            titleView = newTitle
            distanceView = newDistance
            statusView = newStatus
            detailsView = newDetails
            actionsView = newActions
        }
    }

    private fun configureActions(
        visual: DirectionalAlertVisual,
        actions: DirectionalAlertOverlayActions,
    ) {
        val row = actionsView ?: return
        row.removeAllViews()
        row.addView(actionButton("Fechar") {
            dismissByAction0647(action = "CLOSE")
        })
        when (visual.kind) {
            DirectionalAlertKind.SavedPlace -> {
                val savedPlaceId = visual.savedPlaceId ?: return
                actions.onEdit?.let { edit ->
                    row.addView(actionButton("Editar") {
                        dismissByAction0647(action = "EDIT")
                        edit(savedPlaceId)
                    })
                }
                actions.onDelete?.let { delete ->
                    row.addView(actionButton("Excluir") {
                        dismissByAction0647(action = "DELETE")
                        delete(savedPlaceId)
                    })
                }
            }
            DirectionalAlertKind.ImportedRadar -> {
                val radarId = visual.radarId ?: return
                actions.onEditRadar?.let { edit ->
                    row.addView(actionButton("Editar") {
                        dismissByAction0647(action = "EDIT")
                        edit(radarId)
                    })
                }
                actions.onDeleteRadar?.let { delete ->
                    row.addView(actionButton("Excluir") {
                        dismissByAction0647(action = "DELETE")
                        delete(radarId)
                    })
                }
            }
        }
    }

    private fun dismissByAction0647(action: String) {
        val target = activeTargetId
        val acknowledge = activeDismissAction0647
        FarolFlightRecorder0163.record(
            stage = "ALERT_OVERLAY_BUTTON_${action}_0647",
            packageName = null,
            details = "target_hash=${target?.hashCode()}; timeout_cancelled=true; dismiss_until_exit=true",
        )
        cancelActiveTimeout0647(reason = "BUTTON_$action")
        removeView0647()
        runCatching { acknowledge?.invoke() }
    }

    private fun scheduleActiveTimeout0647(targetId: String) {
        cancelActiveTimeout0647(reason = "RESCHEDULE_GUARD")
        val startedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val timeout = Runnable {
            val elapsedMs =
                (android.os.SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L
            if (activeTargetId != targetId || container == null) {
                FarolFlightRecorder0163.record(
                    stage = "ALERT_OVERLAY_TIMEOUT_STALE_CALLBACK_0647",
                    packageName = null,
                    details = "elapsed_ms=$elapsedMs; target_hash=${targetId.hashCode()}; active_hash=${activeTargetId?.hashCode()}",
                )
                return@Runnable
            }

            activeTimeout0647 = null
            activeTimeoutStartedAtNanos0647 = null
            val acknowledge = activeDismissAction0647
            FarolFlightRecorder0163.record(
                stage = "ALERT_OVERLAY_TIMEOUT_DISMISSED_0647",
                packageName = null,
                details = "elapsed_ms=$elapsedMs; expected_ms=$ALERT_TIMEOUT_MILLIS_0647; target_hash=${targetId.hashCode()}; dismiss_until_exit=true",
            )
            if (elapsedMs < ALERT_TIMEOUT_MILLIS_0647 - EARLY_TIMEOUT_TOLERANCE_MILLIS_0647) {
                FarolFlightRecorder0163.record(
                    stage = "FORENSIC_ALERT_POPUP_EARLY_TIMEOUT_0193",
                    packageName = null,
                    details = "elapsed_ms=$elapsedMs; expected_ms=$ALERT_TIMEOUT_MILLIS_0647; target_hash=${targetId.hashCode()}",
                )
            }
            removeView0647()
            runCatching { acknowledge?.invoke() }
        }
        activeTimeout0647 = timeout
        activeTimeoutStartedAtNanos0647 = startedAtNanos
        FarolFlightRecorder0163.record(
            stage = "ALERT_OVERLAY_TIMEOUT_STARTED_0647",
            packageName = null,
            details = "timeout_ms=$ALERT_TIMEOUT_MILLIS_0647; target_hash=${targetId.hashCode()}; starts_once_per_target=true",
        )
        handler.postDelayed(timeout, ALERT_TIMEOUT_MILLIS_0647)
    }

    private fun cancelActiveTimeout0647(reason: String) {
        val timeout = activeTimeout0647 ?: return
        val started = activeTimeoutStartedAtNanos0647
        val elapsedMs = started?.let {
            (android.os.SystemClock.elapsedRealtimeNanos() - it) / 1_000_000L
        } ?: -1L
        handler.removeCallbacks(timeout)
        activeTimeout0647 = null
        activeTimeoutStartedAtNanos0647 = null
        FarolFlightRecorder0163.record(
            stage = "ALERT_OVERLAY_TIMEOUT_CANCELLED_0647",
            packageName = null,
            details = "reason=$reason; elapsed_ms=$elapsedMs; target_hash=${activeTargetId?.hashCode()}",
        )
    }

    private fun removeView0647() {
        val view = container
        if (view != null) {
            runCatching { windowManager.removeView(view) }
        }
        container = null
        titleView = null
        distanceView = null
        statusView = null
        detailsView = null
        actionsView = null
        activeTargetId = null
        activeDismissAction0647 = null
        passedLoggedTargetId0647 = null
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

    private fun formatDistance(distanceMeters: Double): String = when {
        distanceMeters >= 995.0 ->
            String.format(Locale("pt", "BR"), "%.1f km", distanceMeters / 1000.0)
        else -> "${distanceMeters.roundToInt().coerceAtLeast(0)} m"
    }

    private fun headingLabel(source: NavigationHeadingSource): String = when (source) {
        NavigationHeadingSource.GpsAndCompass -> "GPS + bússola"
        NavigationHeadingSource.GpsBearing -> "rumo do GPS"
        NavigationHeadingSource.Movement -> "rumo do deslocamento"
        NavigationHeadingSource.Compass -> "bússola"
        NavigationHeadingSource.Unavailable -> "sem direção"
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val ALERT_TIMEOUT_MILLIS_0647 = 20_000L
        const val EARLY_TIMEOUT_TOLERANCE_MILLIS_0647 = 150L
    }
}

data class DirectionalAlertOverlayActions(
    val onDismiss: () -> Unit = {},
    val onEdit: ((String) -> Unit)? = null,
    val onDelete: ((String) -> Unit)? = null,
    val onEditRadar: ((String) -> Unit)? = null,
    val onDeleteRadar: ((String) -> Unit)? = null,
)
