from pathlib import Path

path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
text = path.read_text(encoding="utf-8")

start_marker = "@Composable\nprivate fun AboutRotaCertaCard() {"
end_marker = "\n\n@Composable\nprivate fun SavedPlacesCard("

start = text.find(start_marker)
if start < 0:
    raise SystemExit("AboutRotaCertaCard start marker not found")
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit("AboutRotaCertaCard end marker not found")
if text.find(start_marker, start + 1) >= 0:
    raise SystemExit("More than one AboutRotaCertaCard definition found")

replacement = """@Composable
private fun AboutRotaCertaCard() {
    br.com.mapeiaia.rotacerta.versioncenter.VersionCenterCard()
}"""

updated = text[:start] + replacement + text[end:]
if updated == text:
    raise SystemExit("No change produced")

path.write_text(updated, encoding="utf-8")
print("Materialized VersionCenterCard into AboutRotaCertaCard")
