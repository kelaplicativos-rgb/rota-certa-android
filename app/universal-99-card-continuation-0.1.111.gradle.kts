// Preserva o nome do local antes de marcadores como "Cidade" quando a linha
// completa um Condominio/Residencial iniciado na linha anterior e aceita
// continuacoes como "Jardim Sao" / "Jose (Sao Mateus)".

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
            val oldLoop = """                while (nextIndex < lines.size && parts.size < 3) {
                    val next = cleanAddressSegment(lines[nextIndex])
                    if (!looksLikeContinuation(next, parts.last())) break
"""
            val newLoop = """                while (nextIndex < lines.size && parts.size < 3) {
                    val preserveNamedPlaceContinuation = isPotentialNamedPlacePrefix(parts.first())
                    val next = if (preserveNamedPlaceContinuation) {
                        normalizeLine(lines[nextIndex])
                    } else {
                        cleanAddressSegment(lines[nextIndex])
                    }
                    if (!looksLikeContinuation(next, parts.last())) break
"""
            if (oldLoop !in text) throw GradleException("Loop de continuacao do card 99 nao encontrado")
            text = text.replaceFirst(oldLoop, newLoop)

            val oldLocalityCondition = """        val wrappedLocalityContinuation = danglingAddressPrefix &&
            value.contains(',') &&
            normalized.length in 3..100
"""
            val newLocalityCondition = """        val wrappedLocalityContinuation = danglingAddressPrefix &&
            (value.contains(',') || value.contains('(')) &&
            normalized.length in 3..100
"""
            if (oldLocalityCondition !in text) {
                throw GradleException("Condicao de localidade quebrada do card 99 nao encontrada")
            }
            text = text.replaceFirst(oldLocalityCondition, newLocalityCondition)
            text += "\n// universal_99_named_continuation_0_1_111\n"
        }
        if ("preserveNamedPlaceContinuation" !in text ||
            "value.contains('(')" !in text ||
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
