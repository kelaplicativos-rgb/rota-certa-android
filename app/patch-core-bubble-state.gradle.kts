// Centraliza o estado da bolinha no CoreBubbleStateController.
// O servico Android passa a refletir estado; o Core guarda a verdade atual.

val coreBubbleStatePatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        if ("private val coreBubbleState = br.com.mapeiaia.rotacerta.core.CoreBubbleStateController()" !in text) {
            text = text.replace(
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n",
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n    private val coreBubbleState = br.com.mapeiaia.rotacerta.core.CoreBubbleStateController()\n",
            )
        }

        if ("core_bubble_state_reset_default_0_1_91" !in text) {
            text = text.replace(
                """        showOverlay(RadarColor.Default)
        if (record) {
""",
                """        val coreState = coreBubbleState.waiting(reason) // core_bubble_state_reset_default_0_1_91
        traceEvent("core.state waiting changed=${'$'}{coreState.changed} reason=${'$'}{coreState.reason}")
        showOverlay(RadarColor.Default, coreState.distanceKm)
        if (record) {
""",
            )
        }

        if ("core_bubble_state_reset_idle_0_1_91" !in text) {
            text = text.replace(
                """        showOverlay(RadarColor.Idle)
        if (record) {
""",
                """        val coreState = coreBubbleState.hidden(reason) // core_bubble_state_reset_idle_0_1_91
        traceEvent("core.state hidden changed=${'$'}{coreState.changed} reason=${'$'}{coreState.reason}")
        showOverlay(RadarColor.Idle, coreState.distanceKm)
        if (record) {
""",
            )
        }

        if ("core_bubble_state_render_0_1_91" !in text) {
            val target = """        currentRadarColor = color
        currentDistanceKm = distanceKm
"""
            val replacement = """        val coreRenderState = coreBubbleState.render(
            mode = when (color) {
                RadarColor.Green -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good
                RadarColor.Red -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad
                RadarColor.Default -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting
                RadarColor.Idle -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden
            },
            distanceKm = distanceKm,
            reason = "Renderizacao solicitada pelo servico Android.",
        ) // core_bubble_state_render_0_1_91
        currentRadarColor = color
        currentDistanceKm = coreRenderState.distanceKm
"""
            if (target !in text) {
                throw org.gradle.api.GradleException("Nao encontrei o ponto de estado do showOverlay para ligar o CoreBubbleStateController.")
            }
            text = text.replace(target, replacement)
        }

        if ("core_bubble_state_render_0_1_91" !in text) {
            throw org.gradle.api.GradleException("CoreBubbleStateController nao assumiu renderizacao.")
        }
        if ("core_bubble_state_reset_default_0_1_91" !in text) {
            throw org.gradle.api.GradleException("CoreBubbleStateController nao assumiu reset default.")
        }
        if ("core_bubble_state_reset_idle_0_1_91" !in text) {
            throw org.gradle.api.GradleException("CoreBubbleStateController nao assumiu reset idle.")
        }
        if ("private val coreBubbleState = br.com.mapeiaia.rotacerta.core.CoreBubbleStateController()" !in text) {
            throw org.gradle.api.GradleException("CoreBubbleStateController nao foi instalado no servico.")
        }

        if (text != original) file.writeText(text)
    }
}

coreBubbleStatePatch.configure {
    mustRunAfter("coreBubblePresenterPatch", "coreBubbleDecisionPatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreBubbleStatePatch)
}
