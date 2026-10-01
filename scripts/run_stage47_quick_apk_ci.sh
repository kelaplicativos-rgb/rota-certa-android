#!/usr/bin/env bash
set -euo pipefail

SOURCE="$(cd "${1:?source}" && pwd)"
PATCHES="$(cd "${2:?patches}" && pwd)"
HIST19="$(cd "${3:?historical19}" && pwd)"
HIST34="$(cd "${4:?historical34}" && pwd)"
mkdir -p "${5:?evidence}"
EVIDENCE="$(cd "$5" && pwd)"
mkdir -p "$EVIDENCE/r8-bootstrap"

R8_MATERIALIZER="$EVIDENCE/run-stage46-r8-materialize-only.sh"
python3 - "$PATCHES/scripts/run_stage46_r8_reproducible_ci.sh" "$R8_MATERIALIZER" <<'PY'
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text(encoding='utf-8')
out = Path(sys.argv[2])
anchor = '''python3 - "$SOURCE" "$PATCHES" <<'PY' | tee "$EVIDENCE/static-r8.txt"'''
if source.count(anchor) != 1:
    raise SystemExit(f'R8 materialize-only anchor expected once, got {source.count(anchor)}')
early = '''if [[ "${STAGE46_R8_MATERIALIZE_ONLY:-0}" == "1" ]]; then
  printf 'stage46_r8_materialized_for_stage47=PASS version=0.1.226/5510\n' | tee "$EVIDENCE/final-status.txt"
  exit 0
fi

'''
out.write_text(source.replace(anchor, early + anchor, 1), encoding='utf-8')
PY

STAGE46_R8_MATERIALIZE_ONLY=1 bash "$R8_MATERIALIZER" "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r8-bootstrap"
grep -Fq 'stage46_r8_materialized_for_stage47=PASS version=0.1.226/5510' "$EVIDENCE/r8-bootstrap/final-status.txt"
grep -Fq 'versionCode = 5510' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.226"' "$SOURCE/app/build.gradle.kts"

FAROL="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
sha256sum "$FAROL" > "$EVIDENCE/farol-before.sha256"

python3 "$PATCHES/scripts/apply_stage47_trip_calendar_booking.py" "$SOURCE" "$PATCHES" | tee "$EVIDENCE/materialize-stage47.txt"
python3 "$PATCHES/scripts/apply_stage47_route_planner_ui.py" "$SOURCE" | tee "$EVIDENCE/materialize-route-planner-ui.txt"
python3 "$PATCHES/scripts/apply_stage47_internal_trip_shortcut.py" "$SOURCE" | tee "$EVIDENCE/materialize-internal-trip-shortcut.txt"

sha256sum "$FAROL" > "$EVIDENCE/farol-after.sha256"
cmp "$EVIDENCE/farol-before.sha256" "$EVIDENCE/farol-after.sha256"
grep -Fq 'versionCode = 5520' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.227"' "$SOURCE/app/build.gradle.kts"
printf 'stage47_quick_materialization=PASS farol_untouched=true\n' | tee "$EVIDENCE/materialization-status.txt"

cd "$SOURCE"
./gradlew --no-daemon testDebugUnitTest --tests 'br.com.mapeiaia.rotacerta.trips.*' assembleDebug | tee "$EVIDENCE/gradle-quick.log"

APK="$EVIDENCE/Rota-Certa-Agenda-Viagens-Stage47-R1-0.1.227.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK" | tee "$EVIDENCE/badging.txt"
grep -q "package: name='br.com.mapeiaia.rotacerta' versionCode='5520' versionName='0.1.227'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
unzip -t "$APK" | tee "$EVIDENCE/zip.txt"
sha256sum "$APK" | tee "$EVIDENCE/apk-sha256.txt"
stat -c '%s' "$APK" | tee "$EVIDENCE/apk-size.txt"
printf 'stage47_quick_apk=PASS version=0.1.227/5520 trip_tests=PASS assemble=PASS signature_v2=PASS farol_untouched=true\n' | tee "$EVIDENCE/final-status.txt"
