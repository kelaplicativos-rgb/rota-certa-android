fun replacePrivateFunctionBlockDirectHome(
    source: String,
    functionName: String,
    replacement: String,
): String {
    val start = source.indexOf("    private fun $functionName")
    if (start < 0) return source
    val next = source.indexOf("\n    private fun ", start + 1)
    return if (next < 0) {
        source.substring(0, start) + replacement
    } else {
        source.substring(0, start) + replacement + source.substring(next + 1)
    }
}

val bubbleDirectHomeNoMenu by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
            "newView.contentDescription = \"Rota Certa - toque duas vezes para salvar card\"",
            "newView.contentDescription = \"Rota Certa - toque para abrir o aplicativo\"",
        )
        text = text.replace(
            "newView.contentDescription = \"Rota Certa\"",
            "newView.contentDescription = \"Rota Certa - toque para abrir o aplicativo\"",
        )
        text = text.replace("newView.isLongClickable = true", "newView.isLongClickable = false")
        text = text.replace(
"""            newView.setOnClickListener { toggleActionMenu() }
""",
"""            newView.setOnClickListener {
                traceEvent("diagnostic.contract bubble_single_tap step=open_home ok=true")
                openApp()
            }
""",
        )
        text = text.replace(
"""            newView.setOnClickListener {
                traceEvent("diagnostic.contract bubble_single_tap step=click ok=true")
                toggleActionMenu()
            }
""",
"""            newView.setOnClickListener {
                traceEvent("diagnostic.contract bubble_single_tap step=open_home ok=true")
                openApp()
            }
""",
        )

        text = replacePrivateFunctionBlockDirectHome(text, "toggleActionMenu", """    private fun toggleActionMenu() {
        traceEvent("diagnostic.contract bubble_menu step=removed_open_home ok=true")
        openApp()
    }

""")

        text = replacePrivateFunctionBlockDirectHome(text, "showActionMenu", """    private fun showActionMenu() {
        traceEvent("diagnostic.contract bubble_menu step=blocked_removed ok=true")
        openApp()
    }

""")

        text = replacePrivateFunctionBlockDirectHome(text, "captureAndSaveCardFromBubbleDoubleTap", """    private fun captureAndSaveCardFromBubbleDoubleTap() {
        traceEvent("diagnostic.contract bubble_double_tap step=removed ok=true")
        openApp()
    }

""")

        text = replacePrivateFunctionBlockDirectHome(text, "captureAndSaveCardFromBubbleLongPress", """    private fun captureAndSaveCardFromBubbleLongPress() {
        traceEvent("diagnostic.contract bubble_long_press step=removed ok=true")
        openApp()
    }

""")

        text = replacePrivateFunctionBlockDirectHome(text, "updateBubbleLongPressCountdown", """    private fun updateBubbleLongPressCountdown(text: String?) {
        bubbleLongPressCountdownText = null
        overlayView?.let { view ->
            val bubbleText = formatBubbleDistanceKm(currentDistanceKm)
            view.text = bubbleText
            view.textSize = bubbleTextSizeSp(bubbleText)
        }
        hideBubbleLongPressCountdownOverlay()
        if (!text.isNullOrBlank()) {
            traceEvent("diagnostic.contract bubble_long_press step=countdown_removed ok=true value=${dollar}{text.orEmpty()}")
        }
    }

""")

        val listenerReplacement = """    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = overlayParams ?: return false
            val manager = windowManager ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    traceEvent("diagnostic.contract bubble_touch step=down ok=true mode=open_home_only")
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (abs(deltaX) > dp(4) || abs(deltaY) > dp(4)) moved = true
                    params.x = (startX + deltaX).roundToInt().coerceAtLeast(0)
                    params.y = (startY + deltaY).roundToInt().coerceAtLeast(0)
                    runCatching { manager.updateViewLayout(view, params) }
                    updateActionMenuPosition()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    traceEvent("diagnostic.contract bubble_touch step=up ok=true moved=${dollar}moved mode=open_home_only")
                    if (!moved) view.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    traceEvent("diagnostic.contract bubble_touch step=cancel ok=true mode=open_home_only")
                    return true
                }
            }
            return false
        }
    }

"""

        val listenerRegex = Regex("(?s)    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
        text = listenerRegex.replace(text) { listenerReplacement + "    private fun dp" }

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleDirectHomeNoMenu)
}
