package br.com.mapeiaia.rotacerta.trips

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StandaloneRemoteAccessActions0739(
    connecting: Boolean,
    message: String,
    onCopy: () -> Unit,
    onVerify: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tenantId = RotaCertaTenantRegistry(context).activeScope().tenantId
    var autoAccess by remember(context, tenantId) {
        mutableStateOf(RemoteAccessDesiredState0771.desired(context))
    }
    var busy by remember(context, tenantId) { mutableStateOf(false) }
    var showAdvanced by remember(context, tenantId) { mutableStateOf(false) }
    var permissionMessage by remember(context, tenantId) { mutableStateOf<String?>(null) }

    suspend fun syncServer(enabled: Boolean): Boolean {
        val settings = withContext(Dispatchers.IO) { TripStore(context).onlineSettings() }
        check(settings.configured && settings.driverToken.isNotBlank() && settings.driverUsername.isNotBlank()) {
            "É necessário conectar o motorista antes de autorizar acesso remoto."
        }
        val api = TripRemoteApi(settings)
        if (enabled) {
            // The same private capability covers the covers list and per-trip detailed HTML.
            api.ensureStandaloneCoversAccess0736()
        }
        return api.setRemoteAccessState0764(enabled).enabled == enabled
    }

    fun stopListener() {
        context.stopService(Intent(context, RemoteCoversPollService0758::class.java))
    }

    fun startListener() {
        ContextCompat.startForegroundService(
            context, Intent(context, RemoteCoversPollService0758::class.java)
        )
    }

    LaunchedEffect(context, tenantId) {
        // No implicit grant: only the already acknowledged local permission
        // may run the listener. A pending ON or OFF is reconciled by WorkManager.
        RemoteAccessDesiredState0771.schedule(context)
        val authorized = RemoteSupportAutoAccess0763.enabled(context, "remote_health_collect")
        if (authorized) {
            RemotePollingRecovery0770.reconcile(context, immediate = true)
            runCatching { startListener() }.onFailure {
                permissionMessage = "Recuperação remota automática em segundo plano."
            }
        } else if (RemoteAccessDesiredState0771.desired(context)) {
            permissionMessage = "ON solicitado. Autorização aguardando confirmação do servidor."
        }
    }

    Column {
        Text(message)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = autoAccess,
                enabled = !busy,
                onCheckedChange = { requested ->
                    busy = true
                    try {
                        // Only explicit user interaction changes the desired
                        // toggle position. A transient DNS failure never does.
                        RemoteAccessDesiredState0771.request(context, requested)
                        autoAccess = requested
                        if (requested) {
                            permissionMessage =
                                "ON registrado. Consultas começam após confirmação automática do servidor."
                        } else {
                            stopListener()
                            permissionMessage =
                                "OFF aplicado no aparelho. Revogação do link em segundo plano."
                        }
                    } catch (error: Exception) {
                        permissionMessage = "Falha ao salvar a escolha; nenhuma permissão adicional foi concedida."
                    } finally {
                        busy = false
                    }

                },
            )
            Text("Permitir consultas remotas", modifier = Modifier.padding(start = 12.dp))
        }
        Text(
            "Um único toggle, desligado por padrão. ON autoriza a escuta e permite consultar capas, " +
                "detalhes por passageiro, Central de Saúde e ZIP técnico. OFF interrompe o aparelho " +
                "e solicita revogação dos links privados no servidor. Não altera viagens, não envia " +
                "senhas e não contorna permissões obrigatórias do Android.",
        )
        permissionMessage?.let { Text(it) }

        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (showAdvanced) "Ocultar opções avançadas" else "Opções avançadas")
        }
        if (showAdvanced) {
            OutlinedButton(
                onClick = onVerify,
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (connecting) "Verificando conexão…" else "Verificar conexão remota")
            }
            OutlinedButton(
                onClick = onCopy,
                enabled = !connecting && autoAccess,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copiar acesso privado remoto")
            }
            Text(
                "Só compartilhe o link privado com quem está autorizado a consultar suas viagens. " +
                    "A consulta detalhada usa o perfil e o ID real da viagem. " +
                    "O sistema não envia cookies ou senhas e não ignora permissões do Android.",
            )
        }
    }
}
