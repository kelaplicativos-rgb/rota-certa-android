val patchHideInsufficientResultCard by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace(
"""        latestResult?.let {
            Spacer(Modifier.height(12.dp))
            ResultCard(it, settings)
        }
""",
"""        latestResult
            ?.takeIf { it.recommendation != Recommendation.InsufficientData }
            ?.let {
                Spacer(Modifier.height(12.dp))
                ResultCard(it, settings)
            }
""",
        )

        text = text.replace(
"""        latestResult?.let { result ->
            ResultCard(result, settings)
        }
""",
"""        latestResult
            ?.takeIf { it.recommendation != Recommendation.InsufficientData }
            ?.let { result ->
                ResultCard(result, settings)
            }
""",
        )

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchHideInsufficientResultCard.configure {
    mustRunAfter("patchBubbleCardParity")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchHideInsufficientResultCard)
}
