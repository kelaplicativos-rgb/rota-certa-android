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
            val exactTarget = """        currentRadarColor = color
        currentDistanceKm = distanceKm
"""
            if (exactTarget in text) {
                text = text.replace(exactTarget, replacement)
            } else {
                val funStart = text.indexOf("    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {")
                val afterManager = if (funStart >= 0) text.indexOf("        val manager = windowManager ?: return\n", funStart) else -1
                val viewStart = if (funStart >= 0) text.indexOf("        val view = overlayView", funStart) else -1
                if (funStart < 0 || afterManager < 0 || viewStart < 0 || afterManager > viewStart) {
                    throw org.gradle.api.GradleException("Nao encontrei o corpo de showOverlay para instalar estado central.")
                }
                val insertStart = afterManager + "        val manager = windowManager ?: return\n".length
                text = text.substring(0, insertStart) + replacement + text.substring(viewStart)
            }
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
