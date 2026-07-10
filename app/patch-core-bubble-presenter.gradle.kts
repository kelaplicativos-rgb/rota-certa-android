// Liga o showOverlay ao CoreBubblePresenter.
// O serviço Android passa a desenhar; o texto/tamanho visual da bolinha vem do Core.

val coreBubblePresenterPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        if ("private val coreBubblePresenter = br.com.mapeiaia.rotacerta.core.CoreBubblePresenter" !in text) {
            text = text.replace(
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n",
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n    private val coreBubblePresenter = br.com.mapeiaia.rotacerta.core.CoreBubblePresenter\n",
            )
        }

        if ("core_bubble_presenter_0_1_90" !in text) {
            val presenterBlock = """        val coreBubblePresentation = coreBubblePresenter.present(
            mode = when (color) {
                RadarColor.Green -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good
                RadarColor.Red -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad
                RadarColor.Default -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting
                RadarColor.Idle -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden
            },
            distanceKm = currentDistanceKm,
        )
        traceEvent("core.presenter mode=${'$'}{coreBubblePresentation.mode} text=${'$'}{coreBubblePresentation.text}") // core_bubble_presenter_0_1_90
        view.text = coreBubblePresentation.text
        view.textSize = coreBubblePresentation.textSizeSp
"""
            val exactOldBlock = """        view.text = formatBubbleDistanceKm(currentDistanceKm)
        view.textSize = bubbleTextSizeSp(view.text.toString())
"""
            if (exactOldBlock in text) {
                text = text.replace(exactOldBlock, presenterBlock)
            } else {
                val funStart = text.indexOf("    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {")
                val backgroundStart = if (funStart >= 0) text.indexOf("        view.background = GradientDrawable().apply {", funStart) else -1
                val viewTextStart = if (funStart >= 0) text.indexOf("        view.text =", funStart) else -1
                if (funStart < 0 || backgroundStart < 0 || viewTextStart < 0 || viewTextStart > backgroundStart) {
                    throw org.gradle.api.GradleException("Nao encontrei o trecho visual da bolinha para ligar o presenter.")
                }
                text = text.substring(0, viewTextStart) + presenterBlock + text.substring(backgroundStart)
            }
        }

        if ("core_bubble_presenter_0_1_90" !in text) {
            throw org.gradle.api.GradleException("CoreBubblePresenter nao foi conectado ao showOverlay.")
        }
        if ("private val coreBubblePresenter = br.com.mapeiaia.rotacerta.core.CoreBubblePresenter" !in text) {
            throw org.gradle.api.GradleException("CoreBubblePresenter nao foi instalado no servico.")
        }

        if (text != original) file.writeText(text)
    }
}

coreBubblePresenterPatch.configure {
    mustRunAfter("coreBubbleDecisionPatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreBubblePresenterPatch)
}
