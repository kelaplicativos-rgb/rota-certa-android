#!/usr/bin/env bash
set -euo pipefail

# Torna o teste 0.1.116 deterministico:
# 1. atividades historicas nao contam como Home aberta;
# 2. depois do toque, consulta a atividade retomada diretamente, sem dezenas de
#    chamadas lentas ao uiautomator.
python3 - <<'PY'
from pathlib import Path

path = Path("scripts/runtime_bubble_ui_test_bounded.sh")
text = path.read_text()

old_activity = '''if grep -Fq "$PACKAGE/.MainActivity" "$OUT/bubble-after-drag-activity.txt"; then
  echo "Arrastar abriu a Home indevidamente" >&2
  exit 1
fi'''
new_activity = '''if grep -E '(^|[[:space:]])(topResumedActivity|mResumedActivity)=.*br\\.com\\.mapeiaia\\.rotacerta/\\.MainActivity' "$OUT/bubble-after-drag-activity.txt" >/dev/null; then
  echo "Arrastar abriu a Home indevidamente" >&2
  exit 1
fi'''
if old_activity not in text:
    raise SystemExit("Verificacao antiga da atividade nao encontrada")
text = text.replace(old_activity, new_activity, 1)

old_home_wait = '''home_opened=false
for _ in $(seq 1 40); do
  if dump_ui /sdcard/bubble-tap-home.xml "$OUT/bubble-tap-home.xml"; then
    if grep -Fq 'Central de bolinhas' "$OUT/bubble-tap-home.xml"; then
      home_opened=true
      break
    fi
  fi
  sleep 0.25
done
if [ "$home_opened" != true ]; then
  echo "Toque na bolinha nao abriu diretamente a Home" >&2
  cat "$OUT/bubble-tap-home.xml" 2>/dev/null || true
  exit 1
fi'''
new_home_wait = '''home_opened=false
for _ in $(seq 1 30); do
  adb shell dumpsys activity activities > "$OUT/bubble-tap-activity-live.txt" 2>&1 || true
  if grep -E '(^|[[:space:]])(topResumedActivity|mResumedActivity)=.*br\\.com\\.mapeiaia\\.rotacerta/\\.MainActivity' "$OUT/bubble-tap-activity-live.txt" >/dev/null; then
    home_opened=true
    break
  fi
  sleep 0.20
done
if [ "$home_opened" != true ]; then
  echo "Toque na bolinha nao abriu diretamente a Home" >&2
  cat "$OUT/bubble-tap-activity-live.txt" 2>/dev/null || true
  exit 1
fi
dump_ui /sdcard/bubble-tap-home.xml "$OUT/bubble-tap-home.xml"
if ! grep -Fq 'Central de bolinhas' "$OUT/bubble-tap-home.xml"; then
  echo "Home abriu sem a Central de bolinhas" >&2
  exit 1
fi'''
if old_home_wait not in text:
    raise SystemExit("Espera antiga da Home nao encontrada")
text = text.replace(old_home_wait, new_home_wait, 1)

path.write_text(text)
PY

exec bash scripts/runtime_bubble_ui_test_bounded.sh
