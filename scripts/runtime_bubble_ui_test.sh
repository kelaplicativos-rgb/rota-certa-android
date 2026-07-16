#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
APK="${RUNTIME_APK:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="br.com.mapeiaia.rotacerta"
ACTIVITY="$PACKAGE/.MainActivity"
SERVICE="$PACKAGE/$PACKAGE.LiveRideAccessibilityService"
mkdir -p "$OUT"

echo "RUNTIME_STAGE=script_started" >> "$OUT/progress.txt"

finish() {
  status=$?
  echo "SCRIPT_EXIT=$status" >> "$OUT/progress.txt"
  adb devices -l > "$OUT/adb-devices-final.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  adb shell dumpsys accessibility > "$OUT/dumpsys-accessibility-final.txt" 2>&1 || true
  exit "$status"
}
trap finish EXIT

wait_boot() {
  adb wait-for-device
  for _ in $(seq 1 180); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      adb shell input keyevent 82 || true
      adb shell wm dismiss-keyguard || true
      return 0
    fi
    sleep 2
  done
  echo "Android nao concluiu o boot" >&2
  return 1
}

dump_ui() {
  remote="$1"
  local_file="$2"
  for _ in $(seq 1 8); do
    if adb shell uiautomator dump "$remote" >/dev/null 2>&1; then
      adb pull "$remote" "$local_file" >/dev/null
      return 0
    fi
    sleep 2
  done
  echo "Falha ao gerar $local_file" >&2
  return 1
}

wait_boot
adb shell getprop > "$OUT/device-properties.txt"
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/start-app.txt"
sleep 8
dump_ui /sdcard/app-before.xml "$OUT/app-before.xml"
adb exec-out screencap -p > "$OUT/app-before.png"

python3 - "$OUT" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1])
root = ET.parse(out / 'app-before.xml').getroot()
nodes = list(root.iter('node'))
parents = {child: parent for parent in root.iter() for child in parent}

def label(node):
    return ' '.join(filter(None, [node.attrib.get('text', ''), node.attrib.get('content-desc', '')])).strip()

def clickable_bounds(node):
    current = node
    while current is not None:
        if current.attrib.get('clickable') == 'true':
            return current.attrib.get('bounds', '')
        current = parents.get(current)
    return node.attrib.get('bounds', '')

all_text = '\n'.join(label(node) for node in nodes)
(out / 'app-before-text.txt').write_text(all_text)
required = ['Central de bolinhas', 'Rota', 'Leitura', 'Acesso', 'WA']
missing = [item for item in required if item not in all_text]
if missing:
    raise SystemExit('Central interna ausente: ' + ', '.join(missing))

rota = next((node for node in nodes if node.attrib.get('text', '').startswith('Rota\n')), None)
if rota is None:
    raise SystemExit('Bolinha Rota nao encontrada')

bounds = clickable_bounds(rota)
match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
if not match:
    raise SystemExit('Bounds clicaveis invalidos: ' + bounds)
x = (int(match.group(1)) + int(match.group(3))) // 2
y = (int(match.group(2)) + int(match.group(4))) // 2
(out / 'rota-coordinates.txt').write_text(f'{x} {y}\n')
(out / 'rota-before.txt').write_text(node_text := rota.attrib.get('text', '').strip())
(out / 'rota-click-bounds.txt').write_text(bounds + '\n')
PY

read -r rota_x rota_y < "$OUT/rota-coordinates.txt"
adb shell input tap "$rota_x" "$rota_y"
sleep 4
dump_ui /sdcard/app-after.xml "$OUT/app-after.xml"
adb exec-out screencap -p > "$OUT/app-after.png"

python3 - "$OUT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1])
before = (out / 'rota-before.txt').read_text().strip()
root = ET.parse(out / 'app-after.xml').getroot()
nodes = list(root.iter('node'))

def label(node):
    return ' '.join(filter(None, [node.attrib.get('text', ''), node.attrib.get('content-desc', '')])).strip()

all_text = '\n'.join(label(node) for node in nodes)
(out / 'app-after-text.txt').write_text(all_text)
rota = next((node for node in nodes if node.attrib.get('text', '').startswith('Rota\n')), None)
if rota is None:
    raise SystemExit('Bolinha Rota desapareceu')
