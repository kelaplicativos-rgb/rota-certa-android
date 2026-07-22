#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
APK="${RUNTIME_APK:-app/build/outputs/apk/debug/app-debug.apk}"
FIXTURE_APK="${RUNTIME_FIXTURE_APK:-runtimefixture/build/outputs/apk/debug/runtimefixture-debug.apk}"
PACKAGE="br.com.mapeiaia.rotacerta"
ACTIVITY="$PACKAGE/.MainActivity"
SERVICE="$PACKAGE/$PACKAGE.LiveRideAccessibilityService"
FIXTURE_PACKAGE="sinet.startup.indriver"
FIXTURE_ACTIVITY="$FIXTURE_PACKAGE/br.com.mapeiaia.rotacerta.runtimefixture.TwoAddressActivity"
PREFS_PATH="shared_prefs/rota_certa_bubble.xml"
mkdir -p "$OUT"

echo "RUNTIME_STAGE=script_started" >> "$OUT/progress.txt"

finish() {
  status=$?
  trap - EXIT
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

read_runtime_prefs() {
  local output="$1"
  adb shell run-as "$PACKAGE" cat "$PREFS_PATH" > "$output" 2>/dev/null
}

wait_runtime_prefs() {
  local output="$1"
  local attempts="$2"
  shift 2
  for _ in $(seq 1 "$attempts"); do
    if read_runtime_prefs "$output"; then
      local all_found=true
      for needle in "$@"; do
        if ! grep -Fq -- "$needle" "$output"; then
          all_found=false
          break
        fi
      done
      if [ "$all_found" = true ]; then
        return 0
      fi
    fi
    sleep 0.1
  done
  echo "Estado runtime esperado nao apareceu: $*" >&2
  cat "$output" 2>/dev/null || true
  return 1
}

find_group_control() {
  local input_xml="$1"
  local label="$2"
  local coordinates_file="$3"
  python3 - "$input_xml" "$label" "$coordinates_file" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
xml_path = Path(sys.argv[1])
label = sys.argv[2]
coordinates_path = Path(sys.argv[3])
root = ET.parse(xml_path).getroot()
nodes = list(root.iter('node'))
parents = {child: parent for parent in root.iter() for child in parent}
node = next((item for item in nodes if item.attrib.get('text', '').strip() == label or item.attrib.get('content-desc', '').strip() == label), None)
if node is None:
    raise SystemExit(f'Bolinha de grupo nao encontrada: {label}')
current = node
bounds = ''
while current is not None:
    if current.attrib.get('clickable') == 'true':
        bounds = current.attrib.get('bounds', '')
        break
    current = parents.get(current)
if not bounds:
    bounds = node.attrib.get('bounds', '')
match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
if not match:
    raise SystemExit('Bounds clicaveis invalidos: ' + bounds)
x = (int(match.group(1)) + int(match.group(3))) // 2
y = (int(match.group(2)) + int(match.group(4))) // 2
coordinates_path.write_text(f'{x} {y}\n')
PY
}

wait_boot
echo "RUNTIME_STAGE=android_booted" >> "$OUT/progress.txt"
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

test -f "$APK"
test -f "$FIXTURE_APK"
adb install -r "$APK" | tee "$OUT/install.txt"
adb install -r "$FIXTURE_APK" | tee "$OUT/fixture-install.txt"
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true

echo "RUNTIME_STAGE=apks_installed" >> "$OUT/progress.txt"
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/start-app.txt"
sleep 8
dump_ui /sdcard/app-before.xml "$OUT/app-before.xml"
adb exec-out screencap -p > "$OUT/app-before.png"

python3 - "$OUT/app-before.xml" "$OUT/app-before-text.txt" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
root = ET.parse(sys.argv[1]).getroot()
text = '\n'.join(' '.join(filter(None, [n.attrib.get('text', ''), n.attrib.get('content-desc', '')])).strip() for n in root.iter('node'))
Path(sys.argv[2]).write_text(text)
required = ['Central de bolinhas', 'Rota', 'Leitura', 'Destino', 'Alertas', 'Aparencia', 'Permissoes']
missing = [item for item in required if item not in text]
if missing:
    raise SystemExit('Central agrupada ausente: ' + ', '.join(missing))
if 'Rota\nON' in text or 'Rota\nOFF' in text:
    raise SystemExit('Bolinhas ainda exibem ON/OFF quebrado')
PY

find_group_control "$OUT/app-before.xml" "Rota" "$OUT/rota-group-coordinates.txt"
find_group_control "$OUT/app-before.xml" "Aparencia" "$OUT/appearance-group-coordinates.txt"
read -r rota_x rota_y < "$OUT/rota-group-coordinates.txt"
read -r appearance_x appearance_y < "$OUT/appearance-group-coordinates.txt"

adb shell input tap "$rota_x" "$rota_y"
sleep 1
adb shell input swipe 500 1150 500 350 450
sleep 1
dump_ui /sdcard/app-rota-group.xml "$OUT/app-rota-group.xml"
adb exec-out screencap -p > "$OUT/app-rota-group.png"
grep -Fq 'Controle geral' "$OUT/app-rota-group.xml"
grep -Fq 'Rota Certa ligado' "$OUT/app-rota-group.xml"
if grep -Fq '>Abrir<' "$OUT/app-rota-group.xml"; then
  echo "Grupo Rota ainda exige segundo toque em Abrir" >&2
  exit 1
fi

adb shell input swipe 500 300 500 1250 500
sleep 1
adb shell input tap "$appearance_x" "$appearance_y"
sleep 1
adb shell input swipe 500 1150 500 350 450
sleep 1
dump_ui /sdcard/app-appearance-group.xml "$OUT/app-appearance-group.xml"
adb exec-out screencap -p > "$OUT/app-appearance-group.png"
grep -Fq 'Bolinha e aparencia' "$OUT/app-appearance-group.xml"
grep -Fq 'Transparencia' "$OUT/app-appearance-group.xml"
if grep -Fq '>Abrir<' "$OUT/app-appearance-group.xml"; then
  echo "Grupo Aparencia ainda exige segundo toque em Abrir" >&2
  exit 1
fi

cat > "$OUT/in-app-result.txt" <<EOF
IN_APP_CENTER=visible
GROUP_BUBBLES=9
BUBBLE_TEXT_WRAP=clean
ROTA_GROUP=visible
APPEARANCE_GROUP=visible
INNER_OPEN_BUTTON=absent
IN_APP_GROUP_NAVIGATION=approved
EOF

echo "RUNTIME_STAGE=in_app_group_navigation_approved" >> "$OUT/progress.txt"
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
sleep 5
adb shell settings get secure enabled_accessibility_services > "$OUT/enabled-services.txt"
adb shell dumpsys accessibility > "$OUT/dumpsys-accessibility.txt"
grep -Fq "$PACKAGE" "$OUT/enabled-services.txt"
grep -Fq 'TYPE_ACCESSIBILITY_OVERLAY' "$OUT/dumpsys-accessibility.txt"

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell dumpsys accessibility > "$OUT/home-bubble-accessibility.txt"
adb exec-out screencap -p > "$OUT/home-bubble.png"
wait_runtime_prefs "$OUT/home-runtime-prefs.xml" 40 '>cinza|'

python3 - "$OUT/home-bubble-accessibility.txt" "$OUT/floating-coordinates.txt" "$OUT/floating-window-result.txt" <<'PY'
import re
import sys
from pathlib import Path
accessibility, coordinates, result = map(Path, sys.argv[1:])
text = accessibility.read_text(errors='replace')
matches = re.findall(r'type=TYPE_ACCESSIBILITY_OVERLAY.*?bounds=Rect\((\d+),\s*(\d+)\s*-\s*(\d+),\s*(\d+)\)', text)
if not matches:
    raise SystemExit('Janela TYPE_ACCESSIBILITY_OVERLAY nao encontrada')
boxes = [tuple(map(int, values)) for values in matches]
x1, y1, x2, y2 = min(boxes, key=lambda b: max(1, b[2]-b[0]) * max(1, b[3]-b[1]))
coordinates.write_text(f'{(x1+x2)//2} {(y1+y2)//2}\n')
result.write_text('ACCESSIBILITY_OVERLAY_WINDOW=visible\n' + f'OVERLAY_BOUNDS={x1},{y1},{x2},{y2}\n')
PY

echo "RUNTIME_STAGE=gray_idle_approved" >> "$OUT/progress.txt"
adb shell am force-stop "$FIXTURE_PACKAGE" || true
screen_started_ms="$(date +%s%3N)"
adb shell am start -W -n "$FIXTURE_ACTIVITY" | tee "$OUT/fixture-start.txt"
sleep 3
wait_runtime_prefs "$OUT/unregistered-runtime-prefs.xml" 40 '>cinza|' '<int name="runtime_visible_addresses" value="0"'
screen_finished_ms="$(date +%s%3N)"
echo "$((screen_finished_ms - screen_started_ms))" > "$OUT/unregistered-screen-check-ms.txt"

if grep -Fq 'runtime_last_destination' "$OUT/unregistered-runtime-prefs.xml"; then
  echo "Destino foi mantido sem modelo cadastrado" >&2
  exit 1
fi
if grep -Fq 'runtime_registered_template' "$OUT/unregistered-runtime-prefs.xml"; then
  echo "Modelo inexistente foi aplicado" >&2
  exit 1
fi

dump_ui /sdcard/unregistered-two-address.xml "$OUT/unregistered-two-address.xml"
adb exec-out screencap -p > "$OUT/unregistered-two-address.png"
python3 - "$OUT/unregistered-two-address.xml" "$OUT/unregistered-two-address-text.txt" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
root = ET.parse(sys.argv[1]).getroot()
text = '\n'.join(' '.join(filter(None, [n.attrib.get('text', ''), n.attrib.get('content-desc', '')])).strip() for n in root.iter('node'))
Path(sys.argv[2]).write_text(text)
for expected in ('Rua das Flores, 120', 'Avenida Brasil, 900'):
    if expected not in text:
        raise SystemExit(f'Endereco controlado ausente: {expected}')
PY

cat > "$OUT/registered-card-result.txt" <<EOF
CONTROLLED_PACKAGE=$FIXTURE_PACKAGE
VISIBLE_NUMBERED_ADDRESSES_ON_SCREEN=2
REGISTERED_MODELS=0
BUBBLE_STATE=gray
DESTINATION_WITHOUT_MODEL=absent
UNREGISTERED_TWO_ADDRESS_BLOCK=approved
REGISTERED_CARD_RUNTIME=approved
EOF

echo "RUNTIME_STAGE=unregistered_addresses_blocked" >> "$OUT/progress.txt"
adb shell input keyevent KEYCODE_HOME
wait_runtime_prefs "$OUT/cleared-runtime-prefs.xml" 40 '>cinza|' '<int name="runtime_visible_addresses" value="0"'
adb exec-out screencap -p > "$OUT/after-address-clear.png"

read -r bubble_x bubble_y < "$OUT/floating-coordinates.txt"
adb shell input tap "$bubble_x" "$bubble_y"
wait_runtime_prefs "$OUT/menu-open-runtime-prefs.xml" 40 '<boolean name="runtime_menu_open" value="true"'
adb shell dumpsys accessibility > "$OUT/floating-menu-accessibility.txt"
adb exec-out screencap -p > "$OUT/floating-menu.png"
overlay_count="$(grep -c 'type=TYPE_ACCESSIBILITY_OVERLAY' "$OUT/floating-menu-accessibility.txt" || true)"
if [ "$overlay_count" -lt 2 ]; then
  echo "Painel nao criou a segunda janela de overlay" >&2
  exit 1
fi

cat > "$OUT/floating-result.txt" <<EOF
ACCESSIBILITY_SERVICE=enabled
FLOATING_BUBBLE=visible
ACCESSIBILITY_OVERLAY_WINDOWS=$overlay_count
MAIN_TAP=opened_grid
FLOATING_RUNTIME=approved
EOF

cat "$OUT/in-app-result.txt" "$OUT/floating-window-result.txt" "$OUT/registered-card-result.txt" "$OUT/floating-result.txt" | tee "$OUT/runtime-validation.txt"
echo "RUNTIME_STAGE=approved" >> "$OUT/progress.txt"
