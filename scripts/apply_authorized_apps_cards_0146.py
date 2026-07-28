from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'

# Materializa primeiro as correções de estabilidade e gesto longo já auditadas.
long_press = ROOT / 'scripts/apply_long_press_shortcuts_0145.py'
if long_press.exists():
    import subprocess
    subprocess.run(['python', str(long_press)], check=True)

store = r'''package br.com.mapeiaia.rotacerta

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Captura complementar vinculada a um pacote autorizado. Nunca participa da decisão do farol. */
data class ManualAppScreenCapture(
    val id: String,
    val packageName: String,
    val textPreview: String,
    val imagePath: String?,
    val createdAtMillis: Long,
)

object ManualAppScreenCaptureStore {
    private const val PREFS = "manual_app_screen_capture_146"
    private const val KEY_ITEMS = "items"
    private const val LEGACY_PREFS = "manual_app_screen_capture_138"

    fun save(context: Context, packageName: String, text: String, bitmap: Bitmap?): ManualAppScreenCapture {
        val normalizedPackage = SelectedRideAppStore.normalize(packageName)
            ?: error("Pacote inválido para captura")
        val directory = File(context.filesDir, "manual-captures/$normalizedPackage").apply { mkdirs() }
        val created = System.currentTimeMillis()
        val id = "$created-${UUID.randomUUID()}"
        val image = bitmap?.let {
            File(directory, "$id.png").also { output ->
                output.outputStream().use { stream -> it.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            }
        }
        val capture = ManualAppScreenCapture(
            id = id,
            packageName = normalizedPackage,
            textPreview = text.trim().take(4_000),
            imagePath = image?.absolutePath,
            createdAtMillis = created,
        )
        writeAll(context, readAll(context) + capture)
        return capture
    }

    fun read(context: Context): ManualAppScreenCapture? = readAll(context).maxByOrNull { it.createdAtMillis }

    fun readAll(context: Context): List<ManualAppScreenCapture> {
        migrateLegacy(context)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ManualAppScreenCapture(
                            id = item.getString("id"),
                            packageName = item.getString("packageName"),
                            textPreview = item.optString("textPreview"),
                            imagePath = item.optString("imagePath").takeIf { it.isNotBlank() },
                            createdAtMillis = item.optLong("createdAtMillis"),
                        ),
                    )
                }
            }.sortedByDescending { it.createdAtMillis }
        }.getOrDefault(emptyList())
    }

    fun readForPackage(context: Context, packageName: String): List<ManualAppScreenCapture> {
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return emptyList()
        return readAll(context).filter { it.packageName == normalized }
    }

    fun remove(context: Context, id: String) {
        val current = readAll(context)
        current.firstOrNull { it.id == id }?.imagePath?.let { runCatching { File(it).delete() } }
        writeAll(context, current.filterNot { it.id == id })
    }

    fun removePackage(context: Context, packageName: String) {
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return
        val current = readAll(context)
        current.filter { it.packageName == normalized }.forEach { capture ->
            capture.imagePath?.let { runCatching { File(it).delete() } }
        }
        writeAll(context, current.filterNot { it.packageName == normalized })
    }

    fun clear(context: Context) {
        readAll(context).forEach { it.imagePath?.let { path -> runCatching { File(path).delete() } } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun writeAll(context: Context, captures: List<ManualAppScreenCapture>) {
        val array = JSONArray()
        captures.distinctBy { it.id }.sortedByDescending { it.createdAtMillis }.forEach { capture ->
            array.put(JSONObject().apply {
                put("id", capture.id)
                put("packageName", capture.packageName)
                put("textPreview", capture.textPreview)
                put("imagePath", capture.imagePath.orEmpty())
                put("createdAtMillis", capture.createdAtMillis)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun migrateLegacy(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val packageName = legacy.getString("package", null)?.takeIf { it.isNotBlank() } ?: return
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return
        val existingRaw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
        if (existingRaw == null) {
            val capture = ManualAppScreenCapture(
                id = "legacy-${legacy.getLong("created", System.currentTimeMillis())}",
                packageName = normalized,
                textPreview = legacy.getString("text", "").orEmpty(),
                imagePath = legacy.getString("image", null),
                createdAtMillis = legacy.getLong("created", System.currentTimeMillis()),
            )
            writeAll(context, listOf(capture))
        }
        legacy.edit().clear().apply()
    }
}
'''
(PKG / 'ManualAppScreenCaptureStore.kt').write_text(store, encoding='utf-8')

