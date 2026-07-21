// Rota Certa 0.1.124
// Remove atrasos internos comprovados pelo diagnostico 0.1.123:
// - nao reler DataStore antes de cada rota;
// - pintar verde/vermelho antes de persistir o historico;
// - ignorar expansoes temporarias de 4/6 enderecos do inDrive.

fun patchInstantFarolDecision124(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o farol instantaneo 0.1.124.")
    var text = file.readText()
    val dollar = "$"

    if ("private val rideCardSnapshotStabilizer = RideCardSnapshotStabilizer()" !in text) {
        val fieldAnchor = "    private val accessibilityEventFloodGate = AccessibilityEventFloodGate()\n"
        if (fieldAnchor !in text) throw GradleException("Campo do filtro de acessibilidade nao encontrado para o estabilizador.")
        text = text.replaceFirst(
            fieldAnchor,
            "    private val rideCardSnapshotStabilizer = RideCardSnapshotStabilizer()\n" + fieldAnchor,
        )
    }

    if ("instant_farol_snapshot_stability_0_1_124" !in text) {
        val triggerAnchor = "        val trigger = UniversalAddressTrigger.evaluate(snapshotText)\n"
        if (triggerAnchor !in text) throw GradleException("Gatilho universal nao encontrado para estabilizar o card.")
        val replacement = triggerAnchor + """        if (rideCardSnapshotStabilizer.shouldIgnore(
                packageName = universalResolvedForegroundPackage(),
                addressCount = trigger.addresses.size,
                active = trigger.active,
                nowMillis = System.currentTimeMillis(),
            )
        ) {
            traceEvent("universal.snapshot.expansion_ignored addresses=${dollar}{trigger.addresses.size}")
            return
        } // instant_farol_snapshot_stability_0_1_124
"""
        text = text.replaceFirst(triggerAnchor, replacement)
    }

    if ("instant_farol_cached_settings_0_1_124" !in text) {
        val oldSettingsRead = """        currentSettings = repository.settings.first()
        val settings = currentSettings
"""
        if (oldSettingsRead !in text) throw GradleException("Leitura bloqueante das configuracoes nao encontrada.")
        text = text.replaceFirst(
            oldSettingsRead,
            "        val settings = currentSettings // instant_farol_cached_settings_0_1_124\n",
        )
    }

    if ("instant_farol_paint_before_history_0_1_124" !in text) {
        val oldResultBlock = """        if (universalAnalysisDeduper.shouldPersist(persistenceSignature)) {
            repository.addAnalysis(result)
        } else {
            traceEvent("universal.history duplicate_skipped=true")
        }
        rememberBubbleReason("universal_result", result.reason)
        showOverlay(color, distanceKm)
        traceEvent("universal.result applied color=${dollar}{color.diagnosticLabel} km=${dollar}{distanceKm?.toString().orEmpty()}")
"""
        if (oldResultBlock !in text) throw GradleException("Bloco final de resultado nao encontrado para antecipar a cor.")
        val newResultBlock = """        val shouldPersistHistory = universalAnalysisDeduper.shouldPersist(persistenceSignature)
        rememberBubbleReason("universal_result", result.reason)
        showOverlay(color, distanceKm)
        traceEvent("universal.result applied color=${dollar}{color.diagnosticLabel} km=${dollar}{distanceKm?.toString().orEmpty()} instant=true") // instant_farol_paint_before_history_0_1_124
        if (shouldPersistHistory) {
            scope.launch {
                runCatching { repository.addAnalysis(result) }
                    .onFailure { error ->
                        traceEvent("universal.history async_failure=${dollar}{error::class.java.simpleName}")
                    }
            }
        } else {
            traceEvent("universal.history duplicate_skipped=true")
        }
"""
        text = text.replaceFirst(oldResultBlock, newResultBlock)
    }

    if ("instant_farol_snapshot_reset_0_1_124" !in text) {
        val resetAnchor = "        universalLiveReadGate.reset()\n"
        if (resetAnchor !in text) throw GradleException("Reset do leitor universal nao encontrado.")
        text = text.replaceFirst(
            resetAnchor,
            resetAnchor + "        rideCardSnapshotStabilizer.reset() // instant_farol_snapshot_reset_0_1_124\n",
        )
    }

    listOf(
        "RideCardSnapshotStabilizer()",
        "instant_farol_snapshot_stability_0_1_124",
        "instant_farol_cached_settings_0_1_124",
        "instant_farol_paint_before_history_0_1_124",
        "instant_farol_snapshot_reset_0_1_124",
        "scope.launch {\n                runCatching { repository.addAnalysis(result) }",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato do farol instantaneo 0.1.124 incompleto: $marker")
    }

    file.writeText(text)
}

fun configureInstantFarolDecision124() {
    val service = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    patchInstantFarolDecision124(service.asFile)
}

tasks.named("radarWorkTracking121").configure {
    doLast { configureInstantFarolDecision124() }
}

tasks.matching { it.name == "workTrackingCardAnchorCleanup121" }.configureEach {
    doLast { configureInstantFarolDecision124() }
}