after = rota.attrib.get('text', '').strip()
if before == after:
    raise SystemExit(f'Rota nao alternou: {before!r}')
if not ((('ON' in before) and ('OFF' in after)) or (('OFF' in before) and ('ON' in after))):
    raise SystemExit(f'Alternancia invalida: {before!r} -> {after!r}')
(out / 'in-app-result.txt').write_text(
    'IN_APP_CENTER=visible\n'
    f'ROTA_BEFORE={before}\n'
    f'ROTA_AFTER={after}\n'
    'IN_APP_TOGGLE=approved\n'
)
PY

adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
sleep 5
adb shell settings get secure enabled_accessibility_services > "$OUT/enabled-services.txt"
adb shell dumpsys accessibility > "$OUT/dumpsys-accessibility.txt"
if ! grep -Fq "$PACKAGE" "$OUT/enabled-services.txt"; then
  echo "Servico nao ficou habilitado" >&2
  exit 1
fi

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/restart-app.txt"
sleep 10
adb shell input keyevent KEYCODE_HOME
sleep 8
dump_ui /sdcard/home-bubble.xml "$OUT/home-bubble.xml"
adb exec-out screencap -p > "$OUT/home-bubble.png"

python3 - "$OUT" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1])
root = ET.parse(out / 'home-bubble.xml').getroot()
nodes = list(root.iter('node'))
parents = {child: parent for parent in root.iter() for child in parent}

def label(node):
    return ' '.join(filter(None, [node.attrib.get('text', ''), node.attrib.get('content-desc', '')])).strip()

def clickable_bounds(node):
    current = node
    while current is not None:
        if current.attrib.get('clickable') == 'true':
            return current.attrib.get('bounds', '')
        current = parents.get(current)
    return node.attrib.get('bounds', '')

all_text = '\n'.join(label(node) for node in nodes)
(out / 'home-bubble-text.txt').write_text(all_text)
bubble = next((node for node in nodes if node.attrib.get('content-desc') == 'Rota Certa'), None)
if bubble is None:
    raise SystemExit('Bolinha flutuante nao apareceu')
bounds = clickable_bounds(bubble)
match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
if not match:
    raise SystemExit('Bounds flutuantes invalidos: ' + bounds)
x = (int(match.group(1)) + int(match.group(3))) // 2
y = (int(match.group(2)) + int(match.group(4))) // 2
(out / 'floating-coordinates.txt').write_text(f'{x} {y}\n')
(out / 'floating-click-bounds.txt').write_text(bounds + '\n')
PY

read -r bubble_x bubble_y < "$OUT/floating-coordinates.txt"
adb shell input tap "$bubble_x" "$bubble_y"
sleep 5
dump_ui /sdcard/floating-menu.xml "$OUT/floating-menu.xml"
adb exec-out screencap -p > "$OUT/floating-menu.png"

python3 - "$OUT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1])
root = ET.parse(out / 'floating-menu.xml').getroot()

def label(node):
    return ' '.join(filter(None, [node.attrib.get('text', ''), node.attrib.get('content-desc', '')])).strip()

text = '\n'.join(label(node) for node in root.iter('node'))
(out / 'floating-menu-text.txt').write_text(text)
required = ['Rota', 'Leitura', 'WA', 'Acesso', 'Fechar']
missing = [item for item in required if item not in text]
if missing:
    raise SystemExit('Grade flutuante incompleta: ' + ', '.join(missing))
(out / 'floating-result.txt').write_text(
    'ACCESSIBILITY_SERVICE=enabled\n'
    'FLOATING_BUBBLE=visible\n'
    'MAIN_TAP=opened_grid\n'
    'GRID_LABELS=Rota,Leitura,WA,Acesso,Fechar\n'
    'FLOATING_RUNTIME=approved\n'
)
PY

cat "$OUT/in-app-result.txt" "$OUT/floating-result.txt" | tee "$OUT/runtime-validation.txt"
echo "RUNTIME_STAGE=approved" >> "$OUT/progress.txt"
