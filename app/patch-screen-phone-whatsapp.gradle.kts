val patchScreenPhoneWhatsApp by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val phoneModuleFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ScreenPhoneLink.kt")
    inputs.files(serviceFile, phoneModuleFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
        if (!phoneModuleFile.asFile.exists()) throw GradleException("ScreenPhoneLink.kt nao encontrado.")

        var text = file.readText()
        val original = text

        if ("import android.net.Uri" !in text) {
            text = text.replace(
                "import android.content.Intent\n",
                "import android.content.Intent\nimport android.net.Uri\n",
            )
        }

        if ("private var whatsappShortcutView" !in text) {
            text = text.replace(
                "    private var overlayMenuParams: WindowManager.LayoutParams? = null\n",
                """    private var overlayMenuParams: WindowManager.LayoutParams? = null
    private var whatsappShortcutView: TextView? = null
    private var whatsappShortcutParams: WindowManager.LayoutParams? = null
    private val phoneCaptureInProgress = AtomicBoolean(false)
""",
            )
        }

        if ("showWhatsAppShortcut() // screen_phone_whatsapp_0_1_84" !in text) {
            text = text.replaceFirst(
                "            showOverlay(RadarColor.Idle)\n",
                """            showOverlay(RadarColor.Idle)
            showWhatsAppShortcut() // screen_phone_whatsapp_0_1_84
""",
            )
        }

        if ("removeWhatsAppShortcut()" !in text.substringAfter("override fun onDestroy()", "")) {
            text = text.replaceFirst(
                "        removeOverlay()\n",
                """        removeOverlay()
        removeWhatsAppShortcut()
""",
            )
        }

        if ("updateWhatsAppShortcutPosition()" !in text.substringAfter("private inner class BubbleTouchListener", "")) {
            text = text.replaceFirst(
                "                    updateActionMenuPosition()\n",
                """                    updateActionMenuPosition()
                    updateWhatsAppShortcutPosition()
""",
            )
        }

        if ("private fun showWhatsAppShortcut()" !in text) {
            val insertionPoint = "    private fun toast(message: String) {"
            if (insertionPoint !in text) {
                throw GradleException("Nao encontrei o ponto de integracao do atalho WhatsApp.")
            }
            val helpers = """    private fun showWhatsAppShortcut() {
        if (!serviceReady || whatsappShortcutView != null) return
        val manager = windowManager ?: return
        val bubbleParams = overlayParams ?: return
        val shortcut = TextView(this).apply {
            text = "WA"
            contentDescription = "Abrir WhatsApp com telefone da tela"
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            elevation = dp(5).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(37, 211, 102))
                setStroke(dp(2), Color.WHITE)
            }
            setOnClickListener { capturePhoneAndOpenWhatsApp() }
        }
        val params = whatsappShortcutLayoutParams(bubbleParams)
        if (runCatching { manager.addView(shortcut, params) }.isSuccess) {
            whatsappShortcutView = shortcut
            whatsappShortcutParams = params
        }
    }

    private fun whatsappShortcutLayoutParams(
        bubbleParams: WindowManager.LayoutParams,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(48),
        dp(48),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = whatsappShortcutX(bubbleParams)
        y = bubbleParams.y + dp(9)
    }

    private fun whatsappShortcutX(bubbleParams: WindowManager.LayoutParams): Int {
        val gap = dp(8)
        val shortcutSize = dp(48)
        val rightX = bubbleParams.x + dp(66) + gap
        val screenWidth = resources.displayMetrics.widthPixels
        return if (rightX + shortcutSize <= screenWidth) {
            rightX
        } else {
            (bubbleParams.x - shortcutSize - gap).coerceAtLeast(0)
        }
    }

    private fun updateWhatsAppShortcutPosition() {
        val manager = windowManager ?: return
        val shortcut = whatsappShortcutView ?: return
        val params = whatsappShortcutParams ?: return
        val bubbleParams = overlayParams ?: return
        params.x = whatsappShortcutX(bubbleParams)
        params.y = bubbleParams.y + dp(9)
        runCatching { manager.updateViewLayout(shortcut, params) }
    }

    private fun removeWhatsAppShortcut() {
        val shortcut = whatsappShortcutView ?: return
        runCatching { windowManager?.removeView(shortcut) }
        whatsappShortcutView = null
        whatsappShortcutParams = null
        phoneCaptureInProgress.set(false)
    }

    private fun capturePhoneAndOpenWhatsApp() {
        if (!phoneCaptureInProgress.compareAndSet(false, true)) return

        val directTarget = ScreenPhoneLink.findBest(collectVisibleTextForAction())
            ?: ScreenPhoneLink.findBest(mergeRideTexts(lastAccessibilityText, lastOcrText))
        if (directTarget != null) {
            phoneCaptureInProgress.set(false)
            openWhatsAppTarget(directTarget)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            phoneCaptureInProgress.set(false)
            toast("Nenhum telefone brasileiro com DDD foi encontrado na tela.")
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
                phoneCaptureInProgress.set(false)
                toast("Nao consegui capturar o telefone agora. Toque em WA novamente.")
                return@launch
            }
            requestPhoneScreenshot()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun requestPhoneScreenshot() {
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            val target = runCatching {
                                val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching null
                                try {
                                    ScreenPhoneLink.findBest(ocrService.extractText(bitmap))
                                } finally {
                                    bitmap.recycle()
                                }
                            }.getOrNull()
                            screenshotInProgress.set(false)
                            phoneCaptureInProgress.set(false)
                            if (target != null) {
                                openWhatsAppTarget(target)
                            } else {
                                toast("Nenhum telefone brasileiro com DDD foi encontrado na tela.")
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        phoneCaptureInProgress.set(false)
                        toast("O Android nao permitiu ler a tela agora. Codigo: " + errorCode)
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            phoneCaptureInProgress.set(false)
            toast("Nao consegui capturar o telefone da tela.")
        }
    }

    private fun openWhatsAppTarget(target: ScreenPhoneTarget) {
        val uri = Uri.parse(target.url)
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        val openedInWhatsApp = packages.any { packageName ->
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
        if (openedInWhatsApp) {
            toast("WhatsApp aberto: " + target.displayNumber)
            return
        }

        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onSuccess {
            toast("Link do WhatsApp aberto: " + target.displayNumber)
        }.onFailure {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("WhatsApp", target.url))
            toast("Nao encontrei o WhatsApp. Link copiado.")
        }
    }

"""
            text = text.replace(insertionPoint, helpers + insertionPoint)
        }

        if (
            "screen_phone_whatsapp_0_1_84" !in text ||
            "private fun capturePhoneAndOpenWhatsApp()" !in text ||
            "private fun openWhatsAppTarget(" !in text
        ) {
            throw GradleException("O modulo de telefone/WhatsApp nao foi integrado ao servico.")
        }

        if (text != original) file.writeText(text)
    }
}

patchScreenPhoneWhatsApp.configure {
    mustRunAfter(
        "patchPassiveEventCompileFix",
        "coreScreenReadEngineInlinePatch",
        "coreLiveAnalysisPipelinePatch",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchScreenPhoneWhatsApp)
}
