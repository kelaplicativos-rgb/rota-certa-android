val patchRemoveLiveDiagnostics by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val parserFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt")
    inputs.files(serviceFile, mainFile, parserFile)
    outputs.upToDateWhen { false }

    doLast {
        fun replaceBetween(source: String, startMarker: String, nextMarker: String, replacement: String): String {
            val start = source.indexOf(startMarker)
            if (start < 0) return source
            val next = source.indexOf(nextMarker, start)
            if (next < 0) return source
            return source.substring(0, start) + replacement + source.substring(next)
        }

        fun stripFunctionCalls(source: String, functionName: String): String {
            var text = source
            var searchFrom = 0
            val callNeedle = "$functionName("
            while (true) {
                val callStart = text.indexOf(callNeedle, searchFrom)
                if (callStart < 0) break
                val prefix = text.substring(maxOf(0, callStart - 24), callStart)
                if (prefix.contains("fun ")) {
                    searchFrom = callStart + callNeedle.length
                    continue
                }

                var index = callStart + functionName.length
                var depth = 0
                var inString = false
                var inTripleString = false
                var escaped = false
                while (index < text.length) {
                    if (inTripleString) {
                        if (text.startsWith("\"\"\"", index)) {
                            inTripleString = false
                            index += 3
                        } else {
                            index += 1
                        }
                        continue
                    }
                    if (inString) {
                        val char = text[index]
                        if (escaped) {
                            escaped = false
                        } else if (char == '\\') {
                            escaped = true
                        } else if (char == '"') {
                            inString = false
                        }
                        index += 1
                        continue
                    }
                    if (text.startsWith("\"\"\"", index)) {
                        inTripleString = true
                        index += 3
                        continue
                    }
                    val char = text[index]
                    if (char == '"') {
                        inString = true
                    } else if (char == '(') {
                        depth += 1
                    } else if (char == ')') {
                        depth -= 1
                        if (depth == 0) {
                            index += 1
                            break
                        }
                    }
                    index += 1
                }
                if (depth != 0) {
                    searchFrom = callStart + callNeedle.length
                    continue
                }

                var end = index
                while (end < text.length && text[end].isWhitespace() && text[end] != '\n') end += 1
                if (end < text.length && text[end] == '\n') end += 1
                text = text.substring(0, callStart) + "Unit\n" + text.substring(end)
                searchFrom = callStart + 5
            }
            return text
        }

        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = stripFunctionCalls(text, "traceEvent")
            text = stripFunctionCalls(text, "recordDiagnostic")

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
                "                NavigationBarItem(selected = tab == TAB_DIAGNOSTIC, onClick = { tab = TAB_DIAGNOSTIC }, label = { Text(\"Diagnostico\") }, icon = {})\n",
                "",
            )
            text = text.replace(
                "requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY || requestedTab == TAB_DIAGNOSTIC",
                "requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY",
            )
            text = text.replace(
                "                TAB_HISTORY -> TAB_DIAGNOSTIC\n",
                "                TAB_HISTORY -> TAB_TOOLS\n",
            )
            text = text.replace(
                Regex("""\n\s*TAB_HISTORY, TAB_DIAGNOSTIC -> DiagnosticScreen\(\n\s*diagnostic = diagnostic,\n\s*cardTemplates = cardTemplates,\n\s*history = history,\n\s*onRegisterRideCard = ::registerRideCard,\n\s*\)"""),
                "\n                else -> Unit",
            )
            text = text.replace(
                Regex("""\n@Composable\nprivate fun DiagnosticScreen\([\s\S]*?\n@Composable\nprivate fun HistoryScreen\(history: List<AnalysisResult>\) \{\n    HistoryDiagnosticCard\(history = history\)\n\}\n"""),
                "\n@Composable\nprivate fun HistoryScreen(history: List<AnalysisResult>) = Unit\n",
            )
            text = replaceBetween(
                source = text,
                startMarker = "private fun copyHistory(context: Context, history: List<AnalysisResult>) {",
                nextMarker = "private fun clearClipboard(context: Context) {",
                replacement = "",
            )
            text = text.replace(
                "private const val TAB_DIAGNOSTIC = \"diagnostico\"\n\n",
                "",
            )
            text = text.replace(
                Regex("""\n        DiagnosticExpander\(\n            diagnostic = diagnostic,\n            cardTemplates = cardTemplates,\n            onRegisterRideCard = onRegisterRideCard,\n        \)"""),
                "",
            )

            if (text != original) file.writeText(text)
        }

        parserFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
                """        val addresses = findAddressCandidates(lines)
        val pickup = findAddressAfterMarker(lines, pickupMarkers) ?: addresses.firstOrNull()
        val destination = findAddressAfterMarker(lines, destinationMarkers) ?: addresses.asReversed().firstOrNull {
            !it.equals(pickup, ignoreCase = true)
        }
""",
                """        val addresses = findAddressCandidates(lines)
        val markerPickup = findAddressAfterMarker(lines, pickupMarkers)
        val markerDestination = findAddressAfterMarker(lines, destinationMarkers)
        val pickup = markerPickup ?: addresses.takeIf { it.size > 1 }?.firstOrNull()
        val destination = markerDestination ?: when {
            addresses.size == 1 -> addresses.first()
            else -> addresses.asReversed().firstOrNull { !it.equals(pickup, ignoreCase = true) }
        }
""",
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
