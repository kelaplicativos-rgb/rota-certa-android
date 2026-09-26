from pathlib import Path
import json


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}\n--- needle ---\n{old[:700]}")
    p.write_text(text.replace(old, new, 1))


nav = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt"
replace_once(
    nav,
    'internal enum class AgendaRootSection0396(val label: String) {\n    ALL_TRIPS("Todas as viagens"),',
    'internal enum class AgendaRootSection0396(val label: String) {\n    CENTRAL_DAY("Central do Dia"),\n    ALL_TRIPS("Todas as viagens"),',
)
replace_once(
    nav,
    "                listOf(\n                    AgendaRootSection0396.ALL_TRIPS,",
    "                listOf(\n                    AgendaRootSection0396.CENTRAL_DAY,\n                    AgendaRootSection0396.ALL_TRIPS,",
)

trips = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
replace_once(
    trips,
    "private enum class TripScreen { LIST, TIMELINE, ASSISTANT, NOTIFICATIONS, PUBLIC_SEARCH, CREATE, SETTINGS, APP_SETTINGS, EXTRA_SEATS, PASSENGERS, AUTO_SYNC, SCRIPTS, DEBUG_REPORT }",
    "private enum class TripScreen { LIST, CENTRAL_DAY, TIMELINE, ASSISTANT, NOTIFICATIONS, PUBLIC_SEARCH, CREATE, SETTINGS, APP_SETTINGS, EXTRA_SEATS, PASSENGERS, AUTO_SYNC, SCRIPTS, DEBUG_REPORT }",
)
replace_once(
    trips,
    "private fun TripScreen.isAgendaRoot0396(): Boolean =\n    this == TripScreen.TIMELINE ||",
    "private fun TripScreen.isAgendaRoot0396(): Boolean =\n    this == TripScreen.CENTRAL_DAY ||\n        this == TripScreen.TIMELINE ||",
)
replace_once(
    trips,
    "private fun TripScreen.agendaRootSection0396(): AgendaRootSection0396 = when (this) {\n    TripScreen.ASSISTANT -> AgendaRootSection0396.ASSISTANT",
    "private fun TripScreen.agendaRootSection0396(): AgendaRootSection0396 = when (this) {\n    TripScreen.CENTRAL_DAY -> AgendaRootSection0396.CENTRAL_DAY\n    TripScreen.ASSISTANT -> AgendaRootSection0396.ASSISTANT",
)
replace_once(
    trips,
    "private fun TripScreen.diagnosticModule0507(): DiagnosticModule0507 = when (this) {\n    TripScreen.TIMELINE -> DiagnosticModule0507.ALL_TRIPS",
    "private fun TripScreen.diagnosticModule0507(): DiagnosticModule0507 = when (this) {\n    TripScreen.CENTRAL_DAY -> DiagnosticModule0507.ALL_TRIPS\n    TripScreen.TIMELINE -> DiagnosticModule0507.ALL_TRIPS",
)
replace_once(
    trips,
    'private fun TripScreen.agendaHeaderLabel0396(): String = when (this) {\n    TripScreen.TIMELINE -> "Todas as viagens"',
    'private fun TripScreen.agendaHeaderLabel0396(): String = when (this) {\n    TripScreen.CENTRAL_DAY -> "Central do Dia"\n    TripScreen.TIMELINE -> "Todas as viagens"',
)
replace_once(
    trips,
    "            when (section) {\n                AgendaRootSection0396.ALL_TRIPS -> {",
    "            when (section) {\n                AgendaRootSection0396.CENTRAL_DAY -> {\n                    parentRootScreen0396 = TripScreen.CENTRAL_DAY\n                    passengerSubscreenOpen0396 = false\n                    screen = TripScreen.CENTRAL_DAY\n                }\n                AgendaRootSection0396.ALL_TRIPS -> {",
)
replace_once(
    trips,
    "            when (screen) {\n                TripScreen.DEBUG_REPORT -> ContextualDebugReportScreen0507(activeDebugModule0507)",
    '''            when (screen) {
                TripScreen.CENTRAL_DAY -> CentralDoDiaScreen0552(
                    trips = trips,
                    bookings = bookings,
                    localProfileLabel = drawerOnlineSettings0397.driverDisplayName.ifBlank { "Agenda" },
                    onRefreshLocal = { refresh() },
                    onOpenTimeline = { tripId, bookingId ->
                        focusedTripId = tripId
                        focusedBookingId = bookingId
                        reservationPendingOnly = false
                        parentRootScreen0396 = TripScreen.TIMELINE
                        screen = TripScreen.TIMELINE
                    },
                    onMessage = { message = it },
                )
                TripScreen.DEBUG_REPORT -> ContextualDebugReportScreen0507(activeDebugModule0507)''',
)

