#!/usr/bin/env bash
set -euo pipefail

# Ajusta somente o falso positivo do teste 0.1.116: dumpsys mantem atividades
# historicas na pilha. O arraste so deve falhar quando a MainActivity estiver
# realmente retomada no topo.
python3 - <<'PY'
from pathlib import Path

path = Path("scripts/runtime_bubble_ui_test_bounded.sh")
text = path.read_text()
old = '''if grep -Fq "$PACKAGE/.MainActivity" "$OUT/bubble-after-drag-activity.txt"; then
  echo "Arrastar abriu a Home indevidamente" >&2
  exit 1
fi'''
new = '''if grep -E '(^|[[:space:]])(topResumedActivity|mResumedActivity)=.*br\\.com\\.mapeiaia\\.rotacerta/\\.MainActivity' "$OUT/bubble-after-drag-activity.txt" >/dev/null; then
  echo "Arrastar abriu a Home indevidamente" >&2
  exit 1
fi'''
if old not in text:
    raise SystemExit("Verificacao antiga da atividade nao encontrada")
path.write_text(text.replace(old, new, 1))
PY

exec bash scripts/runtime_bubble_ui_test_bounded.sh
