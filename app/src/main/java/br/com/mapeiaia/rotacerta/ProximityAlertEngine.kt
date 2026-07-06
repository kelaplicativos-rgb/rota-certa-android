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
        val runtimeCountBeforePrune = runtimeById.size
        runtimeById.keys.retainAll(activeIds)
        val removedRuntimeCount = runtimeCountBeforePrune - runtimeById.size
        trace(
            now = now,
            message = "check.start alerts=${alerts.size} radars=${radars.size} removed_runtime=$removedRuntimeCount alerts_enabled=${settings.proximityAlertsEnabled}",
        )
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
                trace(
                    now = now,
                    message = "saved_alert.near id=${alert.id} distance=${distanceMeters.roundToInt()}m threshold=${threshold}m spoken=${runtime.spokenCount}/$MAX_SAVED_PLACE_SPEECH_COUNT",
                )
                if (runtime.canSpeak(now, MAX_SAVED_PLACE_SPEECH_COUNT)) {
                    trace(now = now, message = "saved_alert.speak.attempt id=${alert.id}")
                    if (speechEngine.speakProximityAlert(alert)) {
                        runtime.recordSpoken(now)
                        trace(now = now, message = "saved_alert.speak.success id=${alert.id} spoken=${runtime.spokenCount}")
                        onDiagnostic(
                            ProximityAlertDiagnostic(
                                stage = "proximity_alert_spoken",
                                reason = diagnosticReason(
                                    "Alerta de proximidade falado: ${speechEngine.proximityAlertSpeech(alert)} a ${distanceMeters.roundToInt()} metros.",
                                ),
                            ),
                        )
                    } else {
                        trace(now = now, message = "saved_alert.speak.failed id=${alert.id} counter_not_consumed=true")
                    }
                } else {
                    trace(now = now, message = "saved_alert.speak.skipped id=${alert.id} reason=limit_or_repeat_gap")
                }
            } else if (distanceMeters > threshold + RESET_BUFFER_METERS) {
                if (runtime.spokenCount > 0 || runtime.lastSpokenAtMillis > 0L) {
                    trace(now = now, message = "saved_alert.reset id=${alert.id} distance=${distanceMeters.roundToInt()}m")
                }
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
        if (radars.isEmpty()) {
            trace(now = now, message = "imported_radar.scan skipped count=0")
            return
        }
        val threshold = settings.proximityAlertDistanceMeters.coerceIn(200, 1000)
        trace(now = now, message = "imported_radar.scan count=${radars.size} threshold=${threshold}m")
        var nearestRadar: ImportedRadar? = null
        var nearestDistanceMeters = Double.MAX_VALUE
        radars.forEach { radar ->
            val distanceMeters = GeoDistance.meters(coordinate, radar.coordinate)
            val key = importedRadarKey(radar)
            if (distanceMeters > threshold + RESET_BUFFER_METERS) {
                val runtime = runtimeById[key]
                if (runtime != null && (runtime.spokenCount > 0 || runtime.lastSpokenAtMillis > 0L)) {
                    trace(now = now, message = "imported_radar.reset id=${radar.id} distance=${distanceMeters.roundToInt()}m")
                    runtime.reset()
                }
            }
            if (distanceMeters <= threshold && distanceMeters < nearestDistanceMeters) {
                nearestRadar = radar
                nearestDistanceMeters = distanceMeters
            }
        }
        val radar = nearestRadar
        if (radar == null) {
            trace(now = now, message = "imported_radar.nearest none_within_threshold=true")
            return
        }
        val distanceMeters = nearestDistanceMeters
        val runtime = runtimeById.getOrPut(importedRadarKey(radar)) { ProximityAlertRuntime() }
        trace(
            now = now,
            message = "imported_radar.nearest id=${radar.id} distance=${distanceMeters.roundToInt()}m spoken=${runtime.spokenCount}/$MAX_IMPORTED_RADAR_SPEECH_COUNT",
        )
        if (runtime.canSpeak(now, MAX_IMPORTED_RADAR_SPEECH_COUNT)) {
            trace(now = now, message = "imported_radar.speak.attempt id=${radar.id}")
            if (speechEngine.speakImportedRadar(radar, distanceMeters)) {
                runtime.recordSpoken(now)
                trace(now = now, message = "imported_radar.speak.success id=${radar.id} spoken=${runtime.spokenCount}")
                onDiagnostic(
                    ProximityAlertDiagnostic(
                        stage = "imported_radar_spoken",
                        reason = diagnosticReason("Radar importado falado: ${importedRadarSpeech(radar, distanceMeters)}"),
                    ),
                )
            } else {
                trace(now = now, message = "imported_radar.speak.failed id=${radar.id} counter_not_consumed=true")
            }
        } else {
            trace(now = now, message = "imported_radar.speak.skipped id=${radar.id} reason=limit_or_repeat_gap")
        }
    }

    private fun trace(now: Long, message: String) {
        DiagnosticLogStore.record(source = "proximity", message = message, nowMillis = now)
    }

    private fun diagnosticReason(reason: String): String {
        val log = DiagnosticLogStore.dump(maxEvents = DIAGNOSTIC_EXPORT_EVENT_LIMIT)
        if (log.isBlank()) return reason
        return buildString {
            appendLine(reason)
            appendLine("--- LOG GLOBAL ---")
            append(log)
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
        const val DIAGNOSTIC_EXPORT_EVENT_LIMIT = 80
    }
}

data class ProximityAlertDiagnostic(
    val stage: String,
    val reason: String,
)
