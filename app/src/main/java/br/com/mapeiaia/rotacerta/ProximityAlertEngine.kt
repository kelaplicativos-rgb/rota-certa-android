package br.com.mapeiaia.rotacerta

import java.util.ArrayDeque
import kotlin.math.min
import kotlin.math.roundToInt

class ProximityAlertEngine(
    private val speechEngine: ProximitySpeech,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val runtimeById = mutableMapOf<String, ProximityAlertRuntime>()
    private var lastCoordinate: Coordinate? = null
    private val recentCoordinates = ArrayDeque<Coordinate>()

    fun check(
        alerts: List<SavedPlace>,
        radars: List<ImportedRadar>,
        coordinate: Coordinate,
        settings: AppSettings,
        onSavedPlacePopup: (SavedPlace, Double) -> Unit = { _, _ -> },
        onSavedPlacePopupState: (ProximityAlertPopupState) -> Unit = {},
        onImportedRadarDetected: (ImportedRadar, Double) -> Unit = { _, _ -> },
        onImportedRadarPassed: (String) -> Unit = {},
        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,
    ) {
        val now = nowProvider()
        val previousCoordinate = lastCoordinate
        recentCoordinates.addLast(coordinate)
        while (recentCoordinates.size > RECENT_COORDINATE_LIMIT) recentCoordinates.removeFirst()
        val movementMeters = previousCoordinate?.let { GeoDistance.meters(it, coordinate) }
        val bearingOrigin = recentCoordinates.firstOrNull { origin ->
            GeoDistance.meters(origin, coordinate) >= MIN_MOVEMENT_FOR_BEARING_METERS
        } ?: previousCoordinate
        val movementBearing = bearingOrigin
            ?.takeIf { GeoDistance.meters(it, coordinate) >= MIN_MOVEMENT_FOR_BEARING_METERS }
            ?.let { GeoDistance.bearingDegrees(it, coordinate) }
        val proximityAlerts = alerts.filter { it.type == SavedPlaceType.ProximityAlert }
        val activeIds = proximityAlerts.map { it.id }.toSet() + radars.map { importedRadarKey(it) }.toSet()
        val runtimeCountBeforePrune = runtimeById.size
        runtimeById.keys.retainAll(activeIds)
        val removedRuntimeCount = runtimeCountBeforePrune - runtimeById.size
        trace {
            "check.start alerts=${proximityAlerts.size} ignored_places=${alerts.size - proximityAlerts.size} " +
                "radars=${radars.size} removed_runtime=$removedRuntimeCount alerts_enabled=${settings.proximityAlertsEnabled} " +
                "movement=${movementMeters?.roundToInt() ?: "unknown"}m bearing=${movementBearing?.roundToInt() ?: "unknown"}"
        }
        checkImportedRadars(radars, coordinate, settings, now, movementBearing, onImportedRadarDetected, onImportedRadarPassed, onDiagnostic)
        checkSavedPlaceAlerts(proximityAlerts, coordinate, settings, now, onSavedPlacePopup, onSavedPlacePopupState, onDiagnostic)
        lastCoordinate = coordinate
    }

    private fun checkSavedPlaceAlerts(
        alerts: List<SavedPlace>,
        coordinate: Coordinate,
        settings: AppSettings,
        now: Long,
        onSavedPlacePopup: (SavedPlace, Double) -> Unit,
        onPopupState: (ProximityAlertPopupState) -> Unit,
        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,
    ) {
        val configuredThreshold = settings.proximityAlertDistanceMeters.coerceIn(MIN_ALERT_DISTANCE_METERS, MAX_ALERT_DISTANCE_METERS)
        alerts.forEach { alert ->
            val threshold = configuredThreshold
            val distanceMeters = GeoDistance.meters(coordinate, alert.coordinate)
            val runtime = runtimeById.getOrPut(alert.id) { ProximityAlertRuntime() }
            if (distanceMeters <= threshold) {
                if (!runtime.savedPlaceZoneInitialized) {
                    runtime.savedPlaceZoneInitialized = true
                    runtime.savedPlaceMutedUntilExit = true
                    runtime.savedPlaceInsideZone = true
                    runtime.lastDistanceMeters = distanceMeters
                    runtime.entryDistanceMeters = distanceMeters
                    runtime.minimumDistanceMeters = distanceMeters
                    trace { "saved_alert.speak.skipped id=${alert.id} reason=initial_inside_waiting_exit" }
                    return@forEach
                }
                runtime.savedPlaceInsideZone = true
                runtime.observeDistance(distanceMeters)
                if (runtime.savedPlaceMutedUntilExit) {
                    trace { "saved_alert.speak.skipped id=${alert.id} reason=waiting_exit_before_first_alert" }
                    return@forEach
                }

                if (!runtime.popupClosedAfterPass) {
                    if (!runtime.popupShownThisApproach) {
                        runtime.popupShownThisApproach = true
                        onSavedPlacePopup(alert, distanceMeters)
                        trace { "saved_alert.popup.shown id=${alert.id}" }
                    }
                    onPopupState(
                        ProximityAlertPopupState.Visible(
                            alert = alert,
                            distanceMeters = distanceMeters,
                            firstAlertDistanceMeters = threshold,
                            progress = (1.0 - distanceMeters / threshold.toDouble()).coerceIn(0.0, 1.0),
                        ),
                    )
                    if (settings.proximityPopupAutoCloseEnabled && runtime.hasPassedPoint()) {
                        runtime.popupClosedAfterPass = true
                        runtime.popupShownThisApproach = false
                        onPopupState(ProximityAlertPopupState.Hidden(alert.id, "ponto_ultrapassado"))
                        trace { "saved_alert.popup.auto_closed id=${alert.id} min=${runtime.minimumDistanceMeters?.roundToInt()}m current=${distanceMeters.roundToInt()}m" }
                    }
                }

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
                if (runtime.popupShownThisApproach || runtime.popupClosedAfterPass) {
                    onPopupState(ProximityAlertPopupState.Hidden(alert.id, "fora_da_zona_de_rearme"))
                }
                runtime.resetSavedPlaceAfterExit(distanceMeters)
            } else {
                runtime.savedPlaceZoneInitialized = true
                runtime.savedPlaceInsideZone = false
                runtime.lastDistanceMeters = distanceMeters
            }
        }
    }

    private fun checkImportedRadars(
        radars: List<ImportedRadar>,
        coordinate: Coordinate,
        settings: AppSettings,
        now: Long,
        movementBearing: Double?,
        onImportedRadarDetected: (ImportedRadar, Double) -> Unit,
        onImportedRadarPassed: (String) -> Unit,
        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,
    ) {
        if (radars.isEmpty()) return
        val threshold = settings.proximityAlertDistanceMeters.coerceIn(MIN_ALERT_DISTANCE_METERS, MAX_ALERT_DISTANCE_METERS)
        var nearestRadar: ImportedRadar? = null
        var nearestDistanceMeters = Double.MAX_VALUE
        radars.forEach { radar ->
            val distanceMeters = GeoDistance.meters(coordinate, radar.coordinate)
            val key = importedRadarKey(radar)
            val runtime = runtimeById.getOrPut(key) { ProximityAlertRuntime() }
            if (distanceMeters > threshold + RESET_BUFFER_METERS) {
                if (runtime.popupShownThisApproach || runtime.popupClosedAfterPass) onImportedRadarPassed(radar.id)
                runtime.reset()
            }
            if (distanceMeters <= threshold) {
                val approaching = runtime.isApproaching(distanceMeters)
                runtime.observeDistance(distanceMeters)
                val directionMatch = radarDirectionMatches(radar, movementBearing)
                trace { "imported_radar.candidate id=${radar.id} distance=${distanceMeters.roundToInt()}m approaching=$approaching direction_match=$directionMatch" }
                if (approaching && directionMatch && distanceMeters < nearestDistanceMeters) {
                    nearestRadar = radar
                    nearestDistanceMeters = distanceMeters
                }
                if (settings.proximityPopupAutoCloseEnabled && runtime.popupShownThisApproach && !runtime.popupClosedAfterPass && runtime.hasPassedPoint()) {
                    runtime.popupClosedAfterPass = true
                    runtime.popupShownThisApproach = false
                    onImportedRadarPassed(radar.id)
                }
            }
        }
        val radar = nearestRadar ?: return
        val distanceMeters = nearestDistanceMeters
        val runtime = runtimeById.getOrPut(importedRadarKey(radar)) { ProximityAlertRuntime() }
        if (!runtime.popupClosedAfterPass && !runtime.popupShownThisApproach) {
            runtime.popupShownThisApproach = true
            onImportedRadarDetected(radar, distanceMeters)
        }
        if (runtime.canSpeak(now, MAX_IMPORTED_RADAR_SPEECH_COUNT)) {
            trace { "imported_radar.speak.attempt id=${radar.id}" }
            if (speechEngine.speakImportedRadar(radar, distanceMeters)) {
                runtime.recordSpoken(now)
                trace { "imported_radar.speak.success id=${radar.id} spoken=${runtime.spokenCount}" }
                onDiagnostic(
                    ProximityAlertDiagnostic(
                        stage = "imported_radar_spoken",
                        reason = diagnosticReason("Radar importado falado: ${importedRadarSpeech(radar, distanceMeters)}"),
                    ),
                )
            } else {
                trace { "imported_radar.speak.failed id=${radar.id} counter_not_consumed=true" }
            }
        }
    }

    private fun radarDirectionMatches(radar: ImportedRadar, movementBearing: Double?): Boolean {
        val radarDirection = radar.direction?.toDouble()?.let(GeoDistance::normalizeDegrees) ?: return true
        val directionType = radar.directionType ?: return true
        if (directionType == 0 || movementBearing == null) return true
        val primaryDiff = GeoDistance.angleDifferenceDegrees(movementBearing, radarDirection)
        val inverseDiff = GeoDistance.angleDifferenceDegrees(movementBearing, GeoDistance.normalizeDegrees(radarDirection + 180.0))
        return primaryDiff <= RADAR_DIRECTION_TOLERANCE_DEGREES ||
            (directionType >= DOUBLE_DIRECTION_TYPE && inverseDiff <= RADAR_DIRECTION_TOLERANCE_DEGREES)
    }

    private inline fun trace(message: () -> String) {
        if (DiagnosticRuntimeGate.isEnabled()) DiagnosticLogStore.record(source = "proximity", message = message())
    }

    private fun diagnosticReason(reason: String): String {
        if (!DiagnosticRuntimeGate.isEnabled()) return reason
        val log = DiagnosticLogStore.dump(maxEvents = 80)
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
        var entryDistanceMeters: Double? = null,
        var minimumDistanceMeters: Double? = null,
        var increasingReadings: Int = 0,
        var savedPlaceZoneInitialized: Boolean = false,
        var savedPlaceMutedUntilExit: Boolean = false,
        var savedPlaceInsideZone: Boolean = false,
        var popupShownThisApproach: Boolean = false,
        var popupClosedAfterPass: Boolean = false,
    ) {
        fun canSpeak(now: Long, maxSpeechCount: Int): Boolean =
            spokenCount < maxSpeechCount && now - lastSpokenAtMillis >= REPEAT_GAP_MS

        fun isApproaching(distanceMeters: Double): Boolean =
            lastDistanceMeters?.let { previous -> distanceMeters <= previous + GPS_DISTANCE_JITTER_METERS } ?: true

        fun observeDistance(distanceMeters: Double) {
            val previous = lastDistanceMeters
            if (entryDistanceMeters == null) entryDistanceMeters = distanceMeters
            minimumDistanceMeters = min(minimumDistanceMeters ?: distanceMeters, distanceMeters)
            increasingReadings = if (previous != null && distanceMeters > previous + GPS_DISTANCE_JITTER_METERS) {
                increasingReadings + 1
            } else if (previous != null && distanceMeters < previous - GPS_DISTANCE_JITTER_METERS) {
                0
            } else {
                increasingReadings
            }
            lastDistanceMeters = distanceMeters
        }

        fun hasPassedPoint(): Boolean {
            val entry = entryDistanceMeters ?: return false
            val minimum = minimumDistanceMeters ?: return false
            val current = lastDistanceMeters ?: return false
            val meaningfulApproach = entry - minimum >= MIN_APPROACH_DELTA_METERS
            val movingAway = current - minimum >= PASS_DISTANCE_DELTA_METERS
            return meaningfulApproach && movingAway && increasingReadings >= REQUIRED_INCREASING_READINGS
        }

        fun recordSpoken(now: Long) {
            spokenCount += 1
            lastSpokenAtMillis = now
        }

        fun resetSavedPlaceAfterExit(distanceMeters: Double) {
            reset()
            savedPlaceZoneInitialized = true
            savedPlaceMutedUntilExit = false
            savedPlaceInsideZone = false
            lastDistanceMeters = distanceMeters
        }

        fun reset() {
            spokenCount = 0
            lastSpokenAtMillis = 0L
            lastDistanceMeters = null
            entryDistanceMeters = null
            minimumDistanceMeters = null
            increasingReadings = 0
            savedPlaceZoneInitialized = false
            savedPlaceMutedUntilExit = false
            savedPlaceInsideZone = false
            popupShownThisApproach = false
            popupClosedAfterPass = false
        }
    }

    private companion object {
        const val REPEAT_GAP_MS = 20_000L
        const val RESET_BUFFER_METERS = 100
        const val MAX_SAVED_PLACE_SPEECH_COUNT = 2
        const val MAX_IMPORTED_RADAR_SPEECH_COUNT = 1
        const val MIN_MOVEMENT_FOR_BEARING_METERS = 8.0
        const val RECENT_COORDINATE_LIMIT = 6
        const val GPS_DISTANCE_JITTER_METERS = 5.0
        const val MIN_APPROACH_DELTA_METERS = 12.0
        const val PASS_DISTANCE_DELTA_METERS = 12.0
        const val REQUIRED_INCREASING_READINGS = 2
        const val RADAR_DIRECTION_TOLERANCE_DEGREES = 65.0
        const val DOUBLE_DIRECTION_TYPE = 2
        const val MIN_ALERT_DISTANCE_METERS = 100
        const val MAX_ALERT_DISTANCE_METERS = 2_000
    }
}

sealed class ProximityAlertPopupState {
    data class Visible(
        val alert: SavedPlace,
        val distanceMeters: Double,
        val firstAlertDistanceMeters: Int,
        val progress: Double,
    ) : ProximityAlertPopupState()

    data class Hidden(val alertId: String, val reason: String) : ProximityAlertPopupState()
}

data class ProximityAlertDiagnostic(
    val stage: String,
    val reason: String,
)
