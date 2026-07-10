val liveCardRouteLink by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private val coreRouteEngine = br.com.mapeiaia.rotacerta.core.CoreRouteEngine()" !in text) {
            text = text.replace(
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n",
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n    private val coreRouteEngine = br.com.mapeiaia.rotacerta.core.CoreRouteEngine()\n",
            )
        }

        if ("FastRideCardMatcher.match(snapshotText, packageName, currentCardTemplates)" !in text) {
            text = text.replace(
                "val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)",
                "val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)\n            ?: FastRideCardMatcher.match(snapshotText, packageName, currentCardTemplates)?.also { fast ->\n                traceEvent(\"card_model.quick_match name=${'$'}{fast.template.name} score=${'$'}{fast.score}\")\n            }",
            )
        }

        if ("core_route_engine_0_1_89" !in text) {
            val routeStartToken = "            val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }\n"
            val routeStart = text.indexOf(routeStartToken)
            val decisionEndToken = "                alternativeDistanceKm = alternativeDistanceKm,\n            )\n"
            val decisionEnd = if (routeStart >= 0) text.indexOf(decisionEndToken, routeStart) else -1
            if (routeStart < 0 || decisionEnd < 0) {
                throw org.gradle.api.GradleException("Nao encontrei o bloco de rota legado/original para substituir pelo CoreRouteEngine.")
            }
            val replaceEnd = decisionEnd + decisionEndToken.length
            val coreRouteBlock = """            val coreRouteTransaction = coreRouteEngine.beginTransaction(
                packageName = packageName,
                fields = fields,
                cardTemplateId = cardMatch?.template?.id,
                cardSignature = cardMatch?.template?.sampleHash?.toString(),
                visibleCardSignature = lastVisibleCardSignature,
            )
            val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
            traceEvent("geocode.destination ok=${'$'}{destinationCoordinate != null}")
            val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)
            val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)
            traceEvent("geocode.config home=${'$'}{homeCoordinate != null} alternative=${'$'}{alternativeCoordinate != null}")
            val cacheKey = coreRouteEngine.keyFor(
                fields = fields,
                settings = settings,
                packageName = cardMatch?.template?.packageName,
                cardSignature = cardMatch?.template?.sampleHash?.toString(),
            )
            val coreCache = coreRouteEngine.cachedRoute(cacheKey)
            val cachedRoute = coreCache.route
            traceEvent("core.route.cache hit=${dollar}{coreCache.fromCache} reason=${dollar}{coreCache.reason} age=${dollar}{cachedRoute?.ageMillis ?: -1}ms") // core_route_engine_0_1_89
            val effectiveDestinationCoordinate = cachedRoute?.destinationCoordinate ?: destinationCoordinate
            val homeDistanceKm = cachedRoute?.homeDistanceKm ?: routeDistanceKm(effectiveDestinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = cachedRoute?.alternativeDistanceKm ?: routeDistanceKm(effectiveDestinationCoordinate, alternativeCoordinate, settings)
            if (!coreRouteEngine.isFresh(coreRouteTransaction, currentWindowPackageName(), lastVisibleCardSignature)) {
                traceEvent("core.route.discard_stale reason=${dollar}{coreRouteEngine.freshnessReason(coreRouteTransaction, currentWindowPackageName(), lastVisibleCardSignature)}") // core_route_engine_0_1_89
                return
            }
            if (cachedRoute == null && effectiveDestinationCoordinate != null) {
                coreRouteEngine.storeRoute(
                    cacheKey,
                    LiveRideRouteCache.CachedRoute(
                        destinationCoordinate = effectiveDestinationCoordinate,
                        homeCoordinate = homeCoordinate,
                        alternativeCoordinate = alternativeCoordinate,
                        homeDistanceKm = homeDistanceKm,
                        alternativeDistanceKm = alternativeDistanceKm,
                    ),
                )
                traceEvent("core.route.cache.store") // core_route_engine_0_1_89
            }
            traceEvent("core.route.distance home=${'$'}{homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${'$'}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")

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
"""
            text = text.substring(0, routeStart) + coreRouteBlock + text.substring(replaceEnd)
        }

        text = text.replace(
            "if (quickResult.recommendation != Recommendation.InsufficientData) {",
            "if (cardMatch != null && quickResult.recommendation != Recommendation.InsufficientData) {",
        )

        if ("core_route_engine_0_1_89" !in text) {
            throw org.gradle.api.GradleException("CoreRouteEngine nao assumiu o cache/rota ao vivo.")
        }
        if ("private val coreRouteEngine = br.com.mapeiaia.rotacerta.core.CoreRouteEngine()" !in text) {
            throw org.gradle.api.GradleException("CoreRouteEngine nao foi instalado no servico.")
        }

        if (text != original) file.writeText(text)
    }
}

liveCardRouteLink.configure {
    mustRunAfter("passiveEventCompileFix")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(liveCardRouteLink)
}
