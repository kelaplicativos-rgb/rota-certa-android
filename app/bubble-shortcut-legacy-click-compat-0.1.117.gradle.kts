// Compatibilidade de compilacao: funcoes antigas ainda chamam onMainBubbleClick.
// A ponte apenas redireciona para a grade leve 0.1.117.

fun enforceBubbleShortcutLegacyClickCompat117(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()
    if ("bubble_shortcut_legacy_click_compat_0_1_117" in text) return

    if ("    private fun onMainBubbleClick() {" !in text) {
        val anchor = "    private fun toggleActionMenu() {\n"
        if (anchor !in text) throw GradleException("toggleActionMenu nao encontrado para compatibilidade.")
        text = text.replaceFirst(
            anchor,
            """    private fun onMainBubbleClick() {
        toggleResourceShortcuts()
    } // bubble_shortcut_legacy_click_compat_0_1_117

""" + anchor,
        )
    } else {
        text += "\n// bubble_shortcut_legacy_click_compat_0_1_117\n"
    }

    if ("toggleResourceShortcuts()" !in text || "bubble_shortcut_legacy_click_compat_0_1_117" !in text) {
        throw GradleException("Ponte de clique 0.1.117 incompleta.")
    }
    file.writeText(text)
}

val bubbleShortcutLegacyClickCompat117 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("bubbleResourceShortcutsRuntime117")
    doLast { enforceBubbleShortcutLegacyClickCompat117(serviceFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleShortcutLegacyClickCompat117)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleShortcutLegacyClickCompat117)
    doFirst {
        enforceBubbleShortcutLegacyClickCompat117(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
