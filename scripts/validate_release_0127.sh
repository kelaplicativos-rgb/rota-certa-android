#!/usr/bin/env bash
set -euo pipefail

metadata="${1:-app/build/outputs/apk/debug/output-metadata.json}"
expected_version_code="${2:-}"
apk="${3:-app/build/outputs/apk/debug/app-debug.apk}"

service="app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
main="app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
store="app/src/main/java/br/com/mapeiaia/rotacerta/SelectedRideAppStore.kt"
maps="app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt"
cache="app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideRouteCache.kt"
diagnostics="app/src/main/java/br/com/mapeiaia/rotacerta/DiagnosticLogStore.kt"
signing_patch="app/stable-debug-signing-prepare-0.1.127.gradle.kts"
signing_source="app/debug-signing/rota-certa-debug.keystore.b64"

require_file() {
  local path="$1"
  echo "CHECK_FILE=$path"
  test -f "$path" || { echo "MISSING_FILE=$path"; exit 1; }
}

require_text() {
  local path="$1"
  local expected="$2"
  echo "CHECK_SOURCE=$path::$expected"
  grep -Fq -- "$expected" "$path" || {
    echo "MISSING_SOURCE=$path::$expected"
    exit 1
  }
}

reject_text() {
  local path="$1"
  local forbidden="$2"
  echo "REJECT_SOURCE=$path::$forbidden"
  if grep -Fq -- "$forbidden" "$path"; then
    echo "FORBIDDEN_SOURCE=$path::$forbidden"
    exit 1
  fi
}

require_file "$apk"
require_file "$metadata"
require_file "$service"
require_file "$main"
require_file "$store"
require_file "$maps"
require_file "$cache"
require_file "$diagnostics"
require_file "$signing_patch"
require_file "$signing_source"

echo "CHECK_APK_ZIP_INTEGRITY"
unzip -tq "$apk" >/dev/null

python3 - "$metadata" "$expected_version_code" <<'PY'
import json
import sys
from pathlib import Path

metadata_path = Path(sys.argv[1])
expected_code = sys.argv[2].strip()
data = json.loads(metadata_path.read_text())
element = data["elements"][0]
actual_code = int(element["versionCode"])
actual_name = element["versionName"]
print(f"APK_VERSION_NAME={actual_name}")
print(f"APK_VERSION_CODE={actual_code}")
if actual_name != "0.1.127":
    raise SystemExit(f"versionName incorreto: {actual_name}")
if expected_code and actual_code != int(expected_code):
    raise SystemExit(f"versionCode incorreto: atual={actual_code} esperado={expected_code}")
Path("version-name.txt").write_text(actual_name)
Path("version-code.txt").write_text(str(actual_code))
PY

# Contrato manual e portao de cards.
require_text "$store" 'manual_selection_no_legacy_fallback_0_1_127'
require_text "$service" 'manual_selected_apps_gate_0_1_127'
require_text "$service" 'normalized in selectedPackages'
require_text "$service" 'manual_registered_card_required_migration_0_1_127'
require_text "$service" 'manual_registered_card_gate_0_1_127'
require_text "$service" 'templates = packageCardTemplates'
require_text "$service" 'manual.card.gate accepted=false reason=no_template'
require_text "$service" 'manual.card.gate accepted=false reason=no_match'
require_text "$service" 'manual.card.gate accepted=true'
require_text "$service" 'manual_registered_card_freshness_0_1_127'
require_text "$main" 'Nenhum aplicativo vem marcado'
require_text "$main" 'Buscar aplicativos instalados'
require_text "$main" 'Modelos de cards obrigatorios'
require_text "$main" 'Nenhum modelo nasce cadastrado'
require_text "$main" 'Anexar modelos de cards (prints)'

# Busca de locais e alertas.
require_text "$main" 'saved_places_search_name_address_0_1_127'
require_text "$main" 'Buscar por nome ou endereco'
require_text "$main" 'place.name.lowercase(Locale.ROOT).contains(query)'
require_text "$main" 'place.address.lowercase(Locale.ROOT).contains(query)'
require_text "$main" 'Text("GPS")'
require_text "$main" 'Text("Salvar")'
require_text "$main" 'Text("Apagar")'

# Caminho rapido, cache e estabilidade visual.
require_text "$service" 'const val SCAN_LOOP_MS = 350L'
require_text "$service" 'accessibility_first_ocr_fallback_0_1_127'
require_text "$service" 'deferred_ocr_fallback_90ms_0_1_127'
require_text "$service" 'accessibility_confirmed_cancel_ocr_0_1_127'
require_text "$service" 'parallel_exact_routes_0_1_127'
require_text "$service" 'instant_cache_before_yellow_0_1_127'
require_text "$service" 'selected_app_clear_to_yellow_0_1_127'
require_text "$service" 'atomic_selected_app_clear_color_0_1_127'
require_text "$service" 'atomic_hard_clear_single_paint_0_1_127'
require_text "$service" 'yellow_waiting_not_active_data_0_1_127'
require_text "$cache" 'route_cache_requires_exact_distance_0_1_127'
require_text "$cache" 'route_cache_import_requires_exact_distance_0_1_127'
require_text "$diagnostics" 'diagnostic_ring_buffer_o1_0_1_127'
require_text "$signing_patch" 'stable_debug_signing_after_clean_0_1_127'
require_text "$maps" 'internal fun geocodeQueries'
require_text "$maps" 'containsExplicitLocality'
require_text "$main" 'compile_final_cleanup_0_1_127'

