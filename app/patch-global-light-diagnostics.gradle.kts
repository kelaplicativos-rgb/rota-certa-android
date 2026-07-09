fun replacePrivateFunctionBlockGlobalDiagnostics(
    source: String,
    functionName: String,
    replacement: String,
): String {
    val start = source.indexOf("    private fun $functionName")
    if (start < 0) return source
    val next = source.indexOf("\n    private fun ", start + 1)
    return if (next < 0) {
        source.substring(0, start) + replacement
    } else {
        source.substring(0, start) + replacement + source.substring(next + 1)
    }
}

val globalLightDiagnostics by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"

        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace("trace = { _ -> },", "trace = ::traceEvent,")

            if ("private var lastDecisionOverlayAtMillis" !in text) {
                text = text.replace(
                    "    private var currentDistanceKm: Double? = null\n",
                    "    private var currentDistanceKm: Double? = null\n    private var lastDecisionOverlayAtMillis: Long = 0L\n",
                )
            }

            if ("private fun hasActiveRegisteredDecision()" !in text) {
                text = text.replace(
"""    private fun resetToDefault(
""",
"""    private fun hasActiveRegisteredDecision(): Boolean =
        currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red

    private fun resetToDefault(
""",
                )
            }

            text = replacePrivateFunctionBlockGlobalDiagnostics(
                text,
                "traceEvent",
"""    private fun traceEvent(message: String) {
        DiagnosticLogStore.record("bubble", message)
    }

""",
            )

            text = text.replace(
                "const val DECISION_OVERLAY_STICKY_MS = 0L",
                "const val DECISION_OVERLAY_STICKY_MS = 2_800L",
            )
            if ("const val DECISION_OVERLAY_STICKY_MS" !in text) {
                text = text.replace(
                    "const val SCREENSHOT_INTERVAL_MS = 420L",
                    "const val SCREENSHOT_INTERVAL_MS = 420L\n        const val DECISION_OVERLAY_STICKY_MS = 2_800L",
                )
                text = text.replace(
                    "const val SCREENSHOT_INTERVAL_MS = 300L",
                    "const val SCREENSHOT_INTERVAL_MS = 300L\n        const val DECISION_OVERLAY_STICKY_MS = 2_800L",
                )
            }

            if ("global.stability keep_decision=true" !in text) {
                text = text.replace(
"""        val manager = windowManager ?: return
""",
"""        val manager = windowManager ?: return
        val globalDecisionNow = System.currentTimeMillis()
        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            globalDecisionNow - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("global.stability keep_decision=true current=${dollar}{currentRadarColor.diagnosticLabel} requested=${dollar}{color.diagnosticLabel} distance=${dollar}{currentDistanceKm?.toString() ?: "none"}")
            return
        }
        if (color == RadarColor.Green || color == RadarColor.Red) lastDecisionOverlayAtMillis = globalDecisionNow
        traceEvent("global.overlay request previous=${dollar}{currentRadarColor.diagnosticLabel} next=${dollar}{color.diagnosticLabel} distance=${dollar}{distanceKm?.toString() ?: "none"}")
""",
                )
            }

            if (text != original) file.writeText(text)
        }

        mainFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            if ("DiagnosticLogStore.record(\"ui\"" !in text) {
                text = text.replace(
                    "    prefs.edit().putString(SYSTEM_ACTION_DIAGNOSTIC_LOG, next.joinToString(\"\\n\")).apply()\n",
                    "    prefs.edit().putString(SYSTEM_ACTION_DIAGNOSTIC_LOG, next.joinToString(\"\\n\")).apply()\n    DiagnosticLogStore.record(\"ui\", \"action=$${cleanAction} status=$${cleanStatus} details=$${cleanDetails}\")\n",
                )
            }

            if ("--- DIAGNOSTICO GLOBAL LEVE ---" !in text) {
                text = text.replace(
"""        appendLine("--- RADARES IMPORTADOS ---")
        appendLine(radarImportSummary.toString())
        appendLine()
        appendLine("--- BACKUP INTERNO ---")
""",
"""        appendLine("--- RADARES IMPORTADOS ---")
        appendLine(radarImportSummary.toString())
        appendLine()
        appendLine("--- DIAGNOSTICO GLOBAL LEVE ---")
        val globalDiagnostic = DiagnosticLogStore.dump(220)
        appendLine(globalDiagnostic.ifBlank { "sem eventos globais recentes" })
        appendLine()
        appendLine("--- BACKUP INTERNO ---")
""",
                )
            }

            if (text != original) file.writeText(text)
        }
    }
}

globalLightDiagnostics.configure {
    mustRunAfter(
        "patchFactoryCleanNoFlicker",
        "patchBubbleStateReportCompileFix",
        "patchVideoBubbleHardening",
        "patchFinalDiagnosticCleanup",
        "systemWideActionDiagnostics",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(globalLightDiagnostics)
}
