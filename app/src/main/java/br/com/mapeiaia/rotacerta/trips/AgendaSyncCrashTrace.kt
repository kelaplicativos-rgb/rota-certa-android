package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Agenda-only crash evidence for the authenticated BlaBlaCar sync Activity.
 *
 * This does not contain passenger values, URLs, exception messages, cookies or
 * request data. It persists only the exception class, source frames and the
 * collector's structural checkpoint, then delegates to Android's original
 * uncaught-exception handler so crash behavior is not changed.
 */
internal object AgendaSyncCrashTraceStore {
    private const val DIRECTORY = "agenda-sync-diagnostic"
    private const val FILE_NAME = "last-java-crash.txt"
    private const val MAX_FRAMES = 36
    private const val MAX_CAUSES = 4
    private const val MAX_EXPORT_CHARS = 16_000
    @Volatile private var lastCheckpoint: String = "not_armed"

    fun arm(context: Context) {
        lastCheckpoint = "sync_armed"
        runCatching { traceFile(context).delete() }
    }

    fun checkpoint(value: String) {
        lastCheckpoint = sanitize(value).take(500).ifBlank { "empty" }
    }

    fun record(
        context: Context,
        thread: Thread,
        error: Throwable,
        structuralSnapshot: () -> String,
    ) {
        val snapshot = runCatching { sanitize(structuralSnapshot()).take(1_500) }
            .getOrDefault("snapshot_failed")
        val text = buildString {
            appendLine("schema=1")
            appendLine("capturedAt=${formatDate(System.currentTimeMillis())}")
            appendLine("thread=${sanitize(thread.name).take(80)}")
            appendLine("exception=${error.javaClass.name}")
            appendLine("checkpoint=${lastCheckpoint}")
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
            val file = traceFile(context)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
        }
    }

    fun export(context: Context): String {
        val file = traceFile(context)
        if (!file.isFile) return "nenhuma exceção Java da sincronização foi capturada nesta instalação/versão."
        return runCatching { file.readText(Charsets.UTF_8).take(MAX_EXPORT_CHARS) }
            .getOrElse { "falha ao ler a evidência de crash da Agenda: ${it.javaClass.simpleName}" }
    }

    private fun traceFile(context: Context): File =
        File(File(context.applicationContext.filesDir, DIRECTORY), FILE_NAME)

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    private fun formatDate(value: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(value))
}

internal class AgendaSyncCrashGuard private constructor(
    private val thread: Thread,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val installed: Thread.UncaughtExceptionHandler,
) {
    fun close() {
        if (thread.uncaughtExceptionHandler === installed) {
            thread.uncaughtExceptionHandler = previous
        }
    }

    companion object {
        fun install(
            context: Context,
            structuralSnapshot: () -> String,
        ): AgendaSyncCrashGuard {
            val thread = Thread.currentThread()
            val previous = thread.uncaughtExceptionHandler
            val handler = object : Thread.UncaughtExceptionHandler {
                override fun uncaughtException(crashedThread: Thread, error: Throwable) {
                    runCatching {
                        AgendaSyncCrashTraceStore.record(
                            context = context.applicationContext,
                            thread = crashedThread,
                            error = error,
                            structuralSnapshot = structuralSnapshot,
                        )
                    }
                    val delegate = previous ?: Thread.getDefaultUncaughtExceptionHandler()
                    if (delegate != null && delegate !== this) {
                        delegate.uncaughtException(crashedThread, error)
                    } else {
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
            thread.uncaughtExceptionHandler = handler
            return AgendaSyncCrashGuard(thread, previous, handler)
        }
    }
}
