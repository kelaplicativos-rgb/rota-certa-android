package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import java.util.Locale

/**
 * Portaria de pacotes do Rota Certa Core.
 * Nenhum modulo de card/rota/bolinha deve decidir sozinho se pode ler um app.
 */
object CorePackageMonitor {
    private const val PACKAGE_99_DRIVER = RideCardTemplateMatcher.NINETY_NINE_PACKAGE
    private const val PACKAGE_UBER_DRIVER = RideCardTemplateMatcher.UBER_PACKAGE
    private const val PACKAGE_INDRIVE_DRIVER = RideCardTemplateMatcher.INDRIVE_PACKAGE

    private val ignoredPackages = setOf(
        "android",
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.openai.chatgpt",
        "com.samsung.android.app.settings",
        "com.samsung.android.honeyboard",
    )

    private val passivePackages = setOf(
        "com.android.launcher",
        "com.android.systemui",
        "com.google.android.apps.maps",
        "com.waze",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.inputmethod.latin",
        "com.openai.chatgpt",
        "com.sec.android.app.launcher",
        "com.android.settings",
        "com.samsung.android.app.settings",
        "com.samsung.android.honeyboard",
        "com.samsung.android.app.smartcapture",
        "com.samsung.android.capture",
    )

    fun classify(
        packageName: String?,
        ownPackageName: String,
        settings: AppSettings,
    ): CorePackageClassification {
        val normalized = normalize(packageName)
            ?: return CorePackageClassification(
                packageName = null,
                kind = CorePackageKind.Unknown,
                module = CoreRideAppModule.Unknown,
                canScan = false,
                reason = "Pacote ativo nao informado pelo Android.",
            )

        if (!settings.appEnabled) {
            return CorePackageClassification(
                packageName = normalized,
                kind = CorePackageKind.Disabled,
                module = CoreRideAppModule.Unknown,
                canScan = false,
                reason = "Rota Certa desligado pelo usuario.",
            )
        }
        if (normalized == normalize(ownPackageName)) {
            return CorePackageClassification(
                packageName = normalized,
                kind = CorePackageKind.OwnApp,
                module = CoreRideAppModule.Unknown,
                canScan = false,
                reason = "Rota Certa esta em primeiro plano; leitura pausada.",
            )
        }
        if (normalized in passivePackages) {
            return CorePackageClassification(
                packageName = normalized,
                kind = CorePackageKind.Passive,
                module = CoreRideAppModule.Unknown,
                canScan = false,
                reason = "Pacote passivo ignorado; bolinha limpa: $normalized.",
            )
        }
        if (normalized in ignoredPackages) {
            return CorePackageClassification(
                packageName = normalized,
                kind = CorePackageKind.Ignored,
                module = CoreRideAppModule.Unknown,
                canScan = false,
                reason = "Pacote ignorado para evitar leitura fora do card: $normalized.",
            )
        }

        val selected = selectedRidePackages(settings)
        if (normalized in selected) {
            return CorePackageClassification(
                packageName = normalized,
                kind = CorePackageKind.RideApp,
                module = moduleFor(normalized),
                canScan = true,
                reason = "Pacote permitido pelo Core: $normalized.",
            )
        }

        return CorePackageClassification(
            packageName = normalized,
            kind = CorePackageKind.NotMonitored,
            module = CoreRideAppModule.Unknown,
            canScan = false,
            reason = "Pacote fora dos apps monitorados; bolinha em espera: $normalized.",
        )
    }

    fun selectedRidePackages(settings: AppSettings): Set<String> {
        val packages = mutableSetOf<String>()
        if (settings.monitor99) packages += PACKAGE_99_DRIVER
        if (settings.monitorUber) packages += PACKAGE_UBER_DRIVER
        if (settings.monitorInDrive) packages += PACKAGE_INDRIVE_DRIVER
        packages += settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .mapNotNull(::normalize)
        return packages
    }

    fun isPassive(packageName: String?, ownPackageName: String): Boolean {
        val normalized = normalize(packageName) ?: return true
        return normalized == normalize(ownPackageName) || normalized in passivePackages
    }

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
