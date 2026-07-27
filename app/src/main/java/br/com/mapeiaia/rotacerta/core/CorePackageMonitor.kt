package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import java.util.Locale

/**
 * Classificação genérica. Nenhum fabricante ou aplicativo é conhecido previamente.
 * O pacote só é elegível quando aparece na seleção persistida pelo usuário.
 */
object CorePackageMonitor {
    fun classify(
        packageName: String?,
        ownPackageName: String,
        settings: AppSettings,
    ): CorePackageClassification {
        if (!settings.appEnabled || !settings.liveReadingEnabled) {
            return CorePackageClassification(
                packageName = normalize(packageName),
                kind = CorePackageKind.Disabled,
                module = CoreRideAppModule.Manual,
                canScan = false,
                reason = "Leitura ao vivo desligada pelo usuário.",
            )
        }
        val normalized = normalize(packageName)
            ?: return CorePackageClassification(null, CorePackageKind.Unknown, CoreRideAppModule.Manual, false, "Pacote não informado pelo Android.")
        if (normalized == normalize(ownPackageName)) {
            return CorePackageClassification(normalized, CorePackageKind.OwnApp, CoreRideAppModule.Manual, false, "Tela do próprio Rota Certa.")
        }
        val selected = selectedRidePackages(settings)
        val allowed = normalized in selected
        return CorePackageClassification(
            packageName = normalized,
            kind = if (allowed) CorePackageKind.RideApp else CorePackageKind.NotMonitored,
            module = CoreRideAppModule.Manual,
            canScan = allowed,
            reason = if (allowed) "Aplicativo selecionado manualmente: $normalized." else "Aplicativo não selecionado pelo usuário: $normalized.",
        )
    }

    /** Compatibilidade temporária: nunca acrescenta pacotes predefinidos. */
    fun selectedRidePackages(settings: AppSettings): Set<String> =
        settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .mapNotNull(::normalize)
            .toSet()

    fun isPassive(packageName: String?, ownPackageName: String): Boolean =
        normalize(packageName) == normalize(ownPackageName)

    fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}

enum class CorePackageKind { RideApp, Passive, OwnApp, Ignored, NotMonitored, Disabled, Unknown }
enum class CoreRideAppModule { Manual, Universal, Unknown, InDrive, NinetyNine, Uber }

data class CorePackageClassification(
    val packageName: String?,
    val kind: CorePackageKind,
    val module: CoreRideAppModule,
    val canScan: Boolean,
    val reason: String,
)
