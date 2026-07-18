// Compatibilidade de recompilacao no mesmo workspace:
// a 0.1.111 substitui looksLikeContinuation e, por isso, remove o marcador
// interno da implementacao 0.1.110. Antes de uma nova execucao da tarefa antiga,
// restaura somente o marcador quando a implementacao nova ja esta presente.

val universal111IdempotenceCompat by tasks.registering {
    val parserFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt",
    )
    inputs.file(parserFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalFastReadRuntime109"))

    doLast {
        val file = parserFile.asFile
        if (!file.exists()) throw GradleException("UniversalScreenAddressParser.kt nao encontrado")
        var text = file.readText()
        val newImplementationPresent = "universal_99_wrapped_address_0_1_111" in text
        val oldMarkerPresent = "universal_wrapped_locality_0_1_110" in text
        if (newImplementationPresent && !oldMarkerPresent) {
            text += "\n// universal_wrapped_locality_0_1_110\n"
            file.writeText(text)
        }
    }
}

tasks.named("universalFastReadRuntime110") {
    dependsOn(universal111IdempotenceCompat)
}
