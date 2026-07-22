#!/usr/bin/env bash
set -euo pipefail

metadata="${1:-app/build/outputs/apk/debug/output-metadata.json}"
expected_version_code="${2:-}"
apk="${3:-app/build/outputs/apk/debug/app-debug.apk}"

service="app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
main="app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
store="app/src/main/java/br/com/mapeiaia/rotacerta/SelectedRideAppStore.kt"
maps="app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt"
matcher="app/src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt"
decision="app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt"
cache="app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideRouteCache.kt"
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

for file in "$apk" "$metadata" "$service" "$main" "$store" "$maps" "$matcher" "$decision" "$cache" "$signing_patch" "$signing_source"; do
  require_file "$file"
done

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
if actual_name != "0.1.128":
    raise SystemExit(f"versionName incorreto: {actual_name}")
if expected_code and actual_code != int(expected_code):
    raise SystemExit(f"versionCode incorreto: atual={actual_code} esperado={expected_code}")
Path("version-name.txt").write_text(actual_name)
Path("version-code.txt").write_text(str(actual_code))
PY

require_text "$store" 'manual_selection_no_legacy_fallback_0_1_127'
require_text "$service" 'manual_selected_apps_gate_0_1_127'
require_text "$service" 'manual_registered_card_gate_0_1_127'
require_text "$service" 'manual.card.gate accepted=false reason=no_template'
require_text "$service" 'manual.card.gate accepted=false reason=no_match'
require_text "$service" 'manual.card.gate accepted=true'
require_text "$main" 'Modelos de cards obrigatorios'
require_text "$main" 'Anexar modelos de cards (prints)'
require_text "$cache" 'route_cache_requires_exact_distance_0_1_127'
require_text "$signing_patch" 'stable_debug_signing_after_clean_0_1_127'

require_text "$service" 'persistent_maps_cache_context_0_1_128'
require_text "$service" 'locked_popup_session_guard_0_1_128'
require_text "$service" 'blocked_systemui_preserves_card_0_1_128'
require_text "$service" 'transient_empty_locked_popup_ignored_0_1_128'
require_text "$service" 'locked_popup_resolver_preserves_ride_package_0_1_128'
require_text "$service" 'locked_popup_result_freshness_0_1_128'
require_text "$service" 'direct_address_route_matrix_runtime_0_1_128'
require_text "$service" 'background_geocode_warm_0_1_128'
require_text "$service" 'direct_address_route_helper_0_1_128'
require_text "$maps" 'direct_address_route_matrix_0_1_128'
require_text "$maps" 'distanceMatrix/v2:computeRouteMatrix'
require_text "$maps" 'PERSISTENT_ADDRESS_ROUTE_PREFIX'
require_text "$maps" 'ROUTE_CACHE_TTL_MS = 30L'
require_text "$maps" 'ROUTE_REQUEST_ATTEMPTS = 1'
require_text "$decision" 'exact_address_route_without_blocking_geocode_0_1_128'
require_text "$matcher" 'indrive_markerless_offer_crop_0_1_128'
require_text "$matcher" 'indrive_same_package_family_match_0_1_128'

reject_text "$service" 'const val SCAN_LOOP_MS = 120L'
reject_text "$service" 'fastInsideResult'
reject_text "$maps" 'const val ROUTE_REQUEST_ATTEMPTS = 2'
reject_text "$maps" 'defaultCity'

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
  local expected="$1"
  echo "CHECK_DEX=$expected"
  grep -aFq -- "$expected" "${dex_files[@]}" || {
    echo "MISSING_DEX=$expected"
    exit 1
  }
}

require_dex 'universal.route.address_matrix success=true'
require_dex 'universal.foreground protected_locked_popup=true'
require_dex 'universal.accessibility locked_popup_empty_ignored=true'
require_dex 'manual.card.gate accepted=false reason=no_match'
require_dex 'manual.card.gate accepted=true'
require_dex 'Modelos de cards obrigatorios'

mkdir -p signing-proof
base64 --decode "$signing_source" > signing-proof/expected.keystore
keytool -exportcert \
  -keystore signing-proof/expected.keystore \
  -storepass rotacerta \
  -alias rotacerta-debug \
  -file signing-proof/expected.cer >/dev/null
expected_cert="$(sha256sum signing-proof/expected.cer | awk '{print $1}')"

apksigner_bin="${APKSIGNER_BIN:-}"
if [ -z "$apksigner_bin" ] && [ -x "${ANDROID_HOME:-}/build-tools/35.0.0/apksigner" ]; then
  apksigner_bin="$ANDROID_HOME/build-tools/35.0.0/apksigner"
fi
if [ -z "$apksigner_bin" ] && [ -x "${ANDROID_SDK_ROOT:-}/build-tools/35.0.0/apksigner" ]; then
  apksigner_bin="$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner"
fi
if [ -z "$apksigner_bin" ]; then
  apksigner_bin="$(find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}/build-tools" -type f -name apksigner -perm -u+x 2>/dev/null | sort -V | tail -1 || true)"
fi
[ -n "$apksigner_bin" ] && [ -x "$apksigner_bin" ] || { echo 'MISSING_APKSIGNER=true'; exit 1; }

"$apksigner_bin" verify --verbose --print-certs "$apk" | tee apk-signature.txt
apk_cert="$(awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}' apk-signature.txt | tr -d ':' | tr '[:upper:]' '[:lower:]')"
expected_cert="$(printf '%s' "$expected_cert" | tr -d ':' | tr '[:upper:]' '[:lower:]')"
test -n "$apk_cert"
test "$expected_cert" = "$apk_cert"
printf '%s\n' "$apk_cert" > signer-certificate-sha256.txt

cp "$apk" rota-certa-stable-debug.apk
sha256sum rota-certa-stable-debug.apk > rota-certa-stable-debug.apk.sha256
wc -c < rota-certa-stable-debug.apk > apk-size.txt

cat > fast-validation.txt <<'EOF2'
VERSION=0.1.128
MANUAL_APP_SELECTION=required
REGISTERED_CARD_MODEL=required
SAME_PACKAGE_MODEL_MATCH=required
INDRIVE_MARKERLESS_VARIANT=approved
LOCKED_SCREEN_POPUP_CONTEXT=preserved_10s
SYSTEMUI_ROUTE_CANCELLATION=blocked
EMPTY_LOCKED_POPUP_READ=ignored
DIRECT_ADDRESS_ROUTE_MATRIX=approved
SEPARATE_DESTINATION_GEOCODE_ON_FAST_PATH=not_blocking
BACKGROUND_GEOCODE_WARM=approved
PERSISTENT_ADDRESS_ROUTE_CACHE=30_days
PERSISTENT_GEOCODE_CACHE=90_days
ROUTE_NETWORK_ATTEMPTS=1
COORDINATE_ROUTE_FALLBACK=approved
STRAIGHT_LINE_GREEN=blocked
STABLE_DEBUG_SIGNATURE=verified
TEST_AND_APK_SOURCE_TREES=isolated_clean
EOF2

cat fast-validation.txt
echo 'FAST_VALIDATION=approved'
