// Se uma linha achatada contem outro logradouro ou estabelecimento depois de um
// endereco ja fechado, esse novo conteudo nao pode ser anexado ao destino.
val universalFlattenedAddressBoundaryGuard by tasks.registering {
    val parserFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt")
    inputs.file(parserFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalRuntimeStabilityGuard"))

    doLast {
        val file = parserFile.asFile
        if (!file.exists()) throw GradleException("UniversalScreenAddressParser.kt nao encontrado")
        var text = file.readText()
        if ("universal_flattened_foreign_suffix_0_1_101" !in text) {
            val old = """                        val localitySuffix = suffix.startsWith(",") ||
                            stateRegex.containsMatchIn(suffix) ||
                            cepRegex.containsMatchIn(suffix)
                        if (suffix.isNotBlank() && !localitySuffix) {
"""
            val replacement = """                        val startsNewContent = streetStartRegex.containsMatchIn(suffix) ||
                            poiStartRegex.containsMatchIn(suffix) ||
                            Regex("^(?:emei|emef|emeb|ubs|upa)\\b", RegexOption.IGNORE_CASE).containsMatchIn(suffix)
                        val localitySuffix = !startsNewContent && (
                            suffix.startsWith(",") ||
                                stateRegex.containsMatchIn(suffix) ||
                                cepRegex.containsMatchIn(suffix)
                            )
                        if (suffix.isNotBlank() && !localitySuffix) {
"""
            if (old !in text) throw GradleException("Regra de sufixo achatado nao encontrada")
            text = text.replaceFirst(old, replacement)
            text += "\n// universal_flattened_foreign_suffix_0_1_101\n"
        }
        if ("startsNewContent" !in text || "universal_flattened_foreign_suffix_0_1_101" !in text) {
            throw GradleException("Limite de endereco achatado incompleto")
        }
        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalFlattenedAddressBoundaryGuard)
}
