// Checklist 12 — captura manual do card pela grade, independente da sessão automática.

fun patchManualCaptureStoreChecklist12(file: java.io.File) {
    if (!file.exists()) throw GradleException("AutomaticRideCaptureStore.kt ausente no checklist 12.")
    var text = file.readText()
    if ("manual_incomplete_capture_store_checklist_12" in text) return

    val parameterAnchor = "        nowMillis: Long = System.currentTimeMillis(),\n    ): AutomaticRideCapture? = withContext(Dispatchers.IO) {\n"
    if (parameterAnchor !in text) throw GradleException("Assinatura saveCard não localizada no checklist 12.")
    text = text.replaceFirst(
        parameterAnchor,
        """        nowMillis: Long = System.currentTimeMillis(),
        allowIncompleteManual: Boolean = false,
    ): AutomaticRideCapture? = withContext(Dispatchers.IO) {
""",
    )

    val guard = "        if (!AutomaticRideCapturePolicy.isUseful(fields, bitmap.width, bitmap.height)) return@withContext null\n"
    if (guard !in text) throw GradleException("Portaria da captura automática não localizada no checklist 12.")
    text = text.replaceFirst(
        guard,
        """        val regularUsefulChecklist12 = AutomaticRideCapturePolicy.isUseful(fields, bitmap.width, bitmap.height)
        val manualUsefulChecklist12 = allowIncompleteManual && ManualRideCardCapturePolicy.evaluate(
            packageSelected = true,
            text = text,
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            looksLikeRideCard = RideCardTemplateMatcher.looksLikeLearnableRideCard(text),
        ).canStoreImage
        if (!regularUsefulChecklist12 && !manualUsefulChecklist12) return@withContext null // manual_incomplete_capture_store_checklist_12
""",
    )
    file.writeText(text)
}

