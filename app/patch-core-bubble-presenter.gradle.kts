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
            val oldBlock = """        view.text = formatBubbleDistanceKm(currentDistanceKm)
        view.textSize = bubbleTextSizeSp(view.text.toString())
"""
            val newBlock = """        val coreBubblePresentation = coreBubblePresenter.present(
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
            if (oldBlock !in text) {
                throw org.gradle.api.GradleException("Nao encontrei o bloco de texto/tamanho antigo da bolinha para ligar o presenter.")
            }
            text = text.replace(oldBlock, newBlock)
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
