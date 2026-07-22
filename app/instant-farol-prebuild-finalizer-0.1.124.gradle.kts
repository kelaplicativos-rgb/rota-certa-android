// Ultima verificacao da versao 0.1.124.
// Executa como a ultima acao do preBuild para impedir que qualquer patch legado
// restaure a protecao da cor anterior ou remova o cache exato persistente.

fun finalizeInstantFarolPreBuild124(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado no finalizador 0.1.124.")
    var text = file.readText()
    val dollar = "$"

    if ("global_idle_never_guarded_0_1_124" !in text) {
        val oldGuard = """        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
            traceEvent("resetToIdle guarded active_ride_window reason=${dollar}reason")
            return
        }
"""
        if (oldGuard !in text) {
            throw GradleException("A protecao final do reset para cinza mudou de formato.")
        }
        text = text.replaceFirst(
            oldGuard,
            "        Unit // global_idle_never_guarded_0_1_124\n",
        )
    }

    if ("global_overlay_idle_allowed_0_1_124" !in text) {
        val oldGuard = """        if (color == RadarColor.Idle && currentRadarColor == RadarColor.Default && shouldScanCurrentWindow()) {
            Unit
            return
        }
"""
        if (oldGuard !in text) {
            throw GradleException("A protecao final do overlay cinza mudou de formato.")
        }
        text = text.replaceFirst(
            oldGuard,
            "        Unit // global_overlay_idle_allowed_0_1_124\n",
        )
    }

    listOf(
        "global_single_passenger_gate_0_1_124",
        "global_passenger_and_addresses_card_0_1_124",
        "global_inactive_clear_now_0_1_124",
        "global_full_screen_hash_0_1_124",
        "global_screen_change_clear_0_1_124",
        "instant_farol_cached_settings_0_1_124",
        "instant_farol_paint_before_history_0_1_124",
        "persistent_route_cache_restore_0_1_124",
        "persistent_route_cache_save_0_1_124",
        "persistent_exact_route_cache_v1",
        "universalRouteCache.importSnapshot(",
        "universalRouteCache.exportSnapshot()",
        "global_idle_never_guarded_0_1_124",
        "global_overlay_idle_allowed_0_1_124",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Finalizador 0.1.124 sem marcador: $marker")
    }

    listOf(
        "traceEvent(\"universal.accessibility transient_empty_ignored_route_inflight=true\")",
        "traceEvent(\"resetToIdle guarded active_ride_window reason=",
    ).forEach { forbiddenExecutable ->
        if (forbiddenExecutable in text) {
            throw GradleException("Finalizador 0.1.124 encontrou protecao executavel antiga: $forbiddenExecutable")
        }
    }

    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        finalizeInstantFarolPreBuild124(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
