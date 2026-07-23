// Checklist 8 — copia manual da confirmação de viagem, sem trabalho contínuo.

fun addTripCopyImportChecklist8(source: String, importLine: String, anchor: String): String {
    if (importLine in source) return source
    if (anchor !in source) throw GradleException("Âncora de importação ausente para $importLine")
    return source.replaceFirst(anchor, anchor + importLine + "\n")
}

fun patchTripConfirmationCatalogChecklist8(file: java.io.File) {
    if (!file.exists()) throw GradleException("BubbleShortcutModule.kt ausente no checklist 8.")
    var catalog = file.readText()

    if ("    CopyTripConfirmation," !in catalog) {
        val enumAnchor = "enum class BubbleShortcutAction {\n"
        if (enumAnchor !in catalog) throw GradleException("Enum de atalhos ausente no checklist 8.")
        catalog = catalog.replaceFirst(enumAnchor, enumAnchor + "    CopyTripConfirmation,\n")
    }

    val listToken = "    val modules: List<BubbleShortcutModule> = listOf(\n"
    val listStart = catalog.indexOf(listToken)
    val listEnd = if (listStart >= 0) catalog.indexOf("    )", listStart + listToken.length) else -1
    if (listStart < 0 || listEnd < 0) throw GradleException("Lista de atalhos ausente no checklist 8.")
    var listRegion = catalog.substring(listStart, listEnd)
    if ("TripConfirmationBubbleShortcutModule," !in listRegion) {
        val whatsappAnchor = "        WhatsAppBubbleShortcutModule,\n"
        val quickReplyAnchor = "        QuickRepliesBubbleShortcutModule,\n"
        listRegion = when {
            whatsappAnchor in listRegion -> listRegion.replaceFirst(
                whatsappAnchor,
                whatsappAnchor + "        TripConfirmationBubbleShortcutModule,\n",
            )
            quickReplyAnchor in listRegion -> listRegion.replaceFirst(
                quickReplyAnchor,
                "        TripConfirmationBubbleShortcutModule,\n" + quickReplyAnchor,
            )
            else -> listRegion + "        TripConfirmationBubbleShortcutModule,\n"
        }
        catalog = catalog.substring(0, listStart) + listRegion + catalog.substring(listEnd)
    }

    val refreshedStart = catalog.indexOf(listToken)
    val refreshedEnd = catalog.indexOf("    )", refreshedStart + listToken.length)
    val refreshedRegion = catalog.substring(refreshedStart, refreshedEnd)
    val count = Regex("(?m)^\\s{8}[A-Za-z0-9_]+,\\s*$").findAll(refreshedRegion).count()
    catalog = catalog.replace(
        Regex("""require\(modules\.size == \d+\) \{ "[^"]*" \}"""),
        "require(modules.size == $count) { \"O popup deve conter $count módulos.\" }",
    )

    listOf(
        "CopyTripConfirmation",
        "TripConfirmationBubbleShortcutModule,",
        "require(modules.size == 15)",
    ).forEach { marker ->
        if (marker !in catalog) throw GradleException("Catálogo da confirmação incompleto: $marker")
    }
    file.writeText(catalog)
}

