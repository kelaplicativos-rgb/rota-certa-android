val patchVideoBubbleHardening by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        fun removeBlockContaining(source: String, marker: String): String {
            var result = source
            while (true) {
                val markerIndex = result.indexOf(marker)
                if (markerIndex < 0) break
                val blockStart = result.lastIndexOf("\n", markerIndex).let { if (it < 0) 0 else it + 1 }
                var index = result.indexOf('{', markerIndex)
                if (index < 0) break
                var depth = 0
                while (index < result.length) {
                    when (result[index]) {
                        '{' -> depth += 1
                        '}' -> {
                            depth -= 1
                            if (depth == 0) {
                                index += 1
                                while (index < result.length && result[index].isWhitespace() && result[index] != '\n') index += 1
                                if (index < result.length && result[index] == '\n') index += 1
                                result = result.substring(0, blockStart) + result.substring(index)
                                break
                            }
                        }
                    }
                    index += 1
                }
            }
            return result
        }

        text = removeBlockContaining(text, "source == TextSource.Ocr && hasActiveRegisteredDecision()")

        text = text.replace(
            """        val snapshotText = if (allowPopupCandidate) {
            text.trim()
        } else {
            mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank { text.trim() }
        }
""",
            """        val snapshotText = text.trim()
""",
        )

        text = text.replace(
            """        if (!allowPopupCandidate && !shouldScanPackage(windowPackageName)) return
""",
            """        if (!allowPopupCandidate && !shouldScanPackage(windowPackageName)) {
            resetToIdle("Janela atual fora do app/card monitorado; bolinha limpa imediatamente.", record = false)
            return
        }
""",
        )

        text = text.replace(
            """        val snapshotHash = snapshotText.snapshotHash()
""",
            """        val snapshotHash = snapshotText.snapshotHash()
        if (!allowPopupCandidate && source == TextSource.Ocr && hasActiveRegisteredDecision() && lastSnapshotHash != null && snapshotHash != lastSnapshotHash) {
            return
        }
""",
        )

        text = text.replace(
            """            if (allowPopupCandidate) return
            lastSnapshotHash = snapshotHash
""",
            """            if (allowPopupCandidate || source == TextSource.Ocr) return
            lastSnapshotHash = snapshotHash
""",
        )
        text = text.replace(
            """            if (allowPopupCandidate) return
            registeredCardGate.clear()
""",
            """            if (allowPopupCandidate || source == TextSource.Ocr) return
            registeredCardGate.clear()
""",
        )
        text = text.replace(
            """            if (allowPopupCandidate) return
            registeredCardGate.clear()
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
""",
            """            if (allowPopupCandidate || source == TextSource.Ocr) return
            registeredCardGate.clear()
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
""",
        )
        text = text.replace(
            """            if (allowPopupCandidate) return
            registeredCardGate.clear()
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
""",
            """            if (allowPopupCandidate || source == TextSource.Ocr) return
            registeredCardGate.clear()
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
""",
        )

        text = text.replace(
            """            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
            val radarColor = when (result.recommendation) {
""",
            """            if (!allowPopupCandidate && snapshotHash != lastSnapshotHash) {
                registeredCardGate.clear()
                if (shouldScanCurrentWindow()) {
                    resetToDefault("Analise antiga ignorada porque a tela mudou antes do resultado.", record = false)
                } else {
                    resetToIdle("Analise antiga ignorada porque o card/app saiu da tela.", record = false)
                }
                return
            }
            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
            val radarColor = when (result.recommendation) {
""",
        )

        text = text.replace(
            Regex("""\n\s*if \(\(color == RadarColor\.Default \|\| color == RadarColor\.Idle\) &&\s*hasActiveRegisteredDecision\(\) &&\s*shouldScanCurrentWindow\(\) &&\s*now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS\s*\) \{[\s\S]*?\n\s*\}"""),
            "",
        )
        text = text.replace(
            "        const val DECISION_OVERLAY_STICKY_MS = 3_500L\n",
            "        const val DECISION_OVERLAY_STICKY_MS = 0L\n",
        )

        if (text != original) file.writeText(text)
    }
}

patchVideoBubbleHardening.configure {
    mustRunAfter("patchFinalDiagnosticCleanup")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchVideoBubbleHardening)
}
