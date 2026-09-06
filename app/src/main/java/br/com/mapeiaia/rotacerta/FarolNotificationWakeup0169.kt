package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Porta de entrada limitada para cards que aparecem como sobreposição de notificação.
 *
 * Não autoriza verde/vermelho. Apenas permite um pequeno conjunto de capturas OCR
 * quando o Android informa uma notificação de um pacote escolhido pelo usuário.
 */
data class FarolNotificationWakeToken0169(
    val packageName: String,
    val generation: Long,
    val startedAtElapsedMillis: Long,
)

class FarolNotificationWakeGate0169(
    private val duplicateWindowMillis: Long = 250L,
    private val tokenTtlMillis: Long = 12_000L,
    private val maxCaptures: Int = 4,
) {
    private var generation: Long = 0L
    private var activeToken: FarolNotificationWakeToken0169? = null
    private var captureCount: Int = 0
    private var lastAcceptedPackage: String? = null
    private var lastAcceptedAtElapsedMillis: Long = Long.MIN_VALUE

    fun begin(
        eventType: Int,
        eventPackageName: String?,
        selectedPackages: Set<String>,
        ownPackageName: String,
        workModeEnabled: Boolean,
        liveReadingEnabled: Boolean,
        serviceReady: Boolean,
        bubbleGestureActive: Boolean,
        nowElapsedMillis: Long,
    ): FarolNotificationWakeToken0169? {
        if (eventType != TYPE_NOTIFICATION_STATE_CHANGED || !workModeEnabled ||
            !liveReadingEnabled || !serviceReady || bubbleGestureActive
        ) return null
        val normalized = normalize(eventPackageName) ?: return null
        val own = normalize(ownPackageName)
        val selected = selectedPackages.mapNotNull(::normalize).toSet()
        if (normalized == own || normalized !in selected) return null
        val duplicate = normalized == lastAcceptedPackage &&
            nowElapsedMillis >= lastAcceptedAtElapsedMillis &&
            nowElapsedMillis - lastAcceptedAtElapsedMillis < duplicateWindowMillis
        if (duplicate) return null

        generation += 1L
        captureCount = 0
        lastAcceptedPackage = normalized
        lastAcceptedAtElapsedMillis = nowElapsedMillis
        return FarolNotificationWakeToken0169(
            packageName = normalized,
            generation = generation,
            startedAtElapsedMillis = nowElapsedMillis,
        ).also { activeToken = it }
    }

    fun reserveCapture(
        token: FarolNotificationWakeToken0169,
        nowElapsedMillis: Long,
    ): Int? {
        if (!isCurrent(token, nowElapsedMillis) || captureCount >= maxCaptures) return null
        return captureCount++
    }

    fun isCurrent(
        token: FarolNotificationWakeToken0169,
        nowElapsedMillis: Long,
    ): Boolean {
        val current = activeToken ?: return false
        if (current != token) return false
        val age = nowElapsedMillis - token.startedAtElapsedMillis
        if (age < 0L || age > tokenTtlMillis) {
            invalidate(token)
            return false
        }
        return true
    }

    fun shouldDeferPassiveRejection(
        eventPackageName: String?,
        rootPackageName: String?,
        ownPackageName: String,
        nowElapsedMillis: Long,
    ): Boolean {
        val token = activeToken ?: return false
        if (!isCurrent(token, nowElapsedMillis)) return false
        val eventPackage = normalize(eventPackageName)
        val rootPackage = normalize(rootPackageName)
        val ownPackage = normalize(ownPackageName)
        return listOf(eventPackage, rootPackage).all { value ->
            value == null || value == ownPackage || isPassiveOrSystem(value)
        }
    }

    fun invalidate(token: FarolNotificationWakeToken0169? = null) {
        if (token != null && activeToken != token) return
        activeToken = null
        captureCount = 0
    }

    private fun isPassiveOrSystem(packageName: String): Boolean =
        packageName.contains("launcher") ||
            packageName == "com.android.systemui" ||
            packageName == "com.samsung.android.app.smartcapture" ||
            packageName == "com.google.android.packageinstaller" ||
            packageName == "com.google.android.apps.nbu.files"

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)

    companion object {
        const val TYPE_NOTIFICATION_STATE_CHANGED: Int = 64
    }
}
