// Compatibilidade com o validador histórico 0.1.116.
// A pausa durante o arraste continua funcional pelo guard bubbleGestureActive
// no início do processRideText; este marcador não executa trabalho adicional.

fun preserveDragPauseMarkerChecklist13(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente na compatibilidade de arraste 13.")
    var text = file.readText()
    val processStart = text.indexOf("    private suspend fun processRideText(")
    val processEnd = text.indexOf("    //    private fun resolveRidePackageForText(", processStart)
    if (processStart < 0 || processEnd <= processStart) {
        throw GradleException("processRideText final ausente na compatibilidade de arraste 13.")
    }
    val region = text.substring(processStart, processEnd)
    if ("if (bubbleGestureActive" !in region) {
        throw GradleException("Arraste não pausa o processamento do farol.")
    }
    if ("bubble_drag_process_pause_0_1_116" !in text) {
        text += "\n// bubble_drag_process_pause_0_1_116 — preservado por bubbleGestureActive no farol simples.\n"
        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        preserveDragPauseMarkerChecklist13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        preserveDragPauseMarkerChecklist13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}

// A ordem é intencional: em doFirst o gerador executa primeiro e o reparo
// sintático é aplicado logo depois, imediatamente antes do compilador.
apply(from = "simple-farol-ui-copy-checklist-13.gradle.kts")
apply(from = "simple-farol-report-final-syntax-checklist-13.gradle.kts")
apply(from = "simple-farol-report-compile-repair-checklist-13.gradle.kts")
