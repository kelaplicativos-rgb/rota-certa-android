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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Editable workspace layered on top of the canonical BlaBlaCar browser-script registry.
 *
 * The APK asset remains the immutable fallback. Saving an override changes only this
 * device's effective script for the already-registered request; restoring removes the
 * override and immediately returns execution to the packaged asset.
 */
internal data class BlaBlaCustomScript0488(
    val id: String,
    val title: String,
    val code: String,
    val targetRequestName: String? = null,
) {
    val targetRequest: BlaBlaBrowserRequest?
        get() = targetRequestName?.let { raw ->
            runCatching { BlaBlaBrowserRequest.valueOf(raw) }.getOrNull()
        }
}

internal enum class BlaBlaScriptsCommand0488 {
    NEW_SCRIPT,
    RESTORE_SELECTION_DEFAULTS,
}

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

    fun customScripts(): List<BlaBlaCustomScript0488> {
        val raw = prefs.getString(KEY_CUSTOM_SCRIPTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val title = item.optString("title").trim()
                    val code = item.optString("code")
                    val target = item.optString("targetRequestName").trim().takeIf(String::isNotEmpty)
                    if (id.isNotEmpty() && title.isNotEmpty() && code.isNotBlank()) {
                        add(
                            BlaBlaCustomScript0488(
                                id = id,
                                title = title,
                                code = code,
                                targetRequestName = target,
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveCustomScript(
        id: String? = null,
        title: String,
        code: String,
        targetRequest: BlaBlaBrowserRequest?,
    ): Pair<BlaBlaCustomScript0488?, String?> {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return null to "Informe um nome para o script."
        BlaBlaScriptWorkspacePolicy0486.validationError(code)?.let { return null to it }

        val item = BlaBlaCustomScript0488(
            id = id?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            title = normalizedTitle,
            code = code,
            targetRequestName = targetRequest?.name,
        )

        if (targetRequest != null) {
            saveOverride(targetRequest, code)?.let { return null to it }
        }

        val next = customScripts()
            .filterNot { it.id == item.id }
            .plus(item)
            .sortedBy { it.title.lowercase() }
        val array = JSONArray()
        next.forEach { script ->
            array.put(
                JSONObject()
                    .put("id", script.id)
                    .put("title", script.title)
                    .put("code", script.code)
                    .put("targetRequestName", script.targetRequestName ?: ""),
            )
        }
        return if (prefs.edit().putString(KEY_CUSTOM_SCRIPTS, array.toString()).commit()) {
            item to null
        } else {
            null to "Não foi possível salvar o novo script neste aparelho."
        }
    }

    fun deleteCustomScript(id: String): Boolean {
        val next = customScripts().filterNot { it.id == id }
        val array = JSONArray()
        next.forEach { script ->
            array.put(
                JSONObject()
                    .put("id", script.id)
                    .put("title", script.title)
                    .put("code", script.code)
                    .put("targetRequestName", script.targetRequestName ?: ""),
            )
        }
        return prefs.edit().putString(KEY_CUSTOM_SCRIPTS, array.toString()).commit()
    }

    private fun scriptKey(request: BlaBlaBrowserRequest): String =
        "script_override_" + request.name

    private companion object {
        const val PREFS_NAME = "rota_certa_blablacar_script_workspace_0486"
        const val KEY_DATE_SCOPE_ENABLED = "date_scope_enabled_requests"
        const val KEY_CUSTOM_SCRIPTS = "custom_scripts_0488"
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
    uiCommand0488: BlaBlaScriptsCommand0488? = null,
    uiCommandToken0488: Int = 0,
) {
    val context = LocalContext.current
    val workspace = remember(context) { BlaBlaScriptWorkspace0486(context) }
    val registry = remember(context) { BlaBlaBrowserScriptRegistry(context) }
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<BlaBlaBrowserRequest?>(null) }
    var draft by remember { mutableStateOf("") }
    var editorError by remember { mutableStateOf<String?>(null) }
    var customEditing by remember { mutableStateOf<BlaBlaCustomScript0488?>(null) }
    var showCustomEditor by remember { mutableStateOf(false) }
    var customTitle by remember { mutableStateOf("") }
    var customCode by remember { mutableStateOf("") }
    var customTarget by remember { mutableStateOf<BlaBlaBrowserRequest?>(null) }
    var customTargetMenuExpanded by remember { mutableStateOf(false) }
    var customError by remember { mutableStateOf<String?>(null) }

    fun openNewCustomScript() {
        customEditing = null
        customTitle = ""
        customCode = ""
        customTarget = null
        customError = null
        showCustomEditor = true
    }

    LaunchedEffect(uiCommandToken0488, uiCommand0488) {
        when (uiCommand0488) {
            BlaBlaScriptsCommand0488.NEW_SCRIPT -> openNewCustomScript()
            BlaBlaScriptsCommand0488.RESTORE_SELECTION_DEFAULTS -> {
                if (workspace.restoreDateScopeDefaults()) {
                    revision++
                    onChanged("Seleção padrão de scripts restaurada para Sincronizar por data/período.")
                } else {
                    onChanged("Não foi possível restaurar a seleção padrão de scripts.")
                }
            }
            null -> Unit
        }
    }

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
                Text(
                    "Use ⋮ para criar um novo script ou restaurar a seleção padrão de sincronização.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val customScripts0488 = remember(revision) { workspace.customScripts() }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Meus scripts", style = MaterialTheme.typography.titleMedium)
                if (customScripts0488.isEmpty()) {
                    Text(
                        "Nenhum script criado. Use ⋮ → Novo script.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    customScripts0488.forEachIndexed { index, script ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(script.title)
                                val target = script.targetRequest
                                Text(
                                    if (target == null) {
                                        "Biblioteca • sem vínculo • não executa automaticamente"
                                    } else {
                                        "Vinculado a " + BlaBlaDateScopeScriptCatalog0449.label(target) +
                                            " • " + BlaBlaDateScopeScriptCatalog0449.operationLabel(target)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    customEditing = script
                                    customTitle = script.title
                                    customCode = script.code
                                    customTarget = script.targetRequest
                                    customError = null
                                    showCustomEditor = true
                                },
                            ) {
                                Text("Editar")
                            }
                        }
                    }
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

    if (showCustomEditor) {
        AlertDialog(
            onDismissRequest = {
                showCustomEditor = false
                customError = null
            },
            title = { Text(if (customEditing == null) "Novo script" else "Editar script") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = {
                            customTitle = it
                            customError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome do script") },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = { customTargetMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            customTarget?.let { "Vincular a: " + BlaBlaDateScopeScriptCatalog0449.label(it) }
                                ?: "Sem vínculo com o coletor",
                        )
                    }
                    DropdownMenu(
                        expanded = customTargetMenuExpanded,
                        onDismissRequest = { customTargetMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sem vínculo • biblioteca") },
                            onClick = {
                                customTarget = null
                                customTargetMenuExpanded = false
                            },
                        )
                        BlaBlaDateScopeScriptCatalog0449.groups.forEach { group ->
                            group.requests.forEach { request ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            group.title + " • " +
                                                BlaBlaDateScopeScriptCatalog0449.label(request),
                                        )
                                    },
                                    onClick = {
                                        customTarget = request
                                        customTargetMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        if (customTarget == null) {
                            "Sem vínculo: o script fica guardado para você editar e preparar, mas não executa automaticamente."
                        } else {
                            "Vinculado: ao salvar, este código vira a versão efetiva dessa ação no coletor. " +
                                "REMOTE_WRITE continua exigindo a operação explícita do orquestrador."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = customCode,
                        onValueChange = {
                            customCode = it
                            customError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp),
                        label = { Text("JavaScript") },
                        minLines = 12,
                    )
                    Text(
                        "${customCode.length} caracteres",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    customError?.let {
                        Text("⚠️ $it", color = MaterialTheme.colorScheme.error)
                    }
                    customEditing?.let { current ->
                        OutlinedButton(
                            onClick = {
                                if (workspace.deleteCustomScript(current.id)) {
                                    showCustomEditor = false
                                    customEditing = null
                                    revision++
                                    onChanged(
                                        "Script removido da biblioteca. Um override já aplicado ao coletor não é apagado automaticamente.",
                                    )
                                } else {
                                    customError = "Não foi possível excluir o script."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Excluir da biblioteca")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val (saved, error) = workspace.saveCustomScript(
                            id = customEditing?.id,
                            title = customTitle,
                            code = customCode,
                            targetRequest = customTarget,
                        )
                        if (error == null && saved != null) {
                            showCustomEditor = false
                            customEditing = null
                            revision++
                            onChanged(
                                if (saved.targetRequest == null) {
                                    "Script ${saved.title} salvo na biblioteca."
                                } else {
                                    "Script ${saved.title} salvo e vinculado a " +
                                        BlaBlaDateScopeScriptCatalog0449.label(saved.targetRequest!!) + "."
                                },
                            )
                        } else {
                            customError = error ?: "Não foi possível salvar o script."
                        }
                    },
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomEditor = false
                        customError = null
                    },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

}
