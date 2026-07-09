val patchBubbleRenderStability by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast

        var text = file.readText()
        val original = text

        if ("private var lastVisibleCardSignature: String? = null" !in text) {
            text = text.replace(
                "    private var lastDecisionOverlayAtMillis: Long = 0L\n",
                "    private var lastDecisionOverlayAtMillis: Long = 0L\n    private var lastVisibleCardSignature: String? = null\n",
            )
        }

        if ("private fun buildVisibleCardSignature(" !in text) {
            text = text.replace(
                "    private fun hasActiveRegisteredDecision(): Boolean =\n",
                """    private fun buildVisibleCardSignature(
        packageName: String?,
        fields: RideFields,
        cardMatch: RideCardTemplateMatch,
    ): String = listOf(
        normalizePackageName(packageName).orEmpty(),
        cardMatch.template.id,
        fields.pickup.stableSignaturePart(),
        fields.destination.stableSignaturePart(),
        fields.fare.stableSignaturePart(),
    ).joinToString("|")

    private fun String?.stableSignaturePart(): String =
        this.orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)

    private fun hasActiveRegisteredDecision(): Boolean =
""",
            )
        }

        if ("bubble_render_stability_clear_signature_0_1_81" !in text) {
            text = text.replace(
                "registeredCardGate.clear()",
                "registeredCardGate.clear()\n            lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81",
            )
        }

        if ("screen_changed.defer_visual_until_card_match" !in text) {
            text = text.replace(
                """                lastAnalyzedHash = null
                showOverlay(RadarColor.Default)
""",
                """                lastAnalyzedHash = null
                traceEvent("screen_changed.defer_visual_until_card_match source=${'$'}source hash=${'$'}snapshotHash") // bubble_render_stability_0_1_81
""",
            )
        }

        if ("visible_card.signature_changed" !in text) {
            text = text.replace(
                """        registeredCardGate.markSeen()
        traceEvent("card_model.match name=${'$'}{cardMatch.template.name} score=${'$'}{cardMatch.score}")
""",
                """        val visibleCardSignature = buildVisibleCardSignature(packageName, fields, cardMatch)
        if (lastVisibleCardSignature != null && lastVisibleCardSignature != visibleCardSignature) {
            lastDecisionOverlayAtMillis = 0L
            traceEvent("visible_card.signature_changed previous=${'$'}lastVisibleCardSignature next=${'$'}visibleCardSignature") // bubble_render_stability_0_1_81
            showOverlay(RadarColor.Default)
        }
        lastVisibleCardSignature = visibleCardSignature
        registeredCardGate.markSeen()
        traceEvent("card_model.match name=${'$'}{cardMatch.template.name} score=${'$'}{cardMatch.score}")
""",
            )
        }

        if ("bubble_render_background_uses_current_state_0_1_81" !in text) {
            text = text.replace(
                "setColor(color.argb(currentSettings))",
                "setColor(currentRadarColor.argb(currentSettings)) // bubble_render_background_uses_current_state_0_1_81",
            )
        }

        if ("screen_changed.defer_visual_until_card_match" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar a estabilizacao de screen_changed da bolinha.")
        }
        if ("visible_card.signature_changed" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar a assinatura visual do card da bolinha.")
        }
        if ("bubble_render_background_uses_current_state_0_1_81" !in text) {
            throw org.gradle.api.GradleException("Nao consegui alinhar a cor renderizada com o estado real da bolinha.")
        }

        if (text != original) file.writeText(text)
    }
}

patchBubbleRenderStability.configure {
    mustRunAfter(
        "patchLiveRideOverlayStability",
        "patchBubbleStateReport",
        "keepDecisionDuringTransientText",
        "hardClearUnregisteredCardDecision",
        "modularLiveBubbleCore",
        "noStickyDecisionCleanup",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchBubbleRenderStability)
}
