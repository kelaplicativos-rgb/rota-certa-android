#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
SOURCE_REPOSITORY="$(git rev-parse --show-toplevel)"
BEFORE_HASHES="$(mktemp)"
AFTER_HASHES="$(mktemp)"
TEST_COUNT_STAGING="$(mktemp)"
cleanup() { rm -f "$BEFORE_HASHES" "$AFTER_HASHES" "$TEST_COUNT_STAGING"; }
trap cleanup EXIT

# 1) Reproduz exatamente a 0.1.194 validada fisicamente pelo relatório de 09/08/2026.
bash "$PATCH_REPOSITORY/scripts/build_rota_certa_0194.sh" "$PATCH_REPOSITORY"
grep -Fq 'versionCode = 5478' app/build.gradle.kts
grep -Fq 'versionName = "0.1.194"' app/build.gradle.kts
grep -Fq 'UNIVERSAL_SECOND_PLACE_BOUNDARY_0194' app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt

# 2) A 0.1.195 altera somente a prioridade/deduplicação do farol e adiciona um helper puro.
# Parser, gate visual, decisão, rota, recovery, Manifest e alertas ficam protegidos byte a byte.
PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisualPriority0189.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/FailedCardRecovery0161.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt
)
for file in "${PROTECTED_FILES[@]}"; do
  test -f "$file" || { echo "Arquivo protegido ausente: $file" >&2; exit 1; }
done
sha256sum "${PROTECTED_FILES[@]}" > "$BEFORE_HASHES"

python3 "$PATCH_REPOSITORY/scripts/apply_farol_route_priority_0195.py" "$SOURCE_REPOSITORY"

sha256sum "${PROTECTED_FILES[@]}" > "$AFTER_HASHES"
diff -u "$BEFORE_HASHES" "$AFTER_HASHES"

grep -Fq 'versionCode = 5479' app/build.gradle.kts
grep -Fq 'versionName = "0.1.195"' app/build.gradle.kts
grep -Fq 'BUBBLE_FAST_DESTINATION_DUPLICATE_SKIPPED_0195' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'FarolDestinationFastGate0195.shouldSkipHeavyAnalysis' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'universalLastActiveReadAtElapsedMillis0187 = android.os.SystemClock.elapsedRealtime()' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
! grep -Fq 'universalLastActiveReadAtMillis = System.currentTimeMillis()' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'FAROL_CONFIRMED_DESTINATION_FAST_GATE_0195' app/src/main/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195.kt
grep -Fq 'sameConfirmedDestinationWithFareChangeSkipsWhileRouteIsActive' app/src/test/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195Test.kt
grep -Fq 'changedDestinationNeverSkipsHeavyAnalysis' app/src/test/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195Test.kt
grep -Fq 'closedCardNeverSkipsHeavyAnalysis' app/src/test/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195Test.kt
grep -Fq 'differentPackageNeverReusesAnotherAppsDestination' app/src/test/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195Test.kt

# Prova estrutural fail-closed: usa identidade semântica das declarações, não o RHS textual
# do snapshot. A otimização deve ficar depois do snapshot e imediatamente antes da única
# avaliação pesada real do processRideText.
python3 - <<'PY'
from pathlib import Path
import re

p = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
text = p.read_text(encoding='utf-8')
fn = '    private suspend fun processRideText(\n'
if text.count(fn) != 1:
    raise SystemExit(f'processRideText estrutural inválido: {text.count(fn)} ocorrências')
start = text.index(fn)
end = text.find('\n    private ', start + len(fn))
if end < 0:
    end = len(text)
region = text[start:end]

snapshot_pattern = re.compile(r'(?m)^[ \t]*val[ \t]+snapshotTextChecklist13[ \t]*=')
heavy_pattern = re.compile(
    r'(?ms)^[ \t]*val[ \t]+evaluationChecklist13[ \t]*=[ \t]*'
    r'withContext\([ \t\r\n]*Dispatchers\.Default[ \t\r\n]*\)[ \t\r\n]*\{[ \t\r\n]*'
    r'SimpleSavedAppFarolPolicy\.evaluate[ \t\r\n]*\('
)
fast = '            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(\n'
snapshot_matches = list(snapshot_pattern.finditer(region))
heavy_matches = list(heavy_pattern.finditer(region))
if len(snapshot_matches) != 1:
    raise SystemExit(f'snapshot semântico 0.1.195 inválido/duplicado: {len(snapshot_matches)}')
if len(heavy_matches) != 1:
    raise SystemExit(f'avaliação pesada semântica 0.1.195 inválida/duplicada: {len(heavy_matches)}')
