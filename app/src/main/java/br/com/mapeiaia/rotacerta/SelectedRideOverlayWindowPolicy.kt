package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Algumas telas selecionadas aparecem em uma janela Android transitória. Durante
 * poucos segundos, preservamos o último aplicativo escolhido pelo usuário para
 * que essa janela não faça a bolinha voltar para cinza antes do OCR.
 */
object SelectedRideOverlayWindowPolicy {
    const val GRACE_MILLIS = 6_000L

    private val transientPackages = setOf(
        "android",
        "com.android.systemui",
        "com.samsung.android.systemui",
        "com.samsung.android.app.smartcapture",
        "com.samsung.android.capture",
    )

    fun resolve(
        rootPackageName: String?,
        lastSelectedPackageName: String?,
        lastSelectedAtMillis: Long,
        selectedPackages: Set<String>,
        nowMillis: Long,
    ): String? {
        val root = normalize(rootPackageName)
        val selected = selectedPackages.mapNotNull(::normalize).toSet()
        if (root != null && root in selected) return root

        val remembered = normalize(lastSelectedPackageName) ?: return null
        val fresh = nowMillis >= lastSelectedAtMillis &&
            nowMillis - lastSelectedAtMillis <= GRACE_MILLIS
        return remembered.takeIf {
            it in selected && fresh && (root == null || root in transientPackages)
        }
    }

    fun isTransient(packageName: String?): Boolean = normalize(packageName) in transientPackages

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
