package br.com.mapeiaia.rotacerta.core

import java.text.Normalizer
import java.util.Locale

/**
 * Controla o ciclo de vida do card visivel.
 * Ele e a fonte de verdade para saber se o card atual entrou, mudou, continuou igual ou saiu.
 */
class CoreVisibleCardLifecycle(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var current: CoreVisibleCardSnapshot? = null

    @Synchronized
    fun observe(
        packageName: String?,
        snapshotHash: Int,
        text: String,
        stableSignature: String? = null,
    ): CoreVisibleCardEvent {
        val normalizedPackage = CorePackageMonitor.normalize(packageName).orEmpty()
        val signature = stableSignature
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: signatureFor(normalizedPackage, snapshotHash, text)
        val previous = current
        val now = nowMillis()
        val nextSnapshot = CoreVisibleCardSnapshot(
            signature = signature,
            packageName = normalizedPackage,
            snapshotHash = snapshotHash,
            textFingerprint = text.fingerprint(),
            observedAtMillis = now,
        )
        current = nextSnapshot
        return when {
            previous == null -> CoreVisibleCardEvent(
                action = CoreVisibleCardAction.Entered,
                previousSignature = null,
                currentSignature = signature,
                reason = "Card visivel entrou no ciclo de vida do Core.",
            )
            previous.signature == signature -> CoreVisibleCardEvent(
                action = CoreVisibleCardAction.Same,
                previousSignature = previous.signature,
                currentSignature = signature,
                reason = "Mesmo card visivel continua ativo.",
            )
            previous.packageName != normalizedPackage -> CoreVisibleCardEvent(
                action = CoreVisibleCardAction.Replaced,
                previousSignature = previous.signature,
                currentSignature = signature,
                reason = "Pacote do card visivel mudou.",
            )
            else -> CoreVisibleCardEvent(
                action = CoreVisibleCardAction.Changed,
                previousSignature = previous.signature,
                currentSignature = signature,
                reason = "Assinatura do card visivel mudou.",
            )
        }
    }

    @Synchronized
    fun clear(reason: String): CoreVisibleCardEvent {
        val previous = current
        current = null
        return CoreVisibleCardEvent(
            action = if (previous == null) CoreVisibleCardAction.None else CoreVisibleCardAction.Exited,
            previousSignature = previous?.signature,
            currentSignature = null,
            reason = reason,
        )
    }

    @Synchronized
    fun currentSignature(): String? = current?.signature

    @Synchronized
    fun isCurrent(signature: String?): Boolean =
        signature != null && current?.signature == signature

    private fun signatureFor(packageName: String, snapshotHash: Int, text: String): String =
        listOf(packageName, snapshotHash.toString(), text.fingerprint()).joinToString("|")

    private fun String.fingerprint(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(320)
            .hashCode()
            .toString()
}

data class CoreVisibleCardSnapshot(
    val signature: String,
    val packageName: String,
    val snapshotHash: Int,
    val textFingerprint: String,
    val observedAtMillis: Long,
)

data class CoreVisibleCardEvent(
    val action: CoreVisibleCardAction,
    val previousSignature: String?,
    val currentSignature: String?,
    val reason: String,
) {
    val shouldClearPreviousDecision: Boolean
        get() = action == CoreVisibleCardAction.Entered ||
            action == CoreVisibleCardAction.Changed ||
            action == CoreVisibleCardAction.Replaced ||
            action == CoreVisibleCardAction.Exited
}

enum class CoreVisibleCardAction {
    None,
    Entered,
    Same,
    Changed,
    Replaced,
    Exited,
}
