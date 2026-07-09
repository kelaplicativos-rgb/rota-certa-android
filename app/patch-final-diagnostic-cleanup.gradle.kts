val patchFinalDiagnosticCleanup by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
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

        val file = mainFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        text = stripFunctionCalls(text, "SavedPlaceNameDialog")
        text = text.replace(
            "    val history by repository.analyses.collectAsState(initial = emptyList())\n    val diagnostic by repository.diagnostic.collectAsState(initial = null)",
            "    val history = emptyList<AnalysisResult>()",
        )
        text = text.replace("    val diagnostic: LiveDiagnostic? = null\n", "")
        text = text.replace("                    diagnostic = diagnostic,\n", "")
        text = text.replace("                    onRegisterRideCard = ::registerRideCard,\n", "")
        text = text.replace("    diagnostic: LiveDiagnostic?,\n", "")
        text = text.replace("    onRegisterRideCard: (String?, String) -> Unit,\n", "")
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
            Regex("""\n\s*TAB_HISTORY,\s*TAB_DIAGNOSTIC -> DiagnosticScreen\([\s\S]*?\n\s*\)"""),
            "\n                else -> Unit",
        )
        text = text.replace(
            Regex("""\n\s*TAB_DIAGNOSTIC -> DiagnosticScreen\([\s\S]*?\n\s*\)"""),
            "\n                else -> Unit",
        )
        text = text.replace(
            Regex("""\n@Composable\nprivate fun DiagnosticScreen\([\s\S]*?\n@Composable\nprivate fun HistoryScreen\(history: List<AnalysisResult>\) \{\n    HistoryDiagnosticCard\(history = history\)\n\}\n"""),
            "\n@Composable\nprivate fun HistoryScreen(history: List<AnalysisResult>) = Unit\n",
        )
        text = replaceBetween(
            source = text,
            startMarker = "@Composable\nprivate fun HistoryDiagnosticCard(",
            nextMarker = "@Composable\nprivate fun HistoryScreen(",
            replacement = "",
        )
        text = replaceBetween(
            source = text,
            startMarker = "@Composable\nprivate fun DiagnosticExpander(",
            nextMarker = "@Composable\nprivate fun SavedPlacesCard(",
            replacement = "",
        )
        text = replaceBetween(
            source = text,
            startMarker = "private fun LiveDiagnostic.toShareText(): String = buildString {",
            nextMarker = "private fun savedPlaceTypeLabel(",
            replacement = "",
        )
        text = replaceBetween(
            source = text,
            startMarker = "private fun copyHistory(context: Context, history: List<AnalysisResult>) {",
            nextMarker = "private fun clearClipboard(context: Context) {",
            replacement = "",
        )
        text = text.replace("private const val TAB_DIAGNOSTIC = \"diagnostico\"\n\n", "")

        if (text != original) file.writeText(text)
    }
}

patchFinalDiagnosticCleanup.configure {
    mustRunAfter("patchManualSupportReport")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchFinalDiagnosticCleanup)
}
