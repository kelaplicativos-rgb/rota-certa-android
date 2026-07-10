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

        if ("LiveRideRouteCache.keyFor(\n                fields = fields," !in text) {
            text = text.replace(
                "val cacheKey = LiveRideRouteCache.keyFor(fields, settings)",
                "val cacheKey = LiveRideRouteCache.keyFor(\n                fields = fields,\n                settings = settings,\n                packageName = cardMatch?.template?.packageName,\n                cardSignature = cardMatch?.template?.sampleHash?.toString(),\n            )",
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
