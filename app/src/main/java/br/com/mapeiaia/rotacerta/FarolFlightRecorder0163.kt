package br.com.mapeiaia.rotacerta

import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Gravador de voo circular do farol.
 *
 * Fica sempre ativo, mas somente mantém uma janela limitada de eventos em memória.
 * Não cria eventos por temporizador: registra apenas acontecimentos reais já emitidos
 * pelo farol, acessibilidade, OCR, rota, decisão e desenho da bolinha.
 */
object FarolFlightRecorder0163 {
    internal const val MAX_MEMORY_EVENTS = 2_500
    internal const val MAX_DISK_EVENTS = 1_200
    internal const val MAX_DETAILS = 1_200
    internal const val MAX_TEXT_CHUNKS = 6
    internal const val CHECKPOINT_EVERY_EVENTS = 48L
    internal const val MAX_PREVIOUS_CHECKPOINT_CHARS = 1_800_000

    private const val CURRENT_FILE = "farol-flight-recorder-current.log"
    private const val PREVIOUS_FILE = "farol-flight-recorder-previous.log"
    private const val TEMP_FILE = "farol-flight-recorder-current.tmp"
    private const val AUTO_DISK_CHECKPOINT_ENABLED_STAGE26 = false // AUTO_DISK_CHECKPOINT_DISABLED_STAGE26

