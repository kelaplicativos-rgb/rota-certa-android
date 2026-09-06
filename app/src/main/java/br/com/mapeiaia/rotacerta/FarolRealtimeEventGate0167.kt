package br.com.mapeiaia.rotacerta

/**
 * Admissor determinístico do caminho crítico do farol.
 *
 * O primeiro evento útil passa imediatamente. Rajadas idênticas da mesma origem,
 * janela, tipo e classe são confluídas por uma janela curta. Trocas de janela e
 * de estado nunca esperam, preservando a limpeza e a troca de card instantâneas.
 */
class FarolRealtimeEventGate0167(
    private val duplicateWindowMillis: Long = 72L,
) {
    private var lastKey: EventKey? = null
    private var lastAcceptedElapsedMillis: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldCollect(
        selectedPackageName: String,
        sourcePackageName: String?,
        windowId: Int,
        eventType: Int,
        eventClassName: String?,
        nowElapsedMillis: Long,
    ): Boolean {
        val key = EventKey(
            selectedPackageName = selectedPackageName.normalized(),
            sourcePackageName = sourcePackageName.normalized(),
            windowId = windowId,
            eventType = eventType,
            eventClassName = eventClassName.orEmpty(),
        )
        val urgent = eventType == AccessibilityEventFloodGate.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEventFloodGate.TYPE_WINDOWS_CHANGED
        val elapsed = if (lastAcceptedElapsedMillis == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            nowElapsedMillis - lastAcceptedElapsedMillis
        }
        val duplicateBurst = !urgent &&
            key == lastKey &&
            elapsed >= 0L &&
            elapsed < duplicateWindowMillis.coerceAtLeast(0L)
        if (duplicateBurst) return false
        lastKey = key
        lastAcceptedElapsedMillis = nowElapsedMillis
        return true
    }

    @Synchronized
    fun reset() {
        lastKey = null
        lastAcceptedElapsedMillis = Long.MIN_VALUE
    }

    private fun String?.normalized(): String = this?.trim()?.lowercase().orEmpty()

    private data class EventKey(
        val selectedPackageName: String,
        val sourcePackageName: String,
        val windowId: Int,
        val eventType: Int,
        val eventClassName: String,
    )
}
