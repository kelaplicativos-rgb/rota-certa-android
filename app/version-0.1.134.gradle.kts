val androidExtension134 = extensions.getByName("android")
val defaultConfig134 = androidExtension134.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension134)
defaultConfig134.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig134, "0.1.134")

// Registrado antes do checklist 15. Como doFirst usa ordem inversa, esta ponte
// roda depois do finalizador anti-pisca e antes dos validadores/compilador.
fun restoreResourceShortcutClickAfterChecklist15(file: java.io.File) {
    if (!file.exists()) return
    var text = file.readText()
    text = text.replace(
        "newView.setOnClickListener { toggleActionMenu() }",
        "newView.setOnClickListener { toggleResourceShortcuts() } // resource_shortcut_click_preserved_checklist_15",
    )
    file.writeText(text)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        restoreResourceShortcutClickAfterChecklist15(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
