// Compatibilidade entre o patch universal antigo (corpo de expressao) e o
// contrato final de dois enderecos (corpo em bloco). Nao altera a regra de
// negocio; apenas deixa a funcao em formato deterministico para o patch final.

val universalTwoAddressCompatibilityPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
        var text = file.readText()
        val oldExpression = """    private fun shouldScanPackage(packageName: String?): Boolean =
        serviceReady && currentSettings.appEnabled && !packageName.isNullOrBlank() // universal_all_packages_v2_0_1_95

"""
        if (oldExpression in text) {
            text = text.replaceFirst(
                oldExpression,
                """    private fun shouldScanPackage(packageName: String?): Boolean {
        return serviceReady && currentSettings.appEnabled && !packageName.isNullOrBlank()
    } // universal_all_packages_v2_compat_0_1_98

""",
            )
        }
        if ("private fun shouldScanPackage(packageName: String?): Boolean {" !in text) {
            throw GradleException("Nao consegui normalizar shouldScanPackage para o contrato final.")
        }
        file.writeText(text)
    }
}

universalTwoAddressCompatibilityPatch.configure {
    mustRunAfter(
        tasks.matching { task ->
            task.name != name &&
                task.name != "universalTwoAddressRuntimeFinal" &&
                !task.name.startsWith("compile") &&
                !task.name.startsWith("test") &&
                task.name !in setOf("preBuild", "assemble", "assembleDebug") &&
                (task.name.contains("patch", true) ||
                    task.name.contains("fix", true) ||
                    task.name.contains("final", true) ||
                    task.name.startsWith("enforce", true))
        },
    )
}

tasks.matching { it.name == "universalTwoAddressRuntimeFinal" }.configureEach {
    dependsOn(universalTwoAddressCompatibilityPatch)
}
