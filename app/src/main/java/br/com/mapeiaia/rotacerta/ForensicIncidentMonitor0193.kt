package br.com.mapeiaia.rotacerta

import android.os.SystemClock
import java.util.Locale

/**
 * Observa o gravador de voo existente sem criar um segundo logger.
 * Não usa timer, coroutine, screenshot, OCR, rede ou escrita contínua em disco.
 * Apenas mantém estado mínimo para detectar violações de contrato e injeta
 * marcadores seguros no próprio FarolFlightRecorder0163.
 */
object ForensicIncidentMonitor0193 {
    private const val EVENT_STORM_WINDOW_NANOS = 500_000_000L
    private const val OCR_STORM_WINDOW_NANOS = 500_000_000L
    private const val EVENT_STORM_THRESHOLD = 8
    private const val OCR_STORM_THRESHOLD = 4

    private var lastFingerprint: Int = 0
    private var lastFingerprintAt: Long = 0L
    private var repeatCount: Int = 0
    private var lastStormReportedAt: Long = 0L
    private var highestGeneration: Long = -1L
    private var highestWindowGeneration: Long = -1L
    private var anomalyCount: Long = 0L

    @Synchronized
    fun observe(stage: String, packageName: String?, details: String) {
        if (stage.startsWith("FORENSIC_")) return
        val now = SystemClock.elapsedRealtimeNanos()
        val fingerprint = 31 * (31 * stage.hashCode() + details.hashCode()) + (packageName?.hashCode() ?: 0)
        if (fingerprint == lastFingerprint && now - lastFingerprintAt <= EVENT_STORM_WINDOW_NANOS) {
            repeatCount += 1
        } else {
            lastFingerprint = fingerprint
            repeatCount = 1
        }
        lastFingerprintAt = now

        val generation = numericToken(details, "generation")
        val windowGeneration = numericToken(details, "windowGeneration")
        if (generation != null) highestGeneration = maxOf(highestGeneration, generation)
        if (windowGeneration != null) highestWindowGeneration = maxOf(highestWindowGeneration, windowGeneration)

        if (repeatCount >= EVENT_STORM_THRESHOLD && now - lastStormReportedAt > EVENT_STORM_WINDOW_NANOS) {
            lastStormReportedAt = now
            anomaly(
                packageName,
                "FORENSIC_EVENT_STORM_0193",
                "stage=${safeStage(stage)}; repeats=$repeatCount; window_ms=500",
            )
        }

        if (stage.contains("OCR", ignoreCase = true) && repeatCount >= OCR_STORM_THRESHOLD && now - lastStormReportedAt > OCR_STORM_WINDOW_NANOS) {
            lastStormReportedAt = now
            anomaly(
                packageName,
                "FORENSIC_OCR_STORM_0193",
                "stage=${safeStage(stage)}; repeats=$repeatCount; window_ms=500",
            )
        }

        if (generation != null && generation < highestGeneration && isResultStage(stage)) {
            anomaly(
                packageName,
                "FORENSIC_STALE_GENERATION_RESULT_0193",
                "stage=${safeStage(stage)}; result_generation=$generation; latest_generation=$highestGeneration",
            )
        }
        if (windowGeneration != null && windowGeneration < highestWindowGeneration && isResultStage(stage)) {
            anomaly(
                packageName,
                "FORENSIC_STALE_WINDOW_RESULT_0193",
                "stage=${safeStage(stage)}; result_window_generation=$windowGeneration; latest_window_generation=$highestWindowGeneration",
            )
        }

        if (stage == "OVERLAY_RENDER_APPLIED") {
            val normalized = details.lowercase(Locale.ROOT)
            val finalColor = normalized.contains("green") || normalized.contains("verde") || normalized.contains("red") || normalized.contains("vermelh")
            val missingDistance = normalized.contains("distance=null") || normalized.contains("distance=none") || normalized.contains("km=null")
            if (finalColor && missingDistance) {
                anomaly(packageName, "FORENSIC_FINAL_COLOR_WITHOUT_DISTANCE_0193", "stage=OVERLAY_RENDER_APPLIED")
            }
        }
    }

    fun markManualReport() {
        FarolFlightRecorder0163.record(
            stage = "FORENSIC_MANUAL_INCIDENT_MARK_0193",
            packageName = null,
            details = "manual_report_requested=true; anomalies=$anomalyCount; latest_generation=$highestGeneration; latest_window_generation=$highestWindowGeneration",
        )
    }

    @Synchronized
    internal fun resetForTest() {
        lastFingerprint = 0
        lastFingerprintAt = 0L
        repeatCount = 0
        lastStormReportedAt = 0L
        highestGeneration = -1L
        highestWindowGeneration = -1L
        anomalyCount = 0L
    }

    private fun isResultStage(stage: String): Boolean =
        stage.contains("DECISION") ||
            stage.contains("RESULT") ||
            stage.contains("OVERLAY_RENDER_APPLIED") ||
            stage.contains("ROUTE_APPLIED") ||
            stage.contains("CACHE_APPLIED")

    /** Parser sem Regex/substrings no caminho comum do gravador. */
    private fun numericToken(details: String, key: String): Long? {
        val needle = "$key="
        var searchFrom = 0
        while (searchFrom < details.length) {
            val start = details.indexOf(needle, searchFrom)
            if (start < 0) return null
            val boundaryOk = start == 0 || details[start - 1] == ';' || details[start - 1] == ' ' || details[start - 1] == ','
            if (!boundaryOk) {
                searchFrom = start + needle.length
                continue
            }
            var index = start + needle.length
            var negative = false
            if (index < details.length && details[index] == '-') {
                negative = true
                index += 1
            }
            val digitStart = index
            var value = 0L
            while (index < details.length) {
                val ch = details[index]
                if (ch !in '0'..'9') break
                val digit = ch.code - '0'.code
                if (value > (Long.MAX_VALUE - digit) / 10L) return null
                value = value * 10L + digit
                index += 1
            }
            if (index == digitStart) return null
            return if (negative) -value else value
        }
        return null
    }

    private fun safeStage(stage: String): String = buildString(minOf(stage.length, 96)) {
        var index = 0
        while (index < stage.length && index < 96) {
            val ch = stage[index]
            append(if (ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '-') ch else '_')
            index += 1
        }
    }

    private fun anomaly(packageName: String?, stage: String, details: String) {
        anomalyCount += 1L
        FarolFlightRecorder0163.record(stage = stage, packageName = packageName, details = details)
    }
}
