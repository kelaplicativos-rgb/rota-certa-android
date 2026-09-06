package br.com.mapeiaia.rotacerta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MessageTemplatesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                MessageTemplatesScreen0172(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun MessageTemplatesScreen0172(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var trip by remember { mutableStateOf(TenantMessageTemplateStore.readTrip(context)) }
    var value by remember { mutableStateOf(TenantMessageTemplateStore.readValue(context)) }
    var editingTrip by remember { mutableStateOf(false) }
    var editingValue by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Frases predefinidas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Voltar") }
        }
        Text("Estas frases são usadas pelas bolinhas Copiar viagem e Valor e permanecem isoladas por usuário do Rota Certa.", style = MaterialTheme.typography.bodySmall)
        TemplateCard0172("Copiar viagem", trip, "{saudacao}, {nome}, {origem}, {destino}, {dia_semana}, {dia}, {mes}, {horario}") { editingTrip = true }
        TemplateCard0172("Valor", value, "{nome}, {lugares}, {origem}, {destino}, {valor}") { editingValue = true }
        OutlinedButton(
            onClick = {
                TenantMessageTemplateStore.restoreDefaults(context)
                trip = TenantMessageTemplateStore.readTrip(context)
                value = TenantMessageTemplateStore.readValue(context)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restaurar frases originais") }
    }

    if (editingTrip) {
        TemplateEditor0172(
            title = "Editar frase de viagem",
            initial = trip,
            onSave = { saved ->
                TenantMessageTemplateStore.saveTrip(context, saved)
                trip = TenantMessageTemplateStore.readTrip(context)
                editingTrip = false
            },
            onDismiss = { editingTrip = false },
        )
    }
    if (editingValue) {
        TemplateEditor0172(
            title = "Editar frase de valor",
            initial = value,
            onSave = { saved ->
                TenantMessageTemplateStore.saveValue(context, saved)
                value = TenantMessageTemplateStore.readValue(context)
                editingValue = false
            },
            onDismiss = { editingValue = false },
        )
    }
}

@Composable
private fun TemplateCard0172(title: String, text: String, placeholders: String, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text)
            Text("Campos disponíveis: $placeholders", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar frase") }
        }
    }
}

@Composable
private fun TemplateEditor0172(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                label = { Text("Frase") },
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
