package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TesterExclusiveLink0378Test {
    @Test
    fun androidAdminUsesExistingRemoteApiAndExposesFullLinkLifecycle() {
        val api = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaSettingsUi.kt").readText()
        assertTrue(api.contains("/v1/driver/test-link"))
        assertTrue(api.contains("generateTesterLink"))
        assertTrue(api.contains("revokeTesterLink"))
        assertTrue(ui.contains("🧪 Link exclusivo de teste"))
        assertTrue(ui.contains("Gerar link de teste"))
        assertTrue(ui.contains("Gerar novo link de teste"))
        assertTrue(ui.contains("Copiar link de teste"))
        assertTrue(ui.contains("Compartilhar link de teste"))
        assertTrue(ui.contains("Revogar link de teste"))
        assertTrue(ui.contains("estado sombra"))
        assertFalse(ui.contains("?bypass=true"))
    }

    @Test
    fun testerSecretIsNotPersistedInAndroidSettings() {
        val store = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripStore.kt").readText()
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaSettingsUi.kt").readText()
        assertFalse(store.contains("testerBootstrapToken"))
        assertFalse(store.contains("testerLinkUrl"))
        assertTrue(ui.contains("Por segurança, o segredo do link não é recuperado do servidor"))
    }
}
