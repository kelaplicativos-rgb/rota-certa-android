package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaVehiclePanelCollapsed0354Test {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun vehicleEntryIsRemovedFromTimelineAdministration() {
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        val header = timeline.indexOf("Text(if (showArchived)")
        val passengers = timeline.indexOf("GlobalPassengerFlowPanel(", header)
        val topAdministration = timeline.substring(header, passengers)

        assertFalse(topAdministration.contains("driverDefaultsExpanded"))
        assertFalse(topAdministration.contains("\"Dados do veículo\""))
        assertFalse(topAdministration.contains("\"Fechar dados do veículo\""))
        assertFalse(topAdministration.contains("TripDriverDefaultsCard("))
    }

    @Test
    fun integrationVehicleExpanderReusesCanonicalLocalVehicleSettings() {
        val settings = source("br/com/mapeiaia/rotacerta/trips/PublicAgendaSettingsUi.kt")
        val vehicleToggle = settings.indexOf("if (vehicleExpanded) \"🚗 Veículo ▲\" else \"🚗 Veículo ▼\"")
        val vehicleGate = settings.indexOf("if (vehicleExpanded) {", vehicleToggle)
        val nextSection = settings.indexOf("PublicProfileTextField(\"Preferências\"", vehicleGate)
        val vehicleSection = settings.substring(vehicleGate, nextSection)

        assertTrue(vehicleToggle >= 0)
        assertTrue(vehicleGate > vehicleToggle)
        assertTrue(vehicleSection.contains("TripDriverDefaultsCard("))
        assertTrue(vehicleSection.contains("vehicleSettingsRepository"))
        assertTrue(vehicleSection.contains("vehicleReferenceOrigin"))
        assertTrue(vehicleSection.contains("PublicProfileTextField(\"Marca/modelo\""))
        assertTrue(vehicleSection.contains("PublicProfileTextField(\"Cor\""))
        assertTrue(vehicleSection.contains("PublicProfileTextField(\"Comodidades\""))
    }

    @Test
    fun canonicalVehicleEditorStillUsesOriginalSettingsRepositoryAndStorageContract() {
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        assertTrue(timeline.contains("internal fun TripDriverDefaultsCard("))
        assertTrue(timeline.contains("repository.saveSettings(settings.copy(vehicleCapacity = parsed))"))
        assertTrue(timeline.contains("referenceStore.save(origin)"))
    }
}
