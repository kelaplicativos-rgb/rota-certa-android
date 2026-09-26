package br.com.mapeiaia.rotacerta

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

class QuickRepliesActivity : ComponentActivity() {
    private val targetPackageName: String?
        get() = QuickReplyTargetPolicy.normalize(
            intent?.getStringExtra(EXTRA_QUICK_REPLY_TARGET_PACKAGE),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                QuickRepliesScreen(
                    repository = remember { SettingsRepository(applicationContext) },
                    startCreating = intent?.getBooleanExtra(EXTRA_QUICK_REPLY_CREATE, false) == true,
                    onApply = ::applyReply,
                    onClose = ::closeAndRevealPreviousApp,
                )
            }
        }
    }

    private fun applyReply(text: String) {
        sendBroadcast(
            Intent(ACTION_APPLY_QUICK_REPLY)
                .setPackage(packageName)
                .putExtra(EXTRA_QUICK_REPLY_TEXT, text)
                .putExtra(EXTRA_QUICK_REPLY_TARGET_PACKAGE, targetPackageName),
        )
        closeAndRevealPreviousApp()
    }

    private fun closeAndRevealPreviousApp() {
        if (intent?.getBooleanExtra(EXTRA_QUICK_REPLY_OVERLAY_MODE_0172, false) == true) {
            moveTaskToBack(true)
        }
        finish()
    }
}

@Composable
private fun QuickRepliesScreen(
    repository: SettingsRepository,
    startCreating: Boolean,
    onApply: (String) -> Unit,
    onClose: () -> Unit,
) {
    val replies by repository.quickReplies.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<QuickReply?>(null) }
    var creating by remember(startCreating) { mutableStateOf(startCreating) }
    val query = search.trim().lowercase(Locale.ROOT)
    val filtered = remember(replies, query) {
        if (query.isBlank()) replies else replies.filter {
            it.title.lowercase(Locale.ROOT).contains(query) || it.text.lowercase(Locale.ROOT).contains(query)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Respostas rápidas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onClose) { Text("Fechar") }
        }
        Text(
            "Toque em Usar para voltar e preencher a caixa de mensagem que estava aberta.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar resposta") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Nova resposta") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                item { Text(if (replies.isEmpty()) "Nenhuma resposta salva." else "Nenhuma resposta encontrada.") }
            }
            items(filtered, key = { it.id }) { reply ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(reply.title.ifBlank { "Sem título" }, fontWeight = FontWeight.Bold)
                        Text(reply.text, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { onApply(reply.text) }, modifier = Modifier.weight(1f)) { Text("Usar") }
                            OutlinedButton(onClick = { editing = reply }, modifier = Modifier.weight(1f)) { Text("Editar") }
                            OutlinedButton(
                                onClick = { scope.launch { repository.removeQuickReply(reply.id) } },
                                modifier = Modifier.weight(1f),
                            ) { Text("Excluir") }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val current = editing
        QuickReplyEditorDialog(
            initialTitle = current?.title.orEmpty(),
            initialText = current?.text.orEmpty(),
            onDismiss = { creating = false; editing = null },
            onSave = { title, text ->
                scope.launch {
                    repository.upsertQuickReply(
                        QuickReply(
                            id = current?.id ?: "reply-${System.currentTimeMillis()}",
                            title = title.trim(),
                            text = text.trim(),
                            createdAtMillis = current?.createdAtMillis ?: System.currentTimeMillis(),
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun QuickReplyEditorDialog(
    initialTitle: String,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialText.isBlank()) "Nova resposta" else "Editar resposta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text("Mensagem completa") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(title, text) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

const val ACTION_APPLY_QUICK_REPLY = "br.com.mapeiaia.rotacerta.APPLY_QUICK_REPLY"
const val EXTRA_QUICK_REPLY_TEXT = "quick_reply_text"
const val EXTRA_QUICK_REPLY_TARGET_PACKAGE = "quick_reply_target_package"
const val EXTRA_QUICK_REPLY_CREATE = "quick_reply_create"
