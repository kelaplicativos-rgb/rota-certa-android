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
    ) {
        if (alert.type != SavedPlaceType.ProximityAlert) {
            trace("proximity.popup.ignored_non_alert id=${alert.id}")
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
        container.addView(TextView(context).apply {
            text = "A aproximadamente ${distanceMeters.roundToInt()} m"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(8))
        })
        container.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Fechar") { hideProximityAlert(); actions.onDismiss() })
            addView(popupButton("Editar") { hideProximityAlert(); actions.onEdit(alert) })
            addView(popupButton("Excluir") { showDeleteConfirmation(alert, actions) })
        })

        val params = WindowManager.LayoutParams(
            dp(310).coerceAtMost(context.resources.displayMetrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }
        if (runCatching { windowManager.addView(container, params) }.isSuccess) {
            alertPopupView = container
            trace("proximity.popup.shown id=${alert.id} distance=${distanceMeters.roundToInt()}")
        }
    }

    fun hideProximityAlert() {
        val view = alertPopupView ?: return
        runCatching { windowManager.removeView(view) }
        alertPopupView = null
        trace("proximity.popup.closed")
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
        val menuWidth = dp(206)
        val estimatedMenuHeight = dp(14 + rows * 66)
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

        val (menuX, menuY) = anchoredPosition(
            anchor = anchor,
            menuWidth = menuWidth,
            menuHeight = estimatedMenuHeight,
        )
        val params = WindowManager.LayoutParams(
            menuWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = menuX
            y = menuY
        }
        if (runCatching { windowManager.addView(menu, params) }.isSuccess) {
            shortcutView = menu
            trace(
                "bubble.shortcuts.opened count=${BubbleShortcutCatalog.modules.size} " +
                    "anchor=${anchor.x},${anchor.y},${anchor.width},${anchor.height} menu=$menuX,$menuY,$menuWidth,$estimatedMenuHeight",
            )
        }
    }

    /**
     * Mantem a bolinha principal sempre visivel e com um pequeno espaco entre
     * ela e a grade. Primeiro tenta direita/esquerda; em telas estreitas usa
     * abaixo/acima, sem qualquer interseccao entre os dois retangulos.
     */
    private fun anchoredPosition(
        anchor: WindowManager.LayoutParams,
        menuWidth: Int,
        menuHeight: Int,
    ): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val safe = dp(4)
        val gap = dp(8)
        val anchorWidth = anchor.width.takeIf { it > 0 && it < metrics.widthPixels } ?: dp(66)
        val anchorHeight = anchor.height.takeIf { it > 0 && it < metrics.heightPixels } ?: dp(66)
        val rightX = anchor.x + anchorWidth + gap
        val leftX = anchor.x - menuWidth - gap
        val maxX = (metrics.widthPixels - menuWidth - safe).coerceAtLeast(safe)
        val maxY = (metrics.heightPixels - menuHeight - safe).coerceAtLeast(safe)
        val alignedY = anchor.y.coerceIn(safe, maxY)

        return when {
            rightX + menuWidth <= metrics.widthPixels - safe -> rightX to alignedY
            leftX >= safe -> leftX to alignedY
            anchor.y + anchorHeight + gap + menuHeight <= metrics.heightPixels - safe ->
                anchor.x.coerceIn(safe, maxX) to (anchor.y + anchorHeight + gap)
            else ->
                anchor.x.coerceIn(safe, maxX) to (anchor.y - menuHeight - gap).coerceIn(safe, maxY)
        }
    }

    private fun shortcutBubble(spec: BubbleShortcutSpec, action: () -> Unit): TextView = TextView(context).apply {
        text = spec.displayText
        textSize = 10f
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
            width = dp(62)
            height = dp(62)
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
