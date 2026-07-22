// Rota Certa 0.1.127
// Busca simples dentro de Locais salvos e Alertas, sem criar outra tela.
// O resultado conserva os controles existentes: GPS, editar/salvar e apagar.

fun replaceKotlinFunctionSavedPlaces127(
    source: String,
    signature: String,
    replacement: String,
): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao nao encontrada para busca de locais: $signature")
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo da funcao nao encontrado para busca de locais: $signature")
    var depth = 0
    var index = braceStart
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(0, start) + replacement + source.substring(index + 1)
                }
            }
        }
        index += 1
    }
    throw GradleException("Fim da funcao nao encontrado para busca de locais: $signature")
}

fun patchSavedPlacesSearch127(mainFile: java.io.File) {
    if (!mainFile.exists()) throw GradleException("MainActivity.kt nao encontrado para busca de locais.")
    var main = mainFile.readText()
    val dollar = "$"

    val replacement = """private fun SavedPlacesModuleCard(
    savedPlaces: List<SavedPlace>,
    type: SavedPlaceType,
    highlightedSavedPlaceId: String?,
    onCreate: () -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val items = savedPlaces.filter { it.type == type }
    val isAlert = type == SavedPlaceType.ProximityAlert
    var search by remember(type) { mutableStateOf("") }
    val filteredItems = remember(items, search) {
        val query = search.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) {
            items
        } else {
            items.filter { place ->
                place.name.lowercase(Locale.ROOT).contains(query) ||
                    place.address.lowercase(Locale.ROOT).contains(query)
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isAlert) "Alertas de proximidade (${dollar}{items.size})" else "Locais salvos (${dollar}{items.size})",
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (isAlert) {
                    "Somente pontos que geram aviso de aproximacao."
                } else {
                    "Somente locais salvos para consultar ou voltar depois. Nao geram alerta."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(if (isAlert) "Criar alerta neste local" else "Salvar local atual")
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar por nome ou endereco") },
                singleLine = true,
            )
            if (search.isNotBlank()) {
                Text(
                    "Encontrados: ${dollar}{filteredItems.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                items.isEmpty() -> Text(
                    if (isAlert) "Nenhum alerta criado." else "Nenhum local salvo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                filteredItems.isEmpty() -> Text(
                    if (isAlert) {
                        "Nenhum alerta encontrado por nome ou endereco."
                    } else {
                        "Nenhum local encontrado por nome ou endereco."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> filteredItems.forEach { place ->
                    SavedPlaceEditor(
                        place = place,
                        highlighted = place.id == highlightedSavedPlaceId,
                        onRenameSavedPlace = onRenameSavedPlace,
                        onDeleteSavedPlace = onDeleteSavedPlace,
                    )
                }
            }
        }
    }
} // separate_saved_place_modules_0_1_120 saved_places_search_name_address_0_1_127
"""

    main = replaceKotlinFunctionSavedPlaces127(
        source = main,
        signature = "private fun SavedPlacesModuleCard(",
        replacement = replacement,
    )

    listOf(
        "saved_places_search_name_address_0_1_127",
        "Buscar por nome ou endereco",
        "place.name.lowercase(Locale.ROOT).contains(query)",
        "place.address.lowercase(Locale.ROOT).contains(query)",
        "SavedPlaceEditor(",
        "Text(\"GPS\")",
        "Text(\"Salvar\")",
        "Text(\"Apagar\")",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Busca de locais incompleta: ${dollar}marker")
    }

    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchSavedPlacesSearch127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
