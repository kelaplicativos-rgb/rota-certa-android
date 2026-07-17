// Corrige destinos que chegam como nome de estabelecimento seguido do endereco
// entre parenteses, por exemplo:
// "Pronto Socorro ... (Rua Jose Martinho - Parque Imperial, Barueri - SP)".
// Esse formato deve formar um segundo destino quando vier em uma linha propria,
// sem transformar estabelecimentos achatados de outros cards em destino atual.

val universalPoiDestinationBoundary by tasks.registering {
    val parserFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt",
    )
    inputs.file(parserFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalFlattenedAddressBoundaryGuard"))
    dependsOn(tasks.named("universalOverlayWindowResolver"))

    doLast {
        val file = parserFile.asFile
        if (!file.exists()) throw GradleException("UniversalScreenAddressParser.kt nao encontrado")
        var text = file.readText()

        if ("universal_poi_parenthesized_address_0_1_107" !in text) {
            val localityAnchor = "    private val localityStartRegex = Regex(\n"
            if (localityAnchor !in text) {
                throw GradleException("Ponto dos reconhecedores de endereco nao encontrado")
            }
            val parenthesizedStreetRegex = """    private val parenthesizedStreetRegex = Regex(
        "\\(((?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)(?:\\b|(?=\\s)))",
        RegexOption.IGNORE_CASE,
    )
"""
            text = text.replaceFirst(localityAnchor, parenthesizedStreetRegex + localityAnchor)

            val oldStreetLookup = """        val streetMatch = streetStartRegex.find(value) ?: return false
        val streetGroup = streetMatch.groups[1] ?: return false
"""
            val newStreetLookup = """        val streetGroup = streetStartRegex.find(value)?.groups?.get(1)
            ?: parenthesizedStreetRegex.find(value)?.groups?.get(1)
            ?: return false
"""
            val streetLookupCount = oldStreetLookup.toRegex(RegexOption.LITERAL).findAll(text).count()
            if (streetLookupCount != 2) {
                throw GradleException("Esperava dois pontos de leitura de logradouro; encontrei $streetLookupCount")
            }
            text = text.replace(oldStreetLookup, newStreetLookup)

            val oldContinuation = "        if (streetStartRegex.containsMatchIn(value)) return false\n"
            val newContinuation = "        if (streetStartRegex.containsMatchIn(value) || parenthesizedStreetRegex.containsMatchIn(value)) return false\n"
            if (oldContinuation !in text) {
                throw GradleException("Regra de continuacao de endereco nao encontrada")
            }
            text = text.replaceFirst(oldContinuation, newContinuation)

            val oldCleanFunction = """    private fun cleanAddressSegment(value: String): String {
        val withoutMarker = value.replace(markerPrefix, "").trim()
        val starts = listOfNotNull(
            streetStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            localityStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            poiStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
        )
        val start = starts.minOrNull()
        return if (start != null) withoutMarker.substring(start).trim() else withoutMarker
    }
"""
            val newCleanFunction = """    private fun cleanAddressSegment(value: String): String {
        val withoutMarker = value.replace(markerPrefix, "").trim()
        if (parenthesizedStreetRegex.containsMatchIn(withoutMarker)) {
            return withoutMarker // universal_poi_parenthesized_address_0_1_107
        }
        val starts = listOfNotNull(
            streetStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            localityStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            poiStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
        )
        val start = starts.minOrNull()
        return if (start != null) withoutMarker.substring(start).trim() else withoutMarker
    }
"""
            if (oldCleanFunction !in text) {
                throw GradleException("Funcao de limpeza de endereco nao encontrada")
            }
            text = text.replaceFirst(oldCleanFunction, newCleanFunction)
            text += "\n// universal_poi_destination_boundary_0_1_107\n"
        }

        listOf(
            "parenthesizedStreetRegex",
            "universal_poi_parenthesized_address_0_1_107",
            "universal_poi_destination_boundary_0_1_107",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Contrato de destino com estabelecimento incompleto: $marker")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalPoiDestinationBoundary)
}
