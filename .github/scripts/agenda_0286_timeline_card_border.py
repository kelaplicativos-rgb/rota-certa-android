from pathlib import Path

root = Path(__file__).resolve().parents[2]
ui_path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
gradle_path = root / "app/build.gradle.kts"

ui = ui_path.read_text()
gradle = gradle_path.read_text()

old_import = "import androidx.compose.foundation.background\n"
new_import = "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.background\n"
if "import androidx.compose.foundation.BorderStroke\n" not in ui:
    if old_import not in ui:
        raise SystemExit("TripTimelineUi BorderStroke import anchor missing")
    ui = ui.replace(old_import, new_import, 1)

old_card = '''    Card(\n        modifier = Modifier.fillMaxWidth(),\n        colors = CardDefaults.cardColors(containerColor = cardColor),\n    ) {\n'''
new_card = '''    Card(\n        modifier = Modifier\n            .fillMaxWidth()\n            .padding(vertical = 6.dp),\n        shape = RoundedCornerShape(14.dp),\n        colors = CardDefaults.cardColors(containerColor = cardColor),\n        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),\n    ) {\n'''
if old_card not in ui:
    raise SystemExit("TimelineEntryCard baseline anchor missing or already changed")
ui = ui.replace(old_card, new_card, 1)

if 'versionCode = 5578' not in gradle or 'versionName = "0.1.285"' not in gradle:
    raise SystemExit("0.1.285 version baseline missing")
gradle = gradle.replace('versionCode = 5578', 'versionCode = 5579', 1)
gradle = gradle.replace('versionName = "0.1.285"', 'versionName = "0.1.286"', 1)

ui_path.write_text(ui)
gradle_path.write_text(gradle)
