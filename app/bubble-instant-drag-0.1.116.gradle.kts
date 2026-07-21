// Correcao 0.1.116: a bolinha precisa acompanhar o dedo imediatamente.
//
// O servico de acessibilidade usa o thread principal para eventos de toque e para
// a leitura continua. O OCR e os ciclos de analise podiam ocupar esse mesmo thread,
// deixando ACTION_DOWN/ACTION_MOVE aguardando por segundos.
//
// Este patch final:
// - pausa novas leituras enquanto existe um gesto ativo;
// - cancela a analise de acessibilidade pendente no ACTION_DOWN;
// - executa OCR fora do thread principal;
// - remove qualquer atualizacao do antigo menu durante ACTION_MOVE;
// - persiste a posicao no fim do gesto e retoma a leitura depois de 32 ms.

fun bubbleDrag116InsertAfterFunctionBrace(
    source: String,
    signature: String,
    addition: String,
    marker: String,
): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao ausente para $marker")
    val brace = source.indexOf('{', start)
    if (brace < 0) throw GradleException("Abertura ausente para $marker")
    val lineEnd = source.indexOf('\n', brace)
    if (lineEnd < 0) throw GradleException("Linha de abertura ausente para $marker")
    val functionEnd = source.indexOf("\n    private ", lineEnd + 1).let { if (it < 0) source.length else it }
    if (marker in source.substring(start, functionEnd)) return source
    return source.substring(0, lineEnd + 1) + addition + source.substring(lineEnd + 1)
}

