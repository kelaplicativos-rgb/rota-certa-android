package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import java.util.Locale

object CorePackageMonitor {
    fun classify(packageName: String?, ownPackageName: String, settings: AppSettings): CorePackageClassification {
        if (!settings.appEnabled || !settings.liveReadingEnabled) {
            return CorePackageClassification(normalize(packageName), CorePackageKind.Disabled, false, "Leitura ao vivo desligada pelo usuário.")
        }
        val normalized = normalize(packageName)
            ?: return CorePackageClassification(null, CorePackageKind.Unknown, false, "Pacote não informado pelo Android.")
        if (normalized == normalize(ownPackageName)) {
            return CorePackageClassification(normalized, CorePackageKind.OwnApp, false, "Tela do próprio Rota Certa.")
        }
        val allowed = normalized in selectedRidePackages(settings)
        return CorePackageClassification(
            normalized,
            if (allowed) CorePackageKind.SelectedApp else CorePackageKind.NotSelected,
            allowed,
            if (allowed) "Aplicativo selecionado manualmente: $normalized." else "Aplicativo não selecionado pelo usuário: $normalized.",
        )
    }

    fun selectedRidePackages(settings: AppSettings): Set<String> = settings.extraMonitoredPackages
        .split(Regex("""[,;\s]+"""))
        .mapNotNull(::normalize)
        .toSet()

    fun isPassive(packageName: String?, ownPackageName: String): Boolean = normalize(packageName) == normalize(ownPackageName)
    fun normalize(packageName: String?): String? = packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
}

enum class CorePackageKind { SelectedApp, OwnApp, NotSelected, Disabled, Unknown }
data class CorePackageClassification(val packageName: String?, val kind: CorePackageKind, val canScan: Boolean, val reason: String)
