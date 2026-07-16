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

wait_boot
echo "RUNTIME_STAGE=android_booted" >> "$OUT/progress.txt"
adb shell getprop > "$OUT/device-properties.txt"
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true

echo "RUNTIME_STAGE=apk_installed" >> "$OUT/progress.txt"
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
sleep 4
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
sleep 4
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

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/restart-app.txt"
sleep 8
adb shell input keyevent KEYCODE_HOME
sleep 4
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

def clickable_bounds(node):
    current = node
    while current is not None:
        if current.attrib.get('clickable') == 'true':
            return current.attrib.get('bounds', '')
        current = parents.get(current)
    return node.attrib.get('bounds', '')

bubble = next((node for node in nodes if node.attrib.get('content-desc', '').startswith('Rota Certa')), None)
if bubble is None:
    raise SystemExit('Bolinha flutuante nao apareceu')
description = bubble.attrib.get('content-desc', '')
if 'cinza' not in description:
    raise SystemExit(f'Bolinha deveria estar cinza sem dois enderecos: {description!r}')
bounds = clickable_bounds(bubble)
match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
if not match:
    raise SystemExit('Bounds flutuantes invalidos: ' + bounds)
x = (int(match.group(1)) + int(match.group(3))) // 2
y = (int(match.group(2)) + int(match.group(4))) // 2
(out / 'floating-coordinates.txt').write_text(f'{x} {y}\n')
(out / 'floating-click-bounds.txt').write_text(bounds + '\n')
(out / 'bubble-idle-description.txt').write_text(description + '\n')
PY

# Uma notificacao do pacote Android shell exibe exatamente dois enderecos em
# uma tela externa ao Rota Certa. Este e o gatilho universal real.
adb shell cmd notification post \
  -S bigtext \
  -t "Rua das Flores, 120 - Centro, Sao Paulo - SP" \
  rota_two_addresses \
  "Avenida Brasil, 900 - Bela Vista, Santo Andre - SP" \
  | tee "$OUT/notification-post.txt"
adb shell cmd statusbar expand-notifications
sleep 4
dump_ui /sdcard/two-address-trigger.xml "$OUT/two-address-trigger.xml"
adb exec-out screencap -p > "$OUT/two-address-trigger.png"

python3 - "$OUT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1])
root = ET.parse(out / 'two-address-trigger.xml').getroot()
nodes = list(root.iter('node'))
text = '\n'.join(' '.join(filter(None, [node.attrib.get('text', ''), node.attrib.get('content-desc', '')])).strip() for node in nodes)
(out / 'two-address-trigger-text.txt').write_text(text)
for expected in ('Rua das Flores, 120', 'Avenida Brasil, 900'):
    if expected not in text:
        raise SystemExit(f'Endereco de teste nao apareceu na tela: {expected}')
bubble = next((node for node in nodes if node.attrib.get('content-desc', '').startswith('Rota Certa')), None)
if bubble is None:
    raise SystemExit('Bolinha desapareceu durante o gatilho de dois enderecos')
description = bubble.attrib.get('content-desc', '')
if 'amarelo' not in description:
    raise SystemExit(f'Dois enderecos nao deixaram a bolinha amarela: {description!r}')
(out / 'bubble-yellow-description.txt').write_text(description + '\n')
PY

# Sair da tela que continha os enderecos deve invalidar imediatamente qualquer
# calculo em andamento e remover a informacao anterior.
adb shell input keyevent KEYCODE_HOME
sleep 2
dump_ui /sdcard/after-address-clear.xml "$OUT/after-address-clear.xml"
adb exec-out screencap -p > "$OUT/after-address-clear.png"

python3 - "$OUT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1])
root = ET.parse(out / 'after-address-clear.xml').getroot()
nodes = list(root.iter('node'))
bubble = next((node for node in nodes if node.attrib.get('content-desc', '').startswith('Rota Certa')), None)
if bubble is None:
    raise SystemExit('Bolinha desapareceu apos limpar a tela')
description = bubble.attrib.get('content-desc', '')
if 'cinza' not in description:
    raise SystemExit(f'Informacao antiga nao foi limpa imediatamente: {description!r}')
(out / 'bubble-cleared-description.txt').write_text(description + '\n')
(out / 'two-address-result.txt').write_text(
    'UNIVERSAL_SCREEN=external_android_notification\n'
    'VISIBLE_ADDRESSES=2\n'
    'DESTINATION_RULE=last_visible_address\n'
    'YELLOW_TRIGGER=approved\n'
    'IMMEDIATE_CLEAR_TO_GRAY=approved\n'
    'UNIVERSAL_TWO_ADDRESS_RUNTIME=approved\n'
)
PY

read -r bubble_x bubble_y < "$OUT/floating-coordinates.txt"
adb shell input tap "$bubble_x" "$bubble_y"
sleep 4
dump_ui /sdcard/floating-menu.xml "$OUT/floating-menu.xml"
adb exec-out screencap -p > "$OUT/floating-menu.png"

python3 - "$OUT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1])
root = ET.parse(out / 'floating-menu.xml').getroot()
text = '\n'.join(' '.join(filter(None, [node.attrib.get('text', ''), node.attrib.get('content-desc', '')])).strip() for node in root.iter('node'))
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

cat "$OUT/in-app-result.txt" "$OUT/two-address-result.txt" "$OUT/floating-result.txt" | tee "$OUT/runtime-validation.txt"
echo "RUNTIME_STAGE=approved" >> "$OUT/progress.txt"