if region.count(fast) != 1:
    raise SystemExit(f'fast gate 0.1.195 inválido/duplicado: {region.count(fast)}')

snapshot_pos = snapshot_matches[0].start()
fast_pos = region.index(fast)
heavy_pos = heavy_matches[0].start()
if not snapshot_pos < fast_pos < heavy_pos:
    raise SystemExit('Ordem insegura: fast gate não está entre snapshot e avaliação pesada')

between_fast_and_heavy = region[fast_pos:heavy_pos]
monotonic = 'universalLastActiveReadAtElapsedMillis0187 = android.os.SystemClock.elapsedRealtime()'
if monotonic not in between_fast_and_heavy:
    raise SystemExit('Fast gate não atualiza a leitura ativa pelo relógio monotônico 0.1.187')
if 'universalLastActiveReadAtMillis' in between_fast_and_heavy:
    raise SystemExit('Fast gate reintroduziu relógio civil legado')
print('farol_fast_gate_order_0195=passed')
print('farol_fast_gate_monotonic_clock_0195=passed')
PY

# O helper não pode conhecer marca/pacote específico e não pode autorizar destino novo.
! grep -E -n 'com\.app99\.driver|com\.ubercab\.driver|sinet\.startup\.indriver' \
  app/src/main/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195.kt
! grep -E -n 'GoogleMaps|DecisionEngine|FarolRealDeviceGate0188|showOverlay\(' \
  app/src/main/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195.kt

echo 'farol_route_priority_0195_contracts=passed'

# 3) Suíte completa. Esta etapa será executada apenas quando o workflow 0.1.195 for acionado.
./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
python3 - <<'PY' > "$TEST_COUNT_STAGING"
import glob
import xml.etree.ElementTree as ET
count = 0
failures = 0
for report in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    root = ET.parse(report).getroot()
    count += int(root.attrib.get('tests', 0))
    failures += int(root.attrib.get('failures', 0)) + int(root.attrib.get('errors', 0))
print(f'tests={count}')
print(f'failures={failures}')
if count < 407:
    raise SystemExit(f'Esperados pelo menos 407 testes após 0.1.195, encontrados {count}')
if failures:
    raise SystemExit('Há testes com falha na 0.1.195')
PY
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

# 4) Validação do APK e prova de que o fast gate entrou no bytecode.
APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.195"
APK_NAME="rota-certa-0.1.195-farol-resposta-prioritaria-validada-em-ci.apk"
test -s "$APK_SOURCE"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"
unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"
test -x "$APKSIGNER"
test -x "$AAPT"
"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5479' versionName='0.1.195'" "$OUTPUT_DIR/badging.txt"
printf '%s\n' 'br.com.mapeiaia.rotacerta' > "$OUTPUT_DIR/package.txt"
printf '%s\n' 'versionName=0.1.195' 'versionCode=5479' > "$OUTPUT_DIR/version.txt"

"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"; done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'FAROL_CONFIRMED_DESTINATION_FAST_GATE_0195' "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'BUBBLE_FAST_DESTINATION_DUPLICATE_SKIPPED_0195' "$OUTPUT_DIR/dex-strings.txt"

cp "$TEST_COUNT_STAGING" "$OUTPUT_DIR/test-count.txt"
grep -Fxq 'failures=0' "$OUTPUT_DIR/test-count.txt"
sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$AFTER_HASHES" "$OUTPUT_DIR/protected-source-sha256.txt"
sha256sum \
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt \
  app/src/main/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195.kt \
  > "$OUTPUT_DIR/changed-source-sha256.txt"
cat > "$OUTPUT_DIR/validation.txt" <<'VALIDATION'
package=br.com.mapeiaia.rotacerta
versionName=0.1.195
versionCode=5479
status=ci_candidate_pending_real_device
confirmed_destination_fast_gate=true
fast_gate_runs_after_materialized_prior_guards=true
fast_gate_uses_monotonic_clock_0187=true
heavy_duplicate_analysis_before_route_result_removed=true
same_destination_fare_time_changes_do_not_reanalyze=true
changed_destination_falls_back_to_full_gate=true
closed_card_falls_back_to_full_gate=true
cross_package_reuse_blocked=true
short_ambiguous_identity_blocked=true
parser_0194_preserved=true
farol_route_gate_0188_unchanged=true
visual_priority_0189_unchanged=true
failed_card_recovery_0161_unchanged=true
decision_engine_unchanged=true
route_engine_unchanged=true
manifest_permissions_unchanged=true
VALIDATION
cat "$OUTPUT_DIR/test-count.txt"
cat "$OUTPUT_DIR/sha256.txt"
echo 'rota_certa_0195_build=passed'
