#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
mkdir -p "$OUT"

# Impede que adb wait-for-device deixe a validacao presa. O script interno
# possui trap EXIT e salva logcat/dumpsys/emulator-tail quando recebe TERM.
if ! timeout --signal=TERM --kill-after=20s 720s bash scripts/runtime_bubble_ui_test.sh; then
  status=$?
  echo "BOUNDED_RUNTIME_EXIT=$status" >> "$OUT/progress.txt"
  if [ -f "$OUT/emulator.log" ]; then
    tail -500 "$OUT/emulator.log" > "$OUT/emulator-tail.txt" || true
  fi
  exit "$status"
fi
