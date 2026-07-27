package br.com.mapeiaia.rotacerta

/** Pure timing rules kept outside the farol decision path. */
object FarolCriticalPathPolicy {
    const val TARGET_RESULT_MILLIS = 850L
    const val OCR_FALLBACK_DELAY_MILLIS = 40L

    fun shouldSkipOcr(screenshotRequestedAtMillis: Long, accessibilityAcceptedAtMillis: Long): Boolean =
        screenshotRequestedAtMillis > 0L && accessibilityAcceptedAtMillis >= screenshotRequestedAtMillis

    fun elapsedWithinTarget(startedAtMillis: Long, nowMillis: Long): Boolean =
        startedAtMillis > 0L && nowMillis >= startedAtMillis && nowMillis - startedAtMillis <= TARGET_RESULT_MILLIS
}
