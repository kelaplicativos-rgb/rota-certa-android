from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

p = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/UserFixes148ContractTest.kt"
text = p.read_text()
text = text.replace('assertTrue("authorized_apps_first_0_1_148" in picker)', 'assertTrue("captured_apps_first_0_1_149" in picker)', 1)
text = text.replace('assertTrue("compareByDescending<InstalledRideAppInfo>" in picker)', 'assertTrue("compareByDescending<InstalledRideAppInfo> { it.packageName in capturedPackages }" in picker)', 1)
p.write_text(text)

p = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/UserSuggestions147ContractTest.kt"
text = p.read_text()
text = text.replace('assertTrue("Tamanho da bolinha central" in File(root, "MainActivity.kt").readText())', 'assertTrue("Tamanho da bolinha principal" in File(root, "MainActivity.kt").readText())', 1)
p.write_text(text)

print("Contratos 0.1.150 atualizados")
