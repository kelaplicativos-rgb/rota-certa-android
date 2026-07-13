// Ajusta a posicao do marcador da guarda de screenshot instalada pelo patch do relatorio.
// O comentario precisa ficar depois da chave; antes dela comentava a abertura do bloco Kotlin.

val reportStaleLifecycleCompileFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        val broken = "if (screenshotAgeMillis > OCR_RESULT_MAX_AGE_MS || currentPackageAfterScreenshot != screenshotRequestPackage || !shouldScanPackage(currentRootPackageName())) // report_screenshot_root_guard_0_1_86 {"
        val fixed = "if (screenshotAgeMillis > OCR_RESULT_MAX_AGE_MS || currentPackageAfterScreenshot != screenshotRequestPackage || !shouldScanPackage(currentRootPackageName())) { // report_screenshot_root_guard_0_1_86"
        text = text.replace(broken, fixed)

        if (broken in text) {
            throw org.gradle.api.GradleException("A guarda de screenshot ainda comenta a abertura do bloco Kotlin.")
        }
        if (fixed !in text) {
            throw org.gradle.api.GradleException("Nao encontrei a guarda de screenshot corrigida.")
        }

        if (text != original) file.writeText(text)
    }
}

reportStaleLifecycleCompileFix.configure {
    mustRunAfter("reportStaleLifecycleFix")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(reportStaleLifecycleCompileFix)
}
