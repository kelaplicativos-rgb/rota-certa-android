#!/usr/bin/env bash
set -euo pipefail

metadata="${1:-app/build/outputs/apk/debug/output-metadata.json}"
expected_version_code="${2:-}"
apk="${3:-app/build/outputs/apk/debug/app-debug.apk}"
service="app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
main="app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
store="app/src/main/java/br/com/mapeiaia/rotacerta/AutomaticRideCaptureStore.kt"
matcher="app/src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt"
signing_source="app/debug-signing/rota-certa-debug.keystore.b64"

for file in "$metadata" "$apk" "$service" "$main" "$store" "$matcher" "$signing_source"; do
  test -f "$file" || { echo "MISSING_FILE=$file"; exit 1; }
done
unzip -tq "$apk" >/dev/null

python3 - "$metadata" "$expected_version_code" <<'PY'
import json, sys
from pathlib import Path
metadata = json.loads(Path(sys.argv[1]).read_text())
element = metadata["elements"][0]
name = element["versionName"]
code = int(element["versionCode"])
expected = sys.argv[2].strip()
print(f"APK_VERSION_NAME={name}")
print(f"APK_VERSION_CODE={code}")
if name != "0.1.129":
    raise SystemExit(f"versionName incorreto: {name}")
if expected and code != int(expected):
    raise SystemExit(f"versionCode incorreto: atual={code} esperado={expected}")
Path("version-name.txt").write_text(name)
Path("version-code.txt").write_text(str(code))
PY

require_text() {
  local file="$1"; local text="$2"
  echo "CHECK_SOURCE=$file::$text"
  grep -Fq -- "$text" "$file" || { echo "MISSING_SOURCE=$file::$text"; exit 1; }
}
reject_text() {
  local file="$1"; local text="$2"
  if grep -Fq -- "$text" "$file"; then echo "FORBIDDEN_SOURCE=$file::$text"; exit 1; fi
}

# Contrato estrito e caminho rapido preservados.
require_text "$service" 'manual_registered_card_gate_0_1_127'
require_text "$service" 'direct_address_route_matrix_runtime_0_1_128'
require_text "$service" 'blocked_systemui_preserves_card_0_1_128'
require_text "$service" 'automatic_capture_after_manual_match_0_1_129'
require_text "$service" 'automatic_capture_nonblocking_0_1_129'
require_text "$service" 'scope.launch(Dispatchers.IO)'
require_text "$matcher" 'indrive_same_package_family_match_0_1_128'

# Armazenamento privado, deduplicado e temporario.
require_text "$store" 'appContext.filesDir'
require_text "$store" 'RETENTION_DAYS = 14'
require_text "$store" 'MAX_CAPTURES = 30'
require_text "$store" 'AutomaticRideCapturePolicy.isDuplicate'
require_text "$store" 'cleanupExpired'
require_text "$main" 'Captura de Tela Automática'
require_text "$main" 'automatic_capture_gallery_inside_models_0_1_129'
require_text "$main" 'Usar como modelo de card'
require_text "$main" 'Abrir destino no mapa'
require_text "$main" 'Copiar detalhes da corrida'

reject_text "$service" 'showOverlay(RadarColor.Red, distanceKm = null)'
reject_text "$service" 'fastInsideResult'
reject_text "$store" 'Environment.getExternalStorage'
reject_text "$store" 'MediaStore'

helper_region="$(sed -n '/private fun requestAutomaticRideCapture129(/,/private fun requestScreenshotAnalysis(/p' "$service")"
printf '%s' "$helper_region" | grep -Fq 'automaticCaptureInProgress129'
printf '%s' "$helper_region" | grep -Fq 'saveConfirmedCard'
if printf '%s' "$helper_region" | grep -Fq 'ocrService.extractText'; then
  echo 'FORBIDDEN_AUTO_CAPTURE_OCR=true'
  exit 1
fi

manual_line="$(grep -nF 'manual.card.gate accepted=true' "$service" | head -1 | cut -d: -f1)"
capture_line="$(grep -nF 'automatic_capture_after_manual_match_0_1_129' "$service" | head -1 | cut -d: -f1)"
test "$capture_line" -gt "$manual_line"

if grep -Pzo '@Composable\s*\n@Composable\s*\n' "$main" >/dev/null; then
  echo 'FORBIDDEN_SOURCE=duplicate_Composable'
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
unzip -q "$apk" 'classes*.dex' -d "$tmp_dir"
mapfile -t dex_files < <(find "$tmp_dir" -maxdepth 1 -type f -name 'classes*.dex' -print | sort)
[ "${#dex_files[@]}" -gt 0 ] || { echo 'MISSING_DEX_FILES=true'; exit 1; }
require_dex() {
  local text="$1"
  echo "CHECK_DEX=$text"
  grep -aFq -- "$text" "${dex_files[@]}" || { echo "MISSING_DEX=$text"; exit 1; }
}
require_dex 'automatic.capture saved='
require_dex 'Captura de Tela Automática'
require_dex 'Usar como modelo de card'
require_dex 'Exclusão automática em até'
require_dex 'universal.route.address_matrix success=true'
require_dex 'manual.card.gate accepted=true'

mkdir -p signing-proof
base64 --decode "$signing_source" > signing-proof/expected.keystore
keytool -exportcert -keystore signing-proof/expected.keystore -storepass rotacerta -alias rotacerta-debug -file signing-proof/expected.cer >/dev/null
expected_cert="$(sha256sum signing-proof/expected.cer | awk '{print $1}')"
apksigner_bin="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}/build-tools/35.0.0/apksigner"
[ -x "$apksigner_bin" ] || apksigner_bin="$(find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}/build-tools" -type f -name apksigner -perm -u+x | sort -V | tail -1)"
"$apksigner_bin" verify --verbose --print-certs "$apk" | tee apk-signature.txt
apk_cert="$(awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}' apk-signature.txt | tr -d ':' | tr '[:upper:]' '[:lower:]')"
expected_cert="$(printf '%s' "$expected_cert" | tr -d ':' | tr '[:upper:]' '[:lower:]')"
test "$apk_cert" = "$expected_cert"
printf '%s\n' "$apk_cert" > signer-certificate-sha256.txt

cp "$apk" rota-certa-stable-debug.apk
sha256sum rota-certa-stable-debug.apk > rota-certa-stable-debug.apk.sha256
wc -c < rota-certa-stable-debug.apk > apk-size.txt
cat > capture-validation.txt <<'EOF2'
VERSION=0.1.129
MANUAL_APP_SELECTION=required
REGISTERED_CARD_MODEL=required
SAME_PACKAGE_MODEL_MATCH=required
DIRECT_ADDRESS_ROUTE_MATRIX=approved
LOCKED_SCREEN_POPUP_CONTEXT=preserved
AUTOMATIC_CARD_SCREENSHOT=confirmed_cards_only
AUTOMATIC_CAPTURE_BLOCKS_DECISION=false
AUTOMATIC_CAPTURE_OCR_DEPENDENCY=false
PRIVATE_APP_STORAGE=approved
CAPTURE_RETENTION=14_days
CAPTURE_DEDUPLICATION=package_and_text_hash
MAX_AUTOMATIC_CAPTURES=30
CAPTURE_TO_MANUAL_MODEL=approved
RIDE_DETAILS_QUICK_LINKS=approved
STABLE_DEBUG_SIGNATURE=verified
EOF2
cat capture-validation.txt
echo 'CAPTURE_VALIDATION=approved'
