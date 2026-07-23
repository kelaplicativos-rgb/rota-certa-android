// Etapa 10 — reparos finais sobre os fontes materializados pelo preBuild.

fun repairFinalOverlayCompileChecklist10(file: java.io.File) {
    if (!file.exists()) throw GradleException("BubbleShortcutOverlayController.kt ausente no reparo Kotlin 10.")
    var text = file.readText()
    val start = text.indexOf("    fun showImportedRadarAlert(")
    val end = if (start >= 0) text.indexOf("    fun hideProximityAlert()", start) else -1
    if (start < 0 || end <= start) return
    var region = text.substring(start, end)
    if ("val scale = appearanceStore.scale()" !in region) {
        val anchor = "        hideProximityAlert()\n"
        if (anchor !in region) throw GradleException("Âncora do alerta de radar ausente no reparo Kotlin 10.")
        region = region.replaceFirst(anchor, anchor + "        val scale = appearanceStore.scale()\n")
    }
    region = region
        .replace("val container = alertContainer()", "val container = alertContainer(scale)")
        .replace("popupButton(\"Fechar\")", "popupButton(\"Fechar\", scale)")
    text = text.substring(0, start) + region + text.substring(end)
    file.writeText(text)
}

fun repairFinalMainCompileChecklist10(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no reparo Kotlin 10.")
    var text = file.readText()

    text = text.replace(
        "name = if (isAlert) \"Alerta\" else \"\" // blank_saved_place_name_checklist_7,",
        "name = if (isAlert) \"Alerta\" else \"\", // blank_saved_place_name_checklist_7",
    )
    while ("@Composable\n@Composable\n" in text) {
        text = text.replace("@Composable\n@Composable\n", "@Composable\n")
    }

    val oldHistoryCall = """                TAB_HISTORY -> ReportsGroupScreen(
                    diagnostic = null,
                    history = history,
                )
"""
    val newHistoryCall = """                TAB_HISTORY -> ReportsGroupScreen(
                    settings = settings,
                    cardTemplates = cardTemplates,
                    diagnostic = null,
                    history = history,
                )
"""
    if (oldHistoryCall in text) text = text.replaceFirst(oldHistoryCall, newHistoryCall)

    val oldSignature = """private fun ReportsGroupScreen(
    diagnostic: LiveDiagnostic?,
    history: List<AnalysisResult>,
) {
"""
    val newSignature = """private fun ReportsGroupScreen(
    settings: AppSettings,
    cardTemplates: List<RideCardTemplate>,
    diagnostic: LiveDiagnostic?,
    history: List<AnalysisResult>,
) {
"""
    if (oldSignature in text) text = text.replaceFirst(oldSignature, newSignature)

    val oldDiagnosticCall = """        DiagnosticExpander(
            settings = draft,
            diagnostic = diagnostic,
        )
"""
    val newDiagnosticCall = """        DiagnosticExpander(
            settings = settings,
            diagnostic = diagnostic,
            cardTemplates = cardTemplates,
            onRegisterRideCard = { _, _ -> },
        )
"""
    if (oldDiagnosticCall in text) text = text.replaceFirst(oldDiagnosticCall, newDiagnosticCall)

    listOf(
        "name = if (isAlert) \"Alerta\" else \"\", // blank_saved_place_name_checklist_7",
        "settings = settings,\n                    cardTemplates = cardTemplates,",
        "onRegisterRideCard = { _, _ -> },",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("MainActivity final ainda incompleto: $marker")
    }
    if ("@Composable\n@Composable\n" in text || "settings = draft" in text) {
        throw GradleException("MainActivity final ainda contém código inválido antigo.")
    }
    file.writeText(text)
}

fun removeInvalidComposeWeightImportChecklist10(file: java.io.File) {
    if (!file.exists()) throw GradleException("${file.name} ausente no reparo do import weight 10.")
    val repaired = file.readText().replace("import androidx.compose.foundation.layout.weight\n", "")
    file.writeText(repaired)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        repairFinalOverlayCompileChecklist10(java.io.File(root, "BubbleShortcutOverlayController.kt"))
        repairFinalMainCompileChecklist10(java.io.File(root, "MainActivity.kt"))
        removeInvalidComposeWeightImportChecklist10(java.io.File(root, "QuickRepliesActivity.kt"))
        removeInvalidComposeWeightImportChecklist10(java.io.File(root, "WorkRegionPinsCard.kt"))
    }
}
