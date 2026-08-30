package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaVehiclePanelCollapsed0354Test {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun vehicleDefaultsPanelStartsCollapsedAndUsesTheSameAgendaActionPattern() {
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        assertTrue(timeline.contains("var driverDefaultsExpanded by remember { mutableStateOf(false) }"))
        assertTrue(timeline.contains("ResponsiveTripActions("))
        assertTrue(
            timeline.contains(
                "if (driverDefaultsExpanded) \"Fechar dados do veículo\" else \"Dados do veículo\"",
            ),
        )
        assertFalse(timeline.contains("🚗 Dados do veículo ▲"))
        assertFalse(timeline.contains("🚗 Dados do veículo ▼"))

        val toggleLabel = timeline.indexOf("if (driverDefaultsExpanded) \"Fechar dados do veículo\" else \"Dados do veículo\"")
        val toggleAction = timeline.indexOf("{ driverDefaultsExpanded = !driverDefaultsExpanded }", toggleLabel)
        val gate = timeline.indexOf("if (driverDefaultsExpanded) {", toggleAction)
        val card = timeline.indexOf("TripDriverDefaultsCard(", gate)
        assertTrue(toggleLabel >= 0)
        assertTrue(toggleAction > toggleLabel)
        assertTrue(gate > toggleAction)
        assertTrue(card > gate)
    }

    @Test
    fun loadingAndEditableVehicleContentStayInsideTheCollapsedGate() {
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        val gate = timeline.indexOf("if (driverDefaultsExpanded) {")
        val passengers = timeline.indexOf("GlobalPassengerFlowPanel(", gate)
        val section = timeline.substring(gate, passengers)

        assertTrue(section.contains("if (settingsLoaded) {"))
        assertTrue(section.contains("TripDriverDefaultsCard("))
        assertTrue(section.contains("Carregando configurações do veículo…"))
    }
}
