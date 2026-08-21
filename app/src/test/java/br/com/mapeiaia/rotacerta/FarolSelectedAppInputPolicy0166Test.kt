package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolSelectedAppInputPolicy0166Test {
    @Test
    fun `evento de overlay nao troca a janela real do aplicativo selecionado`() {
        assertEquals(
            1759,
            FarolSelectedAppInputPolicy0166.resolveStableWindowId(
                eventPackageName = null,
                rootPackageName = "com.exemplo.motorista",
                selectedPackageName = "com.exemplo.motorista",
                eventWindowId = 1766,
                rootWindowId = 1759,
                lastStableWindowId = 1759,
            ),
        )
    }

    @Test
    fun `qualquer pacote selecionado pode usar OCR pontual`() {
        val customPackage = "com.parceiro.corridas.driver"
        assertTrue(
            FarolSelectedAppInputPolicy0166.shouldAttemptOcr(
                packageName = customPackage,
                selectedPackages = setOf(customPackage),
                strictRootPackageName = customPackage,
                parserAlreadyActive = false,
            ),
        )
    }

    @Test
    fun `pacote nao selecionado nunca autoriza OCR`() {
        assertFalse(
            FarolSelectedAppInputPolicy0166.shouldAttemptOcr(
                packageName = "com.nao.selecionado",
                selectedPackages = setOf("com.outro.selecionado"),
                strictRootPackageName = "com.nao.selecionado",
                parserAlreadyActive = false,
            ),
        )
    }

    @Test
    fun `OCR nao compete com parser que ja encontrou os enderecos`() {
        val selected = "com.exemplo.driver"
        assertFalse(
            FarolSelectedAppInputPolicy0166.shouldAttemptOcr(
                packageName = selected,
                selectedPackages = setOf(selected),
                strictRootPackageName = selected,
                parserAlreadyActive = true,
            ),
        )
    }
}
