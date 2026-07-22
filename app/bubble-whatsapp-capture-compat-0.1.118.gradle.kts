// Captura de telefone independente para o atalho WhatsApp 0.1.118.
// Nao depende do antigo botao WA permanente.
fun enforceBubbleWhatsAppCapture118(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()

    if ("bubble_whatsapp_capture_0_1_118" in text) {
        listOf(
            "private fun capturePhoneAndOpenWhatsApp118()",
            "private fun openWhatsAppTarget118(",
            "private fun requestPhoneScreenshot118()",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("WhatsApp 0.1.118 incompleto: $marker")
        }
        return
    }

    if ("import android.net.Uri" !in text) {
        text = text.replace("import android.content.Intent\n", "import android.content.Intent\nimport android.net.Uri\n")
    }

    val atomicAnchor = "    private val screenshotInProgress = AtomicBoolean(false)\n"
    if (atomicAnchor !in text) throw GradleException("Controle de screenshot nao encontrado.")
    text = text.replaceFirst(
        atomicAnchor,
        atomicAnchor + "    private val phoneCaptureInProgress118 = AtomicBoolean(false)\n",
    )

    val oldCall = "BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp()"
    if (oldCall !in text) throw GradleException("Acao WhatsApp modular nao encontrada.")
    text = text.replace(oldCall, "BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()")

    val insertionPoint = "    private fun openSavedPlaceEditor("
    if (insertionPoint !in text) throw GradleException("Ponto de insercao do WhatsApp nao encontrado.")
    val helpers = """    private fun capturePhoneAndOpenWhatsApp118() {
        if (!phoneCaptureInProgress118.compareAndSet(false, true)) return
        DiagnosticLogStore.record("bubble_action", "whatsapp.capture.started")

        val directTarget = ScreenPhoneLink.findBest(collectVisibleTextForAction())
            ?: ScreenPhoneLink.findBest(mergeRideTexts(lastAccessibilityText, lastOcrText))
        if (directTarget != null) {
            phoneCaptureInProgress118.set(false)
            openWhatsAppTarget118(directTarget)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            phoneCaptureInProgress118.set(false)
            toast("Nenhum telefone brasileiro com DDD foi encontrado na tela.")
            DiagnosticLogStore.record("bubble_action", "whatsapp.capture.no_number sdk_too_old=true")
            return
        }

        scope.launch {
            var acquiredScreenshot = false
            var attempts = 0
            while (!acquiredScreenshot && attempts < 6) {
                acquiredScreenshot = screenshotInProgress.compareAndSet(false, true)
                if (!acquiredScreenshot) delay(120L)
                attempts += 1
            }
            if (!acquiredScreenshot) {
                phoneCaptureInProgress118.set(false)
                toast("Nao consegui capturar o telefone agora. Toque em WhatsApp novamente.")
                DiagnosticLogStore.record("bubble_action", "whatsapp.capture.busy")
                return@launch
            }
            requestPhoneScreenshot118()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestPhoneScreenshot118() {
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            val target = runCatching {
                                val buffer = screenshot.hardwareBuffer
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                buffer.close()
                                if (bitmap == null) return@runCatching null
                                try {
                                    ScreenPhoneLink.findBest(ocrService.extractText(bitmap))
                                } finally {
                                    bitmap.recycle()
                                }
                            }.getOrNull()
                            screenshotInProgress.set(false)
                            phoneCaptureInProgress118.set(false)
                            if (target != null) {
                                DiagnosticLogStore.record("bubble_action", "whatsapp.capture.ocr_success number=" + target.displayNumber)
                                openWhatsAppTarget118(target)
                            } else {
                                toast("Nenhum telefone brasileiro com DDD foi encontrado na tela.")
                                DiagnosticLogStore.record("bubble_action", "whatsapp.capture.no_number")
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        phoneCaptureInProgress118.set(false)
                        toast("O Android nao permitiu ler a tela agora. Codigo: " + errorCode)
                        DiagnosticLogStore.record("bubble_action", "whatsapp.capture.failed code=" + errorCode)
                    }
                },
            )
        }.onFailure { error ->
            screenshotInProgress.set(false)
            phoneCaptureInProgress118.set(false)
            toast("Nao consegui capturar o telefone da tela.")
            DiagnosticLogStore.record("bubble_action", "whatsapp.capture.error=" + error::class.java.simpleName)
        }
    }

    private fun openWhatsAppTarget118(target: ScreenPhoneTarget) {
        val uri = Uri.parse(target.url)
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        val opened = packages.any { packageName ->
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
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                toast("WhatsApp nao encontrado no celular.")
            }
        }
        DiagnosticLogStore.record("bubble_action", "whatsapp.open number=" + target.displayNumber + " package_opened=" + opened)
    } // bubble_whatsapp_capture_0_1_118

"""
    text = text.replaceFirst(insertionPoint, helpers + insertionPoint)

    listOf(
        "bubble_whatsapp_capture_0_1_118",
        "capturePhoneAndOpenWhatsApp118()",
        "ScreenPhoneLink.findBest",
        "ocrService.extractText(bitmap)",
        "openWhatsAppTarget118(target)",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("WhatsApp 0.1.118 incompleto: $marker")
    }
    file.writeText(text)
}

val bubbleWhatsAppCaptureCompat118 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("bubbleResourceShortcutsRuntime117")
    doLast { enforceBubbleWhatsAppCapture118(serviceFile.asFile) }
}

bubbleWhatsAppCaptureCompat118.configure {
    mustRunAfter("bubbleResourceShortcutsRuntime117")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleWhatsAppCaptureCompat118)
}
