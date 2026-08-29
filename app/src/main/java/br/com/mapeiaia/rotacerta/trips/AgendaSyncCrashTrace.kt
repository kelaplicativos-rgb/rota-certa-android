package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Agenda-only crash evidence.
 *
 * Normal checkpoints are memory-only and schedule coalesced background
 * persistence. Crash handling performs the minimum synchronous flush required
 * for process-death recovery. No passenger values, URLs, exception messages,
 * cookies, tokens or request bodies are persisted.
 */
internal object AgendaSyncCrashTraceStore {
    private const val DIRECTORY = "agenda-sync-diagnostic"
    private const val FILE_NAME = "last-java-crash.txt"
    private const val CHECKPOINT_FILE_NAME = "last-sync-checkpoint.txt"
    private const val BREADCRUMB_FILE_NAME = "sync-breadcrumbs.txt"
    private const val MAX_FRAMES = 36
    private const val MAX_CAUSES = 4
    private const val MAX_EXPORT_CHARS = 16_000
    private const val MAX_CHECKPOINT_CHARS = 700
    private const val MAX_BREADCRUMB_LINES = 64
    private const val MAX_BREADCRUMB_CHARS = 18_000

    private data class Breadcrumb(val wallMs: Long, val value: String)

    private val breadcrumbLock = Any()
    private val breadcrumbs = ArrayDeque<Breadcrumb>(MAX_BREADCRUMB_LINES)
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "agenda-crash-trace-io").apply { isDaemon = true }
    }
    private val persistScheduled = AtomicBoolean(false)
    private val persistVersion = AtomicLong(0L)
    private val persistedVersion = AtomicLong(0L)

    @Volatile private var lastCheckpoint: String = "not_armed"

    fun arm(context: Context) {
        checkpoint(context, "sync_armed")
    }

    /**
     * Hot-path checkpoint: compact memory mutation only. Disk persistence is
     * coalesced and happens off the caller thread.
     */
    fun checkpoint(context: Context, value: String) {
        val safe = sanitize(value).take(MAX_CHECKPOINT_CHARS).ifBlank { "empty" }
        lastCheckpoint = safe
        synchronized(breadcrumbLock) {
            while (breadcrumbs.size >= MAX_BREADCRUMB_LINES) breadcrumbs.removeFirst()
            breadcrumbs.addLast(Breadcrumb(System.currentTimeMillis(), safe))
        }
        persistVersion.incrementAndGet()
        schedulePersist(context.applicationContext)
    }

    fun record(
        context: Context,
        thread: Thread,
        error: Throwable,
        structuralSnapshot: () -> String,
    ) {
        val appContext = context.applicationContext
        val checkpoint = currentCheckpoint(appContext)
        val snapshot = runCatching { sanitize(structuralSnapshot()).take(1_500) }
            .getOrDefault("snapshot_failed")
        val activeContext = sanitize(AgendaTrace.activeOperationSummary()).take(700)
        val incomplete = !activeContext.contains("operationId=none")
        val text = buildString {
            appendLine("schema=3")
            appendLine("capturedAt=${formatDate(System.currentTimeMillis())}")
            appendLine("thread=${sanitize(thread.name).take(80)}")
            appendLine("exception=${error.javaClass.name}")
            appendLine("checkpoint=$checkpoint")
            appendLine("activeContext=$activeContext")
            appendLine("OPERATION_INCOMPLETE_DUE_PROCESS_TERMINATION=$incomplete")
            appendLine("snapshot=$snapshot")
            var current: Throwable? = error
            var causeIndex = 0
            while (current != null && causeIndex < MAX_CAUSES) {
                appendLine("cause[$causeIndex]=${current.javaClass.name}")
                current.stackTrace.take(MAX_FRAMES).forEachIndexed { frameIndex, frame ->
                    appendLine(
                        "frame[$causeIndex][$frameIndex]=" +
                            "${frame.className}#${frame.methodName}(${frame.fileName ?: "unknown"}:${frame.lineNumber})",
                    )
                }
                current = current.cause
                causeIndex++
            }
            appendLine("messageCaptured=false")
            appendLine("personalValuesCaptured=false")
        }.take(MAX_EXPORT_CHARS)

        runCatching {
            persistCheckpointAndBreadcrumbsNow(appContext)
            writeAtomically(traceFile(appContext), text)
        }
    }

    fun export(context: Context): String {
        val appContext = context.applicationContext
        val checkpoint = currentCheckpoint(appContext)
        val file = traceFile(appContext)
        val crash = if (!file.isFile) {
            "nenhuma exceção Java da sincronização foi capturada nesta instalação/versão."
        } else {
            runCatching { file.readText(Charsets.UTF_8).take(MAX_EXPORT_CHARS) }
                .getOrElse { "falha ao ler a evidência de crash da Agenda: ${it.javaClass.simpleName}" }
        }
        val memoryBreadcrumbs = snapshotBreadcrumbs()
        val breadcrumbText = if (memoryBreadcrumbs.isNotEmpty()) {
            renderBreadcrumbs(memoryBreadcrumbs)
        } else {
            readPersistedBreadcrumbs(appContext)
        }
        return buildString {
            appendLine("lastCheckpoint=$checkpoint")
            appendLine("checkpointPersistence=memory_hot_path_async_coalesced")
            appendLine("--- AGENDA SYNC BREADCRUMBS ---")
            appendLine(breadcrumbText)
            append(crash)
        }.trimEnd()
    }

    private fun schedulePersist(context: Context) {
        if (!persistScheduled.compareAndSet(false, true)) return
        ioExecutor.execute {
            try {
                persistCheckpointAndBreadcrumbsNow(context.applicationContext)
            } finally {
                persistScheduled.set(false)
                if (persistedVersion.get() < persistVersion.get()) {
                    schedulePersist(context.applicationContext)
                }
            }
        }
    }

    private fun persistCheckpointAndBreadcrumbsNow(context: Context) {
        val version = persistVersion.get()
        val checkpoint = lastCheckpoint
        val snapshot = snapshotBreadcrumbs()
        runCatching {
            persistCheckpoint(context, checkpoint)
            writeAtomically(breadcrumbFile(context), renderBreadcrumbs(snapshot))
            persistedVersion.set(version)
        }
    }

    private fun persistCheckpoint(context: Context, value: String) {
        writeAtomically(
            checkpointFile(context),
            sanitize(value).take(MAX_CHECKPOINT_CHARS).ifBlank { "empty" },
        )
    }

    private fun snapshotBreadcrumbs(): List<Breadcrumb> =
        synchronized(breadcrumbLock) { breadcrumbs.toList() }

    private fun renderBreadcrumbs(values: List<Breadcrumb>): String =
        values.joinToString("\n") { breadcrumb ->
            "${formatDate(breadcrumb.wallMs)} | ${breadcrumb.value}"
        }.takeLast(MAX_BREADCRUMB_CHARS).ifBlank { "sem checkpoints persistidos da Agenda" }

    private fun readPersistedBreadcrumbs(context: Context): String = runCatching {
        breadcrumbFile(context)
            .takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
            ?.takeLast(MAX_BREADCRUMB_CHARS)
            ?.takeIf(String::isNotBlank)
            ?: "sem checkpoints persistidos da Agenda"
    }.getOrElse { "falha ao ler checkpoints persistidos: ${it.javaClass.simpleName}" }

    private fun currentCheckpoint(context: Context): String {
        if (lastCheckpoint != "not_armed") return lastCheckpoint
        val persisted = runCatching {
            checkpointFile(context)
                .takeIf(File::isFile)
                ?.readText(Charsets.UTF_8)
                ?.let(::sanitize)
                ?.take(MAX_CHECKPOINT_CHARS)
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
        return persisted ?: lastCheckpoint
    }

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeText(text, Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun traceFile(context: Context): File =
        File(File(context.applicationContext.filesDir, DIRECTORY), FILE_NAME)

    private fun checkpointFile(context: Context): File =
        File(File(context.applicationContext.filesDir, DIRECTORY), CHECKPOINT_FILE_NAME)

    private fun breadcrumbFile(context: Context): File =
        File(File(context.applicationContext.filesDir, DIRECTORY), BREADCRUMB_FILE_NAME)

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("(?i)https?://[^\\s|;]+"), "[url mascarada]")
            .replace(
                Regex("(?i)\\b(token|cookie|authorization|password|senha|secret|jwt|sessionToken|accessToken|viewToken)\\s*[:=]\\s*[^|;\\s]+"),
            ) { match -> "${match.groupValues[1]}=[segredo mascarado]" }
            .trim()

    private fun formatDate(value: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(value))
}

