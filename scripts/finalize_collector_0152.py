from pathlib import Path

activity = Path('app/src/main/java/br/com/mapeiaia/rotacerta/BlaBlaCarCollectorActivity.kt')
text = activity.read_text(encoding='utf-8')
needle = 'import androidx.compose.foundation.layout.Row\n'
if 'import androidx.compose.foundation.layout.RowScope\n' not in text:
    text = text.replace(needle, needle + 'import androidx.compose.foundation.layout.RowScope\n')
activity.write_text(text, encoding='utf-8')

build = Path('app/build.gradle.kts')
text = build.read_text(encoding='utf-8')
text = text.replace('versionCode = 5070', 'versionCode = 5130')
text = text.replace('versionName = "0.1.146"', 'versionName = "0.1.152"')
build.write_text(text, encoding='utf-8')
