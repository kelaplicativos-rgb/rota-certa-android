// Checklist 5 — GPS preciso, bússola, sentido de deslocamento e contagem regressiva.
// Executa depois dos finalizadores históricos e não interfere no leitor dos cards.

fun patchDirectionalAlertServiceChecklist5(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 5.")
    var text = file.readText()

    if ("directional_alert_fields_checklist_5" !in text) {
        val fieldAnchor = "    private lateinit var proximityAlertEngine: ProximityAlertEngine\n"
        if (fieldAnchor !in text) throw GradleException("Motor de proximidade não encontrado para o checklist 5.")
        val fields = """    private lateinit var preciseNavigationTrackerChecklist5: PreciseNavigationTracker
    private lateinit var directionalAlertEngineChecklist5: DirectionalProximityAlertEngine
    private lateinit var directionalAlertOverlayChecklist5: DirectionalAlertOverlayController
    private var missingPreciseFixSinceChecklist5: Long = 0L
    // directional_alert_fields_checklist_5
"""
        text = text.replaceFirst(fieldAnchor, fieldAnchor + fields)
    }

    if ("directional_alert_init_checklist_5" !in text) {
        val initAnchor = "        proximityAlertEngine = ProximityAlertEngine(speechEngine)\n"
        if (initAnchor !in text) throw GradleException("Inicialização de proximidade não encontrada no checklist 5.")
        val init = """        preciseNavigationTrackerChecklist5 = PreciseNavigationTracker(applicationContext)
        directionalAlertEngineChecklist5 = DirectionalProximityAlertEngine(speechEngine)
        directionalAlertOverlayChecklist5 = DirectionalAlertOverlayController(
            context = applicationContext,
            windowManager = requireNotNull(windowManager),
        ) // directional_alert_init_checklist_5
"""
        text = text.replaceFirst(initAnchor, initAnchor + init)
    }

    if ("directional_alert_destroy_checklist_5" !in text) {
        val destroyAnchor = "    override fun onDestroy() {\n"
        if (destroyAnchor !in text) throw GradleException("onDestroy não encontrado para alertas direcionais.")
        val cleanup = """        if (::preciseNavigationTrackerChecklist5.isInitialized) preciseNavigationTrackerChecklist5.stop()
        if (::directionalAlertOverlayChecklist5.isInitialized) directionalAlertOverlayChecklist5.hide()
        // directional_alert_destroy_checklist_5
"""
        text = text.replaceFirst(destroyAnchor, destroyAnchor + cleanup)
    }

    val monitorStart = text.indexOf("    private fun startProximityAlertMonitor() {")
    val scheduleStart = if (monitorStart >= 0) text.indexOf("    private fun scheduleVisibleTextAnalysis", monitorStart) else -1
    if (monitorStart < 0 || scheduleStart < 0) {
        throw GradleException("Bloco do monitor de proximidade não encontrado para substituição direcional.")
    }

    val replacement = """    private fun startProximityAlertMonitor() {
        if (proximityAlertMonitorStarted || !serviceReady) return
        proximityAlertMonitorStarted = true
        scope.launch {
            while (serviceReady) {
                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                val radars = currentImportedRadars
                val hasTargets = alerts.isNotEmpty() || radars.isNotEmpty()
                val enabled = currentSettings.appEnabled && currentSettings.proximityAlertsEnabled

                if (!enabled || !hasTargets) {
                    preciseNavigationTrackerChecklist5.stop()
                    directionalAlertOverlayChecklist5.hide()
                    missingPreciseFixSinceChecklist5 = 0L
                    delay(DIRECTIONAL_ALERT_IDLE_LOOP_MILLIS_CHECKLIST_5)
                    continue
                }

                preciseNavigationTrackerChecklist5.start()
                checkDirectionalProximityAlertsChecklist5(alerts, radars)
                delay(DIRECTIONAL_ALERT_ACTIVE_LOOP_MILLIS_CHECKLIST_5)
            }
        }
    } // directional_alert_monitor_checklist_5

    private fun checkDirectionalProximityAlertsChecklist5(
        alerts: List<SavedPlace>,
        radars: List<ImportedRadar>,
    ) {
        if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled) {
            directionalAlertOverlayChecklist5.hide()
            return
        }

        val now = System.currentTimeMillis()
        val fix = preciseNavigationTrackerChecklist5.currentFix(now)
        if (fix == null) {
            if (missingPreciseFixSinceChecklist5 == 0L) missingPreciseFixSinceChecklist5 = now
            if (now - missingPreciseFixSinceChecklist5 >= PRECISE_FIX_OVERLAY_GRACE_MILLIS_CHECKLIST_5) {
                directionalAlertOverlayChecklist5.hide()
            }
            return
        }
        missingPreciseFixSinceChecklist5 = 0L

        directionalAlertEngineChecklist5.check(
            alerts = alerts,
            radars = radars,
            fix = fix,
            settings = currentSettings,
            onVisual = { visual ->
                if (visual == null) {
                    directionalAlertOverlayChecklist5.hide()
                } else {
                    directionalAlertOverlayChecklist5.showOrUpdate(
                        visual = visual,
                        actions = DirectionalAlertOverlayActions(
                            onEdit = { savedPlaceId ->
                                currentSavedPlaces.firstOrNull { it.id == savedPlaceId }
                                    ?.let(::openSavedPlaceEditor)
                            },
                            onDelete = { savedPlaceId ->
                                scope.launch {
                                    repository.removeSavedPlace(savedPlaceId)
                                    directionalAlertOverlayChecklist5.hide()
                                    toast("Alerta excluído.")
                                }
                            },
                        ),
                    )
                }
            },
        )
    } // directional_alert_check_checklist_5

"""
    text = text.substring(0, monitorStart) + replacement + text.substring(scheduleStart)

    val companionAnchor = "    private companion object {\n"
    if ("DIRECTIONAL_ALERT_ACTIVE_LOOP_MILLIS_CHECKLIST_5" !in text) {
        if (companionAnchor !in text) throw GradleException("Companion do serviço ausente para constantes direcionais.")
        val constants = """        const val DIRECTIONAL_ALERT_ACTIVE_LOOP_MILLIS_CHECKLIST_5 = 500L
        const val DIRECTIONAL_ALERT_IDLE_LOOP_MILLIS_CHECKLIST_5 = 1_500L
        const val PRECISE_FIX_OVERLAY_GRACE_MILLIS_CHECKLIST_5 = 1_800L
"""
        text = text.replaceFirst(companionAnchor, companionAnchor + constants)
    }

    listOf(
        "directional_alert_fields_checklist_5",
        "directional_alert_init_checklist_5",
        "directional_alert_destroy_checklist_5",
        "directional_alert_monitor_checklist_5",
        "directional_alert_check_checklist_5",
        "PreciseNavigationTracker",
        "DirectionalProximityAlertEngine",
        "DirectionalAlertOverlayController",
        "DIRECTIONAL_ALERT_ACTIVE_LOOP_MILLIS_CHECKLIST_5",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato direcional ausente no serviço: $marker")
    }

    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchDirectionalAlertServiceChecklist5(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
