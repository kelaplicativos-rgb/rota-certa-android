// Compatibilidade de compilacao do popup 0.1.119.
// Reutiliza o limpador existente e o capturador OCR de WhatsApp da 0.1.118.
fun enforcePopupOnlyCompileCleanup119(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()

    text = text.replace(
        "BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp()",
        "BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()",
    )

    val duplicatedClipboardHelper = """    private fun clearClipboardFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        toast("Area de transferencia limpa.")
        DiagnosticLogStore.record("bubble_action", "clipboard cleared")
    }

"""
    text = text.replace(duplicatedClipboardHelper, "")

    if ("BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()" !in text) {
        throw GradleException("Atalho WhatsApp nao foi ligado ao capturador 0.1.118.")
    }
    if (Regex("private fun clearClipboardFromBubble\\(\\)").findAll(text).count() != 1) {
        throw GradleException("O limpador da area de transferencia precisa existir uma unica vez.")
    }
    if ("popup_only_compile_cleanup_0_1_119" !in text) {
        text += "\n// popup_only_compile_cleanup_0_1_119\n"
    }
    file.writeText(text)
}

val popupOnlyCompileCleanup119 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("popupOnlyControlCenter119", "bubbleWhatsAppCaptureCompat118")
    doLast { enforcePopupOnlyCompileCleanup119(serviceFile.asFile) }
}

popupOnlyCompileCleanup119.configure {
    mustRunAfter("popupOnlyControlCenter119", "bubbleWhatsAppCaptureCompat118")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupOnlyCompileCleanup119)
}
