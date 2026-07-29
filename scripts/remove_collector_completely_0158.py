from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta"
TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE = ROOT / "app/build.gradle.kts"

# 1) Delete every dedicated Collector source/test tree or file.
for path in [
    MAIN / "BlaBlaCarCollectorActivity.kt",
    MAIN / "coletor",
    TEST / "coletor",
]:
    if path.is_dir():
        shutil.rmtree(path)
    elif path.exists():
        path.unlink()

for base in (MAIN, TEST):
    if not base.exists():
        continue
    for path in list(base.rglob("*")):
        if not path.is_file():
            continue
        lowered = path.name.lower()
        if "collector" in lowered or "coletor" in lowered or "blablacarcollector" in lowered:
            path.unlink()

# 2) Remove the Activity from AndroidManifest.xml.
manifest = MANIFEST.read_text(encoding="utf-8")
manifest = re.sub(
    r'\n\s*<activity\b(?:(?!</activity>|/>).)*?BlaBlaCarCollectorActivity(?:(?!</activity>|/>).)*(?:</activity>|/>)\s*',
    "\n",
    manifest,
    flags=re.S,
)
manifest = re.sub(
    r'\n\s*<activity\s+android:name="\.BlaBlaCarCollectorActivity"[^>]*/>\s*',
    "\n",
    manifest,
    flags=re.S,
)
MANIFEST.write_text(manifest, encoding="utf-8")

# 3) Remove the Collector shortcut from the bubble catalog and action enum.
shortcut = MAIN / "BubbleShortcutModule.kt"
if shortcut.exists():
    text = shortcut.read_text(encoding="utf-8")
    text = re.sub(r'^\s*OpenCollector,\s*\n', '', text, flags=re.M)
    text = re.sub(
        r'\nobject\s+CollectorBubbleShortcutModule\s*:\s*BubbleShortcutModule\s*\{.*?\n\}\s*\n',
        '\n',
        text,
        flags=re.S,
    )
    text = re.sub(r'^\s*CollectorBubbleShortcutModule,\s*\n', '', text, flags=re.M)
    text = text.replace('require(modules.size == 16)', 'require(modules.size == 15)')
    text = text.replace('require(modules.size == 15)', 'require(modules.size == 14)') if 'PassengerFareBubbleShortcutModule' not in text else text
    shortcut.write_text(text, encoding="utf-8")

# 4) Remove Collector dispatch and launcher function from the accessibility service.
service = MAIN / "LiveRideAccessibilityService.kt"
text = service.read_text(encoding="utf-8")
# Remove only the OpenCollector branch. Keep the following ClearClipboard branch intact.
text = re.sub(
    r'^\s*BubbleShortcutAction\.OpenCollector\s*->\s*openCollectorFromBubble\(\)\s*\n',
    '',
    text,
    flags=re.M,
)
text = re.sub(
    r'\n\s*private fun openCollectorFromBubble\s*\([^)]*\)\s*\{.*?\n\s*\}\s*(?=\n\s*private fun)',
    '\n',
    text,
    flags=re.S,
)
text = re.sub(
    r'\n\s*private fun openBlaBlaCarCollector[^\{]*\{.*?\n\s*\}\s*(?=\n\s*private fun)',
    '\n',
    text,
    flags=re.S,
)
service.write_text(text, encoding="utf-8")

# 5) Remove Collector UI and callbacks from MainActivity without touching the farol.
main_activity = MAIN / "MainActivity.kt"
main_text = main_activity.read_text(encoding="utf-8")
main_text = re.sub(
    r'\n\s*onOpenBlaBlaCarCollector\s*=\s*\{.*?\n\s*\},',
    '',
    main_text,
    flags=re.S,
)
main_text = re.sub(r'^\s*onOpenCollector:\s*\(\)\s*->\s*Unit,\s*\n', '', main_text, flags=re.M)
main_text = re.sub(
    r'^\s*ProfessionalBubbleItem\("🚗",\s*"Coletor",\s*false,\s*onOpenCollector\),\s*\n',
    '',
    main_text,
    flags=re.M,
)
main_text = re.sub(r'^\s*onOpenBlaBlaCarCollector:\s*\(\)\s*->\s*Unit,\s*\n', '', main_text, flags=re.M)
main_text = re.sub(
    r'\n\s*Card\(modifier\s*=\s*Modifier\.fillMaxWidth\(\)\)\s*\{\s*\n\s*Column\([^\n]*\)\s*\{\s*\n\s*Text\("Coletor BlaBlaCar".*?\n\s*\}\s*\n\s*\}',
    '',
    main_text,
    flags=re.S,
)
main_text = re.sub(r'^.*label\s*=\s*"Coletor".*\n?', '', main_text, flags=re.M)
main_text = re.sub(r'^.*//\s*label\s*=\s*"Coletor".*\n?', '', main_text, flags=re.M)
main_activity.write_text(main_text, encoding="utf-8")

# 6) Remove remaining Collector-only assertions/references from regular Kotlin tests.
# Contract tests are preserved, but the obsolete Collector expectation is removed.
for path in list(MAIN.rglob("*.kt")) + list(TEST.rglob("*.kt")):
    if not path.exists():
        continue
    content = path.read_text(encoding="utf-8")
    if not re.search(r'Collector|Coletor|BlaBlaCarCollector|OpenCollector', content, re.I):
        continue
    if "test" in path.parts:
        content = re.sub(r'^.*(?:Collector|Coletor|BlaBlaCarCollector|OpenCollector).*$\n?', '', content, flags=re.M | re.I)
    else:
        content = re.sub(r'^.*(?:BlaBlaCarCollector|OpenCollector|CollectorBubbleShortcutModule).*$\n?', '', content, flags=re.M)
    path.write_text(content, encoding="utf-8")

# 7) Version bump after the stable 0.1.157 chain.
gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.158"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5190', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

# 8) Hard fail if executable source or tests still contain Collector references.
for root in (MAIN, TEST):
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix not in {".kt", ".xml"}:
            continue
        content = path.read_text(encoding="utf-8", errors="ignore")
        if re.search(r'BlaBlaCarCollector|CollectorBubbleShortcutModule|OpenCollector|\bColetor\b', content, re.I):
            raise SystemExit(f"collector reference remains: {path}")

print("0.1.158: Collector sources, tests, manifest entry and bubble shortcut removed")