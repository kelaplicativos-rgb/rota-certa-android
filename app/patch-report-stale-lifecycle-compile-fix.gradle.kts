// Ajustes finais de compilacao apos mover o lifecycle para depois do match real.

val reportStaleLifecycleCompileFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        // O comentario precisa ficar depois da chave; antes dela comentava a abertura do bloco Kotlin.
        val broken = "if (screenshotAgeMillis > OCR_RESULT_MAX_AGE_MS || currentPackageAfterScreenshot != screenshotRequestPackage || !shouldScanPackage(currentRootPackageName())) // report_screenshot_root_guard_0_1_86 {"
        val fixed = "if (screenshotAgeMillis > OCR_RESULT_MAX_AGE_MS || currentPackageAfterScreenshot != screenshotRequestPackage || !shouldScanPackage(currentRootPackageName())) { // report_screenshot_root_guard_0_1_86"
        text = text.replace(broken, fixed)

        // Dependendo da ordem dos patches, o bloco Read antigo fica junto do lifecycle precoce
        // e e removido. Recupera a transacao pelo hash ou a recria vinculada ao snapshot atual.
        text = text.replace(
            "transaction = corePipelineRead,",
            "transaction = coreLivePipeline.transactionFor(snapshotHash) ?: coreLivePipeline.readReady(corePipelineTransaction, snapshotHash, snapshotText.length), // report_snapshot_read_binding_0_1_86",
        )

        if (broken in text) {
            throw org.gradle.api.GradleException("A guarda de screenshot ainda comenta a abertura do bloco Kotlin.")
        }
        if (fixed !in text) {
            throw org.gradle.api.GradleException("Nao encontrei a guarda de screenshot corrigida.")
        }
        if ("transaction = corePipelineRead," in text) {
            throw org.gradle.api.GradleException("Lifecycle ainda depende da variavel Read removida.")
        }
        if ("report_snapshot_read_binding_0_1_86" !in text) {
            throw org.gradle.api.GradleException("Nao vinculei o lifecycle ao snapshot atual.")
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
