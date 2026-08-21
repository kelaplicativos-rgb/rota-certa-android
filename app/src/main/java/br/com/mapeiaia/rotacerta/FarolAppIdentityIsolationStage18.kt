package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Stage 18 isolates app identity before any selected root is allowed to override
 * an accessibility event. Selected apps authorize reading; they do not authorize
 * cross-app reuse of roots, sessions, cards, OCR, cache entries or route results.
 */
object FarolAppIdentityIsolationStage18 {
    const val CONTRACT_MARKER = "EXPLICIT_SELECTED_APP_IDENTITY_ISOLATION_STAGE18"

    enum class Outcome {
        EXPLICIT_SELECTED_APP_MATCH,
        PASSIVE_EVENT_VISIBLE_SELECTED_APP,
        FAIL_CLOSED_CROSS_APP_ROOT,
        FAIL_CLOSED_EXPLICIT_APP_WITHOUT_COMPATIBLE_ROOT,
        NO_SELECTED_VISUAL_AUTHORITY,
    }

    data class Resolution(
        val outcome: Outcome,
        val authorityPackageName: String?,
        val explicitSelectedPackageName: String?,
        val allowVisibleRootOverride: Boolean,
        val failClosed: Boolean,
        val confirmedAppSwitch: Boolean,
        val preserveCurrentSession: Boolean,
    )

    data class IdentityBinding(
        val packageName: String,
        val sessionGeneration: Long,
        val windowId: Int,
        val screenGeneration: Long,
        val windowGeneration: Long,
        val screenHash: Int,
        val addressSignature: String,
    )

    fun resolve(
        eventPackageName: String?,
        visibleSelectedPackageName: String?,
        selectedPackages: Set<String>,
        activeSessionPackageName: String?,
    ): Resolution {
        val selected = selectedPackages.mapNotNull(::normalizePackage).toSet()
        val event = normalizePackage(eventPackageName)
        val visible = normalizePackage(visibleSelectedPackageName)?.takeIf { it in selected }
        val active = normalizePackage(activeSessionPackageName)
        val explicitSelected = event?.takeIf { it in selected }

        if (explicitSelected != null) {
            if (visible == null) {
                return Resolution(
                    outcome = Outcome.FAIL_CLOSED_EXPLICIT_APP_WITHOUT_COMPATIBLE_ROOT,
                    authorityPackageName = null,
                    explicitSelectedPackageName = explicitSelected,
                    allowVisibleRootOverride = false,
                    failClosed = true,
                    confirmedAppSwitch = false,
                    preserveCurrentSession = true,
                )
            }
            if (visible != explicitSelected) {
                return Resolution(
                    outcome = Outcome.FAIL_CLOSED_CROSS_APP_ROOT,
                    authorityPackageName = null,
                    explicitSelectedPackageName = explicitSelected,
                    allowVisibleRootOverride = false,
                    failClosed = true,
                    confirmedAppSwitch = false,
                    preserveCurrentSession = true,
                )
            }
            return Resolution(
                outcome = Outcome.EXPLICIT_SELECTED_APP_MATCH,
                authorityPackageName = explicitSelected,
                explicitSelectedPackageName = explicitSelected,
                allowVisibleRootOverride = true,
                failClosed = false,
                confirmedAppSwitch = active != null && active != explicitSelected,
                preserveCurrentSession = false,
            )
        }

        if (visible != null) {
            return Resolution(
                outcome = Outcome.PASSIVE_EVENT_VISIBLE_SELECTED_APP,
                authorityPackageName = visible,
                explicitSelectedPackageName = null,
                allowVisibleRootOverride = true,
                failClosed = false,
                confirmedAppSwitch = active != null && active != visible,
                preserveCurrentSession = false,
            )
        }

        return Resolution(
            outcome = Outcome.NO_SELECTED_VISUAL_AUTHORITY,
            authorityPackageName = null,
            explicitSelectedPackageName = null,
            allowVisibleRootOverride = false,
            failClosed = false,
            confirmedAppSwitch = false,
            preserveCurrentSession = true,
        )
    }

    fun bindingMatchesCurrent(bound: IdentityBinding, current: IdentityBinding): Boolean =
        normalizePackage(bound.packageName) == normalizePackage(current.packageName) &&
            bound.sessionGeneration == current.sessionGeneration &&
            bound.windowId == current.windowId &&
            bound.screenGeneration == current.screenGeneration &&
            bound.windowGeneration == current.windowGeneration &&
            bound.screenHash == current.screenHash &&
            bound.addressSignature == current.addressSignature

    fun blocksBelongToSingleAuthority(authorityPackageName: String, blockPackages: List<String>): Boolean {
        val authority = normalizePackage(authorityPackageName) ?: return false
        if (blockPackages.isEmpty()) return false
        return blockPackages.all { normalizePackage(it) == authority }
    }

    private fun normalizePackage(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
