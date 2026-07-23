val fastCardCacheFlowPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        if ("private val routeCache = LiveRideRouteCache()" !in text) {
            text = text.replace(
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n",
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n    private val routeCache = LiveRideRouteCache()\n",
            )
        }

        if ("FastRideCardMatcher.match(snapshotText, packageName, currentCardTemplates)" !in text) {
            text = text.replace(
                "val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)",
                "val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)\n            ?: FastRideCardMatcher.match(snapshotText, packageName, currentCardTemplates)?.also { fast ->\n                traceEvent(\"card_model.fast_match name=${'$'}{fast.template.name} score=${'$'}{fast.score}\")\n            }",
            )
        }

        if ("route.cache hit" !in text) {
            val start = text.indexOf("            val settings = currentSettings\n            val region = DeviceRegion(country = \"Brasil\")")
            val endMarker = "            traceEvent(\"route.distance home=${'$'}{homeDistanceKm?.let(::formatDiagnosticKm) ?: \"null\"} alternative=${'$'}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: \"null\"}\")\n"
            val end = if (start >= 0) text.indexOf(endMarker, start) else -1
            if (start >= 0 && end >= 0) {
                val replacement = """            val settings = currentSettings
            val region = DeviceRegion(country = "Brasil")
            val cacheKey = LiveRideRouteCache.keyFor(
                fields = fields,
                settings = settings,
                packageName = cardMatch?.template?.packageName,
                cardSignature = cardMatch?.template?.sampleHash?.toString(),
            )
            val cachedRoute = routeCache.get(cacheKey)
            if (cachedRoute != null) {
                traceEvent("route.cache hit age_ms=${'$'}{cachedRoute.ageMillis}")
            } else {
                traceEvent("route.cache miss")
            }
            val destinationCoordinate = cachedRoute?.destinationCoordinate ?: fields.destination?.let { geocodeBest(it, region, settings) }
            traceEvent("geocode.destination ok=${'$'}{destinationCoordinate != null} cached=${'$'}{cachedRoute?.destinationCoordinate != null}")
            val homeCoordinate = cachedRoute?.homeCoordinate ?: settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)
            val alternativeCoordinate = cachedRoute?.alternativeCoordinate ?: settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)
            traceEvent("geocode.config home=${'$'}{homeCoordinate != null} alternative=${'$'}{alternativeCoordinate != null}")
            val homeDistanceKm = cachedRoute?.homeDistanceKm ?: routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = cachedRoute?.alternativeDistanceKm ?: routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
            if (cachedRoute == null) {
                routeCache.put(
                    cacheKey,
                    LiveRideRouteCache.CachedRoute(
                        destinationCoordinate = destinationCoordinate,
                        homeCoordinate = homeCoordinate,
                        alternativeCoordinate = alternativeCoordinate,
                        homeDistanceKm = homeDistanceKm,
                        alternativeDistanceKm = alternativeDistanceKm,
                    ),
                )
            }
            traceEvent("route.distance home=${'$'}{homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${'$'}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")
"""
                text = text.substring(0, start) + replacement + text.substring(end + endMarker.length)
            }
        }

        if (text != original) file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(fastCardCacheFlowPatch)
}
