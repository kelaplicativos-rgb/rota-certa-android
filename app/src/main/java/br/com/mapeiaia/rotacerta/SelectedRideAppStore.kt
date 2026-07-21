package br.com.mapeiaia.rotacerta

import android.content.Context
import java.util.Locale

/**
 * Persistencia independente dos aplicativos escolhidos pelo usuario.
 *
 * A selecao do aplicativo controla onde a acessibilidade e o OCR podem rodar.
 * Nenhum aplicativo nasce marcado e configuracoes antigas jamais viram selecao
 * automatica. Os modelos de cards sao opcionais e permanecem sob controle do usuario.
 */
object SelectedRideAppStore {
    private const val PREFS_NAME = "rota_certa_selected_ride_apps"
    private const val KEY_PACKAGES = "selected_packages"

    fun hasExplicitSelection(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_PACKAGES)

    fun read(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_PACKAGES, emptySet())
            .orEmpty()
            .mapNotNull(::normalize)
            .toSortedSet()

    /**
     * Mantem a assinatura antiga para compatibilidade, mas nunca importa 99, Uber,
     * inDrive ou pacotes extras das configuracoes legadas. A unica fonte valida e
     * a escolha explicita salva pelo usuario.
     */
    fun selectedPackages(context: Context, legacySettings: AppSettings? = null): Set<String> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredLegacySettings = legacySettings
        return read(context)
    } // manual_selection_no_legacy_fallback_0_1_127

    fun save(context: Context, packages: Set<String>) {
        val normalized = packages.mapNotNull(::normalize).toSortedSet()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, normalized)
            .apply()
    }

    fun legacyPackages(settings: AppSettings): Set<String> = buildSet {
        if (settings.monitor99) add(PACKAGE_99_DRIVER)
        if (settings.monitorUber) add(PACKAGE_UBER_DRIVER)
        if (settings.monitorInDrive) add(PACKAGE_INDRIVE_DRIVER)
        settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .mapNotNull(::normalize)
            .forEach(::add)
    }

    fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }

    const val PACKAGE_99_DRIVER = "com.app99.driver"
    const val PACKAGE_UBER_DRIVER = "com.ubercab.driver"
    const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
}
