#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
APK="${RUNTIME_APK:-app/build/outputs/apk/debug/app-debug.apk}"
FIXTURE_APK="${RUNTIME_FIXTURE_APK:-runtimefixture/build/outputs/apk/debug/runtimefixture-debug.apk}"
PACKAGE="br.com.mapeiaia.rotacerta"
ACTIVITY="$PACKAGE/.MainActivity"
SERVICE="$PACKAGE/$PACKAGE.LiveRideAccessibilityService"
FIXTURE_PACKAGE="br.com.mapeiaia.rotacerta.runtimefixture"
FIXTURE_ACTIVITY="$FIXTURE_PACKAGE/.TwoAddressActivity"
PREFS_PATH="shared_prefs/rota_certa_bubble.xml"
mkdir -p "$OUT"

echo "RUNTIME_STAGE=script_started" >> "$OUT/progress.txt"

finish() {
  status=$?
  echo "SCRIPT_EXIT=$status" >> "$OUT/progress.txt"
  adb devices -l > "$OUT/adb-devices-final.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  adb shell dumpsys accessibility > "$OUT/dumpsys-accessibility-final.txt" 2>&1 || true
  return "$status"
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
      local needle
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

wait_boot
echo "RUNTIME_STAGE=android_booted" >> "$OUT/progress.txt"
adb shell getprop > "$OUT/device-properties.txt"
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
before = rota.attrib.get('text', '').strip()
if 'ON' not in before:
    raise SystemExit(f'Rota deveria iniciar ligada para o teste: {before!r}')
(out / 'rota-coordinates.txt').write_text(f'{x} {y}\n')
(out / 'rota-before.txt').write_text(before)
(out / 'rota-click-bounds.txt').write_text(bounds + '\n')
PY

read -r rota_x rota_y < "$OUT/rota-coordinates.txt"
adb shell input tap "$rota_x" "$rota_y"
sleep 3
dump_ui /sdcard/app-after-off.xml "$OUT/app-after-off.xml"
adb exec-out screencap -p > "$OUT/app-after-off.png"

python3 - "$OUT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
out = Path(sys.argv[1])
before = (out / 'rota-before.txt').read_text().strip()
root = ET.parse(out / 'app-after-off.xml').getroot()
nodes = list(root.iter('node'))
rota = next((node for node in nodes if node.attrib.get('text', '').startswith('Rota\n')), None)
if rota is None:
    raise SystemExit('Bolinha Rota desapareceu apos desligar')
after = rota.attrib.get('text', '').strip()
if 'ON' not in before or 'OFF' not in after:
    raise SystemExit(f'Alternancia ON para OFF invalida: {before!r} -> {after!r}')
(out / 'rota-after-off.txt').write_text(after)
PY

adb shell input tap "$rota_x" "$rota_y"
sleep 3
dump_ui /sdcard/app-restored-on.xml "$OUT/app-restored-on.xml"
adb exec-out screencap -p > "$OUT/app-restored-on.png"

python3 - "$OUT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
out = Path(sys.argv[1])
before = (out / 'rota-before.txt').read_text().strip()
after_off = (out / 'rota-after-off.txt').read_text().strip()
root = ET.parse(out / 'app-restored-on.xml').getroot()
nodes = list(root.iter('node'))
rota = next((node for node in nodes if node.attrib.get('text', '').startswith('Rota\n')), None)
if rota is None:
    raise SystemExit('Bolinha Rota desapareceu ao religar')
restored = rota.attrib.get('text', '').strip()
if 'ON' not in restored:
    raise SystemExit(f'Rota nao voltou para ON: {restored!r}')
(out / 'in-app-result.txt').write_text(
    'IN_APP_CENTER=visible\n'
    f'ROTA_BEFORE={before}\n'
    f'ROTA_AFTER_FIRST_TAP={after_off}\n'
    f'ROTA_AFTER_SECOND_TAP={restored}\n'
    'IN_APP_TOGGLE=approved\n'
)
PY

echo "RUNTIME_STAGE=in_app_toggle_approved" >> "$OUT/progress.txt"
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
sleep 5
adb shell settings get secure enabled_accessibility_services > "$OUT/enabled-services.txt"
adb shell dumpsys accessibility > "$OUT/dumpsys-accessibility.txt"
if ! grep -Fq "$PACKAGE" "$OUT/enabled-services.txt"; then
  echo "Servico nao ficou habilitado" >&2
  exit 1
fi
if ! grep -Fq 'TYPE_ACCESSIBILITY_OVERLAY' "$OUT/dumpsys-accessibility.txt"; then
  echo "Janela da bolinha nao foi criada" >&2
  exit 1
fi

# Nao use force-stop depois de ativar a acessibilidade: isso encerra o servico.
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell dumpsys accessibility > "$OUT/home-bubble-accessibility.txt"
adb exec-out screencap -p > "$OUT/home-bubble.png"
wait_runtime_prefs "$OUT/home-runtime-prefs.xml" 30 '>cinza|'

python3 - "$OUT" <<'PY'
import re
import sys
from pathlib import Path
out = Path(sys.argv[1])
text = (out / 'home-bubble-accessibility.txt').read_text(errors='replace')
matches = re.findall(
    r'type=TYPE_ACCESSIBILITY_OVERLAY.*?bounds=Rect\((\d+),\s*(\d+)\s*-\s*(\d+),\s*(\d+)\)',
    text,
)
if not matches:
    raise SystemExit('Janela TYPE_ACCESSIBILITY_OVERLAY nao encontrada no Android')
boxes = [tuple(map(int, values)) for values in matches]
box = min(boxes, key=lambda b: max(1, b[2]-b[0]) * max(1, b[3]-b[1]))
x1, y1, x2, y2 = box
if x2 <= x1 or y2 <= y1:
    raise SystemExit(f'Bounds invalidos da bolinha: {box}')
(out / 'floating-coordinates.txt').write_text(f'{(x1+x2)//2} {(y1+y2)//2}\n')
(out / 'floating-window-bounds.txt').write_text(f'{x1},{y1},{x2},{y2}\n')
(out / 'floating-window-result.txt').write_text(
    'ACCESSIBILITY_OVERLAY_WINDOW=visible\n'
    f'OVERLAY_BOUNDS={x1},{y1},{x2},{y2}\n'
)
PY

echo "RUNTIME_STAGE=gray_idle_approved" >> "$OUT/progress.txt"
adb shell am force-stop "$FIXTURE_PACKAGE"
yellow_started_ms="$(date +%s%3N)"
adb shell am start -W -n "$FIXTURE_ACTIVITY" | tee "$OUT/fixture-start.txt"
wait_runtime_prefs \
  "$OUT/yellow-runtime-prefs.xml" \
  80 \
  '>amarelo|' \
  '<int name="runtime_visible_addresses" value="2"' \
  'Avenida Brasil, 900 - Bela Vista, Santo Andre - SP'
yellow_finished_ms="$(date +%s%3N)"
yellow_latency_ms="$((yellow_finished_ms - yellow_started_ms))"
echo "$yellow_latency_ms" > "$OUT/yellow-latency-ms.txt"
if [ "$yellow_latency_ms" -gt 1200 ]; then
  echo "Gatilho amarelo demorou ${yellow_latency_ms}ms" >&2
  exit 1
fi

dump_ui /sdcard/two-address-trigger.xml "$OUT/two-address-trigger.xml"
adb exec-out screencap -p > "$OUT/two-address-trigger.png"

python3 - "$OUT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
out = Path(sys.argv[1])
root = ET.parse(out / 'two-address-trigger.xml').getroot()
text = '\n'.join(' '.join(filter(None, [n.attrib.get('text', ''), n.attrib.get('content-desc', '')])).strip() for n in root.iter('node'))
(out / 'two-address-trigger-text.txt').write_text(text)
for expected in ('Rua das Flores, 120', 'Avenida Brasil, 900'):
    if expected not in text:
        raise SystemExit(f'Endereco de teste nao apareceu na tela controlada: {expected}')
PY

echo "RUNTIME_STAGE=yellow_two_addresses_approved" >> "$OUT/progress.txt"
clear_started_ms="$(date +%s%3N)"
adb shell input keyevent KEYCODE_HOME
wait_runtime_prefs \
  "$OUT/cleared-runtime-prefs.xml" \
  30 \
  '>cinza|' \
  '<int name="runtime_visible_addresses" value="0"'
clear_finished_ms="$(date +%s%3N)"
clear_latency_ms="$((clear_finished_ms - clear_started_ms))"
echo "$clear_latency_ms" > "$OUT/clear-latency-ms.txt"
if [ "$clear_latency_ms" -gt 1200 ]; then
  echo "Limpeza demorou ${clear_latency_ms}ms" >&2
  exit 1
fi
adb shell dumpsys accessibility > "$OUT/after-address-clear-accessibility.txt"
adb exec-out screencap -p > "$OUT/after-address-clear.png"

cat > "$OUT/two-address-result.txt" <<EOF
UNIVERSAL_SCREEN=controlled_external_android_activity
VISIBLE_ADDRESSES=2
PICKUP=Rua das Flores, 120 - Centro, Sao Paulo - SP
DESTINATION=Avenida Brasil, 900 - Bela Vista, Santo Andre - SP
DESTINATION_RULE=last_visible_address
YELLOW_TRIGGER_LATENCY_MS=$yellow_latency_ms
YELLOW_TRIGGER=approved
CLEAR_LATENCY_MS=$clear_latency_ms
IMMEDIATE_CLEAR_TO_GRAY=approved
UNIVERSAL_TWO_ADDRESS_RUNTIME=approved
EOF

read -r bubble_x bubble_y < "$OUT/floating-coordinates.txt"
adb shell input tap "$bubble_x" "$bubble_y"
wait_runtime_prefs "$OUT/menu-open-runtime-prefs.xml" 30 '<boolean name="runtime_menu_open" value="true"'
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
GRID_LABELS=validated_in_source_and_apk
FLOATING_RUNTIME=approved
EOF

cat \
  "$OUT/in-app-result.txt" \
  "$OUT/floating-window-result.txt" \
  "$OUT/two-address-result.txt" \
  "$OUT/floating-result.txt" \
  | tee "$OUT/runtime-validation.txt"
echo "RUNTIME_STAGE=approved" >> "$OUT/progress.txt"
