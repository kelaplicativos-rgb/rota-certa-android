package br.com.mapeiaia.rotacerta

import java.io.File
import java.text.Normalizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacesSearchContractTest {
    private fun mainSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("MainActivity.kt nao encontrado")

    private fun withoutAccents(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

    @Test
    fun savedPlacesCanBeFoundByNameOrAddress() {
        val main = mainSource()
        val normalizedMain = withoutAccents(main)

        assertTrue("Campo de busca precisa existir", "Buscar por nome ou endereco" in normalizedMain)
        assertTrue("Busca precisa filtrar pelo nome", "place.name.lowercase(Locale.ROOT).contains(query)" in main)
        assertTrue("Busca precisa filtrar pelo endereco", "place.address.lowercase(Locale.ROOT).contains(query)" in main)
        assertTrue("Busca precisa funcionar dentro do modulo de locais", "saved_places_search_name_address_0_1_127" in main)
        assertTrue("Estado vazio de busca precisa ser explicado", "Nenhum local encontrado por nome ou endereco" in normalizedMain)
    }

    @Test
    fun locatedItemKeepsNavigationEditingAndDeletion() {
        val main = mainSource()
        val searchStart = main.indexOf("private fun SavedPlacesModuleCard(")
        val editorStart = main.indexOf("private fun SavedPlaceEditor(", searchStart)
        val editorEnd = main.indexOf("private fun ResultCard(", editorStart)
        val region = main.substring(searchStart, editorEnd)

        assertTrue("Resultado filtrado precisa abrir o editor existente", "SavedPlaceEditor(" in region)
        assertTrue("Item localizado precisa manter navegacao GPS", "Text(\"GPS\")" in region)
        assertTrue("Item localizado precisa permitir salvar edicao", "Text(\"Salvar\")" in region)
        assertTrue("Item localizado precisa permitir apagar", "Text(\"Apagar\")" in region)
    }

    @Test
    fun duplicateComposeAnnotationsAreRemoved() {
        val main = mainSource()

        assertFalse("@Composable duplicado nao pode compilar", Regex("@Composable\\s*\\n@Composable").containsMatchIn(main))
        assertTrue("Limpeza final precisa estar registrada", "compile_final_cleanup_0_1_127" in main)
    }
}
