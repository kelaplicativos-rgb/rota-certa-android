package br.com.mapeiaia.rotacerta

/**
 * Stage46: immutable visible-surface authority for async OCR/route work plus bounded
 * OCR decontamination/recovery. Ordinary RecyclerView churn in the same visual epoch is
 * not a cancellation boundary; a proven Android window-list transition is.
 */
object FarolVisualEpochNoResultStage46 {
    const val CONTRACT_MARKER = "FAROL_VISUAL_SURFACE_EPOCH_STAGE46"
    const val OCR_SURFACE_MARKER = "OCR_CANNOT_CROSS_VISIBLE_SURFACE_STAGE46"
    const val ROUTE_SURFACE_MARKER = "ROUTE_CANNOT_CROSS_VISIBLE_SURFACE_STAGE46"
    const val SAME_SURFACE_MARKER = "RAW_EVENT_SAME_SURFACE_DOES_NOT_CANCEL_STAGE46"
    const val HARD_BOUNDARY_MARKER = "HARD_WINDOW_BOUNDARY_REVOKES_FINAL_STAGE46"
    const val DUPLICATE_EPOCH_MARKER = "DUPLICATE_CANNOT_CROSS_VISUAL_EPOCH_STAGE46"
    const val UBER_REPLACEMENT_MARKER = "NEW_UBER_OVERLAY_CANNOT_INHERIT_OLD_FINAL_STAGE46"
    const val ORDINARY_CHURN_MARKER = "ORDINARY_CONTENT_CHURN_PRESERVES_STAGE44_STAGE46"
    const val SELF_OVERLAY_MARKER = "SELF_OVERLAY_DECIMAL_EXCLUDED_BEFORE_CLUSTER_STAGE46"
    const val RECONSTRUCTION_MARKER = "OPEN_ADDRESS_PARENTHESIS_CANNOT_CONSUME_DECIMAL_NOISE_STAGE46"
    const val NO_RESULT_MARKER = "NO_RESULT_RECOVERY_USES_LOCAL_NON_OVERLAPPING_ADDRESS_PAIRS_STAGE46"
    const val STAGE21_MARKER = "STAGE21_REVALIDATES_EVERY_RECOVERED_PAIR_STAGE46"
    const val NO_POLLING_MARKER = "NO_POLLING_NO_CONTINUOUS_OCR_STAGE46"

