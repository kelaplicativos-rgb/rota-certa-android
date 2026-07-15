// Ajustes de compilacao e integracao final da central circular.

val unifiedBubbleCompileFinal by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("import android.net.Uri" !in text) {
            text = text.replace("import android.content.Intent\n", "import android.content.Intent\nimport android.net.Uri\n")
        }

        text = text
            .replace("    private var overlayMenuView: LinearLayout? = null\n", "    private var overlayMenuView: View? = null\n")
            .replace("        removeWhatsAppShortcut()\n", "")
            .replace("                capturePhoneAndOpenWhatsApp()\n", "                openWhatsAppFromCurrentScreen()\n")
            .replace("openAppTab(TAB_ANALYSIS)", "openControlCenterTab(TAB_ANALYSIS)")
            .replace("openAppTab(TAB_CONFIG)", "openControlCenterTab(TAB_CONFIG)")
            .replace("openAppTab(TAB_TOOLS)", "openControlCenterTab(TAB_TOOLS)")

        if ("private fun openControlCenterTab(tab: String)" !in text) {
            val anchor = "    private fun showActionMenu() {\n"
            if (anchor !in text) throw GradleException("Nao encontrei showActionMenu para inserir helpers finais.")
            val helpers = """    private fun openControlCenterTab(tab: String) {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, tab),
            )
        }
    }

    private fun openWhatsAppFromCurrentScreen() {
        val target = ScreenPhoneLink.findBest(collectVisibleText(allowPopupCandidate = true))
            ?: ScreenPhoneLink.findBest(mergeRideTexts(lastAccessibilityText, lastOcrText))
        if (target != null) {
            val uri = Uri.parse(target.url)
            val opened = listOf("com.whatsapp", "com.whatsapp.w4b").any { packageName ->
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, uri)
                            .setPackage(packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    true
                }.getOrDefault(false)
            }
            if (!opened) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (launchIntent != null) {
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            toast("WhatsApp nao encontrado e nenhum telefone apareceu na tela.")
        }
    } // unified_whatsapp_inside_grid_compile_0_1_94

"""
            text = text.replace(anchor, helpers + anchor)
        }

        listOf(
            "private var overlayMenuView: View? = null",
            "private fun openControlCenterTab(tab: String)",
            "unified_whatsapp_inside_grid_compile_0_1_94",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Correcao final da central ausente: $marker")
        }
        if ("removeWhatsAppShortcut()" in text) {
            throw GradleException("Atalho WhatsApp separado ainda esta ligado.")
        }
        if ("capturePhoneAndOpenWhatsApp()" in text) {
            throw GradleException("Central ainda depende do helper WhatsApp removido.")
        }

        if (text != original) file.writeText(text)
    }
}

unifiedBubbleCompileFinal.configure {
    mustRunAfter("unifiedBubbleControlCenterFinal")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(unifiedBubbleCompileFinal)
}
