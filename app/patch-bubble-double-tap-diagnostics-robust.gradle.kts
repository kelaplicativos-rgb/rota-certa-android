val bubbleDoubleTapDiagnosticsRobust by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        var text = file.readText()
        val original = text

        if ("val doubleTapDetected =" !in text) {
            val noActionRegex = Regex("(?m)^    val noBubbleActionRegistered = .*$")
            text = noActionRegex.replace(text) {
"""    val doubleTapDetected = has("diagnostic.contract bubble_double_tap step=detected") || has("diagnostic.contract bubble_double_tap step=triggered")
    val doubleTapTriggered = has("diagnostic.contract bubble_double_tap step=triggered")
    val doubleTapScreenshotStarted = has("diagnostic.contract bubble_double_tap step=screenshot_started ok=true source=double_tap")
    val doubleTapOcrSuccess = has("diagnostic.contract bubble_double_tap step=ocr_success ok=true source=double_tap")
    val doubleTapSaveStarted = has("diagnostic.contract bubble_card_capture step=direct_save_start ok=true source=double_tap")
    val doubleTapSaveSuccess = has("diagnostic.contract save_card result=success source=double_tap")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered && !doubleTapDetected"""
            }
        }

        if ("toqueDuploBolinha" !in text) {
            text = text.replace(
"""        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
"""        put("toqueDuploBolinha", doubleTapDetected)
        put("capturaToqueDuploIniciada", doubleTapTriggered)
        put("printToqueDuploSolicitado", doubleTapScreenshotStarted)
        put("ocrToqueDuploConfirmado", doubleTapOcrSuccess)
        put("salvamentoToqueDuploIniciado", doubleTapSaveStarted)
        put("salvamentoToqueDuploConfirmado", doubleTapSaveSuccess)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
            )
        }

        if (text != original) file.writeText(text)
    }
}

bubbleDoubleTapDiagnosticsRobust.configure {
    mustRunAfter("bubbleDoubleTapCardCapture")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleDoubleTapDiagnosticsRobust)
}