fun patchManualCaptureCatalogChecklist12(file: java.io.File) {
    if (!file.exists()) throw GradleException("BubbleShortcutModule.kt ausente no checklist 12.")
    var text = file.readText()
    val listToken = "    val modules: List<BubbleShortcutModule> = listOf(\n"
    val start = text.indexOf(listToken)
    val end = if (start >= 0) text.indexOf("    )", start + listToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Catálogo final não localizado no checklist 12.")
    var region = text.substring(start, end)
    if ("ManualRideCardCaptureBubbleShortcutModule," !in region) {
        val cardsAnchor = "        CardsManagementBubbleShortcutModule,\n"
        region = if (cardsAnchor in region) {
            region.replaceFirst(cardsAnchor, "        ManualRideCardCaptureBubbleShortcutModule,\n" + cardsAnchor)
        } else {
            region + "        ManualRideCardCaptureBubbleShortcutModule,\n"
        }
        text = text.substring(0, start) + region + text.substring(end)
    }
    text = text.replace(
        Regex("""require\(modules\.size == \d+\) \{ "[^"]*" \}"""),
        "require(modules.size == 16) { \"O popup deve conter 16 módulos.\" }",
    )
    file.writeText(text)
}

fun patchManualCaptureServiceChecklist12(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 12.")
    var text = file.readText()
    if ("manual_card_capture_complete_checklist_12" in text) return

    val fieldAnchor = "    private val screenshotInProgress = AtomicBoolean(false)\n"
    if (fieldAnchor !in text) throw GradleException("Controle global de screenshot ausente no checklist 12.")
    text = text.replaceFirst(
        fieldAnchor,
        fieldAnchor + "    private val manualCardCaptureInProgressChecklist12 = AtomicBoolean(false)\n",
    )

    val executeStart = text.indexOf("    private fun executeShortcutModule(spec: BubbleShortcutSpec) {")
    val executeEnd = if (executeStart >= 0) text.indexOf("    private fun ", executeStart + 20) else -1
    if (executeStart < 0 || executeEnd <= executeStart) throw GradleException("Executor dos atalhos ausente no checklist 12.")
    var executeRegion = text.substring(executeStart, executeEnd)
    val existingSaveAction = Regex("BubbleShortcutAction\\.SaveRideCard\\s*->[^\\n]+")
    executeRegion = if (existingSaveAction.containsMatchIn(executeRegion)) {
        executeRegion.replace(existingSaveAction, "BubbleShortcutAction.SaveRideCard -> captureAndRegisterRideCardManualChecklist12()")
    } else {
        val whenAnchor = "        when (spec.action) {\n"
        if (whenAnchor !in executeRegion) throw GradleException("When dos atalhos ausente no checklist 12.")
        executeRegion.replaceFirst(
            whenAnchor,
            whenAnchor + "            BubbleShortcutAction.SaveRideCard -> captureAndRegisterRideCardManualChecklist12()\n",
        )
    }
    text = text.substring(0, executeStart) + executeRegion + text.substring(executeEnd)

    val helperAnchor = when {
        "    private fun copyTripConfirmationFromBubbleChecklist8() {" in text -> "    private fun copyTripConfirmationFromBubbleChecklist8() {"
        "    private fun capturePhoneAndOpenWhatsApp118() {" in text -> "    private fun capturePhoneAndOpenWhatsApp118() {"
        else -> throw GradleException("Ponto de inserção da captura manual ausente no checklist 12.")
    }

    val helper = """    private fun captureAndRegisterRideCardManualChecklist12() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!manualCardCaptureInProgressChecklist12.compareAndSet(false, true)) {
            toast("A captura manual do card já está em andamento.")
            return
        }
        scope.launch {
            delay(220L)
            requestManualRideCardScreenshotChecklist12(attempt = 0)
        }
    }

    private fun requestManualRideCardScreenshotChecklist12(attempt: Int) {
        val selectedPackagesChecklist12 = SelectedRideAppStore.read(applicationContext)
        val nowChecklist12 = System.currentTimeMillis()
        val packageNameChecklist12 = SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = currentRootPackageName(),
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = selectedPackagesChecklist12,
            nowMillis = nowChecklist12,
        ) ?: currentRootPackageName()?.takeIf { it in selectedPackagesChecklist12 }

        if (packageNameChecklist12 == null) {
            manualCardCaptureInProgressChecklist12.set(false)
            toast("Abra um card em um aplicativo selecionado e tente novamente.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            manualCardCaptureInProgressChecklist12.set(false)
            toast("A captura completa exige Android 11 ou superior.")
            return
        }
        if (!screenshotInProgress.compareAndSet(false, true)) {
            if (attempt < 5) {
                scope.launch {
                    delay(140L)
                    requestManualRideCardScreenshotChecklist12(attempt + 1)
                }
            } else {
                manualCardCaptureInProgressChecklist12.set(false)
                toast("A tela está sendo lida. Tente novamente em um instante.")
            }
            return
        }

        toast("Capturando o card completo...")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmapChecklist12: Bitmap? = null
                            try {
                                bitmapChecklist12 = screenshot.toSoftwareBitmap()
                                val bitmap = bitmapChecklist12
                                if (bitmap == null) {
                                    toast("O Android não entregou a imagem da tela.")
                                    return@launch
                                }
                                val textChecklist12 = ocrService.extractText(bitmap)
                                val parsedChecklist12 = parser.parseWithMetadata(textChecklist12, packageNameChecklist12)
                                val triggerChecklist12 = withContext(Dispatchers.Default) {
                                    UniversalAddressTrigger.evaluate(textChecklist12)
                                }
                                val fieldsChecklist12 = RideFields(
                                    pickup = parsedChecklist12.fields.pickup ?: triggerChecklist12.pickup,
                                    destination = parsedChecklist12.fields.destination ?: triggerChecklist12.destination,
                                    fare = parsedChecklist12.fields.fare,
                                )
                                val looksLikeCardChecklist12 = RideCardTemplateMatcher.looksLikeLearnableRideCard(textChecklist12)
                                val evaluationChecklist12 = ManualRideCardCapturePolicy.evaluate(
                                    packageSelected = packageNameChecklist12 in selectedPackagesChecklist12,
                                    text = textChecklist12,
                                    bitmapWidth = bitmap.width,
                                    bitmapHeight = bitmap.height,
                                    looksLikeRideCard = looksLikeCardChecklist12,
                                )
                                if (!evaluationChecklist12.canStoreImage) {
                                    toast(evaluationChecklist12.reason)
                                    return@launch
                                }

                                val templateChecklist12 = if (evaluationChecklist12.canCreateTemplate) {
                                    RideCardTemplateMatcher.createTemplate(packageNameChecklist12, textChecklist12)
                                } else {
                                    null
                                }
                                val captureChecklist12 = automaticRideCaptureStore129.saveCard(
                                    bitmap = bitmap,
                                    packageName = packageNameChecklist12,
                                    text = textChecklist12,
                                    fields = fieldsChecklist12,
                                    kind = if (templateChecklist12 != null) {
                                        AutomaticRideCaptureKind.Matched
                                    } else {
                                        AutomaticRideCaptureKind.Candidate
                                    },
                                    matchedTemplateId = templateChecklist12?.id,
                                    matchedTemplateName = templateChecklist12?.name,
                                    allowIncompleteManual = true,
                                )
                                if (captureChecklist12 == null) {
                                    toast("Não consegui armazenar o print completo.")
                                    return@launch
                                }

                                if (templateChecklist12 != null) {
                                    repository.addCardTemplate(templateChecklist12)
                                }
                                repository.addCapturedScreen(
                                    CapturedRideScreen(
                                        createdAtMillis = System.currentTimeMillis(),
                                        packageName = packageNameChecklist12,
                                        textHash = textChecklist12.hashCode(),
                                        textPreview = textChecklist12.trim().take(500),
                                        parserName = parsedChecklist12.parserName,
                                        pickup = fieldsChecklist12.pickup,
                                        destination = fieldsChecklist12.destination,
                                        fare = fieldsChecklist12.fare,
                                    ),
                                )
                                if (templateChecklist12 != null) {
                                    toast("Card completo salvo e modelo criado. O farol já pode reconhecê-lo.")
                                } else {
                                    toast("Print completo salvo em Cards. Abra um card mais nítido para criar o modelo.")
                                }
                            } catch (_: Throwable) {
                                toast("Não consegui concluir a captura manual do card.")
                            } finally {
                                bitmapChecklist12?.recycle()
                                screenshotInProgress.set(false)
                                manualCardCaptureInProgressChecklist12.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        manualCardCaptureInProgressChecklist12.set(false)
                        toast("O Android bloqueou o print desta tela. Mantenha o card aberto e tente novamente.")
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            manualCardCaptureInProgressChecklist12.set(false)
            toast("Não consegui solicitar o print completo da tela.")
        }
    }

    // manual_card_capture_complete_checklist_12

"""
    text = text.replaceFirst(helperAnchor, helper + helperAnchor)
    file.writeText(text)
}

fun patchManualCardCaptureChecklist12(root: java.io.File) {
    patchManualCaptureStoreChecklist12(java.io.File(root, "AutomaticRideCaptureStore.kt"))
    patchManualCaptureCatalogChecklist12(java.io.File(root, "BubbleShortcutModule.kt"))
    patchManualCaptureServiceChecklist12(java.io.File(root, "LiveRideAccessibilityService.kt"))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchManualCardCaptureChecklist12(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        patchManualCardCaptureChecklist12(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
