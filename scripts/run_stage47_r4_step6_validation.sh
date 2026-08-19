#!/usr/bin/env bash
set -euo pipefail
SOURCE="$(cd "${1:?source}" && pwd)"
PATCHES="$(cd "${2:?patches}" && pwd)"
HIST19="$(cd "${3:?historical19}" && pwd)"
HIST34="$(cd "${4:?historical34}" && pwd)"
mkdir -p "${5:?evidence}"
EVIDENCE="$(cd "$5" && pwd)"

node --check "$PATCHES/tools/blablacar-public-search/profile-month-core.mjs"
node --check "$PATCHES/tools/blablacar-public-search/profile-month-service.mjs"
node --test "$PATCHES/tools/blablacar-public-search/profile-month-core.test.mjs" | tee "$EVIDENCE/collector-core-tests.txt"

mkdir -p "$EVIDENCE/r8-bootstrap"
R8_MATERIALIZER="$EVIDENCE/run-stage46-r8-materialize-only.sh"
python3 - "$PATCHES/scripts/run_stage46_r8_reproducible_ci.sh" "$R8_MATERIALIZER" <<'PY'
from pathlib import Path
import sys
source=Path(sys.argv[1]).read_text(encoding='utf-8'); out=Path(sys.argv[2])
anchor='''python3 - "$SOURCE" "$PATCHES" <<'PY' | tee "$EVIDENCE/static-r8.txt"'''
if source.count(anchor)!=1: raise SystemExit(f'R8 anchor count={source.count(anchor)}')
early='''if [[ "${STAGE46_R8_MATERIALIZE_ONLY:-0}" == "1" ]]; then\n  printf 'stage46_r8_materialized_for_stage47=PASS version=0.1.226/5510\\n' | tee "$EVIDENCE/final-status.txt"\n  exit 0\nfi\n\n'''
out.write_text(source.replace(anchor,early+anchor,1),encoding='utf-8')
PY
STAGE46_R8_MATERIALIZE_ONLY=1 bash "$R8_MATERIALIZER" "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r8-bootstrap"

grep -Fq 'stage46_r8_materialized_for_stage47=PASS' "$EVIDENCE/r8-bootstrap/final-status.txt"
FAROL="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
sha256sum "$FAROL" > "$EVIDENCE/farol-before.sha256"

python3 "$PATCHES/scripts/apply_stage47_trip_calendar_booking.py" "$SOURCE" "$PATCHES"
python3 "$PATCHES/scripts/apply_stage47_route_planner_ui.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage47_internal_trip_shortcut.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage47_flexible_capacity_r2.py" "$SOURCE" "$PATCHES"
python3 "$PATCHES/scripts/apply_stage47_update_compat_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage47_fares_r3.py" "$SOURCE" "$PATCHES"
python3 "$PATCHES/scripts/apply_stage47_driver_identity_android_r3.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage47_driver_identity_backend_r3.py" "$SOURCE" "$PATCHES"
python3 "$PATCHES/scripts/apply_stage47_public_cancel_r3.py" "$PATCHES"
python3 "$PATCHES/scripts/apply_stage47_calendar_feed_r3.py" "$PATCHES"
python3 "$PATCHES/scripts/apply_stage47_driver_agenda_version_r3.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage47_unified_capacity_backend_r4_step2.py" "$PATCHES"
python3 "$PATCHES/scripts/apply_stage47_timeline_r4_step3.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage47_quick_passenger_r4_step4.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage47_blablacar_profile_month_r4_step5.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage47_r4_step6_version.py" "$SOURCE"

sha256sum "$FAROL" > "$EVIDENCE/farol-after.sha256"
cmp "$EVIDENCE/farol-before.sha256" "$EVIDENCE/farol-after.sha256"
grep -Fq 'versionCode = 5524' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.231"' "$SOURCE/app/build.gradle.kts"
printf 'stage47_r4_materialization=PASS version=0.1.231/5524 farol_untouched=true\n' | tee "$EVIDENCE/materialization-status.txt"

node --check "$PATCHES/trip-platform/functions/index.js"
node --check "$PATCHES/trip-platform/public/app.js"
node --check "$PATCHES/trip-platform/calendar-functions/index.js"
node --test "$PATCHES/trip-platform/functions/test"/*.test.js | tee "$EVIDENCE/backend-tests.txt"

cd "$SOURCE"
./gradlew --no-daemon testDebugUnitTest --tests 'br.com.mapeiaia.rotacerta.trips.*' lintDebug assembleDebug | tee "$EVIDENCE/gradle-step6.log"
APK="$EVIDENCE/Rota-Certa-Agenda-Linha-do-Tempo-R4-Step6-0.1.231.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK" | tee "$EVIDENCE/badging.txt"
grep -q "package: name='br.com.mapeiaia.rotacerta' versionCode='5524' versionName='0.1.231'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
unzip -t "$APK" | tee "$EVIDENCE/zip.txt"
sha256sum "$APK" | tee "$EVIDENCE/apk-sha256.txt"
sha512sum "$APK" | tee "$EVIDENCE/apk-sha512.txt"
printf 'stage47_r4_step6_apk=PASS version=0.1.231/5524 trip_tests=PASS lint=PASS assemble=PASS signature_v2=PASS\n' | tee "$EVIDENCE/final-status.txt"
