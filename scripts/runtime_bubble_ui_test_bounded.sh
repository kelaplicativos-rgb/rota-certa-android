#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
mkdir -p "$OUT"

# O script principal ja possui seletores exatos para a bolinha Rota. Este
# limitador apenas impede que adb ou o emulador deixem o workflow preso.
set +e
timeout --signal=TERM --kill-after=20s 720s bash scripts/runtime_bubble_ui_test.sh
status=$?
set -e

echo "BOUNDED_RUNTIME_EXIT=$status" >> "$OUT/progress.txt"
if [ "$status" -ne 0 ]; then
  if [ -f "$OUT/emulator.log" ]; then
    tail -500 "$OUT/emulator.log" > "$OUT/emulator-tail.txt" || true
  fi
  exit "$status"
fi
