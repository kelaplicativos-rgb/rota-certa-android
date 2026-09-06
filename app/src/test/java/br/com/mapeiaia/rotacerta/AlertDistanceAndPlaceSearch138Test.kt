package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDistanceAndPlaceSearch138Test {
    private val source = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()

    @Test fun `alertas oferecem somente 200 500 e 1000 metros e ativam ao selecionar`() {
        assertTrue(source.contains("val values = listOf(200, 500, 1000)"))
        assertTrue(source.contains("proximityAlertsEnabled = true"))
    }

    @Test fun `busca de locais fica fora do expander e mostra botao GPS`() {
        assertTrue(source.contains("SavedPlaceSearchResult138"))
        assertTrue(source.contains("Nenhum local encontrado por nome ou endereço"))
        assertTrue(source.contains("Text(\"GPS\")"))
    }
}
