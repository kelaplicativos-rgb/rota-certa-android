package br.com.mapeiaia.rotacerta.versioncenter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.AppBuildInfo

@Composable
fun VersionCenterCard() {
    val context = LocalContext.current
    val documentResult = remember { runCatching { ReleaseHistoryStore.load(context) } }
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Sobre o Rota Certa", fontWeight = FontWeight.Bold)
                Text(if (expanded) "▼" else "▶")
            }

            if (expanded) {
                InstalledBuildSummary()
                documentResult.fold(
                    onSuccess = { document -> VersionCenterContent(context, document) },
                    onFailure = {
                        Text(
                            "Histórico estruturado indisponível nesta instalação. Os dados objetivos da build continuam acessíveis acima.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
                CopyVersionInfoButton(context)
            }
        }
    }
}

@Composable
private fun InstalledBuildSummary() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Versão: ${AppBuildInfo.versionName}")
        Text("Build: ${AppBuildInfo.versionCode}")
        Text("Commit: ${AppBuildInfo.commitShort}")
        Text("Branch: ${AppBuildInfo.branch}")
        Text("Build gerada em: ${AppBuildInfo.buildGeneratedAt}")
    }
}

@Composable
private fun VersionCenterContent(context: Context, document: ReleaseHistoryDocument) {
    val installed = remember(document) {
        document.releases.firstOrNull {
            it.version == AppBuildInfo.versionName && it.build == AppBuildInfo.versionCode
        }?.copy(
            commit = AppBuildInfo.commit,
            branch = AppBuildInfo.branch,
            generatedAt = AppBuildInfo.buildGeneratedAt,
        )
    }
    val activeRegressions = remember(document) {
        VersionHistoryLogic.activeRegressions(document, AppBuildInfo.versionName)
    }
    val modules = remember(document) { VersionHistoryLogic.availableModules(document) }
    var selectedModule by remember { mutableStateOf<String?>(null) }

    VersionCenterSection("O que mudou nesta versão", initiallyExpanded = true) {
        if (installed == null) {
            Text("Detalhes completos indisponíveis para esta versão.", style = MaterialTheme.typography.bodySmall)
        } else {
            ReleaseDetails(installed, document.regressions, installedMarker = true)
            OutlinedButton(
                onClick = {
                    copyToClipboard(
                        context,
                        "Relatório da versão do Rota Certa",
                        VersionReportBuilder.build(installed, document.regressions),
                    )
                    Toast.makeText(context, "Relatório desta versão copiado.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copiar relatório desta versão")
            }
        }
    }

    VersionCenterSection("Histórico de versões") {
        if (modules.isNotEmpty()) {
            Text("Filtrar por módulo", fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedModule == null,
                    onClick = { selectedModule = null },
                    label = { Text("Todos") },
                )
                modules.forEach { module ->
                    FilterChip(
                        selected = selectedModule == module,
                        onClick = { selectedModule = module },
                        label = { Text(module) },
                    )
                }
            }
        }

        val releases = remember(document, selectedModule) {
            VersionHistoryLogic.filterByModule(document.releases, selectedModule)
        }
        if (releases.isEmpty()) {
            Text("Nenhuma versão registrada para este filtro.")
        } else {
            releases.forEach { release ->
                val isInstalled = release.version == AppBuildInfo.versionName && release.build == AppBuildInfo.versionCode
                val displayed = if (isInstalled) {
                    release.copy(
                        commit = AppBuildInfo.commit,
                        branch = AppBuildInfo.branch,
                        generatedAt = AppBuildInfo.buildGeneratedAt,
                    )
                } else {
                    release
                }
                VersionCenterSection(
                    title = buildString {
                        append("${release.version} — Build ${release.build}")
                        if (isInstalled) append(" • INSTALADA")
                    },
                ) {
                    ReleaseDetails(displayed, document.regressions, isInstalled)
                }
            }
        }
    }

    VersionCenterSection("Problemas conhecidos") {
        if (activeRegressions.isEmpty()) {
            Text("Nenhum problema ativo registrado para a versão instalada.", style = MaterialTheme.typography.bodySmall)
        } else {
            activeRegressions.forEach { regression ->
                RegressionDetails(regression)
            }
        }
    }

    VersionCenterSection("Informações técnicas") {
        Text("Fonte semântica local: release_history.json")
        Text("Metadados da build instalada: BuildConfig → AppBuildInfo")
        Text("Schema do histórico: ${document.schemaVersion}")
        Text("Funcionamento: local/offline")
    }
}

@Composable
private fun ReleaseDetails(
    release: ReleaseRecord,
    regressions: List<RegressionRecord>,
    installedMarker: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (installedMarker) Text("Versão instalada", fontWeight = FontWeight.Bold)
        Text("Status: ${VersionReportBuilder.statusLabel(release.status)}")
        release.generatedAt?.takeIf(String::isNotBlank)?.let { Text("Data/hora: $it") }
        release.commit?.takeIf(String::isNotBlank)?.let { Text("Commit: ${it.take(12)}") }
        release.branch?.takeIf(String::isNotBlank)?.let { Text("Branch: $it") }
        if (release.modulesAffected.isNotEmpty()) {
            Text("Módulos: ${release.modulesAffected.joinToString()}")
        }
        if (!release.detailsComplete) {
            Text("Detalhes completos indisponíveis para esta versão.", style = MaterialTheme.typography.bodySmall)
        }
        ChangeGroup("🆕 Implementado", release.implemented)
        ChangeGroup("🛠 Corrigido", release.fixed)
        ChangeGroup("💡 Melhorado / Inovado", release.improved)

        regressions.filter { it.regressionId in release.regressions }.takeIf { it.isNotEmpty() }?.let { items ->
            Text("⚠️ Regressões conhecidas", fontWeight = FontWeight.Bold)
            items.forEach(::RegressionDetails)
        }
        regressions.filter { it.regressionId in release.resolvedRegressions }.takeIf { it.isNotEmpty() }?.let { items ->
            Text("✅ Regressões resolvidas", fontWeight = FontWeight.Bold)
            items.forEach(::RegressionDetails)
        }
    }
}

@Composable
private fun ChangeGroup(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Text(title, fontWeight = FontWeight.Bold)
    items.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun RegressionDetails(regression: RegressionRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(regression.title, fontWeight = FontWeight.Bold)
        if (regression.description.isNotBlank()) Text(regression.description, style = MaterialTheme.typography.bodySmall)
        if (regression.affectedModules.isNotEmpty()) {
            Text("Módulos: ${regression.affectedModules.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
        Text(VersionHistoryLogic.lifecycle(regression), style = MaterialTheme.typography.bodySmall)
        Text("Status: ${regression.status}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VersionCenterSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (expanded) "▼" else "▶")
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun CopyVersionInfoButton(context: Context) {
    OutlinedButton(
        onClick = {
            copyToClipboard(context, "Versão do Rota Certa", AppBuildInfo.copyText())
            Toast.makeText(context, "Informações da versão copiadas.", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Copiar informações da versão")
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