    private val lock = Any()
    private val fileLock = Any()
    private val events = ArrayDeque<FlightEvent>(MAX_MEMORY_EVENTS)
    private val sequence = AtomicLong(0L)
    private val checkpointPending = AtomicBoolean(false)
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "farol-flight-recorder-io").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    @Volatile private var appContext: Context? = null
    @Volatile private var sessionId: String = "not-initialized"
    @Volatile private var sessionStartedWallMillis: Long = 0L
    @Volatile private var sessionStartedElapsedNanos: Long = 0L
    @Volatile private var lastElapsedNanos: Long = 0L
    @Volatile private var droppedEvents: Long = 0L
    @Volatile private var lastCheckpointReason: String = "none"

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        val shouldInitialize = synchronized(lock) {
            if (appContext != null) return@synchronized false
            appContext = applicationContext
            sessionStartedWallMillis = System.currentTimeMillis()
            sessionStartedElapsedNanos = SystemClock.elapsedRealtimeNanos()
            lastElapsedNanos = sessionStartedElapsedNanos
            sessionId = buildString {
                append(sessionStartedWallMillis)
                append('-')
                append(Process.myPid())
                append('-')
                append(UUID.randomUUID().toString().take(8))
            }
            true
        }
        if (!shouldInitialize) return
        rotatePreviousCheckpoint(applicationContext)
        record(
            stage = "FLIGHT_RECORDER_INITIALIZED",
            packageName = applicationContext.packageName,
            details = "session=$sessionId; max_memory=$MAX_MEMORY_EVENTS; max_disk=$MAX_DISK_EVENTS; checkpoint_every=$CHECKPOINT_EVERY_EVENTS",
        )
    }

    fun record(
        stage: String,
        packageName: String?,
        details: String = "",
        wallTimeMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeNanos: Long = SystemClock.elapsedRealtimeNanos(),
    ) {
        ForensicIncidentMonitor0193.observe(stage, packageName, details)

        runCatching {
            appendEvent(
                stage = stage,
                packageName = packageName,
                details = details,
                wallTimeMillis = wallTimeMillis,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                threadName = Thread.currentThread().name,
                processId = Process.myPid(),
                usedMemoryBytes = usedMemoryBytes(),
                allowCheckpoint = true,
            )
        }
    }

    fun recordTextSnapshot(
        stage: String,
        packageName: String?,
        source: String,
        text: String,
    ) {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) {
            record(
                stage = "${stage}_TEXT_EMPTY",
                packageName = packageName,
                details = "source=$source; text_len=0; text_hash=0",
            )
            return
        }
        val chunkSize = (MAX_DETAILS - 140).coerceAtLeast(200)
        val chunks = normalized.chunked(chunkSize).take(MAX_TEXT_CHUNKS)
        chunks.forEachIndexed { index, chunk ->
            record(
                stage = "${stage}_TEXT",
                packageName = packageName,
                details = "source=$source; text_len=${normalized.length}; text_hash=${normalized.hashCode()}; chunk=${index + 1}/${chunks.size}; text=$chunk",
            )
        }
        if (normalized.length > chunkSize * MAX_TEXT_CHUNKS) {
            record(
                stage = "${stage}_TEXT_TRUNCATED",
                packageName = packageName,
                details = "source=$source; text_len=${normalized.length}; captured_chars=${chunkSize * MAX_TEXT_CHUNKS}",
            )
        }
    }

    fun recordDiagnostic(
        stage: String,
        packageName: String?,
        color: String?,
        reason: String,
        text: String?,
        fields: Any?,
        result: Any?,
        error: Throwable?,
    ) {
        record(
            stage = "DIAGNOSTIC_${stage.uppercase(Locale.ROOT)}",
            packageName = packageName,
            details = buildString {
                append("color=").append(color ?: "none")
                append("; reason=").append(reason)
                append("; text_len=").append(text?.length ?: 0)
                append("; text_hash=").append(text?.hashCode() ?: 0)
                append("; fields=").append(fields ?: "none")
                append("; result=").append(result ?: "none")
                append("; error=").append(error?.let { "${it::class.java.simpleName}:${it.message}" } ?: "none")
            },
        )
    }

    fun forceCheckpoint(reason: String) {
        val context = appContext ?: return
        val snapshot = snapshotText(reason = reason, diskLimit = true)
        synchronized(fileLock) {
            writeCheckpoint(context, snapshot)
        }
    }

    fun exportReport(): String {
        val current = snapshotText(reason = "user_export", diskLimit = false)
        val previous = previousCheckpointText()
        return buildString {
            appendLine("--- GRAVADOR DE VOO DO FAROL 0.1.163 ---")
            appendLine("Estado: ATIVO, circular, sem temporizador artificial")
            appendLine("Stage26: checkpoint automatico em disco=false; forceCheckpoint explicito preservado")
            appendLine("Precisao: horario civil em milissegundos + relogio monotonic em nanossegundos")
            appendLine("Sessao atual: $sessionId")
            appendLine("Eventos descartados por limite: $droppedEvents")
            appendLine("Ultimo checkpoint: $lastCheckpointReason")
            appendLine()
            appendLine(current)
            if (previous.isNotBlank()) {
                appendLine()
                appendLine("--- CHECKPOINT RECUPERADO DA SESSAO ANTERIOR ---")
                appendLine(previous)
            }
        }.trimEnd()
    }

    internal fun resetForTest() {
        synchronized(lock) {
            events.clear()
            sequence.set(0L)
            droppedEvents = 0L
            lastElapsedNanos = 0L
            sessionId = "test-session"
            sessionStartedWallMillis = 0L
            sessionStartedElapsedNanos = 0L
        }
    }

    internal fun recordAtForTest(
        stage: String,
        packageName: String?,
        details: String,
        wallTimeMillis: Long,
        elapsedRealtimeNanos: Long,
    ) {
        appendEvent(
            stage = stage,
            packageName = packageName,
            details = details,
            wallTimeMillis = wallTimeMillis,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            threadName = "test-thread",
            processId = 1,
            usedMemoryBytes = 1024L,
            allowCheckpoint = false,
        )
    }

    internal fun snapshotForTest(): String = snapshotText("test", diskLimit = false)

    private fun appendEvent(
        stage: String,
        packageName: String?,
        details: String,
        wallTimeMillis: Long,
        elapsedRealtimeNanos: Long,
        threadName: String,
        processId: Int,
        usedMemoryBytes: Long,
        allowCheckpoint: Boolean,
    ) {
        val event: FlightEvent
        val shouldCheckpoint: Boolean
        synchronized(lock) {
            val nextSequence = sequence.incrementAndGet()
            val previousElapsed = lastElapsedNanos
            val deltaNanos = if (previousElapsed > 0L) {
                (elapsedRealtimeNanos - previousElapsed).coerceAtLeast(0L)
            } else {
                0L
            }
            lastElapsedNanos = elapsedRealtimeNanos
            event = FlightEvent(
                sequence = nextSequence,
                wallTimeMillis = wallTimeMillis,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                deltaNanos = deltaNanos,
                stage = sanitize(stage).ifBlank { "EVENT" },
                packageName = sanitize(packageName.orEmpty()).ifBlank { "nao informado" },
                details = maskSensitive(sanitize(details)).take(MAX_DETAILS),
                threadName = sanitize(threadName).ifBlank { "unknown" },
                processId = processId,
                usedMemoryBytes = usedMemoryBytes,
            )
            while (events.size >= MAX_MEMORY_EVENTS) {
                events.removeFirst()
                droppedEvents += 1L
            }
            events.addLast(event)
            shouldCheckpoint = AUTO_DISK_CHECKPOINT_ENABLED_STAGE26 && allowCheckpoint && (
                nextSequence % CHECKPOINT_EVERY_EVENTS == 0L || isCriticalStage(event.stage)
            )
        }
        if (shouldCheckpoint) scheduleCheckpoint("event:${event.stage}")
    }

    private fun scheduleCheckpoint(reason: String) {
        val context = appContext ?: return
        if (!checkpointPending.compareAndSet(false, true)) return
        ioExecutor.execute {
            try {
                val snapshot = snapshotText(reason = reason, diskLimit = true)
                synchronized(fileLock) {
                    writeCheckpoint(context, snapshot)
                }
            } finally {
                checkpointPending.set(false)
            }
        }
    } // checkpoint_snapshot_fully_off_main_0_1_167

    private fun snapshotText(reason: String, diskLimit: Boolean): String {
        val snapshot = synchronized(lock) {
            val all = events.toList()
            if (diskLimit) all.takeLast(MAX_DISK_EVENTS) else all
        }
        return buildString {
            appendLine("session=$sessionId")
            appendLine("session_started_wall=${formatWall(sessionStartedWallMillis)}")
            appendLine("session_started_elapsed_ns=$sessionStartedElapsedNanos")
            appendLine("snapshot_reason=$reason")
            appendLine("event_count=${snapshot.size}")
            appendLine("dropped_events=$droppedEvents")
            snapshot.forEach { event ->
                appendLine(event.toLine())
            }
        }.trimEnd()
    }

    private fun FlightEvent.toLine(): String = buildString {
        append("seq=").append(sequence)
        append(" | wall=").append(formatWall(wallTimeMillis))
        append(" | mono_ns=").append(elapsedRealtimeNanos)
        append(" | delta_us=").append(deltaNanos / 1_000L)
        append(" | thread=").append(threadName)
        append(" | pid=").append(processId)
        append(" | mem_kb=").append(usedMemoryBytes / 1024L)
        append(" | stage=").append(stage)
        append(" | pacote=").append(packageName)
        if (details.isNotBlank()) append(" | ").append(details)
    }

    private fun rotatePreviousCheckpoint(context: Context) {
        runCatching {
            val directory = diagnosticsDirectory(context)
            val current = File(directory, CURRENT_FILE)
            if (!current.exists()) return@runCatching
            val previous = File(directory, PREVIOUS_FILE)
            if (previous.exists()) previous.delete()
            if (!current.renameTo(previous)) {
                current.copyTo(previous, overwrite = true)
                current.delete()
            }
        }
    }

    private fun writeCheckpoint(context: Context, content: String) {
        runCatching {
            val directory = diagnosticsDirectory(context)
            val temp = File(directory, TEMP_FILE)
            val current = File(directory, CURRENT_FILE)
            temp.writeText(content, Charsets.UTF_8)
            if (current.exists()) current.delete()
            if (!temp.renameTo(current)) {
                temp.copyTo(current, overwrite = true)
                temp.delete()
            }
            lastCheckpointReason = content.lineSequence()
                .firstOrNull { it.startsWith("snapshot_reason=") }
                ?.substringAfter('=')
                ?: "unknown"
        }
    }

    private fun previousCheckpointText(): String {
        val context = appContext ?: return ""
        return runCatching {
            val file = File(diagnosticsDirectory(context), PREVIOUS_FILE)
            if (!file.exists()) "" else file.readText(Charsets.UTF_8).takeLast(MAX_PREVIOUS_CHECKPOINT_CHARS)
        }.getOrDefault("")
    }

    private fun diagnosticsDirectory(context: Context): File =
        File(context.filesDir, "diagnostics").apply { mkdirs() }

    private fun usedMemoryBytes(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
    }

    private fun isCriticalStage(stage: String): Boolean {
        val upper = stage.uppercase(Locale.ROOT)
        return listOf(
            "ERROR", "FAIL", "REJECT", "DISCARD", "INTERRUPT", "DESTROY",
            "DECISION", "CLEAR", "EXCEPTION", "TIMEOUT",
        ).any(upper::contains)
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    private fun maskSensitive(value: String): String = value
        .replace(Regex("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[-\\s]?\\d{4}(?!\\d)"), "[telefone mascarado]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email mascarado]")

    private fun formatWall(millis: Long): String = if (millis <= 0L) {
        "nao informado"
    } else {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))
    }

    private data class FlightEvent(
        val sequence: Long,
        val wallTimeMillis: Long,
        val elapsedRealtimeNanos: Long,
        val deltaNanos: Long,
        val stage: String,
        val packageName: String,
        val details: String,
        val threadName: String,
        val processId: Int,
        val usedMemoryBytes: Long,
    )
}
