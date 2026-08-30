package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicShortAgendaLink0358Test {
    @Test
    fun officialAgendaUrlContainsOnlyNormalizedPublicSlug() {
        val settings = TripOnlineSettings(
            publicBaseUrl = "https://rota-certa-7ccc8.web.app",
            publicCalendarToken = "12345678901234567890",
            driverUsername = "viagem-certa",
        )
        assertEquals("https://rota-certa-7ccc8.web.app/viagem-certa", settings.publicAgendaUrl)
        assertFalse(settings.publicAgendaUrl.orEmpty().contains("agenda="))
        assertFalse(settings.publicAgendaUrl.orEmpty().contains("motorista="))
    }

    @Test
    fun normalizationAndReservedRoutesFollowPublicContract() {
        assertEquals("ezequiel-viagens", DriverIdentityRules.normalizeUsername("Ézequiel Viagens"))
        assertEquals("motorista123", DriverIdentityRules.normalizeUsername("Motorista123"))
        assertTrue(DriverIdentityRules.isValidPublicUsername("viagem-certa"))
        assertFalse(DriverIdentityRules.isValidPublicUsername("admin"))
        assertFalse(DriverIdentityRules.isValidPublicUsername("calendar"))
        assertFalse(DriverIdentityRules.isValidPublicUsername("v1"))
    }

    @Test
    fun settingsUiMakesShortAddressAndCredentialRotationUnambiguous() {
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaSettingsUi.kt").readText()
        assertTrue(ui.contains("Este campo controla o endereço curto depois da barra"))
        assertTrue(ui.contains("Seu endereço:"))
        assertTrue(ui.contains("Compartilhar link"))
        assertTrue(ui.contains("Trocar credencial interna"))
        assertTrue(ui.contains("O endereço curto continuará o mesmo"))
        assertTrue(ui.contains("Endereços anteriores permanecem como aliases"))
    }
}
