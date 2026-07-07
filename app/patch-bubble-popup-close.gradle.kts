val patchBubblePopupClose by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace("Salvar card de corrida", "Salvar card desta corrida")
        text = text.replace(
            "addView(actionMenuItem(\"Fechar\", { hideActionMenu() }))",
            "addView(actionMenuItem(\"✕  Fechar\") { hideActionMenu() })",
        )

        if ("✕  Fechar" !in text) {
            val lines = text.lines().toMutableList()
            val openLabelIndex = lines.indexOfFirst { line -> line.contains("Abrir Rota Certa") }
            val openIndex = if (openLabelIndex >= 0) {
                (openLabelIndex downTo 0).firstOrNull { index ->
                    lines[index].contains("addView(actionMenuItem(")
                } ?: openLabelIndex
            } else {
                -1
            }
            if (openIndex >= 0) {
                val indent = lines[openIndex].takeWhile { it.isWhitespace() }
                val itemIndent = indent + "    "
                lines.addAll(
                    openIndex,
                    listOf(
                        "${indent}addView(actionMenuItem(",
                        "${itemIndent}label = \"✕  Fechar\",",
                        "${itemIndent}action = { hideActionMenu() },",
                        "${itemIndent}longAction = { hideActionMenu() },",
                        "${indent}))",
                    ),
                )
                text = lines.joinToString("\n")
            }
        }

        text = text.replace(
"""        val params = WindowManager.LayoutParams(
            dp(260),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x + dp(76)
            y = bubbleParams.y
        }
""",
"""        val menuWidth = dp(260)
        val menuPosition = actionMenuPosition(bubbleParams, menuWidth)
        val params = WindowManager.LayoutParams(
            menuWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = menuPosition.first
            y = menuPosition.second
        }
""",
        )

        text = text.replace(
"""            x = overlayMenuX(bubbleParams)
            y = overlayMenuY(bubbleParams)
""",
"""            val menuPosition = actionMenuPosition(bubbleParams, dp(260))
            x = menuPosition.first
            y = menuPosition.second
""",
        )

        if ("private fun actionMenuPosition(" !in text) {
            text = text.replace(
"""    private fun updateActionMenuPosition() {
""",
"""    private fun actionMenuPosition(
        bubbleParams: WindowManager.LayoutParams,
        menuWidth: Int,
    ): Pair<Int, Int> {
        val gap = dp(10)
        val bubbleSize = dp(66)
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - menuWidth).coerceAtLeast(0)
        val rightX = bubbleParams.x + bubbleSize + gap
        val leftX = bubbleParams.x - menuWidth - gap
        val x = when {
            rightX <= maxX -> rightX
            leftX >= 0 -> leftX
            else -> bubbleParams.x.coerceIn(0, maxX)
        }
        val estimatedMenuHeight = dp(310)
        val maxY = (metrics.heightPixels - estimatedMenuHeight).coerceAtLeast(0)
        val belowY = bubbleParams.y + bubbleSize + gap
        val aboveY = bubbleParams.y - estimatedMenuHeight - gap
        val y = when {
            belowY <= maxY -> belowY
            aboveY >= 0 -> aboveY
            bubbleParams.y < metrics.heightPixels / 2 -> belowY.coerceIn(0, maxY)
            else -> aboveY.coerceIn(0, maxY)
        }
        return x to y
    }

    private fun updateActionMenuPosition() {
""",
            )
        }

        text = text.replace(
"""        params.x = bubbleParams.x + dp(76)
        params.y = bubbleParams.y
        runCatching { manager.updateViewLayout(view, params) }
""",
"""        val menuPosition = actionMenuPosition(bubbleParams, params.width.takeIf { it > 0 } ?: dp(260))
        params.x = menuPosition.first
        params.y = menuPosition.second
        runCatching { manager.updateViewLayout(view, params) }
""",
        )

        text = text.replace(
"""        params.x = overlayMenuX(bubbleParams)
        params.y = overlayMenuY(bubbleParams)
        runCatching { manager.updateViewLayout(view, params) }
""",
"""        val menuPosition = actionMenuPosition(bubbleParams, params.width.takeIf { it > 0 } ?: dp(260))
        params.x = menuPosition.first
        params.y = menuPosition.second
        runCatching { manager.updateViewLayout(view, params) }
""",
        )

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchBubblePopupClose.configure {
    mustRunAfter(tasks.matching { it.name.startsWith("patch") && it.name != "patchBubblePopupClose" })
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchBubblePopupClose)
}