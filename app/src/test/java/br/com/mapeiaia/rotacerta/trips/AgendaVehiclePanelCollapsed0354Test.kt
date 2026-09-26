package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaVehiclePanelCollapsed0354Test {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun vehicleIsNoLongerAnOperationalIntegrationCategory() {
        val settings = source("br/com/mapeiaia/rotacerta/trips/PublicAgendaSettingsUi.kt")
        val integration = settings.substringAfter("internal fun OnlineSettingsEditor(").substringBefore("@Composable\ninternal fun AgendaAppSettingsScreen0416")
        assertFalse(integration.contains("🚗 Veículo"))
        assertFalse(integration.contains("TripDriverDefaultsCard("))
        assertFalse(integration.contains("PublicProfileTextField(\"Marca/modelo\""))
        assertTrue(settings.contains("internal fun AgendaAppSettingsScreen0416("))
        assertTrue(settings.contains("Text(\"Dados do veículo\""))
        assertTrue(settings.contains("Text(\"Marca/modelo\")"))
        assertTrue(settings.contains("Text(\"Cor\")"))
    }

    @Test
    fun gpsReferenceIsSeparatedFromVehicleAndManualSeats() {
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        assertTrue(timeline.contains("internal fun TripReferenceOriginSettingsCard0416("))
        assertTrue(timeline.contains("TripReferenceOriginStore(context)"))
        assertTrue(timeline.contains("Origem operacional de referência"))
        val gps = timeline.substringAfter("internal fun TripReferenceOriginSettingsCard0416(")
            .substringBefore("/** Compatibility entry point")
        assertFalse(gps.contains("rotaCertaSeatAllocation"))
        assertFalse(gps.contains("Vagas disponibilizadas no Rota Certa"))
    }

    @Test
    fun extraSeatsAreTripScopedAndUseCanonicalMutationPipeline() {
        val activity = source("br/com/mapeiaia/rotacerta/trips/TripsActivity.kt")
        val seats = activity.substringAfter("private fun TripExtraSeatsScreen0416(")
            .substringBefore("@Composable\nprivate fun AgendaPublicSearchRoot0396")
        assertTrue(seats.contains("trip.rotaCertaSeatAllocation"))
        assertTrue(seats.contains("store.saveTrip("))
        assertTrue(seats.contains("canonicalTripId = saved.id"))
        assertTrue(seats.contains("recordExternalManualMutation("))
        assertTrue(seats.contains("configuredRotaCertaSeatAllocation = parsed"))
        assertTrue(seats.contains("AgendaBackgroundSync0392.enqueueImmediate(activity, \"trip_mutation\")"))
        assertFalse(seats.contains("settings.copy(rotaCertaSeatAllocation"))
    }
}
