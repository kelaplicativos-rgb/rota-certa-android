package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSettingsVehicleUsername0351Test {
    private val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaSettingsUi.kt").readText()
    private val remote = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()

    @Test
    fun vehicleDataLivesInAppSettingsAndIntegrationPreservesExistingValues() {
        val integration = ui.substringAfter("internal fun OnlineSettingsEditor(")
            .substringBefore("@Composable\ninternal fun AgendaAppSettingsScreen0416")
        val appSettings = ui.substringAfter("internal fun AgendaAppSettingsScreen0416(")
        assertFalse(integration.contains("🚗 Veículo"))
        assertFalse(integration.contains("PublicProfileTextField(\"Marca/modelo\""))
        assertTrue(integration.contains("vehicleMakeModel = vehicleMakeModel.trim()"))
        assertTrue(integration.contains("vehicleColor = vehicleColor.trim()"))
        assertTrue(integration.contains("vehicleAmenities = vehicleAmenities.trim()"))
        assertTrue(appSettings.contains("Text(\"Dados do veículo\""))
        assertTrue(appSettings.contains("label = { Text(\"Marca/modelo\") }"))
        assertTrue(appSettings.contains("label = { Text(\"Cor\") }"))
        assertTrue(appSettings.contains("PublicDriverProfileFields.VEHICLE"))
    }

    @Test
    fun usernameChangeUsesDedicatedServerOperationAndKeepsTokenSeparate() {
        val usernameFieldStart = ui.indexOf("label = { Text(\"Nome de usuário no link\") }")
        val usernameFieldEnd = ui.indexOf("HorizontalDivider()", usernameFieldStart)
        val usernameField = ui.substring(usernameFieldStart, usernameFieldEnd)
        assertTrue(usernameField.contains("enabled = !linkRotationInFlight && !usernameChangeInFlight"))
        assertFalse(usernameField.contains("enabled = token.isBlank()"))

        assertTrue(ui.contains("TripRemoteApi(authSettings).changeDriverUsername("))
        assertTrue(ui.contains("currentPublicAgendaToken = candidate.publicCalendarToken"))
        assertTrue(ui.contains("onSave(candidate.copy(driverUsername = response.username))"))
        assertTrue(remote.contains("path = \"/v1/driver/username\""))
        assertTrue(remote.contains("currentPublicAgendaToken = currentPublicAgendaToken.trim()"))
        assertTrue(remote.contains("requestId = requestId.trim()"))
    }
}
