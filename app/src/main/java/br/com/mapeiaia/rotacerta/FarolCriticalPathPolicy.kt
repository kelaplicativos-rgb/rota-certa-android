package br.com.mapeiaia.rotacerta

/** Regras puras para manter OCR, histórico e captura fora do caminho crítico. */
object FarolCriticalPathPolicy {
    const val TARGET_RESULT_MILLIS = 850L
    const val OCR_FALLBACK_DELAY_MILLIS = 40L
    const val MATCHED_CAPTURE_DELAY_MILLIS = 450L
    const val CANDIDATE_CAPTURE_DELAY_MILLIS = 700L
    const val CAPTURE_BUSY_RETRY_MILLIS = 60L
    const val CAPTURE_BUSY_RETRIES = 4

    fun shouldSkipOcr(
        screenshotRequestedAtMillis: Long,
        accessibilityAcceptedAtMillis: Long,
    ): Boolean = screenshotRequestedAtMillis > 0L &&
        accessibilityAcceptedAtMillis >= screenshotRequestedAtMillis

    fun canStartDeferredCapture(
        serviceReady: Boolean,
        packageStillSelected: Boolean,
        sameRootPackage: Boolean,
        routeRunning: Boolean,
        normalScreenshotRunning: Boolean,
        automaticCaptureRunning: Boolean,
    ): Boolean = serviceReady &&
        packageStillSelected &&
        sameRootPackage &&
        !routeRunning &&
        !normalScreenshotRunning &&
        !automaticCaptureRunning

    fun elapsedWithinTarget(startedAtMillis: Long, nowMillis: Long): Boolean =
        startedAtMillis > 0L && nowMillis >= startedAtMillis &&
            nowMillis - startedAtMillis <= TARGET_RESULT_MILLIS
}

data class DeferredAutomaticRideCaptureChecklist6(
    val snapshotText: String,
    val packageName: String,
    val fields: RideFields,
    val cardSignature: String,
    val screenHash: Int,
    val generation: Long,
    val kind: AutomaticRideCaptureKind,
    val matchedTemplateId: String? = null,
    val matchedTemplateName: String? = null,
)
