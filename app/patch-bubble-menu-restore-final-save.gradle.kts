val bubbleMenuRestoreFinalSave by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        val finalToggle = """    private fun toggleActionMenu() {
        if (overlayMenuView != null) {
            traceEvent("diagnostic.contract bubble_menu step=closed_by_bubble ok=true")
            hideActionMenu()
        } else {
            showActionMenu()
        }
    }

"""
        text = text.replace(
            Regex("(?s)    private fun toggleActionMenu\\(\\) \\{.*?    private fun showActionMenu\\(\\)"),
            finalToggle + "    private fun showActionMenu()",
        )

        val finalShowMenu = """    private fun showActionMenu() {
        snapshotCurrentCardCandidateForBubbleAction("menu_open")
        traceEvent("diagnostic.contract bubble_menu step=opened ok=true expected_first=save_card candidate_len=${'$'}{lastCardSaveCandidateText.length} candidate_package=${'$'}{lastCardSaveCandidatePackageName.orEmpty()}")
        toast("Menu de atalhos aberto")
        val manager = windowManager ?: return
        if (overlayMenuView != null) return
        val bubbleParams = overlayParams ?: return
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(238, 32, 32, 32))
                setStroke(dp(1), Color.argb(220, 255, 255, 255))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            setOnTouchListener { _, event ->
                val isTopSaveRegion = event.y <= dp(70)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (isTopSaveRegion) {
                            traceEvent("diagnostic.contract bubble_menu step=top_region_down ok=true")
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isTopSaveRegion) {
                            traceEvent("diagnostic.contract bubble_menu step=top_region_up ok=true")
                            saveCurrentRideCardFromBubble()
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> isTopSaveRegion
                    else -> isTopSaveRegion
                }
            }
            addView(actionMenuItem("✅  SALVAR CARD DESTA TELA") {
                saveCurrentRideCardFromBubble()
            })
            addView(actionMenuItem("📸  Capturar dados do card") {
                saveCurrentRideCardFromBubble()
            })
            addView(actionMenuItem("🏠  Abrir Rota Certa") {
                traceEvent("diagnostic.contract save_card step=not_executed ok=false reason=open_app_clicked")
                traceEvent("bubble.open_app_button clicked")
                openApp()
            })
            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            })
        }
        val params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x + dp(76)
            y = bubbleParams.y
        }
        if (runCatching { manager.addView(menu, params) }.isSuccess) {
            overlayMenuView = menu
            overlayMenuParams = params
        }
    }

"""
        text = text.replace(
            Regex("(?s)    private fun showActionMenu\\(\\) \\{.*?    private fun actionMenuItem"),
            finalShowMenu + "    private fun actionMenuItem",
        )

        val finalActionMenuItem = """    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = if (label.contains("SALVAR CARD") || label.contains("Capturar dados")) 16.5f else 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = if (label.contains("SALVAR CARD") || label.contains("Capturar dados")) dp(56) else dp(42)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            isClickable = true
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        traceEvent("diagnostic.contract menu_item step=down ok=true label=${'$'}{label.take(40)}")
                        traceEvent("bubble.menu_item_down label=${'$'}{label.take(24)}")
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        traceEvent("diagnostic.contract menu_item step=up ok=true label=${'$'}{label.take(40)}")
                        traceEvent("bubble.menu_item_up label=${'$'}{label.take(24)}")
                        action()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
        }

"""
        text = text.replace(
            Regex("(?s)    private fun actionMenuItem\\(label: String, action: \\(\\) -> Unit\\): TextView =.*?    private fun hideActionMenu"),
            finalActionMenuItem + "    private fun hideActionMenu",
        )

        val finalSaveFunction = """    private fun saveCurrentRideCardFromBubble() {
        scope.launch {
            traceEvent("diagnostic.contract save_card step=started ok=true")
            traceEvent("bubble.save_card_start")
            toast("Capturando dados do card...")
            cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
            snapshotCurrentCardCandidateForBubbleAction("save_card_start")

            val livePackageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
                ?.takeIf { isLearnableRideAppPackage(it) }
                ?: lastCardSaveCandidatePackageName?.takeIf { it.isNotBlank() }
            val liveText = mergeRideTexts(lastAccessibilityText, lastOcrText)
                .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: ""

            var candidate = bestCardSaveCandidate(livePackageName, liveText)
                ?: pendingDirectCardSaveCandidate("save_start")

            if (candidate == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                toast("Capturando print da tela...")
                for (attempt in 0 until 3) {
                    cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 2_500L
                    lastScreenshotMillis = 0L
                    requestScreenshotAnalysis(allowPopupCandidate = true)
                    delay(700L)
                    candidate = bestCardSaveCandidate(null, "")
                        ?: pendingDirectCardSaveCandidate("after_screenshot_attempt_${'$'}attempt")
                    if (candidate != null) break
                }
            }

            val packageName = candidate?.first ?: livePackageName
            val text = candidate?.second ?: liveText
            candidate?.let {
                traceEvent("bubble.save_card_candidate.ready package=${'$'}{it.first} length=${'$'}{it.second.length}")
            }

            if (text.isBlank()) {
                traceEvent("diagnostic.contract save_card result=fail reason=text_blank pending_len=${'$'}{pendingDirectCardSaveText.length} last_len=${'$'}{lastCardSaveCandidateText.length} live_len=${'$'}{liveText.length}")
                toast("Nao consegui ler o card. Deixe o card aberto e toque em Capturar dados.")
                recordDiagnostic(
                    stage = "bubble_save_card_empty",
                    color = currentRadarColor,
                    reason = "Nao havia texto lido suficiente para salvar card de corrida pela bolinha.",
                    text = liveText,
                )
                return@launch
            }

            val inferredPackage = packageName?.takeIf { it.isNotBlank() } ?: run {
                traceEvent("diagnostic.contract save_card result=fail reason=missing_package text_len=${'$'}{text.length}")
                toast("Nao consegui identificar o pacote da tela para salvar.")
                recordDiagnostic(
                    stage = "bubble_save_card_missing_package",
                    color = currentRadarColor,
                    reason = "Card nao salvo: pacote da tela nao foi identificado.",
                    text = text,
                )
                return@launch
            }

            hideActionMenu()
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
            repository.addCardTemplate(template)
            rememberCardSaveCandidate(inferredPackage, text, "card_saved")
            pendingDirectCardSavePackageName = null
            pendingDirectCardSaveText = ""
            pendingDirectCardSaveAtMillis = 0L

            val parseResult = parser.parseWithMetadata(text, inferredPackage)
            repository.addCapturedScreen(
                CapturedRideScreen(
                    createdAtMillis = System.currentTimeMillis(),
                    packageName = inferredPackage,
                    textHash = text.snapshotHash(),
                    textPreview = text.trim().take(DIAGNOSTIC_TEXT_LIMIT),
                    parserName = parseResult.parserName,
                    pickup = parseResult.fields.pickup,
                    destination = parseResult.fields.destination,
                    fare = parseResult.fields.fare,
                ),
            )
            traceEvent("diagnostic.contract save_card result=success package=${'$'}inferredPackage text_len=${'$'}{text.length}")
            toast("Card de corrida salvo.")
            recordDiagnostic(
                stage = "bubble_save_card",
                color = currentRadarColor,
                reason = "Card de corrida salvo pela bolinha: ${'$'}{template.name}.",
                text = text,
                fields = parseResult.fields,
            )
        }
    }

"""
        text = text.replace(
            Regex("(?s)    private fun saveCurrentRideCardFromBubble\\(\\) \\{.*?    private fun clearRememberedRideText"),
            finalSaveFunction + "    private fun clearRememberedRideText",
        )

        if (text != original) file.writeText(text)
    }
}

bubbleMenuRestoreFinalSave.configure {
    mustRunAfter("bubblePendingDirectSaveCandidate")
    mustRunAfter("bubbleDirectSaveOnCandidate")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleMenuRestoreFinalSave)
}
