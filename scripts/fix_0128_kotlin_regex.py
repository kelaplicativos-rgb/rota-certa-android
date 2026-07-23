from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app/src/main/java/br/com/mapeiaia/rotacerta"
old = r'Regex("^\d{1,6}(?:[-/][\p{L}\d]+|[\p{L}])?(?:\s|,|\()", RegexOption.IGNORE_CASE)'
new = r'Regex("""^\d{1,6}(?:[-/][\p{L}\d]+|[\p{L}])?(?:\s|,|\()""", RegexOption.IGNORE_CASE)'

for filename in ["RideTextParser.kt", "UniversalScreenAddressParser.kt"]:
    path = root / filename
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Expressao Kotlin nao encontrada em {filename}")
    path.write_text(text.replace(old, new, 1))
