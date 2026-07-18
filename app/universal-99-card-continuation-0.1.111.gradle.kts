// Preserva o nome do local antes de marcadores como "Cidade" quando a linha
// completa um Condominio/Residencial iniciado na linha anterior.

val universal99CardContinuation111 by tasks.registering {
    val parserFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt",
    )
    inputs.file(parserFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universal99CardAddresses111"))

    doLast {
        val file = parserFile.asFile
        if (!file.exists()) throw GradleException("UniversalScreenAddressParser.kt nao encontrado")
        var text = file.readText()
        if ("universal_99_named_continuation_0_1_111" !in text) {
            val oldValue = """                while (nextIndex < lines.size && parts.size < 3) {
                    val next = cleanAddressSegment(lines[nextIndex])
                    if (!looksLikeContinuation(next, parts.last())) break
"""
            val newValue = """                while (nextIndex < lines.size && parts.size < 3) {
                    val preserveNamedPlaceContinuation = isPotentialNamedPlacePrefix(parts.first())
                    val next = if (preserveNamedPlaceContinuation) {
                        normalizeLine(lines[nextIndex])
                    } else {
                        cleanAddressSegment(lines[nextIndex])
                    }
                    if (!looksLikeContinuation(next, parts.last())) break
"""
            if (oldValue !in text) throw GradleException("Loop de continuacao do card 99 nao encontrado")
            text = text.replaceFirst(oldValue, newValue)
            text += "\n// universal_99_named_continuation_0_1_111\n"
        }
        if ("preserveNamedPlaceContinuation" !in text ||
            "universal_99_named_continuation_0_1_111" !in text
        ) {
            throw GradleException("Preservacao do destino nomeado incompleta")
        }
        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universal99CardContinuation111)
}