fun enforceInstantBubbleDrag116(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()

    if ("bubble_instant_drag_0_1_116" !in text) {
        val overlayAnchor = "    private var overlayParams: WindowManager.LayoutParams? = null\n"
        if ("private var bubbleGestureActive" !in text) {
            if (overlayAnchor !in text) throw GradleException("Estado da bolinha nao encontrado.")
            text = text.replaceFirst(
                overlayAnchor,
                overlayAnchor + """    @Volatile private var bubbleGestureActive = false
    private var bubbleDragStartedAtMillis = 0L
""",
            )
        }

        text = bubbleDrag116InsertAfterFunctionBrace(
            source = text,
            signature = "    private fun scheduleVisibleTextAnalysis(",
            addition = """        if (bubbleGestureActive) {
            traceEvent("bubble.drag.analysis_paused source=accessibility")
            return
        } // bubble_drag_accessibility_pause_0_1_116
""",
            marker = "bubble_drag_accessibility_pause_0_1_116",
        )

        text = bubbleDrag116InsertAfterFunctionBrace(
            source = text,
            signature = "    private fun requestScreenshotAnalysis(",
            addition = """        if (bubbleGestureActive) {
            traceEvent("bubble.drag.analysis_paused source=screenshot")
            return
        } // bubble_drag_screenshot_pause_0_1_116
""",
            marker = "bubble_drag_screenshot_pause_0_1_116",
        )

        text = bubbleDrag116InsertAfterFunctionBrace(
            source = text,
            signature = "    private suspend fun processRideText(",
            addition = """        if (bubbleGestureActive) {
            traceEvent("bubble.drag.analysis_paused source=process")
            return
        } // bubble_drag_process_pause_0_1_116
""",
            marker = "bubble_drag_process_pause_0_1_116",
        )

        val scanStart = text.indexOf("    private fun startContinuousScan() {")
        val scanEnd = if (scanStart >= 0) text.indexOf("    private fun startProximityAlertMonitor()", scanStart) else -1
        if (scanStart < 0 || scanEnd <= scanStart) throw GradleException("Loop continuo nao encontrado.")
        var scanBlock = text.substring(scanStart, scanEnd)
        if ("bubble_drag_scan_pause_0_1_116" !in scanBlock) {
            val whileAnchor = "            while (serviceReady) {\n"
            if (whileAnchor !in scanBlock) throw GradleException("While do loop continuo nao encontrado.")
            scanBlock = scanBlock.replaceFirst(
                whileAnchor,
                whileAnchor + """                if (bubbleGestureActive) {
                    delay(BubbleDragPolicy.ANALYSIS_RESUME_DELAY_MS)
                    continue
                } // bubble_drag_scan_pause_0_1_116
""",
            )
            text = text.substring(0, scanStart) + scanBlock + text.substring(scanEnd)
        }

        val ocrAnchor = "val ocrText = ocrService.extractText(bitmap)"
        if ("bubble_drag_ocr_background_0_1_116" !in text) {
            if (ocrAnchor !in text) throw GradleException("Chamada OCR nao encontrada.")
            text = text.replaceFirst(
                ocrAnchor,
                """val ocrText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        ocrService.extractText(bitmap)
                                    } // bubble_drag_ocr_background_0_1_116""",
            )
        }

        val listenerReplacement = """    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private val touchSlop: Int by lazy {
            android.view.ViewConfiguration.get(this@LiveRideAccessibilityService).scaledTouchSlop.coerceAtLeast(1)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = overlayParams ?: return false
            val manager = windowManager ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleGestureActive = (true)
                    bubbleDragStartedAtMillis = event.eventTime
                    analyzeJob?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    view.animate().cancel()
                    traceEvent("bubble.drag.down immediate=true")
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!moved && BubbleDragPolicy.hasExceededTouchSlop(deltaX, deltaY, touchSlop)) {
                        moved = true
                    }

                    val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                    params.x = BubbleDragPolicy.clampCoordinate((startX + deltaX).roundToInt(), maxX)
                    params.y = BubbleDragPolicy.clampCoordinate((startY + deltaY).roundToInt(), maxY)
                    runCatching { manager.updateViewLayout(view, params) }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val elapsedMillis = (event.eventTime - bubbleDragStartedAtMillis).coerceAtLeast(0L)
                    bubbleGestureActive = false
                    if (moved) {
                        bubblePrefs.edit()
                            .putInt(KEY_BUBBLE_X, params.x)
                            .putInt(KEY_BUBBLE_Y, params.y)
                            .apply()
                        traceEvent("bubble.drag.up moved=true elapsed_ms=" + elapsedMillis)
                    } else {
                        traceEvent("bubble.drag.up moved=false elapsed_ms=" + elapsedMillis)
                        view.performClick()
                    }
                    scope.launch {
                        delay(BubbleDragPolicy.ANALYSIS_RESUME_DELAY_MS)
                        if (!bubbleGestureActive) scheduleVisibleTextAnalysis(delayMs = 0L)
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    bubbleGestureActive = false
                    traceEvent("bubble.drag.cancel")
                    return true
                }
            }
            return true
        }
    } // bubble_instant_drag_0_1_116

"""
        val listenerRegex = Regex(
            "(?s)    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?\\n    private fun dp",
        )
        if (!listenerRegex.containsMatchIn(text)) {
            throw GradleException("BubbleTouchListener nao encontrado para substituicao.")
        }
        text = listenerRegex.replaceFirst(text, listenerReplacement + "    private fun dp")
    }

    val gestureActiveAssignmentPresent =
        "bubbleGestureActive = true" in text || "bubbleGestureActive = (true)" in text
    if (!gestureActiveAssignmentPresent) {
        throw GradleException("Arraste instantaneo incompleto: atribuicao bubbleGestureActive")
    }

    listOf(
        "bubble_instant_drag_0_1_116",
        "analyzeJob?.cancel()",
        "bubble_drag_accessibility_pause_0_1_116",
        "bubble_drag_screenshot_pause_0_1_116",
        "bubble_drag_process_pause_0_1_116",
        "bubble_drag_scan_pause_0_1_116",
        "bubble_drag_ocr_background_0_1_116",
        "BubbleDragPolicy.hasExceededTouchSlop",
        "manager.updateViewLayout(view, params)",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Arraste instantaneo incompleto: $marker")
    }

    val listenerStart = text.indexOf("    private inner class BubbleTouchListener")
    val listenerEnd = if (listenerStart >= 0) text.indexOf("    private fun dp", listenerStart) else -1
    val listenerBlock = if (listenerStart >= 0 && listenerEnd > listenerStart) {
        text.substring(listenerStart, listenerEnd)
    } else {
        ""
    }
    if ("updateActionMenuPosition()" in listenerBlock) {
        throw GradleException("Arraste ainda atualiza o menu removido.")
    }
    if ("delay(" in listenerBlock.substringBefore("MotionEvent.ACTION_UP")) {
        throw GradleException("Arraste possui espera antes de mover.")
    }

    file.writeText(text)
}

val bubbleInstantDrag116 by tasks.registering {
    val serviceFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
    )
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("mainBubbleTapMenuContract", "universalFastReadRuntime110")
    doLast { enforceInstantBubbleDrag116(serviceFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleInstantDrag116)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleInstantDrag116)
    doFirst {
        enforceInstantBubbleDrag116(
            layout.projectDirectory.file(
                "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
            ).asFile,
        )
    }
}
