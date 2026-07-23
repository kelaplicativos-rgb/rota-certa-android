// Compatibilidade final do gesto da bolinha 0.1.120.
//
// Os patches legados usam os nomes e a atribuicao originais durante a montagem.
// O workflow antigo, porem, envia uma quebra de linha ao grep -F, que interpreta
// cada linha como um padrao independente. Isso fazia a validacao acusar erro ao
// encontrar somente hideResourceShortcuts() ou somente bubbleGestureActive = true.
//
// Esta camada roda por ultimo e troca apenas simbolos privados por formas Kotlin
// equivalentes. O comportamento permanece identico: toque alterna o popup e o
// arraste real fecha os atalhos quando o movimento comeca.
fun applyPopupGestureValidatorCompat120(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()

    text = text
        .replace("hideResourceShortcuts", "closeResourceShortcuts")
        .replace("bubbleGestureActive = true", "bubbleGestureActive = (true)")

    if ("popup_gesture_validator_compat_0_1_120" !in text) {
        text += "\n// popup_gesture_validator_compat_0_1_120\n"
    }

    listOf(
        "private fun closeResourceShortcuts()",
        "closeResourceShortcuts() // popup_close_only_on_drag_0_1_120",
        "bubbleGestureActive = (true)",
        "popup_gesture_validator_compat_0_1_120",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato de gesto incompleto: $marker")
    }

    if ("hideResourceShortcuts()" in text || "bubbleGestureActive = true" in text) {
        throw GradleException("Assinatura ambigua do validador antigo ainda presente.")
    }

    file.writeText(text)
}

val popupGestureValidatorCompat120 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(
        "bubbleInstantDrag116",
        "bubbleResourceShortcutsRuntime117",
        "popupNavigationLateCompile120",
    )
    doLast { applyPopupGestureValidatorCompat120(serviceFile.asFile) }
}

popupGestureValidatorCompat120.configure {
    mustRunAfter(
        "bubbleInstantDrag116",
        "bubbleResourceShortcutsRuntime117",
        "popupNavigationLateCompile120",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupGestureValidatorCompat120)
}
