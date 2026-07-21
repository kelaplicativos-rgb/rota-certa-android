// Persiste apenas rotas exatas ja calculadas. Nao usa estimativa para decidir cor.
fun patchPersistentRouteCache124(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para cache persistente 0.1.124.")
    var text = file.readText()
    val dollar = "$"

    if ("persistent_route_cache_restore_0_1_124" !in text) {
        val anchor = "        bubblePrefs = getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE)\n"
        if (anchor !in text) throw GradleException("Inicializacao das preferencias da bolinha nao encontrada.")
        val replacement = anchor + """        val restoredRouteCount = universalRouteCache.importSnapshot(
            bubblePrefs.getString("persistent_exact_route_cache_v1", "").orEmpty(),
        )
        traceEvent("universal.route.cache restored=${dollar}restoredRouteCount") // persistent_route_cache_restore_0_1_124
"""
        text = text.replaceFirst(anchor, replacement)
    }

    if ("persistent_route_cache_save_0_1_124" !in text) {
        val anchor = "            traceEvent(\"universal.route.cache stored=true\")\n"
        if (anchor !in text) throw GradleException("Gravacao em memoria do cache de rota nao encontrada.")
        val replacement = anchor + """            bubblePrefs.edit()
                .putString("persistent_exact_route_cache_v1", universalRouteCache.exportSnapshot())
                .apply() // persistent_route_cache_save_0_1_124
"""
        text = text.replaceFirst(anchor, replacement)
    }

    listOf(
        "persistent_route_cache_restore_0_1_124",
        "persistent_route_cache_save_0_1_124",
        "universalRouteCache.importSnapshot(",
        "universalRouteCache.exportSnapshot()",
        "persistent_exact_route_cache_v1",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Cache persistente 0.1.124 incompleto: $marker")
    }

    file.writeText(text)
}

fun persistentRouteCacheService124(): java.io.File =
    layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile

tasks.named("radarWorkTracking121").configure {
    doLast { patchPersistentRouteCache124(persistentRouteCacheService124()) }
}

tasks.matching { it.name == "workTrackingCardAnchorCleanup121" }.configureEach {
    doLast { patchPersistentRouteCache124(persistentRouteCacheService124()) }
}