collector_ui = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt"
old_row = '''@Composable
internal fun DynamicAccountRow(
    account: BlaBlaDynamicAccount,
    snapshot: BlaBlaDynamicSessionSnapshot?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    showBrowserDetails: Boolean = false,
) {
    val connected = snapshot?.identityVerified == true && !account.profileUuid.isNullOrBlank()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(account.displayLabel)
            Text(account.profileUuid ?: "UUID será descoberto após login/validação")
            Text(
                when {
                    snapshot?.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED ->
                        "BlaBlaCar temporariamente restrito • últimos dados preservados ⚠️"
                    connected -> "Conectado • UUID confirmado ✅"
                    snapshot != null -> "Sessão salva • UUID pendente ⏳"
                    else -> "Ainda não conectado"
                },
            )
            if (snapshot != null) Text("Última leitura local: ${snapshot.trips.size} viagens")
            if (snapshot?.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED) {
                Text("Abra esta conta quando quiser revalidar a sessão. O Rota Certa não ficará tentando em segundo plano.")
            }
            if (showBrowserDetails) {
                Text("Perfil do navegador: ${account.webProfileName}")
                Text("Sessão isolada: ${if (snapshot == null) "ainda não iniciada" else "salva no aparelho"}")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onOpen) {
                Text(if (snapshot == null) "Entrar" else if (showBrowserDetails) "Abrir navegador" else "Abrir")
            }
            TextButton(onClick = onRemove) { Text("Remover") }
        }
    }
}'''
new_row = '''@Composable
internal fun DynamicAccountRow(
    account: BlaBlaDynamicAccount,
    snapshot: BlaBlaDynamicSessionSnapshot?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    showBrowserDetails: Boolean = false,
    onVerify: (() -> Unit)? = null,
) {
    val health = BlaBlaCarSessionKeeper0552.health(account, snapshot)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(account.displayLabel)
            Text(account.profileUuid ?: "UUID será descoberto após login/validação")
            Text(
                when (health.state) {
                    BlaBlaSessionState0552.VALID -> "Sessão 🟢 Saudável • UUID confirmado ✅"
                    BlaBlaSessionState0552.LOGIN_REQUIRED,
                    BlaBlaSessionState0552.EXPIRED -> "🔴 Desconectado • login necessário"
                    BlaBlaSessionState0552.PROFILE_MISMATCH -> "🔴 Identidade divergente • operação bloqueada"
                    BlaBlaSessionState0552.NETWORK_ERROR -> "🟡 Rede indisponível • sessão preservada"
                    BlaBlaSessionState0552.REVALIDATING -> "⏳ Verificando sessão…"
                    BlaBlaSessionState0552.SUSPECTED -> "🟡 Sessão preservada • validação necessária"
                },
            )
            if (snapshot != null) Text("Última leitura local: ${snapshot.trips.size} viagens")
            if (health.lastValidatedAtMillis > 0L) {
                val formatted = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(java.time.Instant.ofEpochMilli(health.lastValidatedAtMillis))
                Text("Última validação: $formatted")
            }
            if (health.explanation.isNotBlank()) {
                Text(health.explanation, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
            if (showBrowserDetails) {
                Text("Sessão neste aparelho: ${if (snapshot == null) "não iniciada" else "✅ Persistente"}")
                Text("Perfil WebView (diagnóstico): ${account.webProfileName}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onOpen) {
                Text(if (health.state in setOf(BlaBlaSessionState0552.LOGIN_REQUIRED, BlaBlaSessionState0552.EXPIRED)) "Refazer login" else "Abrir conta")
            }
            if (onVerify != null) {
                OutlinedButton(onClick = onVerify) { Text("Verificar sessão") }
            }
            TextButton(onClick = onRemove) { Text("Remover conta") }
        }
    }
}'''
replace_once(collector_ui, old_row, new_row)
replace_once(
    collector_ui,
    '''                            onRemove = {
                                registry.remove(account.id)
                                refresh()
                                publishCombined("Conta removida")
                            },''',
    '''                            onRemove = {
                                publishCombined("Remova a conta em Contas e navegadores, onde a limpeza destrutiva exige confirmação explícita.")
                            },''',
)

