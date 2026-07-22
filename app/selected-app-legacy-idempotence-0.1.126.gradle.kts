// Compatibilidade de build 0.1.126.
// Em uma segunda invocacao do Gradle, os fontes ja foram finalizados pela politica
// universal. Nessa situacao, o patch legado 0.1.122 nao pode recriar seletor de apps.

fun universalPolicyAlreadyFinalized126(): Boolean {
    val service = layout.projectDirectory
        .file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        .asFile
    val main = layout.projectDirectory
        .file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
        .asFile
    if (!service.exists() || !main.exists()) return false
    val serviceText = service.readText()
    val mainText = main.readText()
    return "universal_package_content_gate_0_1_126" in serviceText &&
        "pre_registered_runtime_cleanup_0_1_126" in serviceText &&
        "no_selected_apps_picker_ui_0_1_126" in mainText &&
        "no_pre_registered_cards_ui_0_1_126" in mainText
}

listOf("radarWorkTracking121", "workTrackingCardAnchorCleanup121").forEach { legacyTaskName ->
    tasks.matching { it.name == legacyTaskName }.configureEach {
        onlyIf {
            val finalized = universalPolicyAlreadyFinalized126()
            if (finalized) {
                logger.lifecycle("$legacyTaskName ignorado: politica universal 0.1.126 ja aplicada.")
            }
            !finalized
        }
    }
}
