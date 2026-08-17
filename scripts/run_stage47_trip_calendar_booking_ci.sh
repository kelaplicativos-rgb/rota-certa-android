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
    raise SystemExit(f'Stage47 R8 materialize-only anchor expected once, got {source.count(anchor)}')
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

# Stage47 must be additive. Hash every existing Kotlin runtime source before
# materialization and require byte-for-byte identity afterwards.
BASE="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta"
find "$BASE" -type f -name '*.kt' ! -path '*/trips/*' -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE/runtime-kotlin-before.sha256"
sha256sum "$BASE/LiveRideAccessibilityService.kt" > "$EVIDENCE/farol-before.sha256"

python3 "$PATCHES/scripts/apply_stage47_trip_calendar_booking.py" "$SOURCE" | tee "$EVIDENCE/materialize-stage47.txt"
python3 "$PATCHES/scripts/apply_stage47_route_planner_ui.py" "$SOURCE" | tee "$EVIDENCE/materialize-route-planner-ui.txt"

find "$BASE" -type f -name '*.kt' ! -path '*/trips/*' -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE/runtime-kotlin-after.sha256"
cmp "$EVIDENCE/runtime-kotlin-before.sha256" "$EVIDENCE/runtime-kotlin-after.sha256"
sha256sum "$BASE/LiveRideAccessibilityService.kt" > "$EVIDENCE/farol-after.sha256"
cmp "$EVIDENCE/farol-before.sha256" "$EVIDENCE/farol-after.sha256"
printf 'stage47_existing_runtime_kotlin_byte_for_byte_preserved=PASS farol_untouched=true\n' | tee "$EVIDENCE/farol-isolation.txt"

grep -Fq 'versionCode = 5520' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.227"' "$SOURCE/app/build.gradle.kts"
grep -Fq 'STAGE47_TRIP_CALENDAR_BOOKING' "$SOURCE/app/src/main/AndroidManifest.xml"
grep -Fq '.trips.TripsActivity' "$SOURCE/app/src/main/AndroidManifest.xml"
grep -Fq '.trips.TripQuickTileService' "$SOURCE/app/src/main/AndroidManifest.xml"
grep -Fq '.trips.TripWidgetProvider' "$SOURCE/app/src/main/AndroidManifest.xml"

python3 - "$SOURCE" "$PATCHES" <<'PY' | tee "$EVIDENCE/static-stage47.txt"
from pathlib import Path
import sys
source=Path(sys.argv[1]); patches=Path(sys.argv[2])
trip=source/'app/src/main/java/br/com/mapeiaia/rotacerta/trips'
required={
 'TripDomain.kt','TripCalendar.kt','TripStore.kt','TripRemoteApi.kt','TripAndroidEntryPoints.kt','TripsActivity.kt',
 'TripRoutePlanner.kt','TripRoutePlannerUi.kt'
}
assert required == {p.name for p in trip.glob('*.kt')}, {p.name for p in trip.glob('*.kt')}
domain=(trip/'TripDomain.kt').read_text()
for marker in ('SeatAvailabilityEngine','segmentLoads','BookingStatus.HELD','TripStatus.PUBLISHED','loads.all { it.availableSeats == 0 }'):
    assert marker in domain, marker
calendar=(trip/'TripCalendar.kt').read_text()
for marker in ('BEGIN:VCALENDAR','CalendarContract.Events.CONTENT_URI','text/calendar','FileProvider.getUriForFile'):
    assert marker in calendar, marker
entry=(trip/'TripAndroidEntryPoints.kt').read_text()
for marker in ('ShortcutManager','requestPinShortcut','TileService','AppWidgetProvider','ACTION_NEW_TRIP'):
    assert marker in entry, marker
route=(trip/'TripRoutePlanner.kt').read_text()
for marker in ('directions/v2:computeRoutes','routes.legs.distanceMeters','routes.legs.duration','TRAFFIC_AWARE','plannedArrivalMillis'):
    assert marker in route, marker
ui=(trip/'TripsActivity.kt').read_text()
for marker in ('Agenda de Viagens','Publicar online','Google/Agenda','Confirmar reserva','Integração online','TripRoutePlannerControl','routePlan'):
    assert marker in ui, marker
service=(source/'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt').read_text()
assert 'br.com.mapeiaia.rotacerta.trips' not in service
api=(patches/'trip-platform/functions/index.js').read_text()
for marker in ('db.runTransaction','segmentLoads','timingSafeEqual','ROTA_CERTA_DRIVER_TOKEN','insufficient_seats'):
    assert marker in api, marker
rules=(patches/'trip-platform/firestore.rules').read_text()
assert 'allow read, write: if false' in rules
page=(patches/'trip-platform/public/index.html').read_text()
assert 'Agenda de Viagens' in page and 'Content-Security-Policy' in page
feed=(patches/'trip-platform/calendar-functions/index.js').read_text()
assert 'ROTA_CERTA_PUBLIC_CALENDAR_TOKEN' in feed and 'text/calendar' in feed
print('stage47_static_architecture=PASS segment_capacity=true calendar=true shortcut=true tile=true widget=true route_eta=true remote_api=true public_booking=true private_firestore=true public_calendar_feed=true farol_reference=false')
PY

