package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoogleMapsSingleSource138Test {
    @Test fun `chave do build tem prioridade e interface nao pede chave duas vezes`() {
        assertEquals("build-key", GoogleMapsApiKeyPolicy.effective("legacy-key", "build-key"))

        val source = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        assertFalse(source.contains("label = { Text(\"Chave Google Maps API\") }"))
        assertFalse(source.contains("onGoogleMapsApiKeyChange"))
    }
}
