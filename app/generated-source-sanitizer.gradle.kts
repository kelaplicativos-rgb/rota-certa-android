// Sanitiza o codigo efetivamente gerado depois de todos os patches acumulados.
// Mantem as correcoes no codigo compilado, sem mascarar erros do Android Lint.

val generatedSourceSanitizer by tasks.registering {
    val serviceFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
    )
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para sanitizacao final.")

        var text = file.readText()
        val original = text

        // Um patch antigo inseria a limpeza da assinatura com 12 espacos fixos,
        // quebrando a estrutura visual de blocos com outras profundidades.
        text = Regex(
            "(?m)^(\\s*)registeredCardGate\\.clear\\(\\)\\n\\s*lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81",
        ).replace(text) { match ->
            val indent = match.groupValues[1]
            "${indent}registeredCardGate.clear()\n" +
                "${indent}lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81"
        }

        // O uso de ScreenshotResult e HardwareBuffer e protegido por Android R,
        // portanto a anotacao precisa permanecer no metodo depois da limpeza final.
        val screenshotMethod = "    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap? {"
        val annotatedScreenshotMethod =
            "    @RequiresApi(Build.VERSION_CODES.R)\n$screenshotMethod"
        if (screenshotMethod in text && annotatedScreenshotMethod !in text) {
            text = text.replace(screenshotMethod, annotatedScreenshotMethod)
        }

        if (screenshotMethod in text && annotatedScreenshotMethod !in text) {
            throw GradleException("A guarda de API do ScreenshotResult nao foi restaurada.")
        }

        if (text != original) file.writeText(text)
    }
}

// Somente os dois geradores que criam os trechos corrigidos precisam anteceder
// o sanitizador. Evita capturar tarefas internas do Android/Gradle por nome.
generatedSourceSanitizer.configure {
    mustRunAfter(
        "patchBubbleRenderStability",
        "patchFinalDiagnosticCleanup",
    )
}

tasks.matching { task ->
    task.name == "preBuild" ||
        task.name.startsWith("compile") ||
        task.name.startsWith("test") ||
        task.name.startsWith("lint")
}.configureEach {
    dependsOn(generatedSourceSanitizer)
}
