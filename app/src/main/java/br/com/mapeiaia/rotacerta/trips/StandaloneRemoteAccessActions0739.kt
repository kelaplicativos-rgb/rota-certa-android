package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                    runCatching { RemoteSupportAutoAccess0763.setEnabled(context, enabled) }
                        .onSuccess {
                            autoAccess = enabled
                            permissionMessage = if (enabled) {
                                "Acesso automático autorizado neste aparelho. A escuta remota deve estar ligada."
                            } else {
                                "Autorização automática revogada. Próximas solicitações exigirão Aceitar."
                            }
                        }
                        .onFailure {
                            permissionMessage = "Não foi possível salvar a autorização. Tente novamente."
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
                "técnico sem tocar em Aceitar a cada solicitação. Somente leitura, por motorista; " +
                "revogue aqui quando desejar. Não dispensa permissões obrigatórias do Android.",
        )
        permissionMessage?.let { Text(it) }
    }
}
