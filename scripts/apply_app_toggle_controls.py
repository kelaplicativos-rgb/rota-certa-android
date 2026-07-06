from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


models_path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
models = models_path.read_text()
models = replace_once(
    models,
    '''    val extraMonitoredPackages: String = "",
    val requireRegisteredRideCard: Boolean = true,
    val proximityAlertDistanceMeters: Int = 200,''',
    '''    val extraMonitoredPackages: String = "",
    val appEnabled: Boolean = true,
    val requireRegisteredRideCard: Boolean = true,
    val proximityAlertsEnabled: Boolean = true,
    val proximityAlertDistanceMeters: Int = 200,''',
    "AppSettings toggles",
)
models_path.write_text(models)

main_path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
main = main_path.read_text()
main = replace_once(
    main,
    '''    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Configuracoes", fontWeight = FontWeight.Bold)
        AlwaysLocationPermissionCard(''',
    '''    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Configuracoes", fontWeight = FontWeight.Bold)
        SystemControlCard(settings = draft, onChange = ::saveDraft)
        AlwaysLocationPermissionCard(''',
    "settings system control card insertion",
)
main = replace_once(
    main,
    '''@Composable
private fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {''',
    '''@Composable
private fun SystemControlCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    ExpandableCard(title = "Controle geral", initiallyExpanded = true) {
        SettingsSwitchRow(
            label = "Rota Certa ligado",
            checked = settings.appEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(appEnabled = enabled)) },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Falar radares e proximidade", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.appEnabled && settings.proximityAlertsEnabled,
                enabled = settings.appEnabled,
                onCheckedChange = { enabled -> onChange(settings.copy(proximityAlertsEnabled = enabled)) },
            )
        }
        Text(
            if (settings.appEnabled) {
                "Desligue apenas quando quiser pausar leitura ao vivo e avisos. A bolinha fica em espera."
            } else {
                "Rota Certa esta pausado: leitura ao vivo e avisos de proximidade ficam desligados."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {''',
    "SystemControlCard composable",
)
main_path.write_text(main)

service_path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
service = service_path.read_text()
service = replace_once(
    service,
    '''    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        val eventPackageName = normalizePackageName(event.packageName?.toString())''',
    '''    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled) {
            if (currentRadarColor != RadarColor.Idle) resetToIdle("Rota Certa desligado pelo usuario.", record = false)
            return
        }
        val eventPackageName = normalizePackageName(event.packageName?.toString())''',
    "accessibility disabled guard",
)
service = replace_once(
    service,
    '''            while (serviceReady) {
                val packageName = currentWindowPackageName()
                if (shouldScanPackage(packageName)) {''',
    '''            while (serviceReady) {
                if (!currentSettings.appEnabled) {
                    if (currentRadarColor != RadarColor.Idle) resetToIdle("Rota Certa desligado pelo usuario.", record = false)
                    delay(SCAN_LOOP_MS)
                    continue
                }
                val packageName = currentWindowPackageName()
                if (shouldScanPackage(packageName)) {''',
    "continuous scan disabled guard",
)
service = replace_once(
    service,
    '''            while (serviceReady) {
                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                val radars = currentImportedRadars
                if (alerts.isNotEmpty() || radars.isNotEmpty()) checkProximityAlerts(alerts, radars)
                delay(PROXIMITY_ALERT_LOOP_MS)
            }''',
    '''            while (serviceReady) {
                if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled) {
                    delay(PROXIMITY_ALERT_LOOP_MS)
                    continue
                }
                val alerts = currentSavedPlaces.filter { it.type == SavedPlaceType.ProximityAlert }
                val radars = currentImportedRadars
                if (alerts.isNotEmpty() || radars.isNotEmpty()) checkProximityAlerts(alerts, radars)
                delay(PROXIMITY_ALERT_LOOP_MS)
            }''',
    "proximity loop disabled guard",
)
service = replace_once(
    service,
    '''    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>, radars: List<ImportedRadar>) {
        val coordinate = locationService.currentCoordinate() ?: return''',
    '''    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>, radars: List<ImportedRadar>) {
        if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled) return
        val coordinate = locationService.currentCoordinate() ?: return''',
    "proximity check disabled guard",
)
service = replace_once(
    service,
    '''        val settings = currentSettings
        return normalized in selectedRidePackages(settings)''',
    '''        val settings = currentSettings
        if (!settings.appEnabled) return false
        return normalized in selectedRidePackages(settings)''',
    "should scan disabled guard",
)
service_path.write_text(service)
