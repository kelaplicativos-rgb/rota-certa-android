package br.com.mapeiaia.rotacerta

import android.content.Context
import java.util.Locale

/**
 * Única fonte de autorização dos aplicativos lidos pelo Rota Certa.
 * A lista nasce vazia e somente é alterada por ação explícita do usuário.
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

    fun selectedPackages(context: Context, legacySettings: AppSettings? = null): Set<String> {
        @Suppress("UNUSED_VARIABLE") val ignored = legacySettings
        return read(context)
    }

    fun save(context: Context, packages: Set<String>) {
        val normalized = packages.mapNotNull(::normalize).toSortedSet()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, normalized)
            .apply()
    }

    fun add(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        save(context, read(context) + normalized)
    }

    fun remove(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        save(context, read(context) - normalized)
    }

    fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
