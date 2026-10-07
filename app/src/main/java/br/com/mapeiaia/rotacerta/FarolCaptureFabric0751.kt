package br.com.mapeiaia.rotacerta

/**
 * 0.1.751 — deterministic capture fabric and latency budgets for the FAROL hot path.
 *
 * The package is provenance/isolation, the visible card remains semantic authority.
 * Empty Accessibility is never a terminal state: it immediately escalates to OCR of the
 * concrete application window when Android exposes a window id.
 */
object FarolCaptureFabric0751 {
    const val CONTRACT_MARKER = "FAROL_CAPTURE_FABRIC_0751"
    const val INCLUDE_NOT_IMPORTANT_VIEWS_MARKER = "ACCESSIBILITY_INCLUDE_NOT_IMPORTANT_VIEWS_0751"
    const val EMPTY_ACCESSIBILITY_OCR_MARKER = "FAROL_EMPTY_ACCESSIBILITY_WINDOW_OCR_0751"
    const val SCREENSHOT_DISPATCH_MARKER = "FAROL_WINDOW_SCREENSHOT_DISPATCH_0751"
    const val SCREENSHOT_DISPATCH_FAILED_MARKER = "FAROL_WINDOW_SCREENSHOT_DISPATCH_FAILED_0751"
    const val SELECTED_WINDOW_RECOVERED_MARKER = "FAROL_SELECTED_WINDOW_RECOVERED_0751"
    const val GEOCODE_HEDGE_STARTED_MARKER = "FAROL_GEOCODE_HEDGE_STARTED_0751"
    const val GEOCODE_HEDGE_WON_MARKER = "FAROL_GEOCODE_HEDGE_WON_0751"
    const val GEOCODE_HEDGE_FAILED_MARKER = "FAROL_GEOCODE_HEDGE_FAILED_0751"

    const val WINDOW_SCREENSHOT_MIN_API = 34
    const val OFFLINE_GEOCODE_BUDGET_MS = 180L
    const val HEDGED_GEOCODE_DEADLINE_MS = 1_250L

    fun shouldEscalateEmptyAccessibility(selectedRideApp: Boolean, sourceText: String): Boolean =
        selectedRideApp && sourceText.isBlank()

    fun shouldUseWindowScreenshot(apiLevel: Int, windowId: Int?): Boolean =
        apiLevel >= WINDOW_SCREENSHOT_MIN_API && (windowId ?: 0) > 0
}
