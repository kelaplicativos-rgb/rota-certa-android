// Compatibilidade final do gesto da bolinha 0.1.120.
//
// Os patches legados precisam da atribuicao literal bubbleGestureActive = true
// durante a montagem. O workflow antigo, porem, tratava uma busca multiline do
// grep como dois padroes independentes e acusava falso positivo sempre que a
// funcao hideResourceShortcuts existia em qualquer ponto do arquivo.
//
// Esta ultima camada renomeia somente a funcao privada de fechamento, depois de
// todos os patches antigos. O comportamento permanece identico: toque alterna o
// popup e arraste real fecha os atalhos quando o movimento comeca.
fun applyPopupGestureValidatorCompat120(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()

    text = text.replace("hideResourceShortcuts", "closeResourceShortcuts")

    if ("popup_gesture_validator_compat_0_1_120" !in text) {
        text += "\n// popup_gesture_validator_compat_0_1_120\n"
    }

    listOf(
        "private fun closeResourceShortcuts()",
        "closeResourceShortcuts() // popup_close_only_on_drag_0_1_120",
        "bubbleGestureActive = true",
        "popup_gesture_validator_compat_0_1_120",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato de gesto incompleto: $marker")
    }

    if ("hideResourceShortcuts()" in text) {
        throw GradleException("Nome antigo de fechamento ainda presente.")
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
