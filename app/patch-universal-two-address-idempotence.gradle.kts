// Garante que a segunda chamada do Gradle no mesmo checkout apenas compile o
// fonte final ja gerado. Nenhum patch legado pode reescrever o leitor universal
// depois que o marcador do contrato de dois enderecos estiver presente.

val universalTwoAddressSource = layout.projectDirectory.file(
    "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
)
val universalTwoAddressInstalledMarker = "universal_two_address_process_0_1_98"

fun universalTwoAddressAlreadyInstalled(): Boolean {
    val file = universalTwoAddressSource.asFile
    return file.exists() && universalTwoAddressInstalledMarker in file.readText()
}

// O patch final precisa rodar somente uma vez por checkout. Na segunda chamada
// do Gradle, o arquivo ja esta pronto e deve seguir diretamente para compilacao.
tasks.named("universalTwoAddressRuntimeFinal").configure {
    onlyIf { !universalTwoAddressAlreadyInstalled() }
}

val explicitLegacyUniversalMutationTasks = setOf(
    "inDriveCardContractMatch",
    "liveRideWindowEventGuard",
    "keepDecisionDuringTransientText",
    "globalLightDiagnostics",
    "hardClearUnregisteredCardDecision",
    "modularLiveBubbleCore",
    "noStickyDecisionCleanup",
    "liveResultFreshnessGuard",
    "rotaCertaCoreGate",
    "hideMonitoredAppsCard",
    "liveCardRouteLink",
    "persistLiveEventTrace",
    "persistentBubbleStateTrace",
    "giguInspiredLiveReaderPatch",
    "giguCoreClassificationPatch",
    "coreScreenReadEngineInlinePatch",
    "coreVisibleCardLifecyclePatch",
    "coreLiveAnalysisPipelinePatch",
    "passiveEventCompileFix",
    "mainBubbleTapMenuContract",
    "functionalBubbleTogglesFinal",
    "functionalBubbleIdempotenceFinal",
    "inAppBubbleHomeFinal",
    "universalLastAddressFinalPatch",
    "universalLastAddressFinalV2",
    "universalLastAddressCompileFix",
)

tasks.configureEach {
    val isCompilationOrLifecycleTask =
        name.startsWith("compile") ||
            name.startsWith("test") ||
            name in setOf("preBuild", "assemble", "assembleDebug", "universalTwoAddressRuntimeFinal")

    val mutatesGeneratedAndroidSource = !isCompilationOrLifecycleTask && (
        name in explicitLegacyUniversalMutationTasks ||
            name.contains("patch", ignoreCase = true) ||
            name.contains("fix", ignoreCase = true) ||
            name.contains("final", ignoreCase = true) ||
            name.startsWith("enforce", ignoreCase = true)
        )

    if (mutatesGeneratedAndroidSource) {
        onlyIf { !universalTwoAddressAlreadyInstalled() }
    }
}
