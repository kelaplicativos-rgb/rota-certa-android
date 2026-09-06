package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyTargetPolicyTest {
    @Test
    fun `aceita somente o mesmo aplicativo que abriu respostas`() {
        assertTrue(
            QuickReplyTargetPolicy.canFill(
                currentPackageName = "com.whatsapp",
                expectedPackageName = "com.whatsapp",
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
        assertFalse(
            QuickReplyTargetPolicy.canFill(
                currentPackageName = "com.instagram.android",
                expectedPackageName = "com.whatsapp",
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }

    @Test
    fun `nunca preenche uma caixa do proprio Rota Certa`() {
        assertFalse(
            QuickReplyTargetPolicy.canFill(
                currentPackageName = "br.com.mapeiaia.rotacerta",
                expectedPackageName = null,
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }

    @Test
    fun `normaliza caixa e espacos do pacote`() {
        assertTrue(
            QuickReplyTargetPolicy.canFill(
                currentPackageName = " COM.WHATSAPP ",
                expectedPackageName = "com.whatsapp",
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }

    @Test
    fun `pacote vazio nunca recebe resposta`() {
        assertFalse(
            QuickReplyTargetPolicy.canFill(
                currentPackageName = " ",
                expectedPackageName = null,
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }
}
