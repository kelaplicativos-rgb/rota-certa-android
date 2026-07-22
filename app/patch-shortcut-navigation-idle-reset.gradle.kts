val shortcutNavigationIdleReset by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchServiceShortcutNavigation(serviceFile.asFile)
        patchMainShortcutNavigation(mainFile.asFile)
    }
}

shortcutNavigationIdleReset.configure {
    mustRunAfter(
        "diagnosticJsonToolsActions",
        "patchPassiveScreenshotFailureGuard",
        "patchSetupWarningForceOverlay",
        "bubbleRouteDistanceOnly",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(shortcutNavigationIdleReset)
}

fun replacePrivateFunctionBlock(text: String, functionName: String, replacement: String): String {
    val start = text.indexOf("    private fun $functionName")
    if (start < 0) return text
    val end = text.indexOf("\n    private fun ", start + 1)
    if (end < 0) return text.substring(0, start) + replacement
    return text.substring(0, start) + replacement + text.substring(end + 1)
}

fun patchServiceShortcutNavigation(file: java.io.File) {
    var text = file.readText()
    val original = text
    val dollar = "$"

    if ("private fun forceIdleOverlay(reason: String)" !in text) {
        val anchor = "    private fun openApp("
        val helper = """
    private fun forceIdleOverlay(reason: String) {
        lastSnapshotHash = null
        lastAnalyzedHash = null
        registeredCardGate.clear()
        clearRememberedRideText()
        currentDistanceKm = null
        currentBubbleLabel = null
        currentRadarColor = RadarColor.Idle
        traceEvent("overlay.force_idle reason=${dollar}reason")
        showOverlay(RadarColor.Idle, distanceKm = null, labelText = null)
    }

"""
        val index = text.indexOf(anchor)
        if (index >= 0) text = text.substring(0, index) + helper + text.substring(index)
    }

    text = replacePrivateFunctionBlock(text, "resetToIdle", """
    private fun resetToIdle(
        reason: String,
        record: Boolean = false,
    ) {
        forceIdleOverlay(reason)
        if (record) {
            recordDiagnostic(stage = "idle", color = RadarColor.Idle, reason = reason)
        }
    }

""")

    text = replacePrivateFunctionBlock(text, "openApp", """
    private fun openApp(tab: String? = null, expander: String? = null) {
        hideActionMenu()
        forceIdleOverlay("Abrindo Rota Certa pela bolinha.")
        traceEvent("shortcut.navigation.open_app tab=${dollar}{tab.orEmpty()} expander=${dollar}{expander.orEmpty()}")
        runCatching {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (!tab.isNullOrBlank()) intent.putExtra(EXTRA_OPEN_TAB, tab)
            if (!expander.isNullOrBlank()) intent.putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", expander)
            startActivity(intent)
        }.onFailure {
            toast("Nao consegui abrir o Rota Certa agora.")
        }
    }

""")

    text = replacePrivateFunctionBlock(text, "openSavedPlaceEditor", """
    private fun openSavedPlaceEditor(place: SavedPlace) {
        hideActionMenu()
        val expander = if (place.type == SavedPlaceType.ProximityAlert) "Alertas de proximidade" else "Locais salvos"
        forceIdleOverlay("Abrindo item salvo pela bolinha.")
        traceEvent("bubble.open_saved_place_editor id=${dollar}{place.id} type=${dollar}{place.type} expander=${dollar}expander")
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_TOOLS)
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", expander)
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
            )
        }.onFailure {
            toast("Local salvo, mas nao consegui abrir a tela de edicao agora.")
        }
    }

""")

    if ("shortcut.navigation.patch_applied" !in text) {
        text = text.replace(
            "        traceEvent(\"service.onServiceConnected ready=true\")\n",
            "        traceEvent(\"service.onServiceConnected ready=true\")\n        traceEvent(\"shortcut.navigation.patch_applied=true\")\n",
        )
    }

    if (text != original) {
        file.writeText(text)
    }
}

fun patchMainShortcutNavigation(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
        "if (initiallyExpanded || requestedExpander == title) expanded = true",
        "if (initiallyExpanded || requestedExpander == title || (requestedExpander != null && title.startsWith(requestedExpander))) expanded = true",
    )

    text = text.replace(
        "val place = savedPlaces.firstOrNull { it.id == id && it.type == SavedPlaceType.Place }",
        "val place = savedPlaces.firstOrNull { it.id == id }",
    )
    text = text.replace(
        "savedPlaces.firstOrNull { it.id == savedPlaceNameDialogId && it.type == SavedPlaceType.Place }?.let { place ->",
        "savedPlaces.firstOrNull { it.id == savedPlaceNameDialogId }?.let { place ->",
    )

    if ("OPEN_EXPANDER" !in text) {
        text = text.replace(
            "highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)",
            "highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)",
        )
    }

    if (text != original) {
        file.writeText(text)
    }
}