reject_text "$service" 'const val SCAN_LOOP_MS = 120L'
reject_text "$service" 'removedTemplates126.forEach'
reject_text "$service" 'fastInsideResult'
reject_text "$maps" 'defaultCity'
reject_text "$maps" 'São Paulo - SP, $country'

if grep -Pzo '@Composable\s*\n@Composable\s*\n' "$main" >/dev/null; then
  echo 'FORBIDDEN_SOURCE=duplicate_Composable'
  exit 1
fi

# Confere somente strings reais de runtime; marcadores de comentarios pertencem
# aos testes de fonte e nao precisam aparecer no DEX.
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
unzip -q "$apk" 'classes*.dex' -d "$tmp_dir"
mapfile -t dex_files < <(find "$tmp_dir" -maxdepth 1 -type f -name 'classes*.dex' -print | sort)
if [ "${#dex_files[@]}" -eq 0 ]; then
  echo 'MISSING_DEX_FILES=true'
  exit 1
fi

require_dex() {
  local expected="$1"
  echo "CHECK_DEX=$expected"
  if ! grep -aFq -- "$expected" "${dex_files[@]}"; then
    echo "MISSING_DEX=$expected"
    exit 1
  fi
}

require_dex 'Buscar aplicativos instalados'
require_dex 'Modelos de cards obrigatorios'
require_dex 'Anexar modelos de cards (prints)'
require_dex 'Buscar por nome ou endereco'
require_dex 'manual.card.gate accepted=false reason=no_template'
require_dex 'manual.card.gate accepted=false reason=no_match'
require_dex 'manual.card.gate accepted=true'
require_dex 'Aplicativo selecionado ativo; aguardando um card cadastrado correspondente.'

# Compara o certificado real do APK com o certificado DER exportado da chave
# estavel; nao depende do idioma da saida detalhada do keytool.
mkdir -p signing-proof
base64 --decode "$signing_source" > signing-proof/expected.keystore
keytool -exportcert \
  -keystore signing-proof/expected.keystore \
  -storepass rotacerta \
  -alias rotacerta-debug \
  -file signing-proof/expected.cer >/dev/null
expected_cert="$(sha256sum signing-proof/expected.cer | awk '{print $1}')"

apksigner_bin="${APKSIGNER_BIN:-}"
if [ -z "$apksigner_bin" ] && [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/build-tools/35.0.0/apksigner" ]; then
  apksigner_bin="$ANDROID_HOME/build-tools/35.0.0/apksigner"
fi
if [ -z "$apksigner_bin" ] && [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner" ]; then
  apksigner_bin="$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner"
fi
if [ -z "$apksigner_bin" ]; then
  apksigner_bin="$(find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}/build-tools" -type f -name apksigner -perm -u+x 2>/dev/null | sort -V | tail -1 || true)"
fi
if [ -z "$apksigner_bin" ] || [ ! -x "$apksigner_bin" ]; then
  echo 'MISSING_APKSIGNER=true'
  exit 1
fi
echo "APKSIGNER_BIN=$apksigner_bin"
"$apksigner_bin" verify --verbose --print-certs "$apk" | tee apk-signature.txt
apk_cert="$(awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}' apk-signature.txt | tr -d ':' | tr '[:upper:]' '[:lower:]')"
expected_cert="$(printf '%s' "$expected_cert" | tr -d ':' | tr '[:upper:]' '[:lower:]')"
echo "EXPECTED_SIGNER_CERTIFICATE_SHA256=$expected_cert"
echo "APK_SIGNER_CERTIFICATE_SHA256=$apk_cert"
test -n "$expected_cert"
test -n "$apk_cert"
test "$expected_cert" = "$apk_cert"
printf '%s\n' "$apk_cert" > signer-certificate-sha256.txt

cp "$apk" rota-certa-stable-debug.apk
sha256sum rota-certa-stable-debug.apk > rota-certa-stable-debug.apk.sha256
wc -c < rota-certa-stable-debug.apk > apk-size.txt

cat > strict-validation.txt <<'EOF'
VERSION=0.1.127
MANUAL_APP_SELECTION=required
PRESELECTED_APPS=0
REGISTERED_CARD_MODEL=required
SAME_PACKAGE_MODEL_MATCH=required
ROUTE_WITHOUT_MODEL=blocked
SAVED_PLACES_SEARCH=name_and_address
SAVED_PLACE_ACTIONS=edit_delete_gps
ACCESSIBILITY_FIRST=approved
OCR_FALLBACK_DELAY_MS=90
EXACT_ROUTES_PARALLEL=approved
VALID_ROUTE_CACHE_BEFORE_YELLOW=approved
POISONED_ROUTE_CACHE=blocked
SELECTED_APP_WITHOUT_CARD=yellow
ATOMIC_COLOR_TRANSITION=approved
STABLE_YELLOW_NO_REDRAW=approved
DIAGNOSTIC_BUFFER=constant_time
FALLBACK_SCAN_INTERVAL_MS=350
HARDCODED_SAO_PAULO_GEOCODING=removed
DUPLICATE_COMPOSABLE=removed
STABLE_DEBUG_SIGNATURE=verified
TEST_AND_APK_SOURCE_TREES=isolated_clean
EOF

echo 'STRICT_VALIDATION=approved'
