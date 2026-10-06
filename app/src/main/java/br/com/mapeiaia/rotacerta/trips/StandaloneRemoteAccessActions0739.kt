package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun StandaloneRemoteAccessActions0739(
    connecting: Boolean,
    message: String,
    onCopy: () -> Unit,
    onVerify: () -> Unit,
) {
    Column {
        Text(message)
        // Always visible, including before access creation or after an error.
        OutlinedButton(onClick = onCopy, enabled = !connecting, modifier = Modifier.fillMaxWidth()) {
            Text("🔐 Copiar acesso privado remoto")
        }
        OutlinedButton(onClick = onVerify, enabled = !connecting, modifier = Modifier.fillMaxWidth()) {
            Text(if (connecting) "Verificando conexão…" else "Verificar conexão remota")
        }
    }
}