accounts_ui = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaAccountsBrowsersUi0399.kt"
replace_once(
    accounts_ui,
    '    var newAccountLabel by remember { mutableStateOf("") }\n    var ridesSnapshotRunning0526',
    '    var newAccountLabel by remember { mutableStateOf("") }\n    var pendingRemovalAccountId0552 by remember { mutableStateOf<String?>(null) }\n    var ridesSnapshotRunning0526',
)
replace_once(
    accounts_ui,
    '''                        onOpen = {
                            sessionLauncher.launch(BlaBlaDynamicSessionIntents.login(context, account))
                        },
                        onRemove = {
                            registry.remove(account.id)
                            val reconciled = sessionStore.combinedResponse(registry.list())
                            BlaBlaCollectorStateStore(context).saveResponse(
                                response = reconciled,
                                preserveOnPartial = false,
                            )
                            revision++
                        },
                        showBrowserDetails = true,''',
    '''                        onOpen = {
                            sessionLauncher.launch(BlaBlaDynamicSessionIntents.login(context, account))
                        },
                        onRemove = { pendingRemovalAccountId0552 = account.id },
                        showBrowserDetails = true,
                        onVerify = {
                            sessionLauncher.launch(BlaBlaDynamicSessionIntents.profile(context, account))
                        },''',
)
removal_dialog = '''

    pendingRemovalAccountId0552?.let { accountId ->
        registry.get(accountId)?.let { account ->
            AlertDialog(
                onDismissRequest = { pendingRemovalAccountId0552 = null },
                title = { Text("Remover conta e sessão deste aparelho?") },
                text = {
                    Text(
                        "Esta ação é destrutiva: remove o cadastro local da conta, o snapshot privado e o perfil WebView persistente. " +
                            "Ela é diferente de Refazer login e não será executada automaticamente."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        BlaBlaCarSessionKeeper0552.clearRuntime(account.id)
                        registry.remove(account.id)
                        val reconciled = sessionStore.combinedResponse(registry.list())
                        BlaBlaCollectorStateStore(context).saveResponse(
                            response = reconciled,
                            preserveOnPartial = false,
                        )
                        pendingRemovalAccountId0552 = null
                        revision++
                    }) { Text("Remover conta e sessão") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRemovalAccountId0552 = null }) { Text("Cancelar") }
                },
            )
        } ?: run { pendingRemovalAccountId0552 = null }
    }
'''
marker = "\n    externalTimeline0535?.takeIf { showExternalTimeline0535 }?.let { projection ->"
p = Path(accounts_ui)
text = p.read_text()
if text.count(marker) != 1:
    raise SystemExit("accounts ui removal dialog marker mismatch")
p.write_text(text.replace(marker, removal_dialog + marker, 1))

dynamic = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt"
replace_once(
    dynamic,
    "        mode = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_MODE) ?: BlaBlaDynamicSessionIntents.MODE_LOGIN\n        ridesSnapshotCaptureId0526",
    "        mode = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_MODE) ?: BlaBlaDynamicSessionIntents.MODE_LOGIN\n        BlaBlaCarSessionKeeper0552.observeAcquire(this, account, mode)\n        ridesSnapshotCaptureId0526",
)
replace_once(
    dynamic,
    '''            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                captureAuthoritativePublicTripNavigation0443(url)
            }''',
    '''            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                BlaBlaCarSessionKeeper0552.observeNavigation(this@BlaBlaDynamicAccountSessionController0401, account, url)
                captureAuthoritativePublicTripNavigation0443(url)
            }''',
)
replace_once(
    dynamic,
    "    private fun handleMainFrameTransportFailure0426(errorCode: Int, targetUrl: String) {\n        if (phase == Phase.IDLE) {",
    "    private fun handleMainFrameTransportFailure0426(errorCode: Int, targetUrl: String) {\n        BlaBlaCarSessionKeeper0552.observeNetworkError(this, account, phase.name)\n        if (phase == Phase.IDLE) {",
)

build = "app/build.gradle.kts"
replace_once(
    build,
    'val releaseVersionCode = 5_843\nval releaseVersionName = "0.1.551"',
    'val releaseVersionCode = 5_844\nval releaseVersionName = "0.1.552"',
)

history_path = Path("app/src/main/assets/release_history.json")
history = json.loads(history_path.read_text())
history["releases"] = [r for r in history.get("releases", []) if r.get("version") != "0.1.552"]
history["releases"].insert(
    0,
    {
        "version": "0.1.552",
        "build": 5844,
        "commit": None,
        "branch": "agent/central-day-session-0.1.552",
        "generatedAt": None,
        "status": "EM_VALIDACAO",
        "implemented": [
            "Central do Dia como read model sobre Agenda canônica e projeção existente da Timeline, sem novo store autoritativo.",
            "Semáforo de integridade, diagnóstico estruturado, próxima ação e continuidade física por perfil.",
            "SessionKeeper central de saúde de sessão multiperfil sobre o perfil WebView persistente existente.",
        ],
        "fixed": [
            "Contas com UUID histórico não são mais apresentadas automaticamente como Conectado quando a WebView está exigindo login.",
            "Remoção de conta/sessão passou a exigir confirmação destrutiva explícita na tela de Contas e navegadores.",
        ],
        "improved": [
            "Correção individual da Central reutiliza AgendaBackgroundSync0392 e a identidade forte existente, sem escrita paralela.",
            "Falha de rede é registrada como sessão preservada e não é convertida automaticamente em LOGIN_REQUIRED.",
            "Perfil WebView deixa de ser a informação principal da conta e passa a aparecer apenas como diagnóstico.",
        ],
        "regressions": [],
        "resolvedRegressions": [],
        "modulesAffected": ["BlaBlaCar", "Agenda", "Timeline", "Central do Dia", "Contas externas"],
        "detailsComplete": True,
    },
)
history_path.write_text(json.dumps(history, ensure_ascii=False, indent=2) + "\n")
