package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Janelas leves usadas pela bolinha principal.
 *
 * O controlador nao conhece regras de negocio. Cada recurso da grade possui um
 * BubbleShortcutModule proprio; aqui apenas renderizamos o catalogo e devolvemos
 * o modulo clicado ao servico.
 */
class BubbleShortcutOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val trace: (String) -> Unit = {},
) {
    private var shortcutView: View? = null
    private var alertPopupView: View? = null
    private var activePopupId: String? = null
    private var alertDistanceView: TextView? = null
    private var alertProgressView: ProgressBar? = null

    val shortcutsVisible: Boolean
        get() = shortcutView != null

    fun toggleShortcuts(
        anchor: WindowManager.LayoutParams,
        onShortcut: (BubbleShortcutSpec) -> Unit,
    ) {
        if (shortcutView != null) hideShortcuts() else showShortcuts(anchor, onShortcut)
    }

    fun hideShortcuts() {
        val view = shortcutView ?: return
        runCatching { windowManager.removeView(view) }
        shortcutView = null
        trace("bubble.shortcuts.closed")
    }

    fun showProximityAlert(
        alert: SavedPlace,
        distanceMeters: Double,
        actions: ProximityAlertPopupActions,
    ) = showOrUpdateProximityAlert(
        alert = alert,
        distanceMeters = distanceMeters,
        firstAlertDistanceMeters = alert.alertDistanceMeters ?: 500,
        actions = actions,
    )

    fun showOrUpdateProximityAlert(
        alert: SavedPlace,
        distanceMeters: Double,
        firstAlertDistanceMeters: Int,
        actions: ProximityAlertPopupActions,
    ) {
        if (alert.type != SavedPlaceType.ProximityAlert) return
        val popupId = "saved:${alert.id}"
        if (activePopupId == popupId && alertPopupView != null) {
            updateDistance(distanceMeters, firstAlertDistanceMeters)
            return
        }
        hideShortcuts()
        hideProximityAlert()
        val container = alertContainer()
        container.addView(TextView(context).apply {
            text = "⚠️  ${alert.name}"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = "Alerta ${alert.name}"
        })
        alertDistanceView = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(4))
        }.also(container::addView)
        alertProgressView = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = firstAlertDistanceMeters.coerceAtLeast(1)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
            setPadding(0, 0, 0, dp(4))
        }.also(container::addView)
        container.addView(TextView(context).apply {
            text = "O popup fecha automaticamente depois que voce passar pelo ponto."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(8))
        })
        container.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Fechar") { hideProximityAlert(); actions.onDismiss() })
            addView(popupButton("Editar") { hideProximityAlert(); actions.onEdit(alert) })
            addView(popupButton("Excluir") { showDeleteConfirmation(alert, actions) })
        })
        val params = popupLayoutParams()
        if (runCatching { windowManager.addView(container, params) }.isSuccess) {
            alertPopupView = container
            activePopupId = popupId
            updateDistance(distanceMeters, firstAlertDistanceMeters)
            trace("proximity.popup.shown id=${alert.id} distance=${distanceMeters.roundToInt()}")
        }
    }

    fun showImportedRadarAlert(
        radar: ImportedRadar,
        distanceMeters: Double,
        firstAlertDistanceMeters: Int = 500,
        onDismiss: () -> Unit = {},
    ) {
        val popupId = "radar:${radar.id}"
        if (activePopupId == popupId && alertPopupView != null) {
            updateDistance(distanceMeters, firstAlertDistanceMeters)
            return
        }
        hideShortcuts()
        hideProximityAlert()
        val container = alertContainer()
        container.addView(TextView(context).apply {
            text = "⚠️  ${importedRadarTypeLabel(radar.type)}"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        radar.speedKmh?.let { speed ->
            container.addView(TextView(context).apply {
                text = "Limite $speed km/h"
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
        }
        alertDistanceView = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(4))
        }.also(container::addView)
        alertProgressView = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = firstAlertDistanceMeters.coerceAtLeast(1)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
        }.also(container::addView)
        container.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Fechar") { hideProximityAlert(); onDismiss() })
        })
        if (runCatching { windowManager.addView(container, popupLayoutParams()) }.isSuccess) {
            alertPopupView = container
            activePopupId = popupId
            updateDistance(distanceMeters, firstAlertDistanceMeters)
            trace("imported_radar.popup.shown id=${radar.id} distance=${distanceMeters.roundToInt()}")
        }
    }

    fun hideProximityAlert(expectedId: String? = null) {
        if (expectedId != null && activePopupId != "saved:$expectedId" && activePopupId != "radar:$expectedId") return
        val view = alertPopupView ?: return
        runCatching { windowManager.removeView(view) }
        alertPopupView = null
        activePopupId = null
        alertDistanceView = null
        alertProgressView = null
        trace("proximity.popup.closed")
    }

    private fun updateDistance(distanceMeters: Double, firstAlertDistanceMeters: Int) {
        val rounded = distanceMeters.roundToInt().coerceAtLeast(0)
        alertDistanceView?.text = if (rounded <= 5) "Agora" else "$rounded m"
        alertProgressView?.let { progress ->
            progress.max = firstAlertDistanceMeters.coerceAtLeast(1)
            progress.progress = (firstAlertDistanceMeters - rounded).coerceIn(0, progress.max)
        }
    }

    private fun popupLayoutParams() = WindowManager.LayoutParams(
        dp(310).coerceAtMost(context.resources.displayMetrics.widthPixels - dp(24)),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = dp(72)
    }

    fun hideAll() {
        hideShortcuts()
        hideProximityAlert()
    }

    private fun showShortcuts(
        anchor: WindowManager.LayoutParams,
        onShortcut: (BubbleShortcutSpec) -> Unit,
    ) {
        hideProximityAlert()
        BubbleShortcutCatalog.requireValid()

        val columns = 3
        val rows = (BubbleShortcutCatalog.modules.size + columns - 1) / columns
        val menuWidth = dp(194)
        val estimatedMenuHeight = dp(14 + rows * 58)
        val menu = GridLayout(context).apply {
            columnCount = columns
            rowCount = rows
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(238, 25, 25, 25))
                setStroke(dp(1), Color.argb(220, 255, 255, 255))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            BubbleShortcutCatalog.modules.forEach { module ->
                addView(shortcutBubble(module.spec) {
                    hideShortcuts()
                    trace("bubble.shortcut.clicked id=${module.spec.id}")
                    onShortcut(module.spec)
                })
            }
        }

        val metrics = context.resources.displayMetrics
        val anchorWidth = anchor.width.takeIf { it > 0 && it < metrics.widthPixels } ?: dp(66)
        val anchorHeight = anchor.height.takeIf { it > 0 && it < metrics.heightPixels } ?: dp(66)
        val position = BubbleShortcutPositionPolicy.place(
            anchorX = anchor.x,
            anchorY = anchor.y,
            anchorWidth = anchorWidth,
            anchorHeight = anchorHeight,
            menuWidth = menuWidth,
            menuHeight = estimatedMenuHeight,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            gap = dp(8),
            safeMargin = dp(4),
        )
        val params = WindowManager.LayoutParams(
            menuWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
        if (runCatching { windowManager.addView(menu, params) }.isSuccess) {
            shortcutView = menu
            trace(
                "bubble.shortcuts.opened count=${BubbleShortcutCatalog.modules.size} " +
                    "anchor=${anchor.x},${anchor.y},$anchorWidth,$anchorHeight " +
                    "menu=${position.x},${position.y},$menuWidth,$estimatedMenuHeight",
            )
        }
    }

    private fun shortcutBubble(spec: BubbleShortcutSpec, action: () -> Unit): TextView = TextView(context).apply {
        text = spec.displayText
        textSize = 9.5f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 2
        contentDescription = spec.label
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(72, 64, 82))
            setStroke(dp(2), Color.argb(230, 205, 180, 255))
        }
        layoutParams = GridLayout.LayoutParams().apply {
            width = dp(54)
            height = dp(54)
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        setOnClickListener { action() }
    }

    private fun showDeleteConfirmation(alert: SavedPlace, actions: ProximityAlertPopupActions) {
        val parent = alertPopupView as? LinearLayout ?: return
        parent.removeAllViews()
        parent.addView(TextView(context).apply {
            text = "⚠️ Excluir \"${alert.name}\"?"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        })
        parent.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Cancelar") { hideProximityAlert() })
            addView(popupButton("Confirmar") { hideProximityAlert(); actions.onDelete(alert) })
        })
    }

    private fun alertContainer(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.argb(246, 38, 38, 38))
            setStroke(dp(3), Color.rgb(255, 193, 7))
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun popupButton(label: String, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        contentDescription = label
        setPadding(dp(8), dp(9), dp(8), dp(9))
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.rgb(79, 68, 88))
        }
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}

data class ProximityAlertPopupActions(
    val onEdit: (SavedPlace) -> Unit,
    val onDelete: (SavedPlace) -> Unit,
    val onDismiss: () -> Unit = {},
)

// popup_only_shortcut_grid_0_1_119

// popup_close_after_tap_0_1_120
