// 0.1.117: cada recurso da grade possui um modulo Kotlin independente.
// Este patch apenas monta o catalogo e encaminha a acao para o servico.

fun modularShortcut117ReplaceRegion(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao do menu modular ausente.")
    return source.substring(0, start) + replacement + source.substring(end)
}

fun enforceModularBubbleShortcuts117(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()
    if ("bubble_shortcut_modules_0_1_117" in text) return
    if ("bubble_resource_shortcuts_0_1_117" !in text) {
        throw GradleException("Runtime-base 0.1.117 precisa executar antes dos modulos.")
    }

    val replacement = """    private fun showActionMenu() {
        val manager = windowManager ?: return
        if (overlayMenuView != null) return
        hideProximityAlertPopup()
        val bubbleParams = overlayParams ?: return
        BubbleShortcutCatalog.requireValid()
        val menuSize = dp(222)
        val menu = GridLayout(this).apply {
            columnCount = 3
            rowCount = 2
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(232, 25, 25, 25))
                setStroke(dp(1), Color.argb(220, 255, 255, 255))
            }
            setPadding(dp(7), dp(7), dp(7), dp(7))
            BubbleShortcutCatalog.modules.forEach { module ->
                addView(resourceShortcutBubble(module.spec.displayText) {
                    handleBubbleShortcut(module.spec)
                })
            }
        }
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            menuSize,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val rightX = bubbleParams.x + dp(74)
            x = if (rightX + menuSize <= screenWidth) rightX else (bubbleParams.x - menuSize - dp(8)).coerceAtLeast(0)
            y = bubbleParams.y.coerceIn(0, (screenHeight - dp(170)).coerceAtLeast(0))
        }
        if (runCatching { manager.addView(menu, params) }.isSuccess) {
            overlayMenuView = menu
            overlayMenuParams = params
            traceEvent("bubble.shortcuts.opened count=" + BubbleShortcutCatalog.modules.size)
        }
    } // bubble_shortcut_modules_0_1_117

    private fun handleBubbleShortcut(spec: BubbleShortcutSpec) {
        hideActionMenu()
        traceEvent("bubble.shortcut.action id=" + spec.id)
        when (spec.action) {
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            BubbleShortcutAction.SaveRideCard -> saveCurrentRideCardFromBubble()
            BubbleShortcutAction.OpenDestination,
            BubbleShortcutAction.OpenReading,
            BubbleShortcutAction.OpenSettings,
            -> openAppGroup(
                group = requireNotNull(spec.targetGroup),
                tab = requireNotNull(spec.targetTab),
            )
        }
    }

    private fun resourceShortcutBubble(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = label.replace("\n", " ")
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(72, 64, 82))
                setStroke(dp(2), Color.argb(230, 205, 180, 255))
            }
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(66)
                height = dp(66)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            setOnClickListener { action() }
        }

"""

    text = modularShortcut117ReplaceRegion(
        source = text,
        startToken = "    private fun showActionMenu() {",
        endToken = "    private fun hideActionMenu() {",
        replacement = replacement,
    )

    listOf(
        "bubble_shortcut_modules_0_1_117",
        "BubbleShortcutCatalog.modules.forEach",
        "handleBubbleShortcut(module.spec)",
        "BubbleShortcutAction.CreateAlert",
        "BubbleShortcutAction.CreateSavedPlace",
        "BubbleShortcutAction.SaveRideCard",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Menu modular incompleto: $marker")
    }
    file.writeText(text)
}

val bubbleShortcutModularCatalog117 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("bubbleResourceShortcutsAlertPopup117")
    doLast { enforceModularBubbleShortcuts117(serviceFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleShortcutModularCatalog117)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleShortcutModularCatalog117)
    doFirst {
        enforceModularBubbleShortcuts117(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
