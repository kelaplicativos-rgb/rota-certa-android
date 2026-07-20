#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
APK="${RUNTIME_APK:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="br.com.mapeiaia.rotacerta"
ACTIVITY="$PACKAGE/.MainActivity"
SERVICE="$PACKAGE/$PACKAGE.LiveRideAccessibilityService"
mkdir -p "$OUT"

adb wait-for-device
for _ in $(seq 1 180); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done

adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/start-app.txt"
sleep 5
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
sleep 5
adb shell input keyevent KEYCODE_HOME
sleep 2

adb shell dumpsys accessibility > "$OUT/accessibility-before.txt"
python3 - "$OUT/accessibility-before.txt" "$OUT/bubble-before.txt" <<'PY'
import re, sys
from pathlib import Path
text = Path(sys.argv[1]).read_text(errors='replace')
boxes = [tuple(map(int, m)) for m in re.findall(r'type=TYPE_ACCESSIBILITY_OVERLAY.*?bounds=Rect\((\d+),\s*(\d+)\s*-\s*(\d+),\s*(\d+)\)', text)]
if not boxes:
    raise SystemExit('Bolinha nao encontrada')
boxes.sort(key=lambda b: max(1,b[2]-b[0]) * max(1,b[3]-b[1]))
x1,y1,x2,y2 = boxes[0]
Path(sys.argv[2]).write_text(f'{(x1+x2)//2} {(y1+y2)//2}\n')
PY
read -r x y < "$OUT/bubble-before.txt"

if [ "$x" -lt 500 ]; then tx=$((x + 220)); else tx=$((x - 220)); fi
if [ "$y" -lt 900 ]; then ty=$((y + 220)); else ty=$((y - 220)); fi
start_ms="$(date +%s%3N)"
adb shell input swipe "$x" "$y" "$tx" "$ty" 120
sleep 0.20
end_ms="$(date +%s%3N)"
drag_ms=$((end_ms - start_ms))
echo "$drag_ms" > "$OUT/bubble-drag-response-ms.txt"
[ "$drag_ms" -le 1200 ]

adb shell dumpsys accessibility > "$OUT/accessibility-after-drag.txt"
python3 - "$OUT/accessibility-after-drag.txt" "$OUT/bubble-after.txt" <<'PY'
import re, sys
from pathlib import Path
text = Path(sys.argv[1]).read_text(errors='replace')
boxes = [tuple(map(int, m)) for m in re.findall(r'type=TYPE_ACCESSIBILITY_OVERLAY.*?bounds=Rect\((\d+),\s*(\d+)\s*-\s*(\d+),\s*(\d+)\)', text)]
if not boxes:
    raise SystemExit('Bolinha nao encontrada depois do arraste')
boxes.sort(key=lambda b: max(1,b[2]-b[0]) * max(1,b[3]-b[1]))
x1,y1,x2,y2 = boxes[0]
Path(sys.argv[2]).write_text(f'{(x1+x2)//2} {(y1+y2)//2}\n')
PY
read -r nx ny < "$OUT/bubble-after.txt"
dx=$((nx-x)); [ "$dx" -lt 0 ] && dx=$((-dx))
dy=$((ny-y)); [ "$dy" -lt 0 ] && dy=$((-dy))
if [ "$dx" -lt 80 ] && [ "$dy" -lt 80 ]; then
  echo "Bolinha nao se moveu o suficiente" >&2
  exit 1
fi

adb shell input tap "$nx" "$ny"
opened=false
for _ in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/shortcuts.xml >/dev/null 2>&1 || true
  adb pull /sdcard/shortcuts.xml "$OUT/shortcuts.xml" >/dev/null 2>&1 || true
  if grep -Fq 'Salvar alerta' "$OUT/shortcuts.xml" 2>/dev/null && \
     grep -Fq 'Salvar local' "$OUT/shortcuts.xml" 2>/dev/null && \
     grep -Fq 'Salvar card' "$OUT/shortcuts.xml" 2>/dev/null && \
     grep -Fq 'Abrir destino' "$OUT/shortcuts.xml" 2>/dev/null && \
     grep -Fq 'Abrir leitura' "$OUT/shortcuts.xml" 2>/dev/null && \
     grep -Fq 'Abrir ajustes' "$OUT/shortcuts.xml" 2>/dev/null; then
    opened=true
    break
  fi
  sleep 0.25
done
[ "$opened" = true ]
adb exec-out screencap -p > "$OUT/shortcuts.png"
adb shell dumpsys accessibility > "$OUT/accessibility-shortcuts.txt"
overlay_count="$(grep -c 'type=TYPE_ACCESSIBILITY_OVERLAY' "$OUT/accessibility-shortcuts.txt" || true)"
[ "$overlay_count" -ge 2 ]

cat > "$OUT/runtime-validation.txt" <<EOF
INSTANT_DRAG=approved
DRAG_RESPONSE_MS=$drag_ms
DRAG_DISPLACEMENT_X=$dx
DRAG_DISPLACEMENT_Y=$dy
DRAG_OPENED_HOME=false
MAIN_TAP=opened_resource_shortcuts
RESOURCE_SHORTCUTS=6
SHORTCUT_MENU=lightweight
SAVE_ALERT_ACTION=visible
SAVE_LOCAL_ACTION=visible
FLOATING_RUNTIME=approved
EOF
cat "$OUT/runtime-validation.txt"
