#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
mkdir -p "$OUT"

# O emulador pode iniciar antes do daemon ADB do runner. Subir o daemon aqui
# permite que o aparelho se conecte antes de adb wait-for-device.
adb kill-server >/dev/null 2>&1 || true
adb start-server | tee "$OUT/adb-start-server.txt"
adb devices -l > "$OUT/adb-devices-after-start-server.txt" 2>&1 || true

# Depois de habilitar a Acessibilidade, force-stop desliga o serviço no Android.
# A simulação deve apenas ir para a Home, preservando a bolinha já conectada.
python3 - <<'PY'
from pathlib import Path
path = Path("scripts/runtime_bubble_ui_test.sh")
text = path.read_text()
old = '''adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/restart-app.txt"
sleep 7
adb shell input keyevent KEYCODE_HOME
'''
new = '''adb shell input keyevent KEYCODE_HOME
'''
if old not in text:
    raise SystemExit("Bloco force-stop obsoleto nao encontrado no teste runtime")
path.write_text(text.replace(old, new, 1))
PY

# O script principal possui seletores exatos para a bolinha Rota. Este
# limitador apenas impede que adb ou o emulador deixem o workflow preso.
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
