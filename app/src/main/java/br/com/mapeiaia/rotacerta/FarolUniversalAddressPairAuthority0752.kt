package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * 0.1.752 — explicit user-requested universal pair authority.
 *
 * A selected-driver-app list is no longer required when the shared visual reader
 * proves two or more positive addresses on one external application window.
 * Package/window are still provenance and stale-work isolation boundaries.
 */
object FarolUniversalAddressPairAuthority0752 {
    const val CONTRACT_MARKER = "FAROL_UNIVERSAL_ADDRESS_PAIR_AUTHORITY_0752"
    const val ANY_APP_PAIR_MARKER = "ANY_APP_TWO_OR_MORE_ADDRESSES_0752"
    const val LAST_ADDRESS_ROUTE_MARKER = "ANY_APP_LAST_ADDRESS_ROUTE_TRIGGER_0752"
    const val VISUAL_RECOVERY_REQUESTED_MARKER = "FAROL_SHARED_VISUAL_READER_REQUESTED_0752"
    const val VISUAL_RECOVERY_RESULT_MARKER = "FAROL_SHARED_VISUAL_READER_RESULT_0752"
    const val VISUAL_RECOVERY_FAILED_MARKER = "FAROL_SHARED_VISUAL_READER_FAILED_0752"
    const val ROOTLESS_RECOVERY_MARKER = "AUTHORIZED_ROOT_MISS_SHARED_VISUAL_RECOVERY_0752"

    fun isEligibleExternalSurface(packageName: String?, ownPackageName: String): Boolean {
        val pkg = packageName?.trim()?.lowercase(Locale.ROOT)
        if (pkg.isNullOrBlank()) return true
        val own = ownPackageName.trim().lowercase(Locale.ROOT)
        if (pkg == own) return false
        if (pkg == "com.android.systemui") return false
        if (pkg.contains("launcher")) return false
        return true
    }

    fun authorize(addresses: List<String>, packageName: String?, ownPackageName: String): Boolean =
        addresses.distinctBy(::canonical).size >= 2 &&
            isEligibleExternalSurface(packageName, ownPackageName)

    fun evaluation(
        windowId: Int,
        addresses: List<String>,
    ): FarolUniversalVisualPipelineStage19.Evaluation? {
        val ordered = addresses
            .map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy(::canonical)
        if (windowId < 0 || ordered.size < 2) return null
        val pickup = ordered.first()
        val destination = ordered.last()
        val signature = DestinationAddressIdentityPolicy.signature("visual0752", destination)
        val identity = "stage752-pair:$windowId:" +
            ordered.joinToString("|") { canonical(it) }
        return FarolUniversalVisualPipelineStage19.Evaluation(
            windowId = windowId,
            blockId = identity,
            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
            analysisText = ordered.joinToString("\n"),
            addresses = ordered,
            pickup = pickup,
            destination = destination,
            addressSignature = signature,
            screenHash = "$identity|$signature".hashCode(),
        )
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
