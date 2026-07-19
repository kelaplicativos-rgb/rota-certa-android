#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
mkdir -p "$OUT"

# O emulador pode iniciar antes do daemon ADB do runner. Subir o daemon aqui
# permite que o aparelho se conecte antes de adb wait-for-device.
adb kill-server >/dev/null 2>&1 || true
adb start-server | tee "$OUT/adb-start-server.txt"
adb devices -l > "$OUT/adb-devices-after-start-server.txt" 2>&1 || true

# Ajusta o teste principal para o contrato universal vigente e para o contrato
# 0.1.114 do toque: a bolinha abre a Home diretamente e nao cria uma segunda
# janela TYPE_ACCESSIBILITY_OVERLAY com a grade flutuante.
python3 - <<'PY'
from pathlib import Path

path = Path("scripts/runtime_bubble_ui_test.sh")
text = path.read_text()

old_xml = "root = ET.parse(sys.argv[1]).getroot()"
new_xml = "root = ET.fromstring(Path(sys.argv[1]).read_text().replace('&#31;', ''))"
if old_xml not in text:
    raise SystemExit("Nenhum parser XML foi encontrado no teste runtime")
text = text.replace(old_xml, new_xml)

old_activation = '''screen_started_ms="$(date +%s%3N)"
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
fi'''
new_activation = '''screen_started_ms="$(date +%s%3N)"
adb shell am start -W -n "$FIXTURE_ACTIVITY" | tee "$OUT/fixture-start.txt"
wait_runtime_prefs \
  "$OUT/universal-runtime-prefs.xml" \
  80 \
  '<int name="runtime_visible_addresses" value="2"' \
  'Avenida Brasil, 900 - Bela Vista, Santo Andre - SP'
screen_finished_ms="$(date +%s%3N)"
echo "$((screen_finished_ms - screen_started_ms))" > "$OUT/universal-screen-check-ms.txt"

if grep -Fq '>cinza|' "$OUT/universal-runtime-prefs.xml"; then
  echo "Dois enderecos numerados permaneceram cinza" >&2
  exit 1
fi
if ! grep -Fq 'runtime_last_destination' "$OUT/universal-runtime-prefs.xml"; then
  echo "Ultimo endereco nao foi salvo como destino" >&2
  exit 1
fi
if grep -Fq 'runtime_registered_template' "$OUT/universal-runtime-prefs.xml"; then
  echo "O leitor universal nao pode depender de modelo cadastrado" >&2
  exit 1
fi'''
if old_activation not in text:
    raise SystemExit("Bloco antigo de bloqueio sem modelo nao foi encontrado")
text = text.replace(old_activation, new_activation)

old_result = '''cat > "$OUT/registered-card-result.txt" <<EOF
CONTROLLED_PACKAGE=$FIXTURE_PACKAGE
VISIBLE_NUMBERED_ADDRESSES_ON_SCREEN=2
REGISTERED_MODELS=0
BUBBLE_STATE=gray
DESTINATION_WITHOUT_MODEL=absent
UNREGISTERED_TWO_ADDRESS_BLOCK=approved
REGISTERED_CARD_RUNTIME=approved
EOF

echo "RUNTIME_STAGE=unregistered_addresses_blocked" >> "$OUT/progress.txt"'''
new_result = '''cat > "$OUT/registered-card-result.txt" <<EOF
CONTROLLED_PACKAGE=$FIXTURE_PACKAGE
VISIBLE_NUMBERED_ADDRESSES_ON_SCREEN=2
REGISTERED_MODELS=0
PACKAGE_SELECTION_REQUIRED=false
MODEL_REQUIRED=false
BUBBLE_STATE=active_not_gray
DESTINATION_WITHOUT_MODEL=present
DESTINATION_RULE=last_complete_numbered_address
UNIVERSAL_TWO_ADDRESS_RUNTIME=approved
EOF

echo "RUNTIME_STAGE=universal_addresses_approved" >> "$OUT/progress.txt"'''
if old_result not in text:
    raise SystemExit("Resultado antigo de card cadastrado nao foi encontrado")
text = text.replace(old_result, new_result)

menu_start_marker = 'read -r bubble_x bubble_y < "$OUT/floating-coordinates.txt"'
menu_start = text.find(menu_start_marker)
if menu_start < 0:
    raise SystemExit("Inicio dos passos antigos do menu nao foi encontrado")

direct_home_block = r'''if ! grep -Fq '>cinza|' "$OUT/cleared-runtime-prefs.xml"; then
  echo "Bolinha nao voltou para cinza depois que os enderecos sumiram" >&2
  exit 1
fi
if grep -Fq 'runtime_last_destination' "$OUT/cleared-runtime-prefs.xml"; then
  echo "Destino anterior permaneceu depois da limpeza" >&2
  exit 1
fi

read -r bubble_x bubble_y < "$OUT/floating-coordinates.txt"
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell input tap "$bubble_x" "$bubble_y"

home_opened=false
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
fi

adb exec-out screencap -p > "$OUT/bubble-tap-home.png"
adb shell dumpsys accessibility > "$OUT/bubble-tap-accessibility.txt"
adb shell dumpsys activity activities > "$OUT/bubble-tap-activity.txt" 2>&1 || true
overlay_count="$(grep -c 'type=TYPE_ACCESSIBILITY_OVERLAY' "$OUT/bubble-tap-accessibility.txt" || true)"
if [ "$overlay_count" -ge 2 ]; then
  echo "Toque ainda criou uma segunda janela de popup" >&2
  exit 1
fi

read_runtime_prefs "$OUT/bubble-tap-runtime-prefs.xml" || true
if grep -Fq '<boolean name="runtime_menu_open" value="true"' "$OUT/bubble-tap-runtime-prefs.xml" 2>/dev/null; then
  echo "Estado antigo do menu flutuante ainda foi ativado" >&2
  exit 1
fi

cat > "$OUT/floating-tap-result.txt" <<EOF
ACCESSIBILITY_SERVICE=enabled
FLOATING_BUBBLE=visible
ACCESSIBILITY_OVERLAY_WINDOWS_AFTER_TAP=$overlay_count
MAIN_TAP=opened_home_direct
FLOATING_POPUP=absent
FLOATING_RUNTIME=approved
EOF

cat "$OUT/in-app-result.txt" "$OUT/floating-window-result.txt" "$OUT/registered-card-result.txt" "$OUT/floating-tap-result.txt" | tee "$OUT/runtime-validation.txt"
cat >> "$OUT/runtime-validation.txt" <<EOF
CLEAR_TO_GRAY=approved
STALE_DESTINATION_AFTER_CLEAR=absent
EOF
echo "RUNTIME_STAGE=approved" >> "$OUT/progress.txt"
'''
text = text[:menu_start] + direct_home_block

path.write_text(text)
PY

# O script principal preserva o servico de acessibilidade, valida o leitor
# universal e termina tocando a bolinha real no launcher.
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
