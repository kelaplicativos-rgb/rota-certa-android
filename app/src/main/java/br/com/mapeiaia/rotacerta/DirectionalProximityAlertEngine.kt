package br.com.mapeiaia.rotacerta

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Motor de alertas independente da bolinha de decisão das corridas.
 *
 * Considera um alvo quando o GPS é recente e preciso e a distância confirma
 * aproximação. O aviso não depende de heading, azimute nem da direção cadastrada
 * no radar; a tendência de distância é a autoridade para aproximação e passagem.
 */
class DirectionalProximityAlertEngine(
    private val speechEngine: ProximitySpeech,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val runtimeById = mutableMapOf<String, RuntimeState>()
    private val dismissGate0178 = ApproachDismissGate0178()
    private var lastVisual: DirectionalAlertVisual? = null
    private var lastVisualAtMillis: Long = 0L

    fun check(
        alerts: List<SavedPlace>,
        radars: List<ImportedRadar>,
        fix: PreciseNavigationFix,
        settings: AppSettings,
        onVisual: (DirectionalAlertVisual?) -> Unit,
        onDiagnostic: (ProximityAlertDiagnostic) -> Unit = {},
    ) {
        val now = nowProvider()
        val proximityAlerts = alerts.filter { it.type == SavedPlaceType.ProximityAlert }
        val activeIds = proximityAlerts.map { savedKey(it) }.toSet() + radars.map { radarKey(it) }.toSet()
        runtimeById.keys.retainAll(activeIds)
        dismissGate0178.retainActive(activeIds)

        if (!settings.appEnabled || !settings.proximityAlertsEnabled) {
            clearVisual(onVisual)
            return
        }

        val thresholds = buildList {
            proximityAlerts.forEach { alert ->
                add((alert.alertDistanceMeters ?: settings.proximityAlertDistanceMeters).coerceIn(200, 1000))
            }
            if (radars.isNotEmpty()) add(settings.proximityAlertDistanceMeters.coerceIn(200, 1000))
        }
        val fixUsableForAnyTarget = thresholds.any { threshold ->
            DirectionalAlertPolicy.isFixUsable(fix, threshold, now)
        }

        var passedVisual: DirectionalAlertVisual? = null
        val candidates = mutableListOf<Candidate>()

        proximityAlerts.forEach { alert ->
            val threshold = (alert.alertDistanceMeters ?: settings.proximityAlertDistanceMeters).coerceIn(200, 1000)
            evaluateSavedAlert(alert, threshold, fix, now)?.let { evaluation ->
                if (evaluation.passedVisual != null) passedVisual = evaluation.passedVisual
                evaluation.candidate?.let(candidates::add)
            }
        }

        radars.forEach { radar ->
            val threshold = settings.proximityAlertDistanceMeters.coerceIn(200, 1000)
            evaluateRadar(radar, threshold, fix, now)?.let { evaluation ->
                if (evaluation.passedVisual != null) passedVisual = evaluation.passedVisual
                evaluation.candidate?.let(candidates::add)
            }
        }

        if (passedVisual != null) {
            lastVisual = null
            lastVisualAtMillis = 0L
            onVisual(passedVisual)
            return
        }

        val selected = candidates.minByOrNull { it.distanceMeters }
        if (selected == null) {
            val visual = lastVisual
            if (!fixUsableForAnyTarget && visual != null && now - lastVisualAtMillis <= VISUAL_GPS_GRACE_MILLIS) {
                onVisual(
                    visual.copy(
                        status = "Confirmando GPS",
                        gpsReliable = false,
                    ),
                )
            } else {
                clearVisual(onVisual)
            }
            return
        }

        val runtime = selected.runtime
        val visual = selected.toVisual(fix)
        lastVisual = visual
        lastVisualAtMillis = now
        onVisual(visual)

        val maxSpeechCount = if (selected.kind == DirectionalAlertKind.ImportedRadar) {
            MAX_RADAR_SPEECH_COUNT
        } else {
            MAX_SAVED_ALERT_SPEECH_COUNT
        }
        if (!runtime.canSpeak(now, maxSpeechCount)) return

        val spoken = when (selected.kind) {
            DirectionalAlertKind.ImportedRadar -> speechEngine.speakImportedRadar(requireNotNull(selected.radar), selected.distanceMeters)
            DirectionalAlertKind.SavedPlace -> speechEngine.speakProximityAlert(requireNotNull(selected.alert))
        }
        if (!spoken) return

        runtime.recordSpoken(now)
        onDiagnostic(
            ProximityAlertDiagnostic(
                stage = if (selected.kind == DirectionalAlertKind.ImportedRadar) {
                    "directional_radar_spoken"
                } else {
                    "directional_saved_alert_spoken"
                },
                reason = "${visual.title} a ${selected.distanceMeters.roundToInt()} metros; aproximação confirmada.",
            ),
        )
    }

    fun dismissUntilExit(targetId: String) {
        dismissGate0178.dismissUntilExit(targetId)
        if (lastVisual?.targetId == targetId) {
            lastVisual = null
            lastVisualAtMillis = 0L
        }
    }

    private fun evaluateSavedAlert(
        alert: SavedPlace,
        threshold: Int,
        fix: PreciseNavigationFix,
        now: Long,
    ): Evaluation? {
        val key = savedKey(alert)
        val runtime = runtimeById.getOrPut(key) { RuntimeState() }
        val distance = GeoDistance.meters(fix.coordinate, alert.coordinate)

        if (!runtime.zoneInitialized) {
            runtime.zoneInitialized = true
            runtime.lastDistanceMeters = distance
            runtime.minimumDistanceMeters = distance
            if (distance <= threshold) {
                runtime.mutedUntilExit = true
                runtime.insideZone = true
                return null
            }
        }

        if (distance > threshold + RESET_BUFFER_METERS) {
            dismissGate0178.clearAfterExit(key)
            runtime.resetAfterExit(distance)
            return null
        }
        if (dismissGate0178.isDismissed(key)) {
            runtime.observe(distance, fix.accuracyMeters)
            return null
        }
        if (runtime.mutedUntilExit) {
            runtime.observe(distance, fix.accuracyMeters)
            return null
        }
        if (!DirectionalAlertPolicy.isFixUsable(fix, threshold, now)) {
            runtime.observe(distance, fix.accuracyMeters)
            return null
        }

        runtime.observe(distance, fix.accuracyMeters)
        runtime.insideZone = distance <= threshold

        if (runtime.hasPassed(distance)) {
            runtime.passed = true
            runtime.mutedUntilExit = true
            return Evaluation(
                candidate = null,
                passedVisual = DirectionalAlertVisual(
                    targetId = key,
                    kind = DirectionalAlertKind.SavedPlace,
                    title = alert.name.ifBlank { "Alerta de proximidade" },
                    distanceMeters = distance,
                    thresholdMeters = threshold,
                    accuracyMeters = fix.accuracyMeters,
                    speedKilometersPerHour = fix.speedKilometersPerHour,
                    headingSource = fix.headingSource,
                    status = "Alerta ultrapassado",
                    gpsReliable = true,
                    shouldClose = true,
                    savedPlaceId = alert.id,
                ),
            )
        }

        val eligible = distance <= threshold &&
            runtime.approachingSamples >= REQUIRED_APPROACHING_SAMPLES &&
            !runtime.passed
        if (!eligible) return null

        return Evaluation(
            candidate = Candidate(
                key = key,
                kind = DirectionalAlertKind.SavedPlace,
                title = alert.name.ifBlank { "Alerta de proximidade" },
                distanceMeters = distance,
                thresholdMeters = threshold,
                runtime = runtime,
                alert = alert,
            ),
        )
    }

    private fun evaluateRadar(
        radar: ImportedRadar,
        threshold: Int,
        fix: PreciseNavigationFix,
        now: Long,
    ): Evaluation? {
        val key = radarKey(radar)
        val runtime = runtimeById.getOrPut(key) { RuntimeState() }
        val distance = GeoDistance.meters(fix.coordinate, radar.coordinate)

        if (distance > threshold + RESET_BUFFER_METERS) {
            dismissGate0178.clearAfterExit(key)
            runtime.resetAfterExit(distance)
            return null
        }
        if (dismissGate0178.isDismissed(key)) {
            runtime.observe(distance, fix.accuracyMeters)
            return null
        }
        if (runtime.passed) return null
        if (!DirectionalAlertPolicy.isFixUsable(fix, threshold, now)) {
            runtime.observe(distance, fix.accuracyMeters)
            return null
        }

        runtime.observe(distance, fix.accuracyMeters)
        runtime.insideZone = distance <= threshold

        if (runtime.hasPassed(distance)) {
            runtime.passed = true
            runtime.mutedUntilExit = true
            return Evaluation(
                candidate = null,
                passedVisual = DirectionalAlertVisual(
                    targetId = key,
                    kind = DirectionalAlertKind.ImportedRadar,
                    title = radarTitle(radar),
                    distanceMeters = distance,
                    thresholdMeters = threshold,
                    accuracyMeters = fix.accuracyMeters,
                    speedKilometersPerHour = fix.speedKilometersPerHour,
                    headingSource = fix.headingSource,
                    status = "Radar ultrapassado",
                    gpsReliable = true,
                    shouldClose = true,
                    radarId = radar.id,
                    speedLimitKmh = radar.speedKmh,
                ),
            )
        }

        val eligible = distance <= threshold &&
            runtime.approachingSamples >= REQUIRED_APPROACHING_SAMPLES &&
            !runtime.passed
        if (!eligible) return null

        return Evaluation(
            candidate = Candidate(
                key = key,
                kind = DirectionalAlertKind.ImportedRadar,
                title = radarTitle(radar),
                distanceMeters = distance,
                thresholdMeters = threshold,
                runtime = runtime,
                radar = radar,
            ),
        )
    }

    private fun clearVisual(onVisual: (DirectionalAlertVisual?) -> Unit) {
        lastVisual = null
        lastVisualAtMillis = 0L
        onVisual(null)
    }

    private fun savedKey(alert: SavedPlace): String = "saved-${alert.id}"
    private fun radarKey(radar: ImportedRadar): String = "radar-${radar.id}"

    private fun radarTitle(radar: ImportedRadar): String = importedRadarDisplayName(radar)

    private data class Evaluation(
        val candidate: Candidate?,
        val passedVisual: DirectionalAlertVisual? = null,
    )

    private data class Candidate(
        val key: String,
        val kind: DirectionalAlertKind,
        val title: String,
        val distanceMeters: Double,
        val thresholdMeters: Int,
        val runtime: RuntimeState,
        val alert: SavedPlace? = null,
        val radar: ImportedRadar? = null,
    ) {
        fun toVisual(fix: PreciseNavigationFix): DirectionalAlertVisual = DirectionalAlertVisual(
            targetId = key,
            kind = kind,
            title = title,
            distanceMeters = distanceMeters,
            thresholdMeters = thresholdMeters,
            accuracyMeters = fix.accuracyMeters,
            speedKilometersPerHour = fix.speedKilometersPerHour,
            headingSource = fix.headingSource,
            status = "Aproximando",
            gpsReliable = true,
            shouldClose = false,
            savedPlaceId = alert?.id,
            radarId = radar?.id,
            speedLimitKmh = radar?.speedKmh,
        )
    }

    private data class RuntimeState(
        var spokenCount: Int = 0,
        var lastSpokenAtMillis: Long = 0L,
        var lastDistanceMeters: Double? = null,
        var minimumDistanceMeters: Double = Double.MAX_VALUE,
        var approachingSamples: Int = 0,
        var increasingSamples: Int = 0,
        var zoneInitialized: Boolean = false,
        var mutedUntilExit: Boolean = false,
        var insideZone: Boolean = false,
        var passed: Boolean = false,
    ) {
        fun observe(distanceMeters: Double, accuracyMeters: Double) {
            val previous = lastDistanceMeters
            if (previous != null) {
                if (DirectionalAlertPolicy.isApproaching(previous, distanceMeters, accuracyMeters)) {
                    approachingSamples = (approachingSamples + 1).coerceAtMost(10)
                } else {
                    approachingSamples = 0
                }
                val jitter = max(DirectionalAlertPolicy.MIN_DISTANCE_JITTER_METERS, accuracyMeters * 0.35)
                increasingSamples = if (distanceMeters > previous + jitter) increasingSamples + 1 else 0
            }
            minimumDistanceMeters = minOf(minimumDistanceMeters, distanceMeters)
            lastDistanceMeters = distanceMeters
        }

        fun hasPassed(distanceMeters: Double): Boolean =
            insideZone && DirectionalAlertPolicy.hasPassedByDistance(
                minimumDistanceMeters = minimumDistanceMeters,
                currentDistanceMeters = distanceMeters,
                increasingSamples = increasingSamples,
            )

        fun canSpeak(nowMillis: Long, maxSpeechCount: Int): Boolean =
            spokenCount < maxSpeechCount && nowMillis - lastSpokenAtMillis >= SPEECH_REPEAT_GAP_MILLIS

        fun recordSpoken(nowMillis: Long) {
            spokenCount += 1
            lastSpokenAtMillis = nowMillis
        }

        fun resetAfterExit(distanceMeters: Double) {
            spokenCount = 0
            lastSpokenAtMillis = 0L
            lastDistanceMeters = distanceMeters
            minimumDistanceMeters = distanceMeters
            approachingSamples = 0
            increasingSamples = 0
            zoneInitialized = true
            mutedUntilExit = false
            insideZone = false
            passed = false
        }
    }

    private companion object {
        const val RESET_BUFFER_METERS = 140.0
        const val REQUIRED_APPROACHING_SAMPLES = 2
        const val SPEECH_REPEAT_GAP_MILLIS = 20_000L
        const val MAX_SAVED_ALERT_SPEECH_COUNT = 2
        const val MAX_RADAR_SPEECH_COUNT = 1
        const val VISUAL_GPS_GRACE_MILLIS = 1_500L
    }
}

data class DirectionalAlertVisual(
    val targetId: String,
    val kind: DirectionalAlertKind,
    val title: String,
    val distanceMeters: Double,
    val thresholdMeters: Int,
    val accuracyMeters: Double,
    val speedKilometersPerHour: Double,
    val headingSource: NavigationHeadingSource,
    val status: String,
    val gpsReliable: Boolean,
    val shouldClose: Boolean,
    val savedPlaceId: String? = null,
    val radarId: String? = null,
    val speedLimitKmh: Int? = null,
)

enum class DirectionalAlertKind {
    SavedPlace,
    ImportedRadar,
}