module_path = PKG / 'BubbleShortcutModule.kt'
module = module_path.read_text(encoding='utf-8')
module = module.replace('    CaptureCurrentAppAndScreen,\n}', '    CaptureCurrentAppAndScreen,\n    OpenAuthorizedAppsAndCards,\n}')
module = module.replace('    DefineDestinationAtCurrentLocation,\n}', '    DefineDestinationAtCurrentLocation,\n    CaptureCurrentAppAndScreen,\n}')
module = module.replace(
'''        action = BubbleShortcutAction.CaptureCurrentAppAndScreen,
    )''',
'''        action = BubbleShortcutAction.OpenAuthorizedAppsAndCards,
        doubleTapAction = BubbleShortcutQuickAction.CaptureCurrentAppAndScreen,
    )''',
)
module_path.write_text(module, encoding='utf-8')

service_path = PKG / 'LiveRideAccessibilityService.kt'
service = service_path.read_text(encoding='utf-8')
service = service.replace(
'            BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation -> openDestinationConfirmationFromBubble138()\n',
'            BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation -> openDestinationConfirmationFromBubble138()\n            BubbleShortcutQuickAction.CaptureCurrentAppAndScreen -> captureCurrentAppAndScreen138()\n',
)
service = service.replace(
'            BubbleShortcutAction.CaptureCurrentAppAndScreen -> captureCurrentAppAndScreen138()\n',
'            BubbleShortcutAction.CaptureCurrentAppAndScreen -> captureCurrentAppAndScreen138()\n            BubbleShortcutAction.OpenAuthorizedAppsAndCards -> openAuthorizedAppsAndCards146()\n',
)
marker = '    private fun captureCurrentAppAndScreen138() {'
helper = '''    private fun openAuthorizedAppsAndCards146() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        runCatching {
            startActivity(
                Intent(this, InstalledRideAppPickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }.onFailure { toast("Não consegui abrir os aplicativos autorizados.") }
    }

'''
if helper not in service:
    service = service.replace(marker, helper + marker)
service_path.write_text(service, encoding='utf-8')

activity = r'''package br.com.mapeiaia.rotacerta

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
            val normalized = packages.mapNotNull(SelectedRideAppStore::normalize).toSortedSet()
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
                Text("Aplicativos e cards autorizados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Pacotes autorizam a leitura. Cards são apenas complementos para OCR e reconhecimento.", style = MaterialTheme.typography.bodySmall)
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
                                }, modifier = Modifier.fillMaxWidth()) { Text("Excluir aplicativo e cards") }
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = { onSave(selectedPackages) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar aplicativos autorizados") }
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
        if (packageName.isBlank() || packageName == ownPackageName) return@mapNotNull null
        InstalledRideAppInfo(runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(packageName), packageName)
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }.toList()
}
'''
(PKG / 'InstalledRideAppPickerActivity.kt').write_text(activity, encoding='utf-8')

manifest_path = ROOT / 'app/src/main/AndroidManifest.xml'
manifest = manifest_path.read_text(encoding='utf-8').replace('android:label="Selecionar aplicativos de corrida"', 'android:label="Aplicativos e cards autorizados"')
manifest_path.write_text(manifest, encoding='utf-8')

gradle_path = ROOT / 'app/build.gradle.kts'
gradle = gradle_path.read_text(encoding='utf-8')
gradle = re.sub(r'versionCode = \d+', 'versionCode = 5070', gradle, count=1)
gradle = re.sub(r'versionName = "[^"]+"', 'versionName = "0.1.146"', gradle, count=1)
gradle_path.write_text(gradle, encoding='utf-8')

# Testes de contrato de fonte, complementares aos testes funcionais já existentes.
test = r'''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorizedAppsCards146ContractTest {
    private val root = File(System.getProperty("user.dir"))
    private fun source(path: String) = File(root, path).readText()

    @Test fun cardsAreComplementaryAndGroupedByPackage() {
        val store = source("src/main/java/br/com/mapeiaia/rotacerta/ManualAppScreenCaptureStore.kt")
        assertTrue(store.contains("readForPackage"))
        assertTrue(store.contains("removePackage"))
        assertTrue(store.contains("Nunca participa da decisão do farol"))
    }

    @Test fun captureUsesShortTapForManagerAndLongPressForCapture() {
        val module = source("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt")
        val overlay = source("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt")
        assertTrue(module.contains("OpenAuthorizedAppsAndCards"))
        assertTrue(module.contains("CaptureCurrentAppAndScreen"))
        assertTrue(overlay.contains("postDelayed(longPressAction, 1_500L)"))
        assertFalse(overlay.contains("onDoubleTap(event"))
    }

    @Test fun visibleSaveHomeButtonIsAbsent() {
        val main = source("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
        assertFalse(main.contains("Text(\"Salvar Casa\")"))
        assertTrue(main.contains("onValueChangeFinished = onSave"))
    }
}
'''
(ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta/AuthorizedAppsCards146ContractTest.kt').write_text(test, encoding='utf-8')

print('Applied authorized apps and complementary cards contract 0.1.146')
