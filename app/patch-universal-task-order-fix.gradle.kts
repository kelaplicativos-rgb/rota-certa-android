// Ordem final: patches legados -> universal V2 -> compile fix -> compilacao/testes.
// Na segunda chamada do Gradle no mesmo workflow, o codigo universal ja esta gravado.
// Nesse caso, os patches legados devem ser ignorados para nao tentarem transformar novamente o arquivo.

val universalServiceSource = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
val universalInstalledMarker = "universal_last_address_process_v2_0_1_95"
val universalTasks = setOf(
    "universalLastAddressFinalPatch",
    "universalLastAddressFinalV2",
    "universalLastAddressCompileFix",
)
val legacyMutationTasks = setOf(
    "inDriveCardContractMatch",
    "liveRideWindowEventGuard",
    "keepDecisionDuringTransientText",
    "globalLightDiagnostics",
    "hardClearUnregisteredCardDecision",
    "modularLiveBubbleCore",
    "noStickyDecisionCleanup",
    "liveResultFreshnessGuard",
    "rotaCertaCoreGate",
    "giguInspiredLiveReaderPatch",
    "giguCoreClassificationPatch",
    "hideMonitoredAppsCard",
    "liveCardRouteLink",
    "persistLiveEventTrace",
    "persistentBubbleStateTrace",
)

tasks.configureEach {
    val legacyMutation = name !in universalTasks && (
        name.contains("patch", ignoreCase = true) ||
            name.contains("fix", ignoreCase = true) ||
            name.startsWith("enforce", ignoreCase = true) ||
            name in legacyMutationTasks
        )
    if (legacyMutation) {
        onlyIf {
            val source = universalServiceSource.asFile
            !source.exists() || universalInstalledMarker !in source.readText()
        }
    }
}

tasks.named("universalLastAddressFinalV2").configure {
    setMustRunAfter(
        tasks.matching { task ->
            task.name !in setOf(
                name,
                "universalLastAddressFinalPatch",
                "universalLastAddressCompileFix",
                "universalLastAddressFinalV2",
            ) &&
                !task.name.startsWith("compile") &&
                !task.name.startsWith("test") &&
                task.name !in setOf("preBuild", "assemble", "assembleDebug") &&
                (task.name.contains("patch", true) || task.name.contains("fix", true) || task.name.startsWith("enforce", true))
        },
    )
}

tasks.named("universalLastAddressCompileFix").configure {
    setMustRunAfter(listOf("universalLastAddressFinalV2"))
}
