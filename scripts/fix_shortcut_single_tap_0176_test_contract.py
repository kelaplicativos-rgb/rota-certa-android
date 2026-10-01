#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
path = root / "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutActivityLaunchContract0176Test.kt"
lines = path.read_text(encoding="utf-8").splitlines()
replacement = '        assertTrue(overlay.contains("bubble.shortcut.clicked id="))'
matched = 0
updated = []
for line in lines:
    if "assertTrue(overlay.contains" in line and "bubble.shortcut.clicked id=" in line:
        updated.append(replacement)
        matched += 1
    else:
        updated.append(line)
if matched != 1:
    raise SystemExit(f"0176 contract: expected one generated trace assertion, found {matched}")
path.write_text("\n".join(updated) + "\n", encoding="utf-8")
print("CONTRATO_DESPACHO_ATALHO_0176_CORRIGIDO")
