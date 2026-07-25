// Rota Certa 0.1.127
// Cache exato e seguro:
// - nunca guarda falha de rota como se fosse resultado valido;
// - ignora snapshots antigos sem nenhuma distancia exata;
// - consulta o cache valido antes de pintar amarelo.

fun patchValidRouteCache127(
    cacheFile: java.io.File,
    serviceFile: java.io.File,
) {
    if (!cacheFile.exists()) throw GradleException("LiveRideRouteCache.kt nao encontrado para seguranca do cache 0.1.127.")
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para cache instantaneo 0.1.127.")

    var cache = cacheFile.readText()
    if ("route_cache_requires_exact_distance_0_1_127" !in cache) {
        val putAnchor = """        if (route.destinationCoordinate == null) return
        entries[key] = Entry(
"""
        if (putAnchor !in cache) throw GradleException("Entrada do cache de rotas nao encontrada.")
        val putReplacement = """        if (route.destinationCoordinate == null) return
        if (route.homeDistanceKm == null && route.alternativeDistanceKm == null) return // route_cache_requires_exact_distance_0_1_127
        entries[key] = Entry(
"""
        cache = cache.replaceFirst(putAnchor, putReplacement)
    }

    if ("route_cache_import_requires_exact_distance_0_1_127" !in cache) {
        val importAnchor = """            if (entry.destinationCoordinate != null && age in 0L..ttlMillis) {
                entries[key] = entry
            }
"""
        if (importAnchor !in cache) throw GradleException("Restauracao do cache de rotas nao encontrada.")
        val importReplacement = """            if (
                entry.destinationCoordinate != null &&
                (entry.homeDistanceKm != null || entry.alternativeDistanceKm != null) &&
                age in 0L..ttlMillis
            ) {
                entries[key] = entry
            } // route_cache_import_requires_exact_distance_0_1_127
"""
        cache = cache.replaceFirst(importAnchor, importReplacement)
    }

    var service = serviceFile.readText()
    if ("instant_cache_before_yellow_0_1_127" !in service) {
        val changedStart = service.indexOf("        val cardChanged = universalActiveAddressSignature != cardDecisionSignature")
        val changedEnd = if (changedStart >= 0) service.indexOf("        } else {", changedStart) else -1
        if (changedStart < 0 || changedEnd < 0) throw GradleException("Ciclo de novo card nao encontrado para cache instantaneo.")
        var changedRegion = service.substring(changedStart, changedEnd)
        val yellowAnchor = """            currentDistanceKm = null
            rememberBubbleReason("universal_waiting", "Novo card identificado; destino em calculo.")
            publishRuntimeValidationTrigger(trigger)
            showOverlay(RadarColor.Default, distanceKm = null)
"""
        if (yellowAnchor !in changedRegion) throw GradleException("Pintura amarela do novo card nao encontrada.")
        val instantCacheBlock = """            currentDistanceKm = null
            val instantSettings127 = currentSettings
            val instantFields127 = RideFields(
                pickup = trigger.pickup,
                destination = trigger.destination,
            )
            val instantCacheKey127 = LiveRideRouteCache.keyFor(
                fields = instantFields127,
                settings = instantSettings127,
                packageName = null,
                cardSignature = null,
            )
            val instantCachedRoute127 = universalRouteCache.get(instantCacheKey127)
            if (instantCachedRoute127 != null) {
                val instantCachedResult127 = decisionEngine.decide(
                    fields = instantFields127,
                    settings = instantSettings127,
                    destinationCoordinate = instantCachedRoute127.destinationCoordinate,
                    homeCoordinate = instantCachedRoute127.homeCoordinate,
                    alternativeCoordinate = instantCachedRoute127.alternativeCoordinate,
                    fullText = snapshotText,
                    homeDistanceKm = instantCachedRoute127.homeDistanceKm,
                    alternativeDistanceKm = instantCachedRoute127.alternativeDistanceKm,
                )
                traceEvent("universal.route.cache before_yellow=true age=${'$'}{instantCachedRoute127.ageMillis}ms")
                applyUniversalTwoAddressResult(
                    instantCachedResult127,
                    analysisHash,
                    cardDecisionSignature,
                    universalScreenGeneration,
                )
                return
            } // instant_cache_before_yellow_0_1_127
            rememberBubbleReason("universal_waiting", "Novo card identificado; destino em calculo.")
            publishRuntimeValidationTrigger(trigger)
            showOverlay(RadarColor.Default, distanceKm = null)
"""
        changedRegion = changedRegion.replaceFirst(yellowAnchor, instantCacheBlock)
        service = service.substring(0, changedStart) + changedRegion + service.substring(changedEnd)
    }

    listOf(
        "route_cache_requires_exact_distance_0_1_127",
        "route_cache_import_requires_exact_distance_0_1_127",
    ).forEach { marker ->
        if (marker !in cache) throw GradleException("Marcador ausente no cache seguro 0.1.127: $marker")
    }
    listOf(
        "instant_cache_before_yellow_0_1_127",
        "manual_selected_apps_gate_0_1_127",
        "manual_registered_card_gate_0_1_127",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Marcador ausente no cache instantaneo 0.1.127: $marker")
    }

    cacheFile.writeText(cache)
    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchValidRouteCache127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideRouteCache.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
