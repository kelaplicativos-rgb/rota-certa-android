from pathlib import Path
import runpy
import re

ROOT = Path(__file__).resolve().parents[1]
runpy.run_path(str(ROOT / "scripts/apply_user_fixes_0149.py"), run_name="__main__")

# Versão: pacote autorizado invisível + tamanho persistente da bolinha principal.
gradle = ROOT / "app/build.gradle.kts"
text = gradle.read_text()
text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5110', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.150"', text, count=1)
gradle.write_text(text)

# Armazenamento dedicado: não mistura tamanho da bolinha principal com escala da grade/pop-up.
store = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/MainBubbleSizeStore.kt"
store.write_text('''package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.Intent

object MainBubbleSizeStore {
    const val ACTION_SIZE_CHANGED = "br.com.mapeiaia.rotacerta.MAIN_BUBBLE_SIZE_CHANGED"
    const val EXTRA_SIZE_DP = "main_bubble_size_dp"
    private const val PREFS = "rota_certa_main_bubble_appearance"
    private const val KEY_SIZE_DP = "main_bubble_size_dp"
    const val MIN_SIZE_DP = 52
    const val MAX_SIZE_DP = 96
    const val DEFAULT_SIZE_DP = 66

    fun read(context: Context): Int = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_SIZE_DP, DEFAULT_SIZE_DP)
        .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)

    fun save(context: Context, value: Int) {
        val normalized = value.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SIZE_DP, normalized)
            .commit()
        context.sendBroadcast(
            Intent(ACTION_SIZE_CHANGED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_SIZE_DP, normalized),
        )
    }
} // main_bubble_size_persistent_0_1_150
''')

# Tela de aplicativos: inclui pacotes salvos/capturados sem atividade launcher.
picker = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/InstalledRideAppPickerActivity.kt"
text = picker.read_text()
old = '''    LaunchedEffect(Unit) {
        applications = withContext(Dispatchers.Default) { loadLaunchableApplications(context.packageManager, context.packageName) }
        loading = false
    }
'''
new = '''    LaunchedEffect(Unit) {
        applications = withContext(Dispatchers.Default) {
            val launchable = loadLaunchableApplications(context.packageManager, context.packageName)
            val visiblePackages = launchable.asSequence().map { it.packageName }.toSet()
            val relatedPackages = (selectedPackages + captures.map { it.packageName })
                .mapNotNull(SelectedRideAppStore::normalize)
                .toSortedSet()
            val hiddenOrRemoved = relatedPackages
                .filterNot { it in visiblePackages }
                .map { packageName -> resolveStoredApplication(context.packageManager, packageName) }
            (hiddenOrRemoved + launchable).distinctBy { it.packageName }
        }
        loading = false
    } // expose_hidden_authorized_packages_0_1_150
'''
if old not in text:
    raise SystemExit("Carregamento de aplicativos não encontrado")
text = text.replace(old, new, 1)
anchor = 'private fun loadLaunchableApplications(packageManager: PackageManager, ownPackageName: String): List<InstalledRideAppInfo> {\n'
helper = '''private fun resolveStoredApplication(packageManager: PackageManager, packageName: String): InstalledRideAppInfo {
    val label = runCatching {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(applicationInfo).toString().ifBlank { packageName }
    }.getOrElse { "Pacote salvo ou aplicativo removido" }
    return InstalledRideAppInfo(label = label, packageName = packageName)
} // expose_hidden_authorized_packages_0_1_150

'''
if anchor not in text:
    raise SystemExit("Função de aplicativos iniciáveis não encontrada")
text = text.replace(anchor, helper + anchor, 1)
picker.write_text(text)

# Aparência: estado local durante o arraste; grava somente ao concluir e não volta ao início.
main = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
text = main.read_text()
old_ui = '''    val popupStore = remember { PopupAppearanceStore(context) }
    var popupScale by remember { mutableStateOf(popupStore.scale()) }

    ExpandableCard(title = "Bolinha e aparência", initiallyExpanded = false) {
        Text("Tamanho da bolinha central: ${settings.bubbleSizeDp.coerceIn(52, 96)} dp", fontWeight = FontWeight.Bold)
        Slider(
            value = settings.bubbleSizeDp.coerceIn(52, 96).toFloat(),
            onValueChange = { raw -> onChange(settings.copy(bubbleSizeDp = raw.roundToInt().coerceIn(52, 96))) },
            valueRange = 52f..96f,
            steps = 43,
            modifier = Modifier.fillMaxWidth(),
        )
'''
new_ui = '''    val popupStore = remember { PopupAppearanceStore(context) }
    var popupScale by remember { mutableStateOf(popupStore.scale()) }
    var mainBubbleSizeDp by remember { mutableStateOf(MainBubbleSizeStore.read(context)) }

    ExpandableCard(title = "Bolinha e aparência", initiallyExpanded = false) {
        Text("Tamanho da bolinha principal: $mainBubbleSizeDp dp", fontWeight = FontWeight.Bold)
        Slider(
            value = mainBubbleSizeDp.toFloat(),
            onValueChange = { raw -> mainBubbleSizeDp = raw.roundToInt().coerceIn(MainBubbleSizeStore.MIN_SIZE_DP, MainBubbleSizeStore.MAX_SIZE_DP) },
            valueRange = MainBubbleSizeStore.MIN_SIZE_DP.toFloat()..MainBubbleSizeStore.MAX_SIZE_DP.toFloat(),
            steps = MainBubbleSizeStore.MAX_SIZE_DP - MainBubbleSizeStore.MIN_SIZE_DP - 1,
            onValueChangeFinished = {
                MainBubbleSizeStore.save(context, mainBubbleSizeDp)
                onChange(settings.copy(bubbleSizeDp = mainBubbleSizeDp))
            },
            modifier = Modifier.fillMaxWidth(),
        )
'''
if old_ui not in text:
    raise SystemExit("Bloco do controle de tamanho da bolinha não encontrado")
