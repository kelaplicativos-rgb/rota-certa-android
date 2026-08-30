package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSettingsVehicleUsername0351Test {
    private val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaSettingsUi.kt").readText()
    private val remote = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()

    @Test
    fun vehicleBlockStartsClosedAndOnlyTogglesPresentationState() {
        assertTrue(ui.contains("var vehicleExpanded by remember { mutableStateOf(false) }"))
        assertTrue(ui.contains("onClick = { vehicleExpanded = !vehicleExpanded }"))
        assertTrue(ui.contains("🚗 Veículo"))
        assertTrue(ui.contains("if (vehicleExpanded)"))
        assertTrue(ui.contains("PublicProfileTextField(\"Marca/modelo\", PublicDriverProfileFields.VEHICLE"))
        assertTrue(ui.contains("PublicProfileTextField(\"Cor\", PublicDriverProfileFields.VEHICLE_COLOR"))
        assertTrue(ui.contains("PublicProfileTextField(\"Comodidades\", PublicDriverProfileFields.AMENITIES"))
        assertTrue(ui.contains("vehicleMakeModel = vehicleMakeModel.trim()"))
        assertTrue(ui.contains("vehicleColor = vehicleColor.trim()"))
        assertTrue(ui.contains("vehicleAmenities = vehicleAmenities.trim()"))
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
