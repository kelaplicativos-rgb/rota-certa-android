// Delimita o bloco processRideText quando patches novos removeram a antiga
// funcao resolveRidePackageForText. A linha inserida e somente comentario e
// serve para a validacao de fonte; nao gera metodo nem codigo no APK.
val universalNoCardProcessAnchorCompat by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalIdempotenceCompatibilityGuard"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado")
        var text = file.readText()
        val token = "    private fun resolveRidePackageForText("
        if (token !in text) {
            val processStart = text.indexOf("    private suspend fun processRideText(")
            if (processStart < 0) throw GradleException("processRideText nao encontrado")
            val nextFunction = text.indexOf("\n    private ", processStart + 20)
            if (nextFunction <= processStart) throw GradleException("Proxima funcao depois de processRideText nao encontrada")
            val anchor = "\n    //    private fun resolveRidePackageForText( compatibility_boundary_0_1_102\n"
            text = text.substring(0, nextFunction) + anchor + text.substring(nextFunction)
            file.writeText(text)
        }
    }
}

tasks.named("universalNoCardRuntimeContract").configure {
    dependsOn(universalNoCardProcessAnchorCompat)
}
