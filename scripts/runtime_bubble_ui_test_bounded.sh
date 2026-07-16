#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
mkdir -p "$OUT"

# O emulador pode iniciar antes do daemon ADB do runner. Subir o daemon aqui
# permite que o aparelho se conecte antes de adb wait-for-device.
adb kill-server >/dev/null 2>&1 || true
adb start-server | tee "$OUT/adb-start-server.txt"
adb devices -l > "$OUT/adb-devices-after-start-server.txt" 2>&1 || true

# O script principal ja preserva o servico de acessibilidade e usa uma tela
# externa controlada com dois enderecos completos. Este limitador apenas evita
# que ADB ou o emulador deixem o workflow preso.
set +e
timeout --signal=TERM --kill-after=20s 480s bash scripts/runtime_bubble_ui_test.sh
status=$?
set -e

echo "BOUNDED_RUNTIME_EXIT=$status" >> "$OUT/progress.txt"
if [ "$status" -ne 0 ]; then
  if [ -f "$OUT/emulator.log" ]; then
    tail -500 "$OUT/emulator.log" > "$OUT/emulator-tail.txt" || true
  fi
  exit "$status"
fi
