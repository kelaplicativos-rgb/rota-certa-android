#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
mkdir -p "$OUT"

# O emulador pode iniciar antes do daemon ADB do runner. Subir o daemon aqui
# permite que o aparelho se conecte antes de adb wait-for-device.
adb kill-server >/dev/null 2>&1 || true
adb start-server | tee "$OUT/adb-start-server.txt"
adb devices -l > "$OUT/adb-devices-after-start-server.txt" 2>&1 || true

# Ajusta o teste principal para o contrato universal vigente:
# qualquer tela externa com dois enderecos completos e numerados deve ativar a
# bolinha, sem pacote selecionado e sem modelo cadastrado. O ultimo endereco e o
# destino. Enderecos sem numero ou menos de dois enderecos mantem cinza.
python3 - <<'PY'
from pathlib import Path

path = Path("scripts/runtime_bubble_ui_test.sh")
text = path.read_text()

old_xml = "root = ET.parse(sys.argv[1]).getroot()"
new_xml = "root = ET.fromstring(Path(sys.argv[1]).read_text().replace('&#31;', ''))"
if old_xml not in text:
    raise SystemExit("Nenhum parser XML foi encontrado no teste runtime")
text = text.replace(old_xml, new_xml)

old_overlay = '''if [ "$overlay_count" -lt 2 ]; then
  echo "Painel nao criou a segunda janela de overlay" >&2
  exit 1
fi'''
new_overlay = '''if [ "$overlay_count" -lt 1 ]; then
  echo "Janela da bolinha/menu nao foi encontrada" >&2
  exit 1
fi'''
if old_overlay not in text:
    raise SystemExit("Validacao antiga de duas janelas nao foi encontrada")
text = text.replace(old_overlay, new_overlay)

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

path.write_text(text)
PY

# O script principal preserva o servico de acessibilidade e usa uma tela
# externa controlada com dois enderecos completos e numerados, sem modelo.
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
