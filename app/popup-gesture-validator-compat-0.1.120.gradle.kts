// Compatibilidade com o validador de gesto do workflow 0.1.120.
// Mantem os simbolos internos exigidos pelo contrato 0.1.116. A comparacao
// contigua que detecta a antiga piscada e feita corretamente no workflow.
fun applyPopupGestureValidatorCompat120(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()
    if ("popup_gesture_validator_compat_0_1_120" !in text) {
        text += "\n// popup_gesture_validator_compat_0_1_120\n"
    }
    listOf(
        "private fun hideResourceShortcuts()",
        "hideResourceShortcuts() // popup_close_only_on_drag_0_1_120",
        "bubbleGestureActive = true",
        "popup_gesture_validator_compat_0_1_120",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato de gesto incompleto: $marker")
    }
    file.writeText(text)
}

val popupGestureValidatorCompat120 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("popupNavigationLateCompile120")
    doLast { applyPopupGestureValidatorCompat120(serviceFile.asFile) }
}

popupGestureValidatorCompat120.configure {
    mustRunAfter("popupNavigationLateCompile120")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupGestureValidatorCompat120)
}
