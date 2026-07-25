// Etapa 10 — reparos finais sobre os fontes materializados pelo preBuild.
// A aplicação decisiva ocorre no doFirst da compilação Kotlin, depois de todos
// os geradores e validadores históricos que ainda reescrevem os fontes.

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

    val reportsStart = text.indexOf("private fun ReportsGroupScreen(")
    val reportsEnd = if (reportsStart >= 0) text.indexOf("\n@Composable\nprivate fun HistoryScreen(", reportsStart) else -1
    if (reportsStart >= 0 && reportsEnd > reportsStart) {
        val replacement = """private fun ReportsGroupScreen(
    settings: AppSettings,
    cardTemplates: List<RideCardTemplate>,
    diagnostic: LiveDiagnostic?,
    history: List<AnalysisResult>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Relatorios e historico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DiagnosticExpander(
            settings = settings,
            diagnostic = diagnostic,
            cardTemplates = cardTemplates,
            onRegisterRideCard = { _, _ -> },
        )
        Text("Historico de decisoes", fontWeight = FontWeight.Bold)
        HistoryScreen(history)
    }
} // grouped_reports_tools_0_1_115 final_reports_compile_repair_checklist_10
"""
        text = text.substring(0, reportsStart) + replacement + text.substring(reportsEnd)
    }

    listOf(
        "name = if (isAlert) \"Alerta\" else \"\", // blank_saved_place_name_checklist_7",
        "settings = settings,\n                    cardTemplates = cardTemplates,",
        "final_reports_compile_repair_checklist_10",
        "onRegisterRideCard = { _, _ -> },",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("MainActivity final ainda incompleto: $marker")
    }
    if ("@Composable\n@Composable\n" in text) {
        throw GradleException("MainActivity final ainda contém anotação Compose duplicada.")
    }
    val finalReportsStart = text.indexOf("private fun ReportsGroupScreen(")
    val finalReportsEnd = if (finalReportsStart >= 0) text.indexOf("\n@Composable\nprivate fun HistoryScreen(", finalReportsStart) else -1
    val finalReportsRegion = if (finalReportsStart >= 0 && finalReportsEnd > finalReportsStart) {
        text.substring(finalReportsStart, finalReportsEnd)
    } else {
        ""
    }
    if ("settings = draft" in finalReportsRegion) {
        throw GradleException("Relatórios ainda usam estado draft inexistente.")
    }
    file.writeText(text)
}

fun removeInvalidComposeWeightImportChecklist10(file: java.io.File) {
    if (!file.exists()) throw GradleException("${file.name} ausente no reparo do import weight 10.")
    val repaired = file.readText().replace("import androidx.compose.foundation.layout.weight\n", "")
    file.writeText(repaired)
}

fun applyFinalKotlinCompileRepairsChecklist10() {
    val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
    repairFinalOverlayCompileChecklist10(java.io.File(root, "BubbleShortcutOverlayController.kt"))
    repairFinalMainCompileChecklist10(java.io.File(root, "MainActivity.kt"))
    removeInvalidComposeWeightImportChecklist10(java.io.File(root, "QuickRepliesActivity.kt"))
    removeInvalidComposeWeightImportChecklist10(java.io.File(root, "WorkRegionPinsCard.kt"))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast { applyFinalKotlinCompileRepairsChecklist10() }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst { applyFinalKotlinCompileRepairsChecklist10() }
}
