package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Janelas leves usadas pela bolinha principal.
 *
 * O tamanho é lido somente quando a janela abre, portanto mudar a preferência não
 * interfere na leitura dos cards nem no caminho crítico do farol.
 */
class BubbleShortcutOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val trace: (String) -> Unit = {},
) {
    private val appearanceStore = PopupAppearanceStore(context)
    private var shortcutView: View? = null
    private var shortcutBackdropView0186: View? = null
    private var alertPopupView: View? = null
    private var silentStatusView: View? = null
    private var silentStatusDismiss: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shortcutGestureCancelers0186 = linkedSetOf<() -> Unit>()

    val shortcutsVisible: Boolean
        get() = shortcutView != null

    fun toggleShortcuts(
        anchor: WindowManager.LayoutParams,
        shortcuts: List<ResolvedShortcutGridEntry0179>,
        onShortcut: (ResolvedShortcutGridEntry0179) -> Unit,
        onShortcutLongPress: (ResolvedShortcutGridEntry0179) -> Unit,
    ) {
        if (shortcutView != null) {
            hideShortcuts()
        } else {
            showShortcuts(
                anchor,
                shortcuts,
                onShortcut,
                onShortcutLongPress,
            )
        }
    }

    fun hideShortcuts() {
        cancelShortcutGestures0186()
        val menu = shortcutView
        val backdrop = shortcutBackdropView0186
        shortcutView = null
        shortcutBackdropView0186 = null
        menu?.let { runCatching { windowManager.removeView(it) } }
        backdrop?.let { runCatching { windowManager.removeView(it) } }
        if (menu != null || backdrop != null) trace("bubble.shortcuts.closed")
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

        val scale = appearanceStore.scale()
        val container = alertContainer(scale)
        container.addView(TextView(context).apply {
            text = "⚠️  ${alert.name}"
            textSize = scaledSp(19f, scale)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = "Alerta ${alert.name}"
        })
        container.addView(TextView(context).apply {
            text = "A aproximadamente ${distanceMeters.roundToInt()} m"
            textSize = scaledSp(14f, scale)
            setTextColor(Color.LTGRAY)
            setPadding(0, scaledDp(4, scale), 0, scaledDp(8, scale))
        })
        container.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Fechar", scale) { hideProximityAlert(); actions.onDismiss() })
            addView(popupButton("Editar", scale) { hideProximityAlert(); actions.onEdit(alert) })
            addView(popupButton("Excluir", scale) { showDeleteConfirmation(alert, actions, scale) })
        })

        val metrics = context.resources.displayMetrics
        val requestedWidth = scaledDp(310, scale)
        val params = WindowManager.LayoutParams(
            requestedWidth.coerceAtMost(metrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = scaledDp(72, scale)
        }
        if (runCatching { windowManager.addView(container, params) }.isSuccess) {
            alertPopupView = container
            trace("proximity.popup.shown id=${alert.id} distance=${distanceMeters.roundToInt()} scale=$scale")
        }
    }

    fun showImportedRadarAlert(
        radar: ImportedRadar,
        distanceMeters: Double,
        onDismiss: () -> Unit = {},
    ) {
        hideShortcuts()
        hideProximityAlert()
        val scale = appearanceStore.scale()

        val container = alertContainer(scale)
        container.addView(TextView(context).apply {
            text = "⚠️  ${importedRadarTypeLabel(radar.type)}"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = importedRadarTypeLabel(radar.type)
        })
        radar.speedKmh?.let { speed ->
            container.addView(TextView(context).apply {
                text = "Limite $speed km/h"
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, 0)
            })
        }
        container.addView(TextView(context).apply {
            text = "A aproximadamente ${distanceMeters.roundToInt()} m"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(8))
        })
        container.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Fechar", scale) { hideProximityAlert(); onDismiss() })
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
            trace("imported_radar.popup.shown id=${radar.id} distance=${distanceMeters.roundToInt()}")
        }
    }

    fun hideProximityAlert() {
        val view = alertPopupView ?: return
        runCatching { windowManager.removeView(view) }
        alertPopupView = null
        trace("proximity.popup.closed")
    }

    fun showSilentStatus159(message: String, success: Boolean) {
        hideShortcuts()
        hideSilentStatus159()
        val scale = appearanceStore.scale()
        val label = TextView(context).apply {
            text = message
            textSize = scaledSp(13f, scale)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = false
            setPadding(scaledDp(14, scale), scaledDp(9, scale), scaledDp(14, scale), scaledDp(9, scale))
            background = GradientDrawable().apply {
                cornerRadius = scaledDp(16, scale).toFloat()
                setColor(if (success) Color.rgb(33, 105, 63) else Color.rgb(130, 72, 25))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = scaledDp(72, scale)
        }
        if (runCatching { windowManager.addView(label, params) }.isSuccess) {
            silentStatusView = label
            val dismiss = Runnable { hideSilentStatus159() }
            silentStatusDismiss = dismiss
            mainHandler.postDelayed(dismiss, 1_500L)
        }
    }

    fun hideSilentStatus159() {
        silentStatusDismiss?.let(mainHandler::removeCallbacks)
        silentStatusDismiss = null
        val view = silentStatusView ?: return
        runCatching { windowManager.removeView(view) }
        silentStatusView = null
    }

    fun hideAll() {
        hideShortcuts()
        hideProximityAlert()
        hideSilentStatus159()
    }

    fun showShortcutConfirmation0171(
        title: String,
        detail: String,
        onConfirm: () -> Unit,
    ) {
        hideAll()
        val scale = appearanceStore.scale()
        val container = alertContainer(scale)
        container.addView(TextView(context).apply {
            text = "⚠️  $title"
            textSize = scaledSp(18f, scale)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        container.addView(TextView(context).apply {
            text = detail
            textSize = scaledSp(14f, scale)
            setTextColor(Color.LTGRAY)
            setPadding(0, scaledDp(6, scale), 0, scaledDp(10, scale))
        })
        container.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Cancelar", scale) { hideProximityAlert() })
            addView(popupButton("Confirmar", scale) { hideProximityAlert(); onConfirm() })
        })
        val metrics = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            scaledDp(320, scale).coerceAtMost(metrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = scaledDp(72, scale)
        }
        if (runCatching { windowManager.addView(container, params) }.isSuccess) {
            alertPopupView = container
            trace("bubble.shortcut.long_press_confirmation_shown")
        }
    }

    private fun showShortcuts(
        anchor: WindowManager.LayoutParams,
        shortcuts: List<ResolvedShortcutGridEntry0179>,
        onShortcut: (ResolvedShortcutGridEntry0179) -> Unit,
        onShortcutLongPress: (ResolvedShortcutGridEntry0179) -> Unit,
    ) {
        hideProximityAlert()
        cancelShortcutGestures0186()
        BubbleShortcutCatalog.requireValid()

        val visibleShortcuts0179 = shortcuts.take(ShortcutGesturePolicy0179.MAX_GRID_ITEMS)
        val totalItems0179 = visibleShortcuts0179.size
        val scale = appearanceStore.scale()
        val columns = if (scale >= LARGE_SCALE_TWO_COLUMNS) 2 else 3
        val rows = (totalItems0179 + columns - 1) / columns
        val bubbleSize = scaledDp(62, scale)
        val itemMargin = scaledDp(2, scale)
        val panelPadding = scaledDp(6, scale)
        val metrics = context.resources.displayMetrics
        val naturalWidth = columns * (bubbleSize + itemMargin * 2) + panelPadding * 2
        val menuWidth = naturalWidth.coerceAtMost(metrics.widthPixels - dp(16))
        val estimatedMenuHeight = rows * (bubbleSize + itemMargin * 2) + panelPadding * 2
        val maxMenuHeight = (metrics.heightPixels - dp(24)).coerceAtLeast(dp(180))
        val visibleMenuHeight = estimatedMenuHeight.coerceAtMost(maxMenuHeight)
        val needsVerticalScroll = estimatedMenuHeight > maxMenuHeight

        val grid = GridLayout(context).apply {
            columnCount = columns
            rowCount = rows
            background = GradientDrawable().apply {
                cornerRadius = scaledDp(18, scale).toFloat()
                setColor(Color.argb(238, 25, 25, 25))
                setStroke(scaledDp(1, scale).coerceAtLeast(1), Color.argb(220, 255, 255, 255))
            }
            setPadding(panelPadding, panelPadding, panelPadding, panelPadding)
            visibleShortcuts0179.forEach { entry0179 ->
                addView(
                    shortcutBubble(
                        spec = entry0179.spec,
                        bubbleSize = bubbleSize,
                        itemMargin = itemMargin,
                        scale = scale,
                        singleAction = {
                            hideShortcuts()
                            trace("bubble.shortcut.clicked entry=${entry0179.entryId} id=${entry0179.spec.id}")
                            onShortcut(entry0179)
                        },
                        longAction = {
                            hideShortcuts()
                            trace("bubble.shortcut.long_press entry=${entry0179.entryId} id=${entry0179.spec.id}")
                            onShortcutLongPress(entry0179)
                        },
                    ),
                )
            }
        }

        val menu: View = if (needsVerticalScroll) {
            ScrollView(context).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                clipToPadding = false
                addView(
                    grid,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        } else {
            grid
        }

        val anchorWidth = anchor.width.takeIf { it > 0 && it < metrics.widthPixels } ?: dp(66)
        val anchorHeight = anchor.height.takeIf { it > 0 && it < metrics.heightPixels } ?: dp(66)
        val position = BubbleShortcutPositionPolicy.place(
            anchorX = anchor.x,
            anchorY = anchor.y,
            anchorWidth = anchorWidth,
            anchorHeight = anchorHeight,
            menuWidth = menuWidth,
            menuHeight = visibleMenuHeight,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            gap = scaledDp(8, scale),
            safeMargin = dp(4),
        )
        val backdrop0186 = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = false
            contentDescription = "Fechar grade de atalhos"
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> true
                    MotionEvent.ACTION_UP -> {
                        hideShortcuts()
                        true
                    }
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> true
                    else -> true
                }
            }
        }
        val backdropParams0186 = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        val params = WindowManager.LayoutParams(
            menuWidth,
            if (needsVerticalScroll) visibleMenuHeight else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
        val backdropAdded0186 = runCatching { windowManager.addView(backdrop0186, backdropParams0186) }.isSuccess
        if (backdropAdded0186 && runCatching { windowManager.addView(menu, params) }.isSuccess) {
            shortcutBackdropView0186 = backdrop0186
            shortcutView = menu
            trace(
                "bubble.shortcuts.opened count=${visibleShortcuts0179.size} plus=0 " +
                    "anchor=${anchor.x},${anchor.y},$anchorWidth,$anchorHeight " +
                    "menu=${position.x},${position.y},$menuWidth,$visibleMenuHeight " +
                    "estimatedHeight=$estimatedMenuHeight scroll=$needsVerticalScroll scale=$scale columns=$columns",
            )
        } else {
            cancelShortcutGestures0186()
            if (backdropAdded0186) runCatching { windowManager.removeView(backdrop0186) }
            trace("bubble.shortcuts.open_failed")
        }
    }

    private fun cancelShortcutGestures0186() {
        val cancelers0186 = shortcutGestureCancelers0186.toList()
        shortcutGestureCancelers0186.clear()
        cancelers0186.forEach { cancel0186 -> runCatching(cancel0186) }
    }

    private fun shortcutBubble(
        spec: BubbleShortcutSpec,
        bubbleSize: Int,
        itemMargin: Int,
        scale: Double,
        singleAction: () -> Unit,
        longAction: () -> Unit,
    ): TextView = TextView(context).apply {
        text = spec.displayText
        textSize = scaledSp(10f, scale).coerceIn(9f, 17f)
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 2
        contentDescription = "${spec.label}. Toque rápido executa; segure por um segundo e meio para a ação configurada."
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(72, 64, 82))
            setStroke(scaledDp(2, scale).coerceAtLeast(1), Color.argb(230, 205, 180, 255))
        }
        layoutParams = GridLayout.LayoutParams().apply {
            width = bubbleSize
            height = bubbleSize
            setMargins(itemMargin, itemMargin, itemMargin, itemMargin)
        }
        // SHORTCUT_DIRECT_TAP_AND_HOLD_0186: toque rápido sem janela e gesto longo consumido no limiar.
        val handler = Handler(Looper.getMainLooper())
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var downX = 0f
        var downY = 0f
        var pressState0186 = ShortcutPressState0186()
        val bubbleView0186 = this
        lateinit var longAction0186: Runnable
        longAction0186 = Runnable {
            if (shortcutView != null && bubbleView0186.isAttachedToWindow &&
                !pressState0186.moved && !pressState0186.longConsumed
            ) {
                pressState0186 = ShortcutInteractionPolicy0186.consumeLong(pressState0186)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                longAction()
            }
        }
        val cancelGesture0186: () -> Unit = {
            handler.removeCallbacks(longAction0186)
            pressState0186 = ShortcutInteractionPolicy0186.moved(pressState0186)
            bubbleView0186.isPressed = false
        }
        shortcutGestureCancelers0186 += cancelGesture0186
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                cancelGesture0186()
                shortcutGestureCancelers0186.remove(cancelGesture0186)
            }
        })
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(longAction0186)
                    downX = event.x
                    downY = event.y
                    pressState0186 = ShortcutPressState0186()
                    view.isPressed = true
                    handler.postDelayed(longAction0186, ShortcutInteractionPolicy0186.HOLD_MILLIS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moved = kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop
                    if (moved && !pressState0186.moved) {
                        pressState0186 = ShortcutInteractionPolicy0186.moved(pressState0186)
                        handler.removeCallbacks(longAction0186)
                        view.isPressed = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longAction0186)
                    view.isPressed = false
                    if (ShortcutInteractionPolicy0186.release(pressState0186) == ShortcutReleaseAction0186.Quick) {
                        performClick()
                        singleAction()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    handler.removeCallbacks(longAction0186)
                    pressState0186 = ShortcutInteractionPolicy0186.moved(pressState0186)
                    view.isPressed = false
                    true
                }
                else -> true
            }
        }
    }

    private fun showDeleteConfirmation(
        alert: SavedPlace,
        actions: ProximityAlertPopupActions,
        scale: Double,
    ) {
        val parent = alertPopupView as? LinearLayout ?: return
        parent.removeAllViews()
        parent.addView(TextView(context).apply {
            text = "⚠️ Excluir \"${alert.name}\"?"
            textSize = scaledSp(18f, scale)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, scaledDp(10, scale))
        })
        parent.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Cancelar", scale) { hideProximityAlert() })
            addView(popupButton("Confirmar", scale) { hideProximityAlert(); actions.onDelete(alert) })
        })
    }

    private fun alertContainer(scale: Double): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = scaledDp(18, scale).toFloat()
            setColor(Color.argb(246, 38, 38, 38))
            setStroke(scaledDp(3, scale).coerceAtLeast(1), Color.rgb(255, 193, 7))
        }
        setPadding(
            scaledDp(14, scale),
            scaledDp(12, scale),
            scaledDp(14, scale),
            scaledDp(12, scale),
        )
    }

    private fun popupButton(label: String, scale: Double, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = scaledSp(14f, scale).coerceAtMost(20f)
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        contentDescription = label
        setPadding(
            scaledDp(8, scale),
            scaledDp(9, scale),
            scaledDp(8, scale),
            scaledDp(9, scale),
        )
        layoutParams = LinearLayout.LayoutParams(0, scaledDp(42, scale), 1f).apply {
            val margin = scaledDp(3, scale)
            setMargins(margin, 0, margin, 0)
        }
        background = GradientDrawable().apply {
            cornerRadius = scaledDp(12, scale).toFloat()
            setColor(Color.rgb(79, 68, 88))
        }
        setOnClickListener { action() }
    }

    private fun scaledDp(value: Int, scale: Double): Int = dp((value * scale).roundToInt())

    private fun scaledSp(value: Float, scale: Double): Float = value * scale.toFloat()

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val LARGE_SCALE_TWO_COLUMNS = 1.35
    }
}

data class ProximityAlertPopupActions(
    val onEdit: (SavedPlace) -> Unit,
    val onDelete: (SavedPlace) -> Unit,
    val onDismiss: () -> Unit = {},
)

// popup_only_shortcut_grid_0_1_119

// popup_close_after_tap_0_1_120
