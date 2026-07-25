// Compatibilidade de ancoras para o patch 0.1.127.
// Alguns patches antigos deixam estas funcoes com corpo por expressao. A correcao
// principal trabalha com blocos para substituir o contrato inteiro com seguranca.

fun normalizeManualAppsAnchors127(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para compatibilidade 0.1.127.")
    var text = file.readText()

    val selectedExpression = """    private fun selectedRidePackages(settings: AppSettings): Set<String> = emptySet() // universal_no_packages_v2_0_1_95
"""
    val selectedBlock = """    private fun selectedRidePackages(settings: AppSettings): Set<String> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredSettings = settings
        return emptySet()
    } // universal_no_packages_v2_0_1_95 manual_selected_packages_anchor_0_1_127
"""
    if (selectedExpression in text) {
        text = text.replaceFirst(selectedExpression, selectedBlock)
    }

    val reasonExpression = """    private fun scanBlockReason(packageName: String?): String =
        br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = packageName,
            ownPackageName = this.packageName,
            settings = currentSettings,
        ).reason
"""
    val reasonBlock = """    private fun scanBlockReason(packageName: String?): String {
        return br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = packageName,
            ownPackageName = this.packageName,
            settings = currentSettings,
        ).reason
    } // manual_scan_reason_anchor_0_1_127
"""
    if (reasonExpression in text) {
        text = text.replaceFirst(reasonExpression, reasonBlock)
    }

    listOf(
        "manual_selected_packages_anchor_0_1_127",
        "manual_scan_reason_anchor_0_1_127",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Ancora 0.1.127 ausente: $marker")
    }

    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        normalizeManualAppsAnchors127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
