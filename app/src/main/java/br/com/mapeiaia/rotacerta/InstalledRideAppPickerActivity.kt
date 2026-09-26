package br.com.mapeiaia.rotacerta

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class InstalledRideAppPickerActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AuthorizedAppsAndCardsScreen(
                    onClose = ::finish,
                    onSave = ::saveSelectedApplications,
                    onDeleteCard = { id -> ManualAppScreenCaptureStore.remove(applicationContext, id) },
                    onDeletePackage = ::confirmDeletePackage,
                )
            }
        }
    }

    private fun saveSelectedApplications(packages: Set<String>) {
        lifecycleScope.launch {
            val previous = SelectedRideAppStore.read(applicationContext)
            val normalized = DriverAppPackagePolicy0162.sanitize(packages, applicationContext.packageName).toSortedSet()
            val removed = previous - normalized
            removed.forEach { ManualAppScreenCaptureStore.removePackage(applicationContext, it) }
            SelectedRideAppStore.save(applicationContext, normalized)
            val current = settingsRepository.settings.first()
            settingsRepository.saveSettings(current.copy(restrictToSelectedRideApps = true, extraMonitoredPackages = normalized.joinToString(",")))
            Toast.makeText(applicationContext, "Aplicativos autorizados atualizados.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun confirmDeletePackage(packageName: String, afterDelete: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Remover aplicativo autorizado?")
            .setMessage("O farol deixará de ler este aplicativo e todos os cards vinculados serão apagados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Remover") { _, _ ->
                lifecycleScope.launch {
                    val remaining = SelectedRideAppStore.read(applicationContext) - packageName
                    ManualAppScreenCaptureStore.removePackage(applicationContext, packageName)
                    SelectedRideAppStore.save(applicationContext, remaining)
                    val current = settingsRepository.settings.first()
                    settingsRepository.saveSettings(current.copy(restrictToSelectedRideApps = true, extraMonitoredPackages = remaining.joinToString(",")))
                    afterDelete()
                    Toast.makeText(applicationContext, "Aplicativo removido e estado autorizado limpo.", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}

private data class InstalledRideAppInfo(val label: String, val packageName: String)

@Composable
private fun AuthorizedAppsAndCardsScreen(
    onClose: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onDeleteCard: (String) -> Unit,
    onDeletePackage: (String, () -> Unit) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var applications by remember { mutableStateOf<List<InstalledRideAppInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf(SelectedRideAppStore.read(context)) }
    var captures by remember { mutableStateOf(ManualAppScreenCaptureStore.readAll(context)) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        applications = withContext(Dispatchers.Default) { loadLaunchableApplications(context.packageManager, context.packageName) }
        loading = false
    }

    val filtered = remember(applications, search) {
        val query = search.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) applications else applications.filter { it.label.lowercase(Locale.ROOT).contains(query) || it.packageName.contains(query) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Aplicativos que ativam a leitura", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Selecione somente aplicativos de corridas. Sistema, launcher, teclado, arquivos e ChatGPT são bloqueados automaticamente.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClose) { Text("Fechar") }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Buscar aplicativo instalado") }, singleLine = true)
        Text("Autorizados: ${selectedPackages.size} • Cards: ${captures.size}", style = MaterialTheme.typography.bodySmall)
        if (loading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedPackages
                    val appCards = captures.filter { it.packageName == app.packageName }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth().clickable {
                                selectedPackages = if (checked) selectedPackages - app.packageName else selectedPackages + app.packageName
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                    Text("${appCards.size} card(s) complementar(es)", style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(checked = checked, onCheckedChange = { enabled -> selectedPackages = if (enabled) selectedPackages + app.packageName else selectedPackages - app.packageName })
                            }
                            appCards.forEach { card ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(card.createdAtMillis)), style = MaterialTheme.typography.bodySmall)
                                        if (card.textPreview.isNotBlank()) Text(card.textPreview.take(180), style = MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick = {
                                            onDeleteCard(card.id)
                                            captures = ManualAppScreenCaptureStore.readAll(context)
                                        }, modifier = Modifier.fillMaxWidth()) { Text("Excluir somente este card") }
                                    }
                                }
                            }
                            if (checked) {
                                OutlinedButton(onClick = {
                                    onDeletePackage(app.packageName) {
                                        selectedPackages = SelectedRideAppStore.read(context)
                                        captures = ManualAppScreenCaptureStore.readAll(context)
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Remover aplicativo e capturas") }
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = { onSave(selectedPackages) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar aplicativos que ativam a leitura") }
    }
}

private fun loadLaunchableApplications(packageManager: PackageManager, ownPackageName: String): List<InstalledRideAppInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION") packageManager.queryIntentActivities(intent, 0)
    }
    return resolved.asSequence().mapNotNull { info ->
        val packageName = info.activityInfo?.packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (!DriverAppPackagePolicy0162.isEligible(packageName, ownPackageName)) return@mapNotNull null
        InstalledRideAppInfo(runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(packageName), packageName)
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }.toList()
}