    data class SurfaceToken(
        val packageName: String?,
        val windowId: Int,
        val visualEpoch: Long,
    )
    data class Fragment(val id: String, val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class PairBand(
        val index: Int,
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val addressStarts: Int,
    )
    data class SanitizedText(
        val text: String,
        val removedStandaloneDecimals: Int,
        val syntheticClosures: Int,
    ) {
        val changed: Boolean get() = removedStandaloneDecimals > 0 || syntheticClosures > 0
    }

    private val pureDecimal = Regex("^\\s*\\d{1,2}[,.]\\d{1,2}\\s*$")
    private val streetLead = Regex(
        "(?iu)(?:^|[\\s(])(?:rua|r\\.|avenida|av\\.?|travessa|estrada|rodovia|alameda|praça|praca|largo|viel[a]?|via)\\s+[\\p{L}]",
    )
    private val parenthesizedStreet = Regex(
        "(?iu)\\((?:rua|r\\.|avenida|av\\.?|travessa|estrada|rodovia|alameda|praça|praca|largo|viel[a]?|via)\\s+[\\p{L}]",
    )

    fun normalizePackage(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun captureSurface(rootPackage: String?, eventPackage: String?, windowId: Int, visualEpoch: Long): SurfaceToken =
        SurfaceToken(normalizePackage(rootPackage) ?: normalizePackage(eventPackage), windowId, visualEpoch)

    fun surfaceFresh(token: SurfaceToken, currentRootPackage: String?, currentVisualEpoch: Long): Boolean {
        val expected = normalizePackage(token.packageName) ?: return false
        val current = normalizePackage(currentRootPackage) ?: return false
        // Window id remains provenance. The monotonic visual epoch is the hard freshness boundary.
        return expected == current && token.visualEpoch == currentVisualEpoch
    }

    /**
     * Android TYPE_WINDOWS_CHANGED with a source-less `window-transition:*` signal is stronger than
     * ordinary CONTENT_CHANGED churn. Stage40 must also have admitted a heavy verification cycle;
     * duplicate/coalesced window-list notifications therefore do not repeatedly revoke the same card.
     */
    fun isHardWindowBoundary(
        eventType: Int,
        structuralSignature: String,
        ownOverlay: Boolean,
        heavyCollect: Boolean,
    ): Boolean =
        eventType == 4_194_304 && !ownOverlay && heavyCollect &&
            structuralSignature.trim().startsWith("window-transition:")

    fun shouldDropSelfOverlayDecimal(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        if (!pureDecimal.matches(text)) return false
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val minLeft = (screenWidth * 0.75).toInt()
        val maxTop = (screenHeight * 0.32).toInt()
        val width = (right - left).coerceAtLeast(0)
        val height = (bottom - top).coerceAtLeast(0)
        return left >= minLeft && top in 0..maxTop &&
            width <= (screenWidth * 0.24).toInt() && height <= (screenHeight * 0.08).toInt()
    }

    fun sanitizeForReconstruction(raw: String): SanitizedText {
        if (raw.isBlank()) return SanitizedText(raw, 0, 0)
        val out = ArrayList<String>()
        var removed = 0
        var closures = 0
        var addressParenDepth = 0
        var addressParenActive = false
        raw.lines().forEach { original ->
            val line = original.trim()
            if (pureDecimal.matches(line) && addressParenDepth > 0 && addressParenActive) {
                out += ")"
                removed += 1
                closures += 1
                addressParenDepth = 0
                addressParenActive = false
                return@forEach
            }
            out += original
            val opens = original.count { it == '(' }
            val closes = original.count { it == ')' }
            if (opens > closes && (streetLead.containsMatchIn(original) || parenthesizedStreet.containsMatchIn(original))) {
                addressParenActive = true
            }
            addressParenDepth = (addressParenDepth + opens - closes).coerceAtLeast(0)
            if (addressParenDepth == 0) addressParenActive = false
        }
        return SanitizedText(out.joinToString("\n"), removed, closures)
    }

    fun containsAddressLead(text: String): Boolean = streetLead.containsMatchIn(text) || parenthesizedStreet.containsMatchIn(text)

    /**
     * Non-overlapping top-to-bottom address pairs only. Odd address counts fail closed, avoiding
     * destination-of-card-A -> pickup-of-card-B recovery when OCR missed one side of a card.
     */
    fun buildLocalAddressPairBands(fragments: List<Fragment>): List<PairBand> {
        val ordered = fragments.filter { it.text.isNotBlank() }.sortedWith(compareBy<Fragment> { it.top }.thenBy { it.left })
        val starts = ordered.indices.filter { containsAddressLead(ordered[it].text) }
        if (starts.size < 2 || starts.size % 2 != 0) return emptyList()
        val result = ArrayList<PairBand>(starts.size / 2)
        var p = 0
        while (p + 1 < starts.size) {
            val firstIndex = starts[p]
            val secondIndex = starts[p + 1]
            val nextPairFirst = starts.getOrNull(p + 2) ?: ordered.size
            if (secondIndex <= firstIndex || nextPairFirst <= secondIndex) return emptyList()
            val slice = ordered.subList(firstIndex, nextPairFirst)
            if (slice.isEmpty()) return emptyList()
            val first = ordered[firstIndex]
            val second = ordered[secondIndex]
            val verticalGap = (second.top - first.bottom).coerceAtLeast(0)
            val unionTop = slice.minOf { it.top }
            val unionBottom = slice.maxOf { it.bottom }
            if (verticalGap <= 720 && unionBottom - unionTop <= 1_200) {
                result += PairBand(
                    index = result.size,
                    text = slice.joinToString("\n") { it.text },
                    left = slice.minOf { it.left },
                    top = unionTop,
                    right = slice.maxOf { it.right },
                    bottom = unionBottom,
                    addressStarts = 2,
                )
            }
            p += 2
        }
        return result
    }
}
