package br.com.mapeiaia.rotacerta

/**
 * Reduz rajadas de eventos da acessibilidade antes que elas cheguem ao OCR,
 * parser e desenho da bolinha.
 *
 * Apps monitorados continuam no caminho rapido. Outros apps recebem apenas uma
 * sondagem leve e espaçada; quando uma tela realmente parece card, ela entra no
 * caminho rapido por alguns segundos.
 */
class AccessibilityEventFloodGate(
    private val fastDebounceMillis: Long = 90L,
    private val discoveryDebounceMillis: Long = 900L,
    private val windowDiscoveryDebounceMillis: Long = 250L,
    private val candidateTtlMillis: Long = 4_000L,
) {
    private var lastFastKey: String? = null
    private var lastFastAtMillis: Long = 0L
    private var lastDiscoveryPackage: String? = null
    private var lastDiscoveryAtMillis: Long = 0L
    private var candidatePackage: String? = null
    private var candidateUntilMillis: Long = 0L

    fun classify(
        packageName: String?,
        eventType: Int,
        monitoredPackage: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): AccessibilityEventMode {
        val normalized = normalize(packageName) ?: return AccessibilityEventMode.Ignore
        val fast = monitoredPackage || isCandidate(normalized, nowMillis)
        if (fast) {
            val key = "$normalized:$eventType"
            val duplicate = key == lastFastKey &&
                nowMillis >= lastFastAtMillis &&
                nowMillis - lastFastAtMillis < fastDebounceMillis
            if (duplicate) return AccessibilityEventMode.Ignore
            lastFastKey = key
            lastFastAtMillis = nowMillis
            return AccessibilityEventMode.Fast
        }

        val interval = if (eventType == TYPE_WINDOW_STATE_CHANGED || eventType == TYPE_WINDOWS_CHANGED) {
            windowDiscoveryDebounceMillis
        } else {
            discoveryDebounceMillis
        }
        val duplicate = normalized == lastDiscoveryPackage &&
            nowMillis >= lastDiscoveryAtMillis &&
            nowMillis - lastDiscoveryAtMillis < interval
        if (duplicate) return AccessibilityEventMode.Ignore
        lastDiscoveryPackage = normalized
        lastDiscoveryAtMillis = nowMillis
        return AccessibilityEventMode.Discovery
    }

    fun markCandidate(packageName: String?, nowMillis: Long = System.currentTimeMillis()) {
        candidatePackage = normalize(packageName)
        candidateUntilMillis = nowMillis + candidateTtlMillis.coerceAtLeast(0L)
    }

    fun isCandidate(packageName: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val normalized = normalize(packageName) ?: return false
        if (candidatePackage != normalized) return false
        if (nowMillis > candidateUntilMillis) {
            clearCandidate()
            return false
        }
        return true
    }

    fun clearCandidate() {
        candidatePackage = null
        candidateUntilMillis = 0L
    }

    private fun normalize(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    companion object {
        const val TYPE_WINDOW_STATE_CHANGED = 32
        const val TYPE_NOTIFICATION_STATE_CHANGED = 64
        const val TYPE_WINDOW_CONTENT_CHANGED = 2_048
        const val TYPE_VIEW_SCROLLED = 4_096
        const val TYPE_WINDOWS_CHANGED = 4_194_304

        fun isRelevantEventType(eventType: Int): Boolean = eventType == TYPE_WINDOW_STATE_CHANGED ||
            eventType == TYPE_NOTIFICATION_STATE_CHANGED ||
            eventType == TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == TYPE_VIEW_SCROLLED ||
            eventType == TYPE_WINDOWS_CHANGED
    }
}

enum class AccessibilityEventMode {
    Fast,
    Discovery,
    Ignore,
}
