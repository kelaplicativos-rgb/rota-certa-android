// Corrige destinos que chegam como nome de estabelecimento seguido do endereco
// entre parenteses, por exemplo:
// "Pronto Socorro ... (Rua Jose Martinho - Parque Imperial, Barueri - SP)".
// Esse formato deve formar um segundo destino, e nao ser anexado ao embarque.

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
            val oldStreetBoundary =
                "(?:^|[\\\\s:;])((?:r\\\\.|av\\\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)(?:\\\\b|(?=\\\\s)))"
            val newStreetBoundary =
                "(?:^|[\\\\s:;(])((?:r\\\\.|av\\\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)(?:\\\\b|(?=\\\\s)))"
            if (oldStreetBoundary !in text) {
                throw GradleException("Limite inicial de logradouro nao encontrado")
            }
            text = text.replaceFirst(oldStreetBoundary, newStreetBoundary)

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
        val streetMatch = streetStartRegex.find(withoutMarker)
        val streetStart = streetMatch?.groups?.get(1)?.range?.first
        val prefixBeforeStreet = streetStart
            ?.takeIf { it > 0 }
            ?.let { withoutMarker.substring(0, it).trimEnd() }
        if (prefixBeforeStreet?.endsWith("(") == true) {
            return withoutMarker // universal_poi_parenthesized_address_0_1_107
        }
        val starts = listOfNotNull(
            streetStart,
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
            "[\\\\s:;(]",
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
