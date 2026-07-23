// Contrato final da etapa 12 — print manual completo, pacote identificado e modelo persistido.

fun verifyManualCardCaptureChecklist12(root: java.io.File) {
    val catalog = java.io.File(root, "BubbleShortcutModule.kt").readText()
    val module = java.io.File(root, "ManualRideCardCaptureBubbleShortcutModule.kt").readText()
    val service = java.io.File(root, "LiveRideAccessibilityService.kt").readText()
    val store = java.io.File(root, "AutomaticRideCaptureStore.kt").readText()

    listOf(
        "require(modules.size == 16)",
        "ManualRideCardCaptureBubbleShortcutModule,",
    ).forEach { marker ->
        if (marker !in catalog) throw GradleException("Catálogo da captura manual incompleto: $marker")
    }
    listOf(
        "id = \"manual_card_capture\"",
        "label = \"Capturar card agora\"",
        "BubbleShortcutAction.SaveRideCard",
    ).forEach { marker ->
        if (marker !in module) throw GradleException("Módulo da captura manual incompleto: $marker")
    }

    listOf(
        "BubbleShortcutAction.SaveRideCard -> captureAndRegisterRideCardManualChecklist12()",
        "manual_card_capture_complete_checklist_12",
        "SelectedRideAppStore.read(applicationContext)",
        "SelectedRideOverlayWindowPolicy.resolve(",
        "takeScreenshot(",
        "Display.DEFAULT_DISPLAY",
        "screenshot.toSoftwareBitmap()",
        "ocrService.extractText(bitmap)",
        "RideCardTemplateMatcher.createTemplate(packageNameChecklist12, textChecklist12)",
        "repository.addCardTemplate(templateChecklist12)",
        "automaticRideCaptureStore129.saveCard(",
        "allowIncompleteManual = true",
        "AutomaticRideCaptureKind.Candidate",
        "AutomaticRideCaptureKind.Matched",
        "Card completo salvo e modelo criado",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Serviço da captura manual incompleto: $marker")
    }

    listOf(
        "allowIncompleteManual: Boolean = false",
        "manual_incomplete_capture_store_checklist_12",
        "ManualRideCardCapturePolicy.evaluate(",
    ).forEach { marker ->
        if (marker !in store) throw GradleException("Armazenamento manual incompleto: $marker")
    }

    val manualStart = service.indexOf("private fun captureAndRegisterRideCardManualChecklist12()")
    val manualEnd = service.indexOf("private fun copyTripConfirmationFromBubbleChecklist8()", manualStart)
        .takeIf { it > manualStart }
        ?: service.indexOf("private fun capturePhoneAndOpenWhatsApp118()", manualStart)
    if (manualStart < 0 || manualEnd <= manualStart) throw GradleException("Fluxo manual não localizado.")
    val manualRegion = service.substring(manualStart, manualEnd)
    if ("startContinuousScan" in manualRegion || "repository.saveDiagnostic" in manualRegion) {
        throw GradleException("A captura manual entrou no ciclo contínuo ou no diagnóstico persistente.")
    }

    val scanStart = service.indexOf("private fun startContinuousScan()")
    val scanEnd = service.indexOf("private fun startProximityAlertMonitor()", scanStart)
    if (scanStart >= 0 && scanEnd > scanStart) {
        val scanRegion = service.substring(scanStart, scanEnd)
        if ("captureAndRegisterRideCardManualChecklist12" in scanRegion) {
            throw GradleException("A captura manual voltou ao ciclo automático.")
        }
    }

    listOf(
        "strict_selected_app_policy_checklist_1",
        "diagnostics_off_checklist_4",
        "overlay_before_storage_final_checklist_6",
        "ocr_outside_critical_path_final_checklist_6",
        "real_session_video_fixes_complete_checklist_11",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Proteção anterior perdida: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        verifyManualCardCaptureChecklist12(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
