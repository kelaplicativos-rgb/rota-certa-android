val patchBubbleShortcutClipboard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""                        longPressRunnable = Runnable {
                            if (view.isPressed && longAction != null) {
                                longPressHandled = true
                                longAction.invoke()
                            }
                        }
                        view.postDelayed(longPressRunnable, 2_000L)
""",
"""                        val runnable = Runnable {
                            if (view.isPressed && longAction != null) {
                                longPressHandled = true
                                longAction.invoke()
                            }
                        }
                        longPressRunnable = runnable
                        view.postDelayed(runnable, 2_000L)
""",
        )

        if ("private fun clearClipboardFromBubble()" !in text) {
            text = text.replace(
"""    private fun openApp(tab: String? = null, expander: String? = null) {
""",
"""    private fun clearClipboardFromBubble() {
        hideActionMenu()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        }.onSuccess {
            traceEvent("bubble.clear_clipboard success=true")
            toast("Area de transferencia limpa.")
        }.onFailure { error ->
            traceEvent("bubble.clear_clipboard success=false error=${dollar}{error.message.orEmpty()}")
            toast("Nao foi possivel limpar a area de transferencia.")
        }
    }

    private fun openApp(tab: String? = null, expander: String? = null) {
""",
            )
        }

        if ("Limpar area de transferencia" !in text) {
            text = text.replace(
"""            addView(actionMenuItem(
                label = "🎯  Definir região de trabalho",
                action = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
            ))
            addView(actionMenuItem(
                label = "🔔  Criar alerta de proximidade",
""",
"""            addView(actionMenuItem(
                label = "🎯  Definir região de trabalho",
                action = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
            ))
            addView(actionMenuItem(
                label = "🧹  Limpar area de transferencia",
                action = { clearClipboardFromBubble() },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Area de transferencia") },
            ))
            addView(actionMenuItem(
                label = "🔔  Criar alerta de proximidade",
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchBubbleShortcutClipboard.configure {
    mustRunAfter("patchResourceGroupsCompileFix")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchBubbleShortcutClipboard)
}
