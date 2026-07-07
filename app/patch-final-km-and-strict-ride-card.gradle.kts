val finalKmAndStrictRideCard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val detectorFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideOfferDetector.kt")
    val parserFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt")
    inputs.files(serviceFile, detectorFile, parserFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"

        serviceFile.asFile.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""                traceEvent("decision.quick recommendation=${dollar}{quickResult.recommendation} distance=${dollar}{quickResult.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
                showOverlay(color = quickColor, distanceKm = quickResult.nearestConfiguredDistanceKm())
""",
"""                traceEvent("decision.quick recommendation=${dollar}{quickResult.recommendation} distance=hidden_until_final_route")
                showOverlay(color = quickColor, distanceKm = null)
""",
            )

            text = text.replace(
"""        showOverlay(color = radarColor, distanceKm = cachedBubbleDistanceKm)
        traceEvent("cache.instant_apply distance=" + (cachedBubbleDistanceKm?.let(::formatDiagnosticKm) ?: "hidden_no_route"))
""",
"""        showOverlay(color = radarColor, distanceKm = cachedBubbleDistanceKm)
        traceEvent("cache.instant_apply final_distance=" + (cachedBubbleDistanceKm?.let(::formatDiagnosticKm) ?: "hidden_no_route"))
""",
            )

            if ("final_km_and_strict_ride_card.patch_applied" !in text) {
                text = text.replace(
                    "        traceEvent(\"card_lifecycle_strict_overlay.patch_applied=true\")\n",
                    "        traceEvent(\"card_lifecycle_strict_overlay.patch_applied=true\")\n        traceEvent(\"final_km_and_strict_ride_card.patch_applied=true\")\n",
                )
            }

            if (text != original) file.writeText(text)
        }

        detectorFile.asFile.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""        val hasPositiveFare = fields.fare?.let {
            Regex(""" + "\"\"\"^R\\$\\s*(?!0+(?:[,.]0{1,2})?\\b)\\d\"\"\"" + ", RegexOption.IGNORE_CASE).containsMatchIn(it)
        } == true
        val hasNinetyNinePackageSignal = normalizedPackage == PACKAGE_99_DRIVER &&
            hasPositiveFare &&
            Regex(""" + "\"\"\"\\b\\d+(?:[,.]\\d+)?\\s*km\\b\"\"\"" + ", RegexOption.IGNORE_CASE).containsMatchIn(text)
        return hasDestinationAddressSignal && (hasRideCardSignal || hasMapPointSignal || hasNinetyNinePackageSignal)
""",
"""        val hasPositiveFare = fields.fare?.let {
            Regex(""" + "\"\"\"^R\\$\\s*(?!0+(?:[,.]0{1,2})?\\b)\\d\"\"\"" + ", RegexOption.IGNORE_CASE).containsMatchIn(it)
        } == true
        val hasExplicitOfferAction = listOf(
            "aceitar", "aceitar por", "selecionar", "ofereça sua tarifa", "ofereca sua tarifa"
        ).any { normalized.contains(it) }
        val hasNinetyNinePackageSignal = normalizedPackage == PACKAGE_99_DRIVER &&
            hasPositiveFare &&
            Regex(""" + "\"\"\"\\b\\d+(?:[,.]\\d+)?\\s*km\\b\"\"\"" + ", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasUberPackageSignal = normalizedPackage == PACKAGE_UBER_DRIVER &&
            hasPositiveFare &&
            hasExplicitOfferAction &&
            (normalized.contains("uberx") || normalized.contains("viagem longa") || normalized.contains("exclusivo"))
        if (normalizedPackage == PACKAGE_UBER_DRIVER && !hasUberPackageSignal) return false
        return hasDestinationAddressSignal && (hasRideCardSignal || hasMapPointSignal || hasNinetyNinePackageSignal || hasUberPackageSignal)
""",
            )

            text = text.replace(
"""        "criar alerta de proximidade",
    )

    private const val PACKAGE_99_DRIVER = "com.app99.driver"
""",
"""        "criar alerta de proximidade",
        "bateria restante no celular",
        "considere conectar-se a um carregador",
        "carregador em breve",
    )

    private const val PACKAGE_99_DRIVER = "com.app99.driver"
    private const val PACKAGE_UBER_DRIVER = "com.ubercab.driver"
""",
            )

            if (text != original) file.writeText(text)
        }

        parserFile.asFile.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""            normalized.contains("conectar") ||
            normalized.contains("cartao") ||
""",
"""            normalized.contains("conectar") ||
            normalized.contains("bateria restante") ||
            normalized.contains("carregador") ||
            normalized.contains("considere conectar") ||
            normalized.contains("cartao") ||
""",
            )

            if (text != original) file.writeText(text)
        }
    }
}

finalKmAndStrictRideCard.configure {
    mustRunAfter(
        "cardLifecycleStrictOverlay",
        "stableBubbleNoFlicker",
        "preciseBubbleRouteKm",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(finalKmAndStrictRideCard)
}
