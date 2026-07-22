package br.com.mapeiaia.rotacerta

import kotlin.math.roundToInt

class ProximityAlertEngine(
    private val speechEngine: ProximitySpeech,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val runtimeById = mutableMapOf<String, ProximityAlertRuntime>()
    private var lastCoordinate: Coordinate? = null

    fun check(
        alerts: List<SavedPlace>,
        radars: List<ImportedRadar>,
        coordinate: Coordinate,
        settings: AppSettings,
        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,
    ) {
        val now = nowProvider()
        val previousCoordinate = lastCoordinate
        val movementMeters = previousCoordinate?.let { GeoDistance.meters(it, coordinate) }
        val movementBearing = previousCoordinate
            ?.takeIf { movementMeters != null && movementMeters >= MIN_MOVEMENT_FOR_BEARING_METERS }
            ?.let { GeoDistance.bearingDegrees(it, coordinate) }
        val activeIds = alerts.map { it.id }.toSet() + radars.map { importedRadarKey(it) }.toSet()
        val runtimeCountBeforePrune = runtimeById.size
        runtimeById.keys.retainAll(activeIds)
        val removedRuntimeCount = runtimeCountBeforePrune - runtimeById.size
        trace(
            now = now,
            message = "check.start alerts=${alerts.size} radars=${radars.size} removed_runtime=$removedRuntimeCount alerts_enabled=${settings.proximityAlertsEnabled} movement=${movementMeters?.roundToInt() ?: "unknown"}m bearing=${movementBearing?.roundToInt() ?: "unknown"}",
        )
        checkImportedRadars(radars, coordinate, settings, now, movementBearing, onDiagnostic)
        checkSavedPlaceAlerts(alerts, coordinate, settings, now, onDiagnostic)
        lastCoordinate = coordinate
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
        movementBearing: Double?,
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
            val runtime = runtimeById.getOrPut(key) { ProximityAlertRuntime() }
            if (distanceMeters > threshold + RESET_BUFFER_METERS) {
                if (runtime.spokenCount > 0 || runtime.lastSpokenAtMillis > 0L) {
                    trace(now = now, message = "imported_radar.reset id=${radar.id} distance=${distanceMeters.roundToInt()}m")
                    runtime.reset()
                }
            }
            if (distanceMeters <= threshold) {
                val approaching = runtime.isApproaching(distanceMeters)
                val directionMatch = radarDirectionMatches(radar, movementBearing)
                trace(
                    now = now,
                    message = "imported_radar.candidate id=${radar.id} distance=${distanceMeters.roundToInt()}m approaching=$approaching direction_match=$directionMatch radar_direction=${radar.direction ?: "unknown"} direction_type=${radar.directionType ?: "unknown"}",
                )
                if (approaching && directionMatch && distanceMeters < nearestDistanceMeters) {
                    nearestRadar = radar
                    nearestDistanceMeters = distanceMeters
                }
            }
            runtime.lastDistanceMeters = distanceMeters
        }
        val radar = nearestRadar
        if (radar == null) {
            trace(now = now, message = "imported_radar.nearest none_eligible=true")
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

    private fun radarDirectionMatches(radar: ImportedRadar, movementBearing: Double?): Boolean {
        val radarDirection = radar.direction?.toDouble()?.let(GeoDistance::normalizeDegrees) ?: return true
        val directionType = radar.directionType ?: return true
        if (directionType == 0) return true
        if (movementBearing == null) return true
        val primaryDiff = GeoDistance.angleDifferenceDegrees(movementBearing, radarDirection)
        val inverseDiff = GeoDistance.angleDifferenceDegrees(movementBearing, GeoDistance.normalizeDegrees(radarDirection + 180.0))
        return primaryDiff <= RADAR_DIRECTION_TOLERANCE_DEGREES ||
            (directionType >= DOUBLE_DIRECTION_TYPE && inverseDiff <= RADAR_DIRECTION_TOLERANCE_DEGREES)
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
        var lastDistanceMeters: Double? = null,
    ) {
        fun canSpeak(now: Long, maxSpeechCount: Int): Boolean =
            spokenCount < maxSpeechCount && now - lastSpokenAtMillis >= REPEAT_GAP_MS

        fun isApproaching(distanceMeters: Double): Boolean =
            lastDistanceMeters?.let { previous -> distanceMeters <= previous + GPS_DISTANCE_JITTER_METERS } ?: true

        fun recordSpoken(now: Long) {
            spokenCount += 1
            lastSpokenAtMillis = now
        }

        fun reset() {
            spokenCount = 0
            lastSpokenAtMillis = 0L
            lastDistanceMeters = null
        }
    }

    private companion object {
        const val REPEAT_GAP_MS = 20_000L
        const val RESET_BUFFER_METERS = 100
        const val MAX_SAVED_PLACE_SPEECH_COUNT = 2
        const val MAX_IMPORTED_RADAR_SPEECH_COUNT = 1
        const val DIAGNOSTIC_EXPORT_EVENT_LIMIT = 80
        const val MIN_MOVEMENT_FOR_BEARING_METERS = 8.0
        const val GPS_DISTANCE_JITTER_METERS = 8.0
        const val RADAR_DIRECTION_TOLERANCE_DEGREES = 65.0
        const val DOUBLE_DIRECTION_TYPE = 2
    }
}

data class ProximityAlertDiagnostic(
    val stage: String,
    val reason: String,
)
