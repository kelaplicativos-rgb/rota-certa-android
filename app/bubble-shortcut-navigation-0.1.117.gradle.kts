// Navegacao da Home para atalhos 0.1.117.

fun enforceBubbleShortcutNavigation117(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()
    if ("bubble_shortcut_navigation_0_1_117" in text) return

    val launchMarker = "        } // grouped_bubble_launch_0_1_115\n"
    val launchIndex = text.indexOf(launchMarker)
    if (launchIndex < 0) throw GradleException("Navegacao agrupada 0.1.115 nao encontrada.")
    val launchEnd = launchIndex + launchMarker.length
    text = text.substring(0, launchEnd) + """        launchIntent?.getStringExtra(EXTRA_OPEN_BUBBLE_GROUP)?.let { requestedGroup ->
            if (requestedGroup in BUBBLE_GROUP_VALUES) selectedBubbleGroup = requestedGroup
        } // bubble_shortcut_navigation_0_1_117
""" + text.substring(launchEnd)

    text = text.replace(
        "                enabled = draftName.trim() != place.name,\n",
        "                enabled = draftName.trim().isNotBlank() && (highlighted || draftName.trim() != place.name),\n",
    )

    val highlightedText = """        if (highlighted) {
            Text("Informe o nome deste item agora. Esse nome aparece na lista e no alerta de voz.", style = MaterialTheme.typography.bodySmall)
        }
"""
    val replacementText = """        if (highlighted) {
            Text(
                if (place.type == SavedPlaceType.ProximityAlert) {
                    "O nome sera falado e aparecera no popup de aproximacao. Edite e toque em Salvar."
                } else {
                    "Local para voltar depois, como estacionamento. Edite o nome e toque em Salvar; ele nao gera alerta."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
"""
    if (highlightedText in text) {
        text = text.replaceFirst(highlightedText, replacementText)
    }

    text = text.replace(
        "    SavedPlaceType.ProximityAlert -> \"Alerta de proximidade\"\n",
        "    SavedPlaceType.ProximityAlert -> \"Alerta\"\n",
    )

    val constantsAnchor = "private const val BUBBLE_GROUP_TOOLS = \"tools\"\n"
    if (constantsAnchor !in text) throw GradleException("Constantes dos grupos nao encontradas.")
    text = text.replaceFirst(
        constantsAnchor,
        constantsAnchor + """private const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
private val BUBBLE_GROUP_VALUES = setOf(
    BUBBLE_GROUP_GENERAL,
    BUBBLE_GROUP_READING,
    BUBBLE_GROUP_DESTINATION,
    BUBBLE_GROUP_ALERTS,
    BUBBLE_GROUP_APPEARANCE,
    BUBBLE_GROUP_ACCESS,
    BUBBLE_GROUP_REPORTS,
    BUBBLE_GROUP_BACKUP,
    BUBBLE_GROUP_TOOLS,
)
""",
    )

    text += "\n// bubble_shortcut_navigation_0_1_117\n"

    listOf(
        "EXTRA_OPEN_BUBBLE_GROUP",
        "bubble_shortcut_navigation_0_1_117",
        "selectedBubbleGroup = requestedGroup",
        "highlighted || draftName.trim() != place.name",
        "Local para voltar depois, como estacionamento",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Navegacao de atalhos incompleta: $marker")
    }

    file.writeText(text)
}

val bubbleShortcutNavigation117 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("inAppGroupedBubbleHome115")
    doLast { enforceBubbleShortcutNavigation117(mainFile.asFile) }
}

bubbleShortcutNavigation117.configure {
    mustRunAfter("inAppGroupedBubbleHome115")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleShortcutNavigation117)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleShortcutNavigation117)
    doFirst {
        enforceBubbleShortcutNavigation117(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
