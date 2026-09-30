package br.com.mapeiaia.rotacerta

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import br.com.mapeiaia.rotacerta.trips.RideAppLearningRequest0700
import br.com.mapeiaia.rotacerta.trips.RideAppLearningResponse0700
import br.com.mapeiaia.rotacerta.trips.RideAppLearningStatusRequest0702
import br.com.mapeiaia.rotacerta.trips.TripRemoteApi
import br.com.mapeiaia.rotacerta.trips.TripStore
import br.com.mapeiaia.rotacerta.trips.isRideAppLearningTransportFailure0702
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RideAppLearningUiState0700(
    val busy: Boolean = false,
    val title: String = "Aprender aplicativo de corrida",
    val message: String = "Anexe o APK completo ou escolha um aplicativo instalado. A análise local produz um dossiê reduzido; somente esse dossiê é enviado ao backend OpenAI.",
    val dossier: RideApkDossier0700? = null,
    val profile: RideReaderProfile0700? = null,
)

class RideAppLearningActivity0700 : ComponentActivity() {
    private var state by mutableStateOf(RideAppLearningUiState0700())
    private var profiles by mutableStateOf<List<RideReaderProfile0700>>(emptyList())
    private var installedApps by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var showInstalledPicker by mutableStateOf(false)
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    private val apkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            UnifiedDebugEventStore.record(
                RideAppLearningContract0702.APK_SELECTED,
                packageName,
                "source=file_picker",
            )
            analyzeAndLearn { RideApkAnalyzer0700.analyzeUri(applicationContext, uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshProfiles()
        lifecycleScope.launch(Dispatchers.Default) {
            val apps = RideApkAnalyzer0700.launchableApps(applicationContext)
            withContext(Dispatchers.Main) { installedApps = apps }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RideAppLearningScreen0700(
                    state = state,
                    profiles = profiles,
                    installedApps = installedApps,
                    showInstalledPicker = showInstalledPicker,
                    onShowInstalled = { showInstalledPicker = true },
                    onDismissInstalled = { showInstalledPicker = false },
                    onInstalledSelected = { pkg ->
                        showInstalledPicker = false
                        UnifiedDebugEventStore.record(
                            RideAppLearningContract0702.APK_SELECTED,
                            pkg,
                            "source=installed_app",
                        )
                        lifecycleScope.launch {
                            analyzeAndLearn { RideApkAnalyzer0700.analyzeInstalled(applicationContext, pkg) }
                        }
                    },
                    onPickApk = {
                        apkPicker.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/octet-stream",
                                "*/*",
                            ),
                        )
                    },
                    onRemove = { packageName ->
                        RideAppLearningStore0700.remove(applicationContext, packageName)
                        refreshProfiles()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    private suspend fun analyzeAndLearn(loader: suspend () -> RideApkDossier0700) {
        if (state.busy) return
        state = RideAppLearningUiState0700(
            busy = true,
            title = "Analisando APK",
            message = "Extraindo package, versão, nomes de recursos e evidências semânticas localmente. O APK não é executado.",
        )
        try {
            val dossier = withContext(Dispatchers.IO) { loader() }
            UnifiedDebugEventStore.record(
                RideAppLearningContract0702.DOSSIER_READY,
                dossier.packageName,
                "sha=${dossier.apkSha256.take(16)}; entries=${dossier.relevantEntries.size}; strings=${dossier.relevantStrings.size}; bytes=${dossier.fileSizeBytes}",
            )
            state = RideAppLearningUiState0700(
                busy = true,
                title = "Dossiê criado",
                message = "${dossier.appLabel}\\n${dossier.packageName}\\n${dossier.relevantEntries.size} recursos • ${dossier.relevantStrings.size} evidências. Consultando o aprendizado persistente e, somente se necessário, a OpenAI…",
                dossier = dossier,
            )

            RideAppLearningStore0700.findByApkSha(applicationContext, dossier.apkSha256)?.let { cached ->
                UnifiedDebugEventStore.record(
                    RideAppLearningContract0702.LOCAL_CACHE_HIT,
                    dossier.packageName,
                    "sha=${dossier.apkSha256.take(16)}; profileVersion=${cached.profileVersion}",
                )
                activateProfile(cached)
                state = RideAppLearningUiState0700(
                    busy = false,
                    title = "Já aprendido",
                    message = "Este SHA do APK já possui Reader Profile local. Nenhuma nova chamada OpenAI foi feita.",
                    dossier = dossier,
                    profile = cached,
                )
                return
            }

            val online = TripStore(applicationContext).onlineSettings()
            check(online.configured) {
                "Backend do motorista não configurado. Configure a Agenda/Viagem Certa antes de usar o aprendizado por IA."
            }

            val api = TripRemoteApi(online)
            val response = requestProfileWithRecovery0702(api, dossier)
            UnifiedDebugEventStore.record(
                RideAppLearningContract0702.BACKEND_STATUS,
                dossier.packageName,
                "status=${response.status}; cached=${response.cached}; confidence=${response.confidence}; contractVersion=${response.contractVersion}",
            )
            check(response.learned) {
                response.reason.ifBlank { "A IA não encontrou evidência suficiente para gerar um leitor seguro." }
            }
            if (response.cached) {
                UnifiedDebugEventStore.record(
                    RideAppLearningContract0702.BACKEND_CACHE_HIT,
                    dossier.packageName,
                    "sha=${dossier.apkSha256.take(16)}; profileVersion=${response.profileVersion}",
                )
            }

            val profile = RideReaderProfile0700(
                packageName = dossier.packageName,
                versionName = dossier.versionName,
                versionCode = dossier.versionCode,
                apkSha256 = dossier.apkSha256.lowercase(),
                profileVersion = maxOf(response.profileVersion.coerceAtLeast(1), dossier.extractionVersion),
                confidence = response.confidence.coerceIn(0.0, 1.0),
                pickupLabels = response.pickupLabels,
                destinationLabels = response.destinationLabels,
                fareLabels = response.fareLabels,
                distanceLabels = response.distanceLabels,
                rideAnchors = response.rideAnchors,
                actionLabels = response.actionLabels,
                resourceHints = response.resourceHints,
                ignoreLabels = response.ignoreLabels,
                provider = response.provider,
                model = response.model,
                reason = response.reason,
                learnedAtMillis = System.currentTimeMillis(),
            )
            RideAppLearningStore0700.save(applicationContext, profile)
            activateProfile(profile)
            UnifiedDebugEventStore.record(
                RideAppLearningContract0702.PROFILE_SAVED,
                dossier.packageName,
                "sha=${dossier.apkSha256.take(16)}; profileVersion=${profile.profileVersion}; backendCached=${response.cached}",
            )
            state = RideAppLearningUiState0700(
                busy = false,
                title = if (response.cached) "Aprendizado recuperado" else "Aplicativo aprendido",
                message = if (response.cached) {
                    "${dossier.appLabel} já havia sido aprendido no servidor. O Reader foi recuperado sem nova chamada paga e salvo neste aparelho."
                } else {
                    "${dossier.appLabel} agora possui um Reader Profile local. Próximos cards são interpretados localmente; OpenAI não é chamada por corrida."
                },
                dossier = dossier,
                profile = profile,
            )
            refreshProfiles()
            Toast.makeText(applicationContext, "Aplicativo aprendido e autorizado no FAROL.", Toast.LENGTH_LONG).show()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            UnifiedDebugEventStore.record(
                RideAppLearningContract0702.FAILED,
                state.dossier?.packageName ?: packageName,
                "type=${error.javaClass.simpleName}; message=${UnifiedDebugEventStore.sanitizeForExport(error.message.orEmpty()).take(240)}",
            )
            state = state.copy(
                busy = false,
                title = "Aprendizado não concluído",
                message = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private suspend fun requestProfileWithRecovery0702(
        api: TripRemoteApi,
        dossier: RideApkDossier0700,
    ): RideAppLearningResponse0700 {
        val request = RideAppLearningRequest0700(
            packageName = dossier.packageName,
            versionName = dossier.versionName,
            versionCode = dossier.versionCode,
            apkSha256 = dossier.apkSha256,
            dossier = dossier.toModelDossier(),
        )
        val statusRequest = RideAppLearningStatusRequest0702(
            packageName = dossier.packageName,
            apkSha256 = dossier.apkSha256,
        )
        UnifiedDebugEventStore.record(
            RideAppLearningContract0702.REQUEST_SENT,
            dossier.packageName,
            "attempt=1; sha=${dossier.apkSha256.take(16)}; readTimeoutMs=${RideAppLearningContract0702.READ_TIMEOUT_MS}",
        )
        state = state.copy(
            busy = true,
            title = "Consultando IA",
            message = "O APK já foi destrinchado localmente. Aguarde a resposta do aprendizado; esta etapa pode levar mais de um minuto em rede móvel.",
        )

        try {
            val first = withContext(Dispatchers.IO) { api.learnRideApp0700(request) }
            return if (first.processing) {
                awaitProcessingResult0702(api, statusRequest, dossier, first)
            } else {
                first
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!isRideAppLearningTransportFailure0702(error)) throw error
            UnifiedDebugEventStore.record(
                RideAppLearningContract0702.TRANSPORT_FAILED,
                dossier.packageName,
                "attempt=1; type=${error.javaClass.simpleName}; sha=${dossier.apkSha256.take(16)}",
            )
        }

        val recovered = withContext(Dispatchers.IO) { api.rideAppLearningStatus0702(statusRequest) }
        if (recovered.learned) return recovered
        if (recovered.processing) {
            return awaitProcessingResult0702(api, statusRequest, dossier, recovered)
        }

        UnifiedDebugEventStore.record(
            RideAppLearningContract0702.RETRY,
            dossier.packageName,
            "attempt=2; reason=status_${recovered.status.ifBlank { "unknown" }}; sha=${dossier.apkSha256.take(16)}",
        )
        state = state.copy(
            busy = true,
            title = "Recuperando aprendizado",
            message = "A primeira conexão caiu antes da resposta. O Rota Certa confirmou o SHA no servidor e fará uma única retomada segura, sem duplicar análise paga.",
        )
        val second = withContext(Dispatchers.IO) { api.learnRideApp0700(request) }
        return if (second.processing) {
            awaitProcessingResult0702(api, statusRequest, dossier, second)
        } else {
            second
        }
    }

    private suspend fun awaitProcessingResult0702(
        api: TripRemoteApi,
        statusRequest: RideAppLearningStatusRequest0702,
        dossier: RideApkDossier0700,
        initial: RideAppLearningResponse0700,
    ): RideAppLearningResponse0700 {
        var latest = initial
        repeat(RideAppLearningContract0702.STATUS_POLL_ATTEMPTS) { index ->
            if (!latest.processing) return latest
            state = state.copy(
                busy = true,
                title = "Aprendizado em andamento",
                message = "O servidor já está estudando este mesmo APK. Aguardando o Reader persistido, sem iniciar outra chamada OpenAI…",
            )
            delay(
                latest.retryAfterMillis
                    .takeIf { it in 500L..5_000L }
                    ?: RideAppLearningContract0702.STATUS_POLL_DELAY_MS,
            )
            latest = withContext(Dispatchers.IO) { api.rideAppLearningStatus0702(statusRequest) }
            UnifiedDebugEventStore.record(
                RideAppLearningContract0702.BACKEND_STATUS,
                dossier.packageName,
                "poll=${index + 1}; status=${latest.status}; cached=${latest.cached}; sha=${dossier.apkSha256.take(16)}",
            )
        }
        return latest
    }
    private suspend fun activateProfile(profile: RideReaderProfile0700) {
        SelectedRideAppStore.add(applicationContext, profile.packageName)
        val selected = SelectedRideAppStore.read(applicationContext)
        val current = settingsRepository.settings.first()
        settingsRepository.saveSettings(
            current.copy(
                restrictToSelectedRideApps = true,
                extraMonitoredPackages = selected.sorted().joinToString(","),
            ),
        )
    }

    private fun refreshProfiles() {
        profiles = RideAppLearningStore0700.all(applicationContext)
    }
}

@Composable
private fun RideAppLearningScreen0700(
    state: RideAppLearningUiState0700,
    profiles: List<RideReaderProfile0700>,
    installedApps: List<Pair<String, String>>,
    showInstalledPicker: Boolean,
    onShowInstalled: () -> Unit,
    onDismissInstalled: () -> Unit,
    onInstalledSelected: (String) -> Unit,
    onPickApk: () -> Unit,
    onRemove: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (showInstalledPicker) {
        AlertDialog(
            onDismissRequest = onDismissInstalled,
            title = { Text("Escolha o aplicativo") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(installedApps, key = { it.second }) { (label, pkg) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onInstalledSelected(pkg) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(label, fontWeight = FontWeight.Bold)
                            Text(pkg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissInstalled) { Text("Cancelar") } },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Aprender aplicativo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClose) { Text("Fechar") }
        }
        Text(
            "A IA ensina uma vez; o Reader Profile resultante executa localmente e nunca contém código executável.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onPickApk, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Anexar APK completo")
        }
        OutlinedButton(
            onClick = onShowInstalled,
            enabled = !state.busy && installedApps.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Analisar aplicativo instalado")
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.title, fontWeight = FontWeight.Bold)
                Text(state.message, style = MaterialTheme.typography.bodySmall)
                if (state.busy) CircularProgressIndicator()
                state.dossier?.let {
                    Text(
                        "SHA-256: ${it.apkSha256.take(16)}… • v${it.versionName} (${it.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.profile?.let {
                    Text(
                        "Confiança: ${(it.confidence * 100).toInt()}% • destino: ${it.destinationLabels.take(4).joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Text("Readers aprendidos: ${profiles.size}", fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.packageName }) { profile ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile.packageName, fontWeight = FontWeight.Bold)
                        Text(
                            "v${profile.versionName} (${profile.versionCode}) • confiança ${(profile.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Destino: ${profile.destinationLabels.take(4).joinToString().ifBlank { "por resource hints" }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Modelo: ${profile.model.ifBlank { profile.provider }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { onRemove(profile.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Remover aprendizado")
                        }
                    }
                }
            }
        }
    }
}
