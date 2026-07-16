#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
mkdir -p "$OUT"

# O primeiro teste selecionava o titulo "Rota Certa" por engano. Ajusta o
# seletor para aceitar somente a bolinha Rota acompanhada do estado ON/OFF.
python3 - <<'PY'
from pathlib import Path
path = Path("scripts/runtime_bubble_ui_test.sh")
text = path.read_text()
old = "label(node).startswith('Rota')"
new = "label(node).replace('\\n', ' ').startswith('Rota ') and ('ON' in label(node) or 'OFF' in label(node))"
count = text.count(old)
if count != 2:
    raise SystemExit(f"Esperava corrigir 2 seletores Rota, encontrei {count}")
path.write_text(text.replace(old, new))
PY

# Impede que adb wait-for-device deixe a validacao presa. O script interno
# possui trap EXIT e salva logcat/dumpsys/emulator-tail quando recebe TERM.
set +e
timeout --signal=TERM --kill-after=20s 720s bash scripts/runtime_bubble_ui_test.sh
status=$?
set -e

if [ "$status" -ne 0 ]; then
  echo "BOUNDED_RUNTIME_EXIT=$status" >> "$OUT/progress.txt"
  if [ -f "$OUT/emulator.log" ]; then
    tail -500 "$OUT/emulator.log" > "$OUT/emulator-tail.txt" || true
  fi
  exit "$status"
fi

echo "BOUNDED_RUNTIME_EXIT=0" >> "$OUT/progress.txt"
