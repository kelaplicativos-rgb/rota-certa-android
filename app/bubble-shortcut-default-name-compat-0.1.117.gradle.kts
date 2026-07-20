// Mantem o servico independente das funcoes privadas da MainActivity.

fun enforceShortcutDefaultNameCompat117(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()
    text = text.replace(
        "defaultName: String = defaultSavedPlaceName(type)",
        "defaultName: String = if (type == SavedPlaceType.ProximityAlert) \"Alerta\" else \"Local salvo\"",
    )
    if ("defaultName: String = defaultSavedPlaceName(type)" in text) {
        throw GradleException("Servico ainda depende do nome privado da MainActivity.")
    }
    file.writeText(text)
}

val bubbleShortcutDefaultNameCompat117 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("bubbleResourceShortcutsRuntime117")
    doLast { enforceShortcutDefaultNameCompat117(serviceFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleShortcutDefaultNameCompat117)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleShortcutDefaultNameCompat117)
    doFirst {
        enforceShortcutDefaultNameCompat117(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
