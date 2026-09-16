from pathlib import Path

SOURCE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/trips/OperationalBrowserVideoStyle0568Test.kt')

text = SOURCE.read_text()
if 'internal fun operationalDateLabel0568' not in text:
    raise SystemExit('0.1.568 video-style source is not materialized; refusing to mutate an unexpected source')
if not TEST.exists():
    raise SystemExit('0.1.568 video-style regression test is missing')

# RowScope exposes Modifier.weight directly inside Row content. Importing the internal
# implementation symbol is invalid on the Compose version used by this project.
text = text.replace('import androidx.compose.foundation.layout.weight\n', '')

# The visual route glyph must stay a Kotlin string with escaped newlines rather than
# becoming a source-level multiline quoted string.
text = text.replace(
    'text = "●\n│\n│\n●",',
    'text = "●\\n│\\n│\\n●",',
)

SOURCE.write_text(text)
print('Operational browser video-style 0.1.568 finalized idempotently.')
