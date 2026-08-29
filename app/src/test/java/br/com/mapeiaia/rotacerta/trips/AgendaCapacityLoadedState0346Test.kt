package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaCapacityLoadedState0346Test {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun capacityUiDoesNotRenderProvisionalDefaultAsEmptyField() {
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        assertTrue(timeline.contains("collectAsState(initial = null)"))
        assertTrue(timeline.contains("val settingsLoaded = appSettingsState != null"))
        assertTrue(timeline.contains("if (settingsLoaded) {"))
        assertTrue(timeline.contains("Carregando configurações do veículo…"))
        assertFalse(timeline.contains("settingsRepository.settings.collectAsState(initial = AppSettings())"))
    }

    @Test
    fun publicAgendaSyncWaitsUntilLocalSettingsHaveActuallyLoaded() {
        val activity = source("br/com/mapeiaia/rotacerta/trips/TripsActivity.kt")
        assertTrue(activity.contains("collectAsState(initial = null)"))
        assertTrue(activity.contains("LaunchedEffect(settingsLoaded, appSettings.vehicleCapacity, publicAgendaSyncRevision)"))
        assertTrue(activity.contains("CAPACITY_PUBLIC_SYNC_DEFERRED"))
        assertTrue(activity.contains("reason=local_settings_not_loaded"))

        val effectStart = activity.indexOf("LaunchedEffect(settingsLoaded, appSettings.vehicleCapacity, publicAgendaSyncRevision)")
        val deferred = activity.indexOf("CAPACITY_PUBLIC_SYNC_DEFERRED", effectStart)
        val onlineRead = activity.indexOf("val online = store.onlineSettings()", effectStart)
        assertTrue(effectStart >= 0)
        assertTrue(deferred > effectStart)
        assertTrue(onlineRead > deferred)
    }

    @Test
    fun realZeroCapacityRemainsAValidLoadedStateInsteadOfBeingConfusedWithLoading() {
        val activity = source("br/com/mapeiaia/rotacerta/trips/TripsActivity.kt")
        val effectStart = activity.indexOf("LaunchedEffect(settingsLoaded, appSettings.vehicleCapacity, publicAgendaSyncRevision)")
        val effectEnd = activity.indexOf("Scaffold(", effectStart)
        val effect = activity.substring(effectStart, effectEnd)

        assertTrue(effect.contains("if (!settingsLoaded)"))
        assertFalse(effect.contains("if (appSettings.vehicleCapacity !in 1..999) return@LaunchedEffect"))
        assertTrue(effect.contains("configuredVehicleCapacity = appSettings.vehicleCapacity"))
    }

    @Test
    fun forensicTraceDistinguishesWaitingLoadedAndUnconfiguredSources() {
        val activity = source("br/com/mapeiaia/rotacerta/trips/TripsActivity.kt")
        val timeline = source("br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        assertTrue(activity.contains("awaiting_local_settings"))
        assertTrue(activity.contains("local_settings_unconfigured"))
        assertTrue(timeline.contains("awaiting_local_settings"))
        assertTrue(timeline.contains("local_settings_unconfigured"))
    }
}
