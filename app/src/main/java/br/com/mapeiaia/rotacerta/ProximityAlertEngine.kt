package br.com.mapeiaia.rotacerta

import kotlin.math.roundToInt

class ProximityAlertEngine(
    private val speechEngine: ProximitySpeech,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val runtimeById = mutableMapOf<String, ProximityAlertRuntime>()

    fun check(
        alerts: List<SavedPlace>,
        radars: List<ImportedRadar>,
        coordinate: Coordinate,
        settings: AppSettings,
        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,
    ) {
        val now = nowProvider()
        val activeIds = alerts.map { it.id }.toSet() + radars.map { importedRadarKey(it) }.toSet()
        runtimeById.keys.retainAll(activeIds)
        checkImportedRadars(radars, coordinate, settings, now, onDiagnostic)
        checkSavedPlaceAlerts(alerts, coordinate, settings, now, onDiagnostic)
    }

    private fun checkSavedPlaceAlerts(
        alerts: List<SavedPlace>,
        coordinate: Coordinate,
        settings: AppSettings,
        now: Long,
        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,
    ) {
        alerts.forEach { alert ->
            val threshold = (alert.alertDistanceMeters ?: settings.proximityAlertDistanceMeters).coerceIn(200, 1000)
            val distanceMeters = GeoDistance.meters(coordinate, alert.coordinate)
            val runtime = runtimeById.getOrPut(alert.id) { ProximityAlertRuntime() }
            if (distanceMeters <= threshold) {
                if (runtime.canSpeak(now, MAX_SAVED_PLACE_SPEECH_COUNT)) {
                    if (speechEngine.speakProximityAlert(alert)) {
                        runtime.recordSpoken(now)
                        onDiagnostic(
                            ProximityAlertDiagnostic(
                                stage = "proximity_alert_spoken",
                                reason = "Alerta de proximidade falado: ${speechEngine.proximityAlertSpeech(alert)} a ${distanceMeters.roundToInt()} metros.",
                            ),
                        )
                    }
                }
            } else if (distanceMeters > threshold + RESET_BUFFER_METERS) {
                runtime.reset()
            }
        }
    }

    private fun checkImportedRadars(
        radars: List<ImportedRadar>,
        coordinate: Coordinate,
        settings: AppSettings,
        now: Long,
        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,
    ) {
        if (radars.isEmpty()) return
        val threshold = settings.proximityAlertDistanceMeters.coerceIn(200, 1000)
        var nearestRadar: ImportedRadar? = null
        var nearestDistanceMeters = Double.MAX_VALUE
        radars.forEach { radar ->
            val distanceMeters = GeoDistance.meters(coordinate, radar.coordinate)
            val key = importedRadarKey(radar)
            if (distanceMeters > threshold + RESET_BUFFER_METERS) {
                runtimeById[key]?.reset()
            }
            if (distanceMeters <= threshold && distanceMeters < nearestDistanceMeters) {
                nearestRadar = radar
                nearestDistanceMeters = distanceMeters
            }
        }
        val radar = nearestRadar ?: return
        val distanceMeters = nearestDistanceMeters
        val runtime = runtimeById.getOrPut(importedRadarKey(radar)) { ProximityAlertRuntime() }
        if (runtime.canSpeak(now, MAX_IMPORTED_RADAR_SPEECH_COUNT)) {
            if (speechEngine.speakImportedRadar(radar, distanceMeters)) {
                runtime.recordSpoken(now)
                onDiagnostic(
                    ProximityAlertDiagnostic(
                        stage = "imported_radar_spoken",
                        reason = "Radar importado falado: ${importedRadarSpeech(radar, distanceMeters)}",
                    ),
                )
            }
        }
    }

    private fun importedRadarKey(radar: ImportedRadar): String = "imported-${radar.id}"

    private data class ProximityAlertRuntime(
        var spokenCount: Int = 0,
        var lastSpokenAtMillis: Long = 0L,
    ) {
        fun canSpeak(now: Long, maxSpeechCount: Int): Boolean =
            spokenCount < maxSpeechCount && now - lastSpokenAtMillis >= REPEAT_GAP_MS

        fun recordSpoken(now: Long) {
            spokenCount += 1
            lastSpokenAtMillis = now
        }

        fun reset() {
            spokenCount = 0
            lastSpokenAtMillis = 0L
        }
    }

    private companion object {
        const val REPEAT_GAP_MS = 20_000L
        const val RESET_BUFFER_METERS = 100
        const val MAX_SAVED_PLACE_SPEECH_COUNT = 2
        const val MAX_IMPORTED_RADAR_SPEECH_COUNT = 1
    }
}

data class ProximityAlertDiagnostic(
    val stage: String,
    val reason: String,
)
