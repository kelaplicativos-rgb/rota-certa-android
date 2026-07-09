val patchRemoveLiveDiagnostics by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        fun replaceBetween(source: String, startMarker: String, nextMarker: String, replacement: String): String {
            val start = source.indexOf(startMarker)
            if (start < 0) return source
            val next = source.indexOf(nextMarker, start)
            if (next < 0) return source
            return source.substring(0, start) + replacement + source.substring(next)
        }

        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = replaceBetween(
                source = text,
                startMarker = "    private suspend fun saveCapturedReadToHistory(",
                nextMarker = "    private suspend fun saveCapturedCardScreen(",
                replacement = """    private suspend fun saveCapturedReadToHistory(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        reason: String,
    ) = Unit

""",
            )

            text = replaceBetween(
                source = text,
                startMarker = "    private suspend fun saveCapturedCardScreen(",
                nextMarker = "    private suspend fun analyzeLiveText(",
                replacement = """    private suspend fun saveCapturedCardScreen(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        parserName: String,
        packageName: String?,
    ) = Unit

""",
            )

            text = replaceBetween(
                source = text,
                startMarker = "    private fun recordDiagnostic(",
                nextMarker = "    private fun traceEvent(",
                replacement = """    private fun recordDiagnostic(
        stage: String,
        color: RadarColor? = null,
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
        result: AnalysisResult? = null,
        error: Throwable? = null,
        cardTemplateMatch: RideCardTemplateMatch? = null,
    ) = Unit

""",
            )

            text = replaceBetween(
                source = text,
                startMarker = "    private fun traceEvent(",
                nextMarker = "    private fun String?.diagnosticValue(",
                replacement = """    private fun traceEvent(message: String) = Unit

    private fun String.withDiagnosticEvents(): String = this

""",
            )

            text = text.replace(
                "            repository.addAnalysis(result)\n            lastSavedReadHash = snapshotHash",
                "            lastSavedReadHash = snapshotHash",
            )
            text = text.replace(
                "            newView.setOnClickListener { toggleActionMenu() }",
                "            newView.setOnClickListener { openApp() }",
            )
            text = text.replace(
                "const val SCAN_LOOP_MS = 850L",
                "const val SCAN_LOOP_MS = 300L",
            )
            text = text.replace(
                "const val SCREENSHOT_INTERVAL_MS = 650L",
                "const val SCREENSHOT_INTERVAL_MS = 300L",
            )
            text = text.replace(
                "else -> distanceKm.roundToInt().coerceAtMost(99).toString()",
                "else -> String.format(Locale(\"pt\", \"BR\"), \"%.1f\", distanceKm).removeSuffix(\",0\")",
            )

            if (text != original) file.writeText(text)
        }

        mainFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
                "    val history by repository.analyses.collectAsState(initial = emptyList())\n    val diagnostic by repository.diagnostic.collectAsState(initial = null)",
                "    val history = emptyList<AnalysisResult>()\n    val diagnostic: LiveDiagnostic? = null",
            )
            text = text.replace(
                "                NavigationBarItem(selected = tab == TAB_HISTORY, onClick = { tab = TAB_HISTORY }, label = { Text(\"Historico\") }, icon = {})\n",
                "",
            )
            text = text.replace(
                Regex("""\n        DiagnosticExpander\(\n            diagnostic = diagnostic,\n            cardTemplates = cardTemplates,\n            onRegisterRideCard = onRegisterRideCard,\n        \)"""),
                "",
            )

            if (text != original) file.writeText(text)
        }
    }
}

patchRemoveLiveDiagnostics.configure {
    mustRunAfter("patchFastPopupAnalysis")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchRemoveLiveDiagnostics)
}
