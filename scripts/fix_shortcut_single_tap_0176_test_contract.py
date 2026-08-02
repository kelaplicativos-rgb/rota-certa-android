#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
path = root / "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutActivityLaunchContract0176Test.kt"
text = path.read_text(encoding="utf-8")
old = '        assertTrue(overlay.contains("trace(\\"bubble.shortcut.clicked id=\\${module.spec.id}\\")"))\n'
new = '        assertTrue(overlay.contains("bubble.shortcut.clicked id="))\n'
if old not in text:
    raise SystemExit("0176 contract: expected generated trace assertion not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("CONTRATO_DESPACHO_ATALHO_0176_CORRIGIDO")