text = text.replace(old_ui, new_ui, 1)
main.write_text(text)

# Serviço: cria a bolinha com o tamanho persistido e recebe atualização imediata.
service = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
text = service.read_text()
text = text.replace(
    '        dp(currentSettings.bubbleSizeDp.coerceIn(52, 96)),\n        dp(currentSettings.bubbleSizeDp.coerceIn(52, 96)),',
    '        dp(MainBubbleSizeStore.read(applicationContext)),\n        dp(MainBubbleSizeStore.read(applicationContext)),',
    1,
)
field_anchor = '    private val screenshotInProgress = AtomicBoolean(false)\n'
receiver = '''    private var mainBubbleSizeReceiverRegistered150 = false
    private val mainBubbleSizeReceiver150 = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MainBubbleSizeStore.ACTION_SIZE_CHANGED) return
            val sizeDp = intent.getIntExtra(
                MainBubbleSizeStore.EXTRA_SIZE_DP,
                MainBubbleSizeStore.read(applicationContext),
            )
            applyBubbleSizeImmediately148(sizeDp)
        }
    } // main_bubble_size_live_receiver_0_1_150
'''
if field_anchor not in text:
    raise SystemExit("Ponto dos campos do serviço não encontrado")
text = text.replace(field_anchor, receiver + field_anchor, 1)
create_anchor = '''        super.onCreate()
'''
create_insert = '''        super.onCreate()
        if (!mainBubbleSizeReceiverRegistered150) {
            ContextCompat.registerReceiver(
                this,
                mainBubbleSizeReceiver150,
                IntentFilter(MainBubbleSizeStore.ACTION_SIZE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            mainBubbleSizeReceiverRegistered150 = true
        }
'''
if create_anchor not in text:
    raise SystemExit("onCreate do serviço não encontrado")
text = text.replace(create_anchor, create_insert, 1)
# Garante o tamanho persistido também logo após a criação do overlay.
text = text.replace(
    '            showOverlay(RadarColor.Idle)\n',
    '            showOverlay(RadarColor.Idle)\n            applyBubbleSizeImmediately148(MainBubbleSizeStore.read(applicationContext))\n',
    1,
)
# Libera o receiver quando o serviço encerra.
destroy_anchor = '    override fun onDestroy() {\n'
if destroy_anchor in text:
    text = text.replace(
        destroy_anchor,
        '''    override fun onDestroy() {
        if (mainBubbleSizeReceiverRegistered150) {
            runCatching { unregisterReceiver(mainBubbleSizeReceiver150) }
            mainBubbleSizeReceiverRegistered150 = false
        }
''',
        1,
    )
service.write_text(text)

contract = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/HiddenAuthorizedAndBubbleSize150ContractTest.kt"
contract.write_text('''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class HiddenAuthorizedAndBubbleSize150ContractTest {
    @Test fun hiddenPackagesAreVisibleAndMainBubbleSizePersists() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta")
        val picker = File(root, "InstalledRideAppPickerActivity.kt").readText()
        assertTrue("expose_hidden_authorized_packages_0_1_150" in picker)
        assertTrue("resolveStoredApplication" in picker)
        val main = File(root, "MainActivity.kt").readText()
        assertTrue("MainBubbleSizeStore.save" in main)
        assertTrue("onValueChangeFinished" in main)
        val service = File(root, "LiveRideAccessibilityService.kt").readText()
        assertTrue("main_bubble_size_live_receiver_0_1_150" in service)
        assertTrue("MainBubbleSizeStore.read(applicationContext)" in service)
        assertTrue(File(root, "MainBubbleSizeStore.kt").readText().contains("main_bubble_size_persistent_0_1_150"))
    }
}
''')

print("0.1.150 aplicada: pacote invisível exposto e tamanho da bolinha principal persistente")
