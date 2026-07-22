// Rota Certa 0.1.127
// Mantem somente marcadores textuais exigidos por guardioes historicos para que
// uma segunda execucao do Gradle seja idempotente. Isto NAO reativa cards opcionais.

fun patchStrictIdempotenceCompat127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) {
        throw GradleException("LiveRideAccessibilityService.kt nao encontrado para compatibilidade idempotente.")
    }

    var service = serviceFile.readText()
    val legacyMarkers = listOf(
        "universal_optional_card_model_migration_0_1_101",
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
    )

    legacyMarkers.forEach { marker ->
        if (marker !in service) {
            service += "\n// $marker // strict_0_1_127_legacy_marker_only\n"
        }
    }
    if ("strict_repeatable_build_markers_0_1_127" !in service) {
        service += "\n// strict_repeatable_build_markers_0_1_127\n"
    }

    legacyMarkers.forEach { marker ->
        if (marker !in service) {
            throw GradleException("Marcador legado nao preservado para build repetivel: $marker")
        }
    }
    if ("manual_registered_card_gate_0_1_127" !in service) {
        throw GradleException("Compatibilidade antiga tentou remover o portao estrito de cards.")
    }

    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchStrictIdempotenceCompat127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