python3 - "$SOURCE" <<'PY' | tee "$EVIDENCE/test-inventory.txt"
from pathlib import Path
import sys
n=sum(p.read_text().count('@Test') for p in (Path(sys.argv[1])/'app/src/test/java').rglob('*.kt'))
print('total_at_test='+str(n)); assert n==1272,n
PY

node --check "$PATCHES/trip-platform/functions/index.js"
node --check "$PATCHES/trip-platform/public/app.js"
node --check "$PATCHES/trip-platform/calendar-functions/index.js"
node --test "$PATCHES/trip-platform/functions/test"/*.test.js | tee "$EVIDENCE/backend-contract-tests.txt"
(
  cd "$PATCHES/trip-platform/functions"
  npm install --ignore-scripts --no-audit --no-fund
  npm ls --depth=0
) | tee "$EVIDENCE/backend-dependencies.txt"
(
  cd "$PATCHES/trip-platform/calendar-functions"
  npm install --ignore-scripts --no-audit --no-fund
  npm ls --depth=0
) | tee "$EVIDENCE/calendar-feed-dependencies.txt"
printf 'stage47_backend_validation=PASS syntax=true contract_tests=true dependencies_resolved=true firestore_direct_access_denied=true\n' | tee "$EVIDENCE/backend-validation.txt"

cd "$SOURCE"
./gradlew --no-daemon --rerun-tasks testDebugUnitTest lintDebug assembleDebug | tee "$EVIDENCE/final-gradle-validation.log"
python3 - <<'PY' | tee "$EVIDENCE/test-counts.txt"
from pathlib import Path
import xml.etree.ElementTree as E
all_t=all_f=all_e=all_s=0
trip_t=trip_f=trip_e=trip_s=0
for p in Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    r=E.parse(p).getroot()
    vals=(int(r.attrib.get('tests',0)),int(r.attrib.get('failures',0)),int(r.attrib.get('errors',0)),int(r.attrib.get('skipped',0)))
    all_t+=vals[0]; all_f+=vals[1]; all_e+=vals[2]; all_s+=vals[3]
    if '.trips.' in p.name:
        trip_t+=vals[0]; trip_f+=vals[1]; trip_e+=vals[2]; trip_s+=vals[3]
print('trip',trip_t,trip_f,trip_e,trip_s)
print('full',all_t,all_f,all_e,all_s)
assert (trip_t,trip_f,trip_e,trip_s)==(10,0,0,0),(trip_t,trip_f,trip_e,trip_s)
assert (all_t,all_f,all_e,all_s)==(1272,0,0,0),(all_t,all_f,all_e,all_s)
PY
printf 'stage47_gradle_validation=PASS trip=10/10 full=1272/1272 lint=PASS assemble=PASS\n' | tee "$EVIDENCE/gradle-validation.txt"

APK="$EVIDENCE/Rota-Certa-Agenda-Viagens-Stage47-0.1.227.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK" | tee "$EVIDENCE/badging.txt"
grep -q "package: name='br.com.mapeiaia.rotacerta' versionCode='5520' versionName='0.1.227'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
unzip -t "$APK" | tee "$EVIDENCE/zip.txt"
mkdir -p "$EVIDENCE/dex"
unzip -Z1 "$APK" | grep -E '^classes([0-9]+)?\.dex$' | tee "$EVIDENCE/dex-inventory.txt"
while IFS= read -r dex; do unzip -p "$APK" "$dex" > "$EVIDENCE/dex/$dex"; done < "$EVIDENCE/dex-inventory.txt"
cat "$EVIDENCE"/dex/classes*.dex > "$EVIDENCE/all-classes.dex"
for marker in \
  'Agenda de Viagens' \
  'rota_certa_trips_stage47' \
  'SeatAvailabilityEngine' \
  'TripRoutePlanner' \
  'TripQuickTileService' \
  'TripWidgetProvider' \
  'FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8' \
  'FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7' \
  'FAROL_ATOMIC_TRANSITION_STAGE46_R5' \
  'FAROL_STABLE_FINAL_LATCH_STAGE46_R4'; do
  grep -a -q "$marker" "$EVIDENCE/all-classes.dex"
done
sha256sum "$APK" | tee "$EVIDENCE/apk-sha256.txt"
sha512sum "$APK" | tee "$EVIDENCE/apk-sha512.txt"
stat -c '%s' "$APK" | tee "$EVIDENCE/apk-size.txt"
tar -czf "$EVIDENCE/rota-certa-trip-platform-stage47.tar.gz" -C "$PATCHES" trip-platform
sha256sum "$EVIDENCE/rota-certa-trip-platform-stage47.tar.gz" | tee "$EVIDENCE/platform-sha256.txt"
printf 'stage47_apk_validation=PASS package=br.com.mapeiaia.rotacerta version=0.1.227/5520 signature_v2=true trip_markers=true inherited_farol_markers=true\n' | tee "$EVIDENCE/apk-validation.txt"
printf 'stage47_end_to_end=PASS version=0.1.227/5520 tests=1272 trip_tests=10 backend_contract=true segment_booking=true route_eta=true public_calendar=true farol_byte_for_byte_preserved=true online_activation_requires_external_firebase_secrets=true\n' | tee "$EVIDENCE/final-status.txt"
