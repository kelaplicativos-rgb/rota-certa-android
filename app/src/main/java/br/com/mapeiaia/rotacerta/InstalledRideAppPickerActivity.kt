package br.com.mapeiaia.rotacerta

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class InstalledRideAppPickerActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                InstalledRideAppPickerScreen(
                    onClose = ::finish,
                    onSave = ::saveSelectedApplications,
                )
            }
        }
    }

    private fun saveSelectedApplications(packages: Set<String>) {
        lifecycleScope.launch {
            val normalized = packages.mapNotNull(SelectedRideAppStore::normalize).toSortedSet()
            SelectedRideAppStore.save(applicationContext, normalized)
            val current = settingsRepository.settings.first()
            settingsRepository.saveSettings(
                current.copy(
                    restrictToSelectedRideApps = true,
                    extraMonitoredPackages = normalized.joinToString(","),
                ),
            )
            Toast.makeText(
                applicationContext,
                if (normalized.isEmpty()) "Nenhum aplicativo selecionado. A leitura ao vivo ficou pausada."
                else "${normalized.size} aplicativo(s) selecionado(s) para leitura.",
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }
}

private data class InstalledRideAppInfo(
    val label: String,
    val packageName: String,
)

@Composable
private fun InstalledRideAppPickerScreen(
    onClose: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val packageManager = context.packageManager
    var applications by remember { mutableStateOf<List<InstalledRideAppInfo>>(emptyList()) }
    var selectedPackages by remember {
        mutableStateOf(
            SelectedRideAppStore.selectedPackages(
                context = context,
                legacySettings = null,
            ),
        )
    }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        applications = withContext(Dispatchers.Default) {
            loadLaunchableApplications(packageManager, context.packageName)
        }
        loading = false
    }

    val filtered = remember(applications, search) {
        val query = search.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) applications else applications.filter { app ->
            app.label.lowercase(Locale.ROOT).contains(query) ||
                app.packageName.lowercase(Locale.ROOT).contains(query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Selecionar aplicativos para leitura", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("A bolinha lerá somente os aplicativos marcados.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClose) { Text("Fechar") }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar aplicativo instalado") },
            singleLine = true,
        )

        Text(
            "Selecionados: ${selectedPackages.size}  •  Encontrados: ${applications.size}",
            style = MaterialTheme.typography.bodySmall,
        )

        if (loading) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Nenhum aplicativo encontrado com essa busca.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedPackages
                    InstalledRideAppRow(
                        app = app,
                        checked = checked,
                        onCheckedChange = { enabled ->
                            selectedPackages = if (enabled) {
                                selectedPackages + app.packageName
                            } else {
                                selectedPackages - app.packageName
                            }
                        },
                    )
                }
            }
        }

        Button(
            onClick = { onSave(selectedPackages) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar aplicativos selecionados")
        }
    }
}

@Composable
private fun InstalledRideAppRow(
    app: InstalledRideAppInfo,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(42.dp))
            } else {
                Spacer(Modifier.size(42.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Bold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun loadLaunchableApplications(
    packageManager: PackageManager,
    ownPackageName: String,
): List<InstalledRideAppInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, 0)
    }

    return resolved
        .asSequence()
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
            if (packageName.isBlank() || packageName == ownPackageName) return@mapNotNull null
            val label = runCatching { resolveInfo.loadLabel(packageManager).toString().trim() }
                .getOrDefault(packageName)
                .ifBlank { packageName }
            InstalledRideAppInfo(label = label, packageName = packageName.lowercase(Locale.ROOT))
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        .toList()
}
