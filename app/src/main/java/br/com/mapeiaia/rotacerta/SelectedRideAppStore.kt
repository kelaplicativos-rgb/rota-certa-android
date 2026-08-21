package br.com.mapeiaia.rotacerta

import android.content.Context
import java.util.Locale

/** Unica fonte de autorizacao dos aplicativos lidos pelo Rota Certa. */
object SelectedRideAppStore {
    private const val PREFS_NAME = "rota_certa_selected_ride_apps"
    private const val KEY_PACKAGES = "selected_packages"

    fun hasExplicitSelection(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_PACKAGES)

    fun read(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().mapNotNull(::normalize).toSet()
        val sanitized = DriverAppPackagePolicy0162.sanitize(raw, context.packageName)
        if (raw != sanitized) prefs.edit().putStringSet(KEY_PACKAGES, sanitized).apply()
        return sanitized
    }

    fun selectedPackages(context: Context, legacySettings: AppSettings? = null): Set<String> {
        @Suppress("UNUSED_VARIABLE") val ignored = legacySettings
        return read(context)
    }

    fun save(context: Context, packages: Set<String>) {
        val normalized = DriverAppPackagePolicy0162.sanitize(packages, context.packageName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, normalized)
            .apply()
    }

    fun add(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        if (!DriverAppPackagePolicy0162.isEligible(normalized, context.packageName)) return
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
