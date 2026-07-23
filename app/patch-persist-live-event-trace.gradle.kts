val persistLiveEventTrace by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            if ("live_trace_attach service_onCreate" !in text) {
                text = text.replace(
                    "        super.onCreate()\n        repository = SettingsRepository(applicationContext)",
                    "        super.onCreate()\n        DiagnosticLogStore.attach(applicationContext)\n        DiagnosticLogStore.record(\"service\", \"live_trace_attach service_onCreate\")\n        repository = SettingsRepository(applicationContext)",
                )
            }

            val oldTraceEvent = """    private fun traceEvent(message: String) {
        diagnosticEvents += "${'$'}{System.currentTimeMillis()} ${'$'}message"
        while (diagnosticEvents.size > DIAGNOSTIC_EVENT_LIMIT) diagnosticEvents.removeAt(0)
    }
"""
            val newTraceEvent = """    private fun traceEvent(message: String) {
        DiagnosticLogStore.record("bubble", message)
        diagnosticEvents += "${'$'}{System.currentTimeMillis()} ${'$'}message"
        while (diagnosticEvents.size > DIAGNOSTIC_EVENT_LIMIT) diagnosticEvents.removeAt(0)
    }
"""
            if ("DiagnosticLogStore.record(\"bubble\", message)" !in text) {
                text = text.replace(oldTraceEvent, newTraceEvent)
            }

            if (text != original) file.writeText(text)
        }

        mainFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            if ("live_trace_attach app_open" !in text) {
                text = text.replace(
                    "    val context = LocalContext.current\n    val lifecycleOwner = LocalLifecycleOwner.current",
                    "    val context = LocalContext.current\n    LaunchedEffect(Unit) {\n        DiagnosticLogStore.attach(context.applicationContext)\n        DiagnosticLogStore.record(\"ui\", \"live_trace_attach app_open\")\n    }\n    val lifecycleOwner = LocalLifecycleOwner.current",
                )
            }

            if (text != original) file.writeText(text)
        }
    }
}

persistLiveEventTrace.configure {
    mustRunAfter("globalLightDiagnostics")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(persistLiveEventTrace)
}
