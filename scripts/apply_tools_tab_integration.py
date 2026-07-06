from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


main_path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
main = main_path.read_text()
main = replace_once(
    main,
    "if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_HISTORY) {",
    "if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {",
    "allowed tabs",
)
main = replace_once(
    main,
    '''                NavigationBarItem(selected = tab == TAB_ANALYSIS, onClick = { tab = TAB_ANALYSIS }, label = { Text("Analise") }, icon = {})
                NavigationBarItem(selected = tab == TAB_CONFIG, onClick = { tab = TAB_CONFIG }, label = { Text("Config") }, icon = {})
                NavigationBarItem(selected = tab == TAB_HISTORY, onClick = { tab = TAB_HISTORY }, label = { Text("Historico") }, icon = {})''',
    '''                NavigationBarItem(selected = tab == TAB_ANALYSIS, onClick = { tab = TAB_ANALYSIS }, label = { Text("Analise") }, icon = {})
                NavigationBarItem(selected = tab == TAB_CONFIG, onClick = { tab = TAB_CONFIG }, label = { Text("Config") }, icon = {})
                NavigationBarItem(selected = tab == TAB_TOOLS, onClick = { tab = TAB_TOOLS }, label = { Text("Ferramentas") }, icon = {})
                NavigationBarItem(selected = tab == TAB_HISTORY, onClick = { tab = TAB_HISTORY }, label = { Text("Historico") }, icon = {})''',
    "navigation bar",
)
main = replace_once(
    main,
    '''                TAB_HISTORY -> HistoryScreen(history)''',
    '''                TAB_TOOLS -> ToolsScreen(
                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                )
                TAB_HISTORY -> HistoryScreen(history)''',
    "tools tab route",
)
main = replace_once(
    main,
    '''@Composable
private fun HistoryScreen(history: List<AnalysisResult>) {''',
    '''@Composable
private fun ToolsScreen(onOpenBlaBlaCarCollector: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ferramentas", fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletor BlaBlaCar", fontWeight = FontWeight.Bold)
                Text(
                    "Registro manual de viagem logada: passageiros, telefones, WhatsApp, rotas, faturamento, despesas e lucro.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir coletor")
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<AnalysisResult>) {''',
    "tools screen composable",
)
main_path.write_text(main)

manifest_path = Path("app/src/main/AndroidManifest.xml")
manifest = manifest_path.read_text()
manifest = replace_once(
    manifest,
    '''        <activity
            android:name=".BlaBlaCarCollectorActivity"
            android:exported="true"
            android:label="Coletor BlaBlaCar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>''',
    '''        <activity
            android:name=".BlaBlaCarCollectorActivity"
            android:exported="false"
            android:label="Coletor BlaBlaCar" />''',
    "BlaBlaCar launcher activity",
)
manifest_path.write_text(manifest)
