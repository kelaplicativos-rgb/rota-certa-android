val persistentBubbleStateTrace by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            val marker = "bubble_state stage=\${'$'}lastBubbleStateStage"
            if (marker !in text) {
                text = text.replace(
"""            .putInt(KEY_STATE_TEMPLATE_COUNT, currentCardTemplates.size)
            .apply()
    }

    private fun resetToDefault(
""",
"""            .putInt(KEY_STATE_TEMPLATE_COUNT, currentCardTemplates.size)
            .apply()
        DiagnosticLogStore.record(
            "bubble_state",
            "stage=${'$'}lastBubbleStateStage color=${'$'}{currentRadarColor.diagnosticLabel} km=${'$'}{currentDistanceKm?.let(::formatDiagnosticKm) ?: "none"} window=${'$'}{currentWindowPackageName().orEmpty()} active=${'$'}{activePackageName.orEmpty()} textPackage=${'$'}{lastTextPackageName.orEmpty()} currentHash=${'$'}{lastSnapshotHash?.toString().orEmpty()} analyzedHash=${'$'}{lastAnalyzedHash?.toString().orEmpty()} pendingHash=${'$'}{pendingAnalysis?.snapshotHash?.toString().orEmpty()} accLen=${'$'}{lastAccessibilityText.length} ocrLen=${'$'}{lastOcrText.length} templates=${'$'}{currentCardTemplates.size} reason=${'$'}lastBubbleStateReason",
        )
    }

    private fun resetToDefault(
""",
                )
            }

            if (text != original) file.writeText(text)
        }
    }
}

persistentBubbleStateTrace.configure {
    mustRunAfter("patchBubbleStateReport", "persistLiveEventTrace")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(persistentBubbleStateTrace)
}
