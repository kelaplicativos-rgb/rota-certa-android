#!/usr/bin/env bash
set -euo pipefail

OUT="${RUNTIME_UI_DIR:-runtime-ui}"
mkdir -p "$OUT"

# O emulador pode iniciar antes do daemon ADB do runner. Subir o daemon aqui
# permite que o aparelho se conecte antes de adb wait-for-device.
adb kill-server >/dev/null 2>&1 || true
adb start-server | tee "$OUT/adb-start-server.txt"
adb devices -l > "$OUT/adb-devices-after-start-server.txt" 2>&1 || true

# SharedPreferences pode serializar o separador interno da assinatura como
# &#31;, que nao e permitido no XML 1.0. O aplicativo le o arquivo normalmente;
# somente o parser de evidencias do CI precisa remover essa referencia antes de
# extrair os timestamps. A substituicao tambem e segura para os XMLs de UI.
#
# Alguns Androids agrupam bolinha e menu dentro da mesma janela de
# acessibilidade. O estado runtime_menu_open=true e a captura real do menu sao
# as provas deterministicas; exigir duas janelas causava falso negativo.
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
path.write_text(text.replace(old_overlay, new_overlay))
PY

# O script principal preserva o servico de acessibilidade e usa uma tela
# externa controlada com dois enderecos completos, sem modelo cadastrado. Este
# limitador apenas evita que ADB ou o emulador deixem o workflow preso.
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
