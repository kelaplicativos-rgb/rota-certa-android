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

fun removeSecondGeneratedFunction134(source: String, signature: String): String {
    val first = source.indexOf(signature)
    if (first < 0) return source
    val second = source.indexOf(signature, first + signature.length)
    if (second < 0) return source
    val open = source.indexOf('{', second)
    if (open < 0) return source
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    var end = index + 1
                    while (end < source.length && source[end] == '\n') end += 1
                    return source.removeRange(second, end)
                }
            }
        }
        index += 1
    }
    return source
}

fun deduplicateGeneratedPackageLifecycle134(packageDir: java.io.File) {
    val repositoryFile = packageDir.listFiles()
        ?.firstOrNull { it.isFile && it.extension == "kt" && "class SettingsRepository" in runCatching { it.readText() }.getOrDefault("") }
        ?: return
    var text = repositoryFile.readText()
    text = removeSecondGeneratedFunction134(
        text,
        "    suspend fun pruneSelectedPackageIfNoCards(packageName: String)",
    )
    repositoryFile.writeText(text)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        val packageDir = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        restoreResourceShortcutClickAfterChecklist15(
            java.io.File(packageDir, "LiveRideAccessibilityService.kt"),
        )
        deduplicateGeneratedPackageLifecycle134(packageDir)
    }
}
