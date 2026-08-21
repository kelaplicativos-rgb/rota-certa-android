package br.com.mapeiaia.rotacerta

import java.util.concurrent.atomic.AtomicLong

/** Stage43 physical truth: logical OFF is not PASS until the gray/no-km renderer actually commits. */
object FarolManualOffVisualCommitStage43 {
    const val CONTRACT_MARKER = "MANUAL_OFF_PHYSICAL_VIEW_COMMIT_STAGE43"
    private val attempts = AtomicLong(0L)
    private val applied = AtomicLong(0L)
    private val anomalies = AtomicLong(0L)

    data class Snapshot(val attempts: Long, val applied: Long, val anomalies: Long) {
        val status: String
            get() = when {
                anomalies > 0L || applied != attempts -> "FAIL"
                attempts > 0L -> "PASS"
                else -> "NOT_TESTED"
            }
    }

    fun recordAttempt(appliedNow: Boolean): Snapshot {
        attempts.incrementAndGet()
        if (appliedNow) applied.incrementAndGet() else anomalies.incrementAndGet()
        return snapshot()
    }

    fun snapshot(): Snapshot = Snapshot(attempts.get(), applied.get(), anomalies.get())

    fun exportReport(): String {
        val s = snapshot()
        return buildString {
            appendLine("ROTA CERTA — STAGE43 COMMIT FISICO DO OFF")
            appendLine("marker=$CONTRACT_MARKER")
            appendLine("status=${s.status}; attempts=${s.attempts}; applied=${s.applied}; anomalies=${s.anomalies}")
            append("rule=OFF so e PASS quando o renderizador atravessa o commit real Idle/cinza com km nulo; ausencia de commit em uma tentativa e FAIL")
        }
    }

    internal fun resetForTests() {
        attempts.set(0L)
        applied.set(0L)
        anomalies.set(0L)
    }
}
