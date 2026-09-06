package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Editable workspace layered on top of the canonical BlaBlaCar browser-script registry.
 *
 * The APK asset remains the immutable fallback. Saving an override changes only this
 * device's effective script for the already-registered request; restoring removes the
 * override and immediately returns execution to the packaged asset.
 */
internal class BlaBlaScriptWorkspace0486(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun overrideScript(request: BlaBlaBrowserRequest): String? =
        prefs.getString(scriptKey(request), null)?.takeIf(String::isNotBlank)

    fun isOverridden(request: BlaBlaBrowserRequest): Boolean =
        overrideScript(request) != null

    fun saveOverride(request: BlaBlaBrowserRequest, script: String): String? {
        BlaBlaScriptWorkspacePolicy0486.validationError(script)?.let { return it }
        return if (prefs.edit().putString(scriptKey(request), script).commit()) {
            null
        } else {
            "Não foi possível salvar o script neste aparelho."
        }
    }

    fun restoreOriginal(request: BlaBlaBrowserRequest): Boolean =
        prefs.edit().remove(scriptKey(request)).commit()

    fun dateScopeEnabledRequests(): Set<BlaBlaBrowserRequest> {
        val stored = prefs.getStringSet(KEY_DATE_SCOPE_ENABLED, null)
            ?: return BlaBlaDateScopeScriptCatalog0449.dateScopeDefaultRequests0478
        return stored.mapNotNullTo(linkedSetOf()) { raw ->
            runCatching { BlaBlaBrowserRequest.valueOf(raw) }.getOrNull()
        }.filterTo(linkedSetOf(), BlaBlaDateScopeScriptCatalog0449.all::contains)
    }

    fun setDateScopeEnabled(request: BlaBlaBrowserRequest, enabled: Boolean): Boolean {
        val next = dateScopeEnabledRequests().toMutableSet().apply {
            if (enabled) add(request) else remove(request)
        }
        return prefs.edit()
            .putStringSet(KEY_DATE_SCOPE_ENABLED, next.mapTo(linkedSetOf(), BlaBlaBrowserRequest::name))
            .commit()
    }

    fun restoreDateScopeDefaults(): Boolean =
        prefs.edit().remove(KEY_DATE_SCOPE_ENABLED).commit()

    private fun scriptKey(request: BlaBlaBrowserRequest): String =
        "script_override_" + request.name

    private companion object {
        const val PREFS_NAME = "rota_certa_blablacar_script_workspace_0486"
        const val KEY_DATE_SCOPE_ENABLED = "date_scope_enabled_requests"
    }
}

internal object BlaBlaScriptWorkspacePolicy0486 {
    const val MAX_SCRIPT_CHARS = 256_000

    fun validationError(script: String): String? = when {
        script.isBlank() -> "O script não pode ficar vazio."
        script.length > MAX_SCRIPT_CHARS ->
            "O script excede o limite de ${MAX_SCRIPT_CHARS} caracteres."
        else -> null
    }
}

@Composable
internal fun BlaBlaScriptsScreen0486(
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val workspace = remember(context) { BlaBlaScriptWorkspace0486(context) }
    val registry = remember(context) { BlaBlaBrowserScriptRegistry(context) }
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<BlaBlaBrowserRequest?>(null) }
    var draft by remember { mutableStateOf("") }
    var editorError by remember { mutableStateOf<String?>(null) }

    val enabled = remember(revision) { workspace.dateScopeEnabledRequests() }
    val overriddenCount = remember(revision) {
        BlaBlaDateScopeScriptCatalog0449.all.count(workspace::isOverridden)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Scripts do coletor", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ponto único para visualizar, colar, editar, corrigir e restaurar os scripts que o coletor BlaBlaCar já executa.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "32 registrados • $overriddenCount personalizados • ${enabled.size} ativos em Sincronizar por data/período",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "O original do APK nunca é apagado. Um script personalizado substitui somente aquele request; Restaurar original remove o override.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        workspace.restoreDateScopeDefaults()
                        revision++
                        onChanged("Seleção padrão de scripts restaurada para Sincronizar por data/período.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Restaurar seleção padrão")
                }
            }
        }

        BlaBlaDateScopeScriptCatalog0449.groups.forEach { group ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium)
                    Text(group.description, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    group.requests.forEachIndexed { index, request ->
                        if (index > 0) HorizontalDivider()
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(BlaBlaDateScopeScriptCatalog0449.label(request))
                                    Text(
                                        request.assetName + " • " +
                                            BlaBlaDateScopeScriptCatalog0449.operationLabel(request),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        if (workspace.isOverridden(request)) "Personalizado neste aparelho" else "Original do APK",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        editing = request
                                        draft = registry.template(request)
                                        editorError = null
                                    },
                                ) {
                                    Text("Editar")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Usar em Sincronizar por data/período",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = request in enabled,
                                    onCheckedChange = { checked ->
                                        if (workspace.setDateScopeEnabled(request, checked)) {
                                            revision++
                                            onChanged(
                                                "${BlaBlaDateScopeScriptCatalog0449.label(request)} " +
                                                    if (checked) "ativado para data/período." else "desativado para data/período.",
                                            )
                                        } else {
                                            onChanged("Não foi possível atualizar a seleção do script.")
                                        }
                                    },
                                )
                            }
                            if (request.operation == BlaBlaBrowserOperation.REMOTE_WRITE) {
                                Text(
                                    "REMOTE_WRITE protegido: editar ou ativar aqui não executa uma alteração remota sozinho. " +
                                        "A escrita continua exigindo a operação explícita já existente no orquestrador.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { request ->
        val original = remember(request) { registry.originalTemplate(request) }
        AlertDialog(
            onDismissRequest = {
                editing = null
                editorError = null
            },
            title = {
                Column {
                    Text(BlaBlaDateScopeScriptCatalog0449.label(request))
                    Text(
                        request.assetName + " • " + BlaBlaDateScopeScriptCatalog0449.operationLabel(request),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Cole ou edite o JavaScript abaixo. Placeholders {{...}} continuam sendo preenchidos pelo mesmo request. " +
                            "Se algum placeholder ficar sem valor, o registry bloqueia a execução antes de enviar ao WebView.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            editorError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp),
                        label = { Text("JavaScript efetivo") },
                        minLines = 12,
                    )
                    Text(
                        "${draft.length} caracteres • original ${original.length}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    editorError?.let {
                        Text("⚠️ $it", color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = {
                            if (workspace.restoreOriginal(request)) {
                                draft = original
                                revision++
                                editorError = null
                                onChanged("${BlaBlaDateScopeScriptCatalog0449.label(request)} restaurado para o original do APK.")
                            } else {
                                editorError = "Não foi possível restaurar o script original."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Restaurar original")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val error = if (draft == original) {
                            if (workspace.restoreOriginal(request)) null else "Não foi possível restaurar o script original."
                        } else {
                            workspace.saveOverride(request, draft)
                        }
                        if (error == null) {
                            revision++
                            editing = null
                            editorError = null
                            onChanged(
                                "${BlaBlaDateScopeScriptCatalog0449.label(request)} salvo. " +
                                    "O coletor usará esta versão na próxima execução desse request.",
                            )
                        } else {
                            editorError = error
                        }
                    },
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editing = null
                        editorError = null
                    },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}