fun patchTripConfirmationServiceChecklist8(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 8.")
    var service = file.readText()
    if ("trip_confirmation_copy_complete_checklist_8" in service) return

    service = addTripCopyImportChecklist8(service, "import android.content.ClipData", "import android.content.Context\n")
    service = addTripCopyImportChecklist8(service, "import android.content.ClipboardManager", "import android.content.ClipData\n")

    if ("tripConfirmationCopyInProgressChecklist8" !in service) {
        val fieldAnchor = "    private val screenshotInProgress = AtomicBoolean(false)\n"
        if (fieldAnchor !in service) throw GradleException("Controle de screenshot ausente no checklist 8.")
        service = service.replaceFirst(
            fieldAnchor,
            fieldAnchor + "    private val tripConfirmationCopyInProgressChecklist8 = AtomicBoolean(false)\n",
        )
    }

    val executeStart = service.indexOf("    private fun executeShortcutModule(spec: BubbleShortcutSpec) {")
    val executeEnd = if (executeStart >= 0) service.indexOf("    private fun ", executeStart + 10) else -1
    if (executeStart < 0 || executeEnd < 0) throw GradleException("Executor de atalhos ausente no checklist 8.")
    var executeRegion = service.substring(executeStart, executeEnd)
    if ("trip_confirmation_action_checklist_8" !in executeRegion) {
        val whenAnchor = "        when (spec.action) {\n"
        if (whenAnchor !in executeRegion) throw GradleException("When dos atalhos ausente no checklist 8.")
        executeRegion = executeRegion.replaceFirst(
            whenAnchor,
            whenAnchor + "            BubbleShortcutAction.CopyTripConfirmation -> copyTripConfirmationFromBubbleChecklist8() // trip_confirmation_action_checklist_8\n",
        )
        service = service.substring(0, executeStart) + executeRegion + service.substring(executeEnd)
    }

    val helperAnchor = when {
        "    private fun openQuickRepliesFromBubble() {" in service -> "    private fun openQuickRepliesFromBubble() {"
        "    private fun openCollectorFromBubble() {" in service -> "    private fun openCollectorFromBubble() {"
        "    private fun toggleLiveReadingFromBubble() {" in service -> "    private fun toggleLiveReadingFromBubble() {"
        else -> throw GradleException("Ponto de inserção do copiador de viagem ausente.")
    }

    val helper = """    private fun copyTripConfirmationFromBubbleChecklist8() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        if (!tripConfirmationCopyInProgressChecklist8.compareAndSet(false, true)) {
            toast("A confirmação da viagem já está sendo preparada.")
            return
        }

        val accessibilityText = collectTripConfirmationVisibleTextChecklist8()
        val immediateMessage = TripConfirmationFormatter.extractAndFormat(accessibilityText)
        if (immediateMessage != null) {
            copyTripConfirmationToClipboardChecklist8(immediateMessage)
            tripConfirmationCopyInProgressChecklist8.set(false)
            return
        }
        requestTripConfirmationOcrChecklist8(accessibilityText, attempt = 0)
    }

    /**
     * Leitura manual, executada somente após o toque em Copiar viagem.
     * Não altera shouldScanPackage, não alimenta o farol e não fica em loop.
     */
    private fun collectTripConfirmationVisibleTextChecklist8(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
    } // manual_trip_tree_read_checklist_8

    private fun requestTripConfirmationOcrChecklist8(accessibilityText: String, attempt: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            tripConfirmationCopyInProgressChecklist8.set(false)
            toast("Abra a conversa no BlaBlaCar deixando rota, dia e horário visíveis.")
            return
        }
        if (!screenshotInProgress.compareAndSet(false, true)) {
            if (attempt < 5) {
                scope.launch {
                    delay(120L)
                    requestTripConfirmationOcrChecklist8(accessibilityText, attempt + 1)
                }
            } else {
                tripConfirmationCopyInProgressChecklist8.set(false)
                toast("A leitura da tela está ocupada. Tente novamente em um instante.")
            }
            return
        }

        toast("Lendo os dados da viagem...")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap: Bitmap? = null
                            try {
                                bitmap = screenshot.toSoftwareBitmap()
                                val ocrText = bitmap?.let { ocrService.extractText(it) }.orEmpty()
                                val combinedText = listOf(accessibilityText, ocrText)
                                    .filter(String::isNotBlank)
                                    .joinToString("\n")
                                val message = TripConfirmationFormatter.extractAndFormat(combinedText)
                                if (message == null) {
                                    toast("Não encontrei rota, dia e horário. Abra a conversa do passageiro no BlaBlaCar e tente novamente.")
                                } else {
                                    copyTripConfirmationToClipboardChecklist8(message)
                                }
                            } catch (_: Throwable) {
                                toast("Não consegui preparar a confirmação desta tela.")
                            } finally {
                                bitmap?.recycle()
                                screenshotInProgress.set(false)
                                tripConfirmationCopyInProgressChecklist8.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        tripConfirmationCopyInProgressChecklist8.set(false)
                        toast("O Android não permitiu ler esta tela. Deixe rota, dia e horário visíveis e tente novamente.")
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            tripConfirmationCopyInProgressChecklist8.set(false)
            toast("Não consegui solicitar a leitura manual da tela.")
        }
    }

    private fun copyTripConfirmationToClipboardChecklist8(message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Confirmação da viagem", message),
        )
        toast("Confirmação copiada. Abra o WhatsApp e cole.")
        overlayView?.announceForAccessibility("Confirmação da viagem copiada")
    }

    // trip_confirmation_copy_complete_checklist_8

"""
    service = service.replaceFirst(helperAnchor, helper + helperAnchor)

    listOf(
        "trip_confirmation_action_checklist_8",
        "trip_confirmation_copy_complete_checklist_8",
        "manual_trip_tree_read_checklist_8",
        "TripConfirmationFormatter.extractAndFormat",
        "collectTripConfirmationVisibleTextChecklist8()",
        "ocrService.extractText",
        "ClipData.newPlainText(\"Confirmação da viagem\"",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Serviço da confirmação incompleto: $marker")
    }
    file.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        patchTripConfirmationCatalogChecklist8(java.io.File(root, "BubbleShortcutModule.kt"))
        patchTripConfirmationServiceChecklist8(java.io.File(root, "LiveRideAccessibilityService.kt"))
    }
}
