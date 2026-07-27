package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import java.util.Locale

/**
 * Portaria universal do Rota Certa Core.
 * Nenhum pacote instalado e bloqueado antes da leitura. O pacote apenas seleciona
 * o modulo especializado quando conhecido; qualquer outro segue pelo Universal.
 */
object CorePackageMonitor {
    private const val PACKAGE_99_DRIVER = RideCardTemplateMatcher.NINETY_NINE_PACKAGE
    private const val PACKAGE_UBER_DRIVER = RideCardTemplateMatcher.UBER_PACKAGE
    private const val PACKAGE_INDRIVE_DRIVER = RideCardTemplateMatcher.INDRIVE_PACKAGE

    fun classify(
        packageName: String?,
        ownPackageName: String,
        settings: AppSettings,
    ): CorePackageClassification {
        if (!settings.appEnabled) {
            return CorePackageClassification(
                packageName = normalize(packageName),
                kind = CorePackageKind.Disabled,
                module = CoreRideAppModule.Unknown,
                canScan = false,
                reason = "Rota Certa desligado pelo usuario.",
            )
        }

        val normalized = normalize(packageName)
            ?: return CorePackageClassification(
                packageName = null,
                kind = CorePackageKind.Unknown,
                module = CoreRideAppModule.Universal,
                canScan = false,
                reason = "Pacote ainda nao informado pelo Android; nova leitura sera tentada imediatamente.",
            )

        val module = moduleFor(normalized)
        return CorePackageClassification(
            packageName = normalized,
            kind = CorePackageKind.RideApp,
            module = module,
            canScan = true,
            reason = if (module == CoreRideAppModule.Universal) {
                "Leitura universal liberada sem trava de aplicativo: " + normalized + "."
            } else {
                "Leitura liberada no modulo " + module.name + ": " + normalized + "."
            },
        )
    }

    fun selectedRidePackages(settings: AppSettings): Set<String> {
        val packages = mutableSetOf(PACKAGE_99_DRIVER, PACKAGE_UBER_DRIVER, PACKAGE_INDRIVE_DRIVER)
        packages += settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .mapNotNull(::normalize)
        return packages
    }

    fun isPassive(packageName: String?, ownPackageName: String): Boolean = false

    fun normalize(packageName: String?): String? =
        packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }

    private fun moduleFor(packageName: String): CoreRideAppModule = when (packageName) {
        PACKAGE_INDRIVE_DRIVER -> CoreRideAppModule.InDrive
        PACKAGE_99_DRIVER -> CoreRideAppModule.NinetyNine
        PACKAGE_UBER_DRIVER -> CoreRideAppModule.Uber
        else -> CoreRideAppModule.Universal
    }
}

enum class CorePackageKind {
    RideApp,
    Passive,
    OwnApp,
    Ignored,
    NotMonitored,
    Disabled,
    Unknown,
}

enum class CoreRideAppModule {
    InDrive,
    NinetyNine,
    Uber,
    Universal,
    Unknown,
}

data class CorePackageClassification(
    val packageName: String?,
    val kind: CorePackageKind,
    val module: CoreRideAppModule,
    val canScan: Boolean,
    val reason: String,
)
// open_all_apps_all_screens_0_1_94
