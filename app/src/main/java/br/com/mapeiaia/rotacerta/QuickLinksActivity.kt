package br.com.mapeiaia.rotacerta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material3.Checkbox
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

class QuickLinksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                QuickLinksScreen0172(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun QuickLinksScreen0172(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var links by remember { mutableStateOf(QuickLinkStore0172.read(context)) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<QuickLink0172?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<QuickLink0172?>(null) }
    val filtered = remember(links, query) { QuickLinkSearchPolicy0186.filter(links, query) }

    fun persist(updated: List<QuickLink0172>) {
        QuickLinkStore0172.save(context, updated)
        links = QuickLinkStore0172.read(context)
    }

    fun copyUrl(link: QuickLink0172) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Link", link.url))
        Toast.makeText(context, "Link copiado", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Links rápidos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Voltar") }
        }
        Text(
            "Salve até 40 links. Pesquise pelo nome, descrição ou por qualquer trecho do endereço.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(300) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar links") },
            singleLine = true,
        )
        Button(
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = QuickLinkCapacityPolicy0186.canCreate(links.size),
        ) { Text("Adicionar link") }
        if (!QuickLinkCapacityPolicy0186.canCreate(links.size)) {
            Text(
                "Limite de 40 links atingido. Exclua um link antes de adicionar outro.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                links.isEmpty() -> item { Text("Nenhum link salvo.") }
                filtered.isEmpty() -> item { Text("Nenhum link encontrado.") }
                else -> items(filtered, key = { it.id }) { link ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text((if (link.primary) "★ " else "") + link.title, fontWeight = FontWeight.Bold)
                            if (link.description.isNotBlank()) Text(link.description, style = MaterialTheme.typography.bodyMedium)
                            Text(link.url, style = MaterialTheme.typography.bodySmall)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        if (!QuickLinkStore0172.open(context, link)) {
                                            Toast.makeText(context, "Não foi possível abrir este link.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Abrir") }
                                OutlinedButton(onClick = { copyUrl(link) }, modifier = Modifier.weight(1f)) { Text("Copiar link") }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { editing = link }, modifier = Modifier.weight(1f)) { Text("Editar") }
                                OutlinedButton(onClick = { pendingDelete = link }, modifier = Modifier.weight(1f)) { Text("Excluir") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        QuickLinkEditor0172(
            current = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { title, description, url, primary ->
                if (editing == null && !QuickLinkCapacityPolicy0186.canCreate(links.size)) {
                    Toast.makeText(context, "Limite de 40 links atingido.", Toast.LENGTH_LONG).show()
                    return@QuickLinkEditor0172
                }
                val safe = QuickLinkStore0172.normalizeHttpUrl(url)
                if (safe == null) {
                    Toast.makeText(context, "Digite um link http ou https válido.", Toast.LENGTH_LONG).show()
                    return@QuickLinkEditor0172
                }
                val now = System.currentTimeMillis()
                val id = editing?.id ?: "link-$now"
                val base = if (primary) links.map { it.copy(primary = false) } else links
                val updated = base.filterNot { it.id == id } + QuickLink0172(
                    id = id,
                    title = title.trim().ifBlank { safe },
                    description = description.trim(),
                    url = safe,
                    primary = primary,
                    updatedAtMillis = now,
                )
                persist(updated)
                creating = false
                editing = null
            },
        )
    }

    pendingDelete?.let { link ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir link?") },
            text = { Text("Confirme a exclusão de ${link.title}.") },
            confirmButton = {
                TextButton(onClick = {
                    persist(links.filterNot { it.id == link.id })
                    pendingDelete = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun QuickLinkEditor0172(
    current: QuickLink0172?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit,
) {
    var title by remember(current?.id) { mutableStateOf(current?.title.orEmpty()) }
    var description by remember(current?.id) { mutableStateOf(current?.description.orEmpty()) }
    var url by remember(current?.id) { mutableStateOf(current?.url.orEmpty()) }
    var primary by remember(current?.id) { mutableStateOf(current?.primary == true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "Novo link" else "Editar link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it.take(240) }, label = { Text("Descrição (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(url, { url = it.take(2048) }, label = { Text("Link") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = primary, onCheckedChange = { primary = it })
                    Text("Usar como link principal", modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, description, url, primary) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
