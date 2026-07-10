val liveCardRouteLink by tasks.registering {
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
                "val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)\n            ?: FastRideCardMatcher.match(snapshotText, packageName, currentCardTemplates)?.also { fast ->\n                traceEvent(\"card_model.quick_match name=${'$'}{fast.template.name} score=${'$'}{fast.score}\")\n            }",
            )
        }

        if ("route.cache.hit" !in text) {
            text = text.replace(
"""            val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
            traceEvent("geocode.destination ok=${'$'}{destinationCoordinate != null}")
            val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)
            val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)
            traceEvent("geocode.config home=${'$'}{homeCoordinate != null} alternative=${'$'}{alternativeCoordinate != null}")
            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
            traceEvent("route.distance home=${'$'}{homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${'$'}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")

            val result = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = destinationCoordinate,
                homeCoordinate = homeCoordinate,
                alternativeCoordinate = alternativeCoordinate,
                fullText = text,
                homeDistanceKm = homeDistanceKm,
                alternativeDistanceKm = alternativeDistanceKm,
            )
""",
"""            val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
            traceEvent("geocode.destination ok=${'$'}{destinationCoordinate != null}")
            val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)
            val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)
            traceEvent("geocode.config home=${'$'}{homeCoordinate != null} alternative=${'$'}{alternativeCoordinate != null}")
            val cacheKey = LiveRideRouteCache.keyFor(
                fields = fields,
                settings = settings,
                packageName = cardMatch?.template?.packageName,
                cardSignature = cardMatch?.template?.sampleHash?.toString(),
            )
            val cachedRoute = routeCache.get(cacheKey)
            if (cachedRoute != null) traceEvent("route.cache.hit age=${'$'}{cachedRoute.ageMillis}ms")
            val effectiveDestinationCoordinate = cachedRoute?.destinationCoordinate ?: destinationCoordinate
            val homeDistanceKm = cachedRoute?.homeDistanceKm ?: routeDistanceKm(effectiveDestinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = cachedRoute?.alternativeDistanceKm ?: routeDistanceKm(effectiveDestinationCoordinate, alternativeCoordinate, settings)
            if (cachedRoute == null && effectiveDestinationCoordinate != null) {
                routeCache.put(
                    cacheKey,
                    LiveRideRouteCache.CachedRoute(
                        destinationCoordinate = effectiveDestinationCoordinate,
                        homeCoordinate = homeCoordinate,
                        alternativeCoordinate = alternativeCoordinate,
                        homeDistanceKm = homeDistanceKm,
                        alternativeDistanceKm = alternativeDistanceKm,
                    ),
                )
                traceEvent("route.cache.store")
            }
            traceEvent("route.distance home=${'$'}{homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${'$'}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")

            val result = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = effectiveDestinationCoordinate,
                homeCoordinate = homeCoordinate,
                alternativeCoordinate = alternativeCoordinate,
                fullText = text,
                homeDistanceKm = homeDistanceKm,
                alternativeDistanceKm = alternativeDistanceKm,
            )
""",
            )
        }

        text = text.replace(
            "if (quickResult.recommendation != Recommendation.InsufficientData) {",
            "if (cardMatch != null && quickResult.recommendation != Recommendation.InsufficientData) {",
        )

        if (text != original) file.writeText(text)
    }
}

liveCardRouteLink.configure {
    mustRunAfter("passiveEventCompileFix")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(liveCardRouteLink)
}
