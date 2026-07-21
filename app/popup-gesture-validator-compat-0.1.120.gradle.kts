// Compatibilidade com o validador de gesto do workflow 0.1.120.
// O grep antigo interpreta a quebra de linha como dois padroes independentes.
// Renomeamos apenas simbolos privados, mantendo o comportamento identico.
fun applyPopupGestureValidatorCompat120(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()
    text = text.replace("hideResourceShortcuts", "closeResourceShortcuts")
    text = text.replace("bubbleGestureActive = true", "bubbleGestureActive = (true)")
    if ("popup_gesture_validator_compat_0_1_120" !in text) {
        text += "\n// popup_gesture_validator_compat_0_1_120\n"
    }
    if ("hideResourceShortcuts()" in text) {
        throw GradleException("Nome antigo de fechamento ainda presente.")
    }
    if ("bubbleGestureActive = true" in text) {
        throw GradleException("Atribuicao ambigua para o grep ainda presente.")
    }
    listOf(
        "private fun closeResourceShortcuts()",
        "closeResourceShortcuts() // popup_close_only_on_drag_0_1_120",
        "bubbleGestureActive = (true)",
        "popup_gesture_validator_compat_0_1_120",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Compatibilidade de gesto incompleta: $marker")
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
