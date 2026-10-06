package br.com.mapeiaia.rotacerta.trips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandaloneRemoteAccessActions0739InstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val copyLabel = "🔐 Copiar acesso privado remoto"

    @Test fun missingAccessShowsEnabledCopyActionThatCanProvision() {
        var copies = 0
        compose.setContent { MaterialTheme {
            StandaloneRemoteAccessActions0739(false, "Acesso ainda não criado", { copies++ }, {})
        } }
        compose.onNodeWithText(copyLabel).assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, copies)
    }

    @Test fun provisioningErrorKeepsCopyAndRetryAvailable() {
        var retries = 0
        compose.setContent { MaterialTheme {
            StandaloneRemoteAccessActions0739(false, "Não foi possível conectar ao servidor", {}, { retries++ })
        } }
        compose.onNodeWithText(copyLabel).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Não foi possível conectar ao servidor").assertIsDisplayed()
        compose.onNodeWithText("Verificar conexão remota").performClick()
        assertEquals(1, retries)
    }

    @Test fun inFlightRequestKeepsActionsVisibleAndBlocksDuplicateRequests() {
        compose.setContent { MaterialTheme {
            StandaloneRemoteAccessActions0739(true, "Verificando acesso", {}, {})
        } }
        compose.onNodeWithText(copyLabel).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Verificando conexão…").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun provisionedAccessCanBeCopiedWithoutTriggeringAnotherVerification() {
        var copies = 0
        var checks = 0
        compose.setContent { MaterialTheme {
            StandaloneRemoteAccessActions0739(false, "Acesso privado disponível", { copies++ }, { checks++ })
        } }
        compose.onNodeWithText(copyLabel).performClick()
        assertEquals(1, copies)
        assertEquals(0, checks)
    }
}
