package br.com.mapeiaia.rotacerta

import android.os.SystemClock
import java.util.ArrayDeque

/** Temporary Stage 9 latency probe. Memory-only; never participates in FAROL decisions. */
internal object FarolLatencyProbeStage9 {
    const val MARKER = "FAROL_LATENCY_STAGE9"
    private const val MAX_EVENTS = 512
    private val lock = Any()
    private val events = ArrayDeque<String>(MAX_EVENTS)

    @Volatile private var accessibilityReadStartNs: Long = 0L
    @Volatile private var ocrReadStartNs: Long = 0L

    private fun sourceStart(source: String): Long = when (source) {
        "Accessibility" -> accessibilityReadStartNs
        "Ocr", "OCR" -> ocrReadStartNs
        else -> 0L
    }

    private fun rememberSourceStart(source: String, startedNs: Long) {
        when (source) {
            "Accessibility" -> accessibilityReadStartNs = startedNs
            "Ocr", "OCR" -> ocrReadStartNs = startedNs
        }
    }

    private fun durationDetails(startedNs: Long, endedNs: Long): String {
        val ns = (endedNs - startedNs).coerceAtLeast(0L)
        return "duration_us=${ns / 1_000L}; duration_ms=${ns / 1_000_000L}"
    }

    private fun record(stage: String, source: String, details: String) {
        val line = "${MARKER}_$stage; source=$source; $details"
        synchronized(lock) {
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(line)
        }
    }

    fun dump(): String = synchronized(lock) { events.joinToString("\n") }
    fun size(): Int = synchronized(lock) { events.size }
    fun clear() = synchronized(lock) { events.clear() }

    fun measureText(stage: String, source: String, block: () -> String): String {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        rememberSourceStart(source, startedNs)
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(stage, source, "${durationDetails(startedNs, endedNs)}; text_length=${result.length}; duplicate_skipped=false")
        return result
    }

    fun <T> measureBlocks(stage: String, source: String, block: () -> List<T>): List<T> {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(stage, source, "${durationDetails(startedNs, endedNs)}; blocks=${result.size}; duplicate_skipped=false")
        return result
    }

    fun <T> measureValue(stage: String, source: String, block: () -> T): T {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record(stage, source, "${durationDetails(startedNs, endedNs)}; duplicate_skipped=false")
        return result
    }

    fun recordOcrStructured(startedNs: Long, textLength: Int, blockCount: Int) {
        val endedNs = SystemClock.elapsedRealtimeNanos()
        rememberSourceStart("OCR", startedNs)
        record("OCR_EXTRACT_STRUCTURED", "OCR", "${durationDetails(startedNs, endedNs)}; text_length=$textLength; ocr_blocks=$blockCount; duplicate_skipped=false")
    }

    fun recordDuplicateTotal(source: String, textLength: Int) {
        val startedNs = sourceStart(source)
        if (startedNs <= 0L) {
            record("READ_TO_DUPLICATE_SKIP", source, "duration_us=-1; duration_ms=-1; text_length=$textLength; duplicate_skipped=true; start_missing=true")
            return
        }
        val endedNs = SystemClock.elapsedRealtimeNanos()
        record("READ_TO_DUPLICATE_SKIP", source, "${durationDetails(startedNs, endedNs)}; text_length=$textLength; duplicate_skipped=true; start_missing=false")
    }
}
