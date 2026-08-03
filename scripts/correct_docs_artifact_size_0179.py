from pathlib import Path

path = Path("docs/PROJECT_STATUS.md")
text = path.read_text(encoding="utf-8")
old = "ID `8845723352`, 31.599.304 bytes, retenção até 01/11/2026"
new = "ID `8845723352`, 33.710.976 bytes, retenção até 01/11/2026"
if old not in text:
    raise SystemExit("expected artifact size entry not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("artifact size corrected")
