from pathlib import Path

activity = Path('app/src/main/java/br/com/mapeiaia/rotacerta/BlaBlaCarCollectorActivity.kt')
text = activity.read_text(encoding='utf-8')
needle = 'import androidx.compose.foundation.layout.Row\n'
if 'import androidx.compose.foundation.layout.RowScope\n' not in text:
    text = text.replace(needle, needle + 'import androidx.compose.foundation.layout.RowScope\n')
text = text.replace(
    'json.decodeFromString(prefs.getString("trips", "[]") ?: "[]")',
    'json.decodeFromString<List<CollectorTrip>>(prefs.getString("trips", "[]") ?: "[]")',
)
text = text.replace(
    'Regex("(?i)([^\\n]{2,50})\\s+(?:→|para)\\s+([^\\n]{2,50})")',
    'Regex("(?i)([^\\\\n]{2,50})\\\\s+(?:→|para)\\\\s+([^\\\\n]{2,50})")',
)
activity.write_text(text, encoding='utf-8')

build = Path('app/build.gradle.kts')
text = build.read_text(encoding='utf-8')
text = text.replace('versionCode = 5070', 'versionCode = 5130')
text = text.replace('versionName = "0.1.146"', 'versionName = "0.1.152"')
build.write_text(text, encoding='utf-8')
