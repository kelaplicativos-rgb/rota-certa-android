from pathlib import Path
p = Path("scripts/apply_staged_manual_contract.py")
text = p.read_text(encoding="utf-8")
old = r'.split(Regex("[,;\\s]+"))'
new = r'.split(Regex("[,;\\\\s]+"))'
if old not in text:
    raise SystemExit("Escape alvo não encontrado no gerador")
p.write_text(text.replace(old, new, 1), encoding="utf-8")
Path(__file__).unlink()
