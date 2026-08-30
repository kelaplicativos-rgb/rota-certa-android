package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaVehiclePanelCollapsed0354Test {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun vehicleDefaultsPanelStartsCollapsedAndOnlyRendersBodyAfterUserOpensIt() {
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        assertTrue(timeline.contains("var driverDefaultsExpanded by remember { mutableStateOf(false) }"))
        assertTrue(timeline.contains("Text(if (driverDefaultsExpanded) \"🚗 Dados do veículo ▲\" else \"🚗 Dados do veículo ▼\")"))

        val toggle = timeline.indexOf("Text(if (driverDefaultsExpanded)")
        val gate = timeline.indexOf("if (driverDefaultsExpanded) {", toggle)
        val card = timeline.indexOf("TripDriverDefaultsCard(", gate)
        assertTrue(toggle >= 0)
        assertTrue(gate > toggle)
        assertTrue(card > gate)

        val beforeGate = timeline.substring(toggle, gate)
        assertFalse(beforeGate.contains("TripDriverDefaultsCard("))
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
        assertTrue(section.indexOf("if (settingsLoaded) {") > 0)
    }
}
