package br.com.mapeiaia.rotacerta.trips

import android.content.Intent
import androidx.core.content.ContextCompat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry

@Composable
internal fun StandaloneRemoteAccessActions0739(
    connecting: Boolean,
    message: String,
    onCopy: () -> Unit,
    onVerify: () -> Unit,
) {
    val context = LocalContext.current
    val tenantId = RotaCertaTenantRegistry(context).activeScope().tenantId
    var autoAccess by remember(context, tenantId) {
        mutableStateOf(RemoteSupportAutoAccess0763.enabled(context, "remote_health_collect"))
    }
    var permissionMessage by remember(context, tenantId) { mutableStateOf<String?>(null) }

    // An opted-in driver gets the listener on opening the screen, with no second
    // activation button. Android may stop a foreground service later.
    LaunchedEffect(context, tenantId) {
        if (RemoteSupportAutoAccess0763.enabled(context, "standalone_covers_collect") &&
            !RemoteCoversPollService0758.isRunning) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, RemoteCoversPollService0758::class.java)
                )
            }.onFailure {
                permissionMessage = "Não foi possível ativar a escuta remota neste momento."
            }
        }
    }

    Column {
        Text(message)
        // Always visible, including before access creation or after an error.
        OutlinedButton(onClick = onCopy, enabled = !connecting, modifier = Modifier.fillMaxWidth()) {
            Text("🔐 Copiar acesso privado remoto")
        }
        OutlinedButton(onClick = onVerify, enabled = !connecting, modifier = Modifier.fillMaxWidth()) {
            Text(if (connecting) "Verificando conexão…" else "Verificar conexão remota")
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = autoAccess,
                onCheckedChange = { enabled ->
                    runCatching {
                        RemoteSupportAutoAccess0763.setEnabled(context, enabled)
                        if (enabled) {
                            ContextCompat.startForegroundService(
                                context, Intent(context, RemoteCoversPollService0758::class.java)
                            )
                        } else {
                            context.stopService(Intent(context, RemoteCoversPollService0758::class.java))
                        }
                    }.onSuccess {
                        autoAccess = enabled
                        permissionMessage = if (enabled) {
                            "Consultas remotas ativadas: capas, detalhes por passageiro, saúde e ZIP."
                        } else {
                            "Consultas remotas desligadas neste aparelho. Nenhuma nova coleta será autorizada."
                        }
                    }.onFailure {
                        if (enabled) runCatching {
                            RemoteSupportAutoAccess0763.setEnabled(context, false)
                        }
                        autoAccess = RemoteSupportAutoAccess0763.enabled(context, "remote_health_collect")
                        permissionMessage = "Não foi possível alterar a escuta remota. Verifique o estado e tente novamente."
                    }
                },
            )
            Text(
                "Autorizar consultas remotas automaticamente",
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            "Opcional e desligado por padrão. Permite consultar capas, viagens, Central de Saúde e ZIP " +
                "técnico sem tocar em Aceitar a cada solicitação. ON também liga a escuta; OFF a desliga e " +
                "bloqueia novas coletas neste aparelho. Somente leitura e por motorista; não dispensa " +
                "permissões obrigatórias do Android. Dados já enviados ao servidor expiram separadamente.",
        )
        permissionMessage?.let { Text(it) }
    }
}