private class AgendaSyncUncaughtHandler(
    private val context: Context,
    private val structuralSnapshot: () -> String,
    val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(crashedThread: Thread, error: Throwable) {
        runCatching {
            AgendaSyncCrashTraceStore.record(
                context = context.applicationContext,
                thread = crashedThread,
                error = error,
                structuralSnapshot = structuralSnapshot,
            )
        }
        if (delegate != null && delegate !== this) {
            delegate.uncaughtException(crashedThread, error)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}

internal class AgendaSyncCrashGuard private constructor(
    private val originalDefault: Thread.UncaughtExceptionHandler?,
    private val installed: AgendaSyncUncaughtHandler,
) {
    fun close() {
        if (Thread.getDefaultUncaughtExceptionHandler() === installed) {
            Thread.setDefaultUncaughtExceptionHandler(originalDefault)
        }
    }

    companion object {
        fun install(
            context: Context,
            structuralSnapshot: () -> String,
        ): AgendaSyncCrashGuard {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            val original = (current as? AgendaSyncUncaughtHandler)?.delegate ?: current
            val handler = AgendaSyncUncaughtHandler(
                context = context.applicationContext,
                structuralSnapshot = structuralSnapshot,
                delegate = original,
            )
            Thread.setDefaultUncaughtExceptionHandler(handler)
            return AgendaSyncCrashGuard(original, handler)
        }
    }
}

private class AgendaTimelineUncaughtHandler(
    private val context: Context,
    val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(crashedThread: Thread, error: Throwable) {
        runCatching {
            AgendaSyncCrashTraceStore.record(
                context = context.applicationContext,
                thread = crashedThread,
                error = error,
                structuralSnapshot = { "owner=TripsActivity parent_sync_orchestration=true" },
            )
        }
        if (delegate != null && delegate !== this) {
            delegate.uncaughtException(crashedThread, error)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}

internal class AgendaTimelineCrashGuard private constructor(
    private val originalDefault: Thread.UncaughtExceptionHandler?,
    private val installed: AgendaTimelineUncaughtHandler,
) {
    fun close() {
        if (Thread.getDefaultUncaughtExceptionHandler() === installed) {
            Thread.setDefaultUncaughtExceptionHandler(originalDefault)
        }
    }

    companion object {
        fun install(context: Context): AgendaTimelineCrashGuard {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            val original = (current as? AgendaTimelineUncaughtHandler)?.delegate ?: current
            val handler = AgendaTimelineUncaughtHandler(
                context = context.applicationContext,
                delegate = original,
            )
            Thread.setDefaultUncaughtExceptionHandler(handler)
            return AgendaTimelineCrashGuard(original, handler)
        }
    }
}
