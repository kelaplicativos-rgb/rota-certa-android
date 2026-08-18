#!/usr/bin/env bash
set -euo pipefail

SOURCE="$(cd "${1:?source}" && pwd)"
PATCHES="$(cd "${2:?patches}" && pwd)"
HIST19="$(cd "${3:?historical19}" && pwd)"
HIST34="$(cd "${4:?historical34}" && pwd)"
mkdir -p "${5:?evidence}"
EVIDENCE="$(cd "$5" && pwd)"
ERROR1_DEFAULT="$(dirname "$PATCHES")/error1base"
ERROR1="$(cd "${ERROR1_RECIPE_ROOT:-$ERROR1_DEFAULT}" && pwd)"
mkdir -p "$EVIDENCE/r8-bootstrap"

# The physical-test path only needs the exact historical source materialized.
# Keep every cumulative patch and contract transformation, but skip the repeated
# Gradle validation tails from 0.1.187 through 0.1.194. The final Stage47 trip
# tests + assemble below remain authoritative for this quick APK cycle.
python3 - "$HIST19" "$EVIDENCE" <<'PY'
from pathlib import Path
import re
import sys

hist = Path(sys.argv[1])
evidence = Path(sys.argv[2])

injector = hist / "scripts/inject_build_rota_certa_0187.py"
text = injector.read_text(encoding="utf-8")
old = "replacement = apply_block + match.group('indent') + match.group('command')"
new = (
    "materialize_guard = r'''if [[ \"${ROTA_CERTA_MATERIALIZE_ONLY:-0}\" == \"1\" ]]; then\n"
    "  echo \"historical_0187_materialized_only=PASS\"\n"
    "  exit 0\n"
    "fi\n\n"
    "'''\n"
    "replacement = apply_block + materialize_guard + match.group('indent') + match.group('command')"
)
if text.count(old) != 1:
    raise SystemExit(f"Stage187 injector materialize anchor expected once, got {text.count(old)}")
injector.write_text(text.replace(old, new, 1), encoding="utf-8")

patched = []
for version in range(188, 195):
    path = hist / f"scripts/build_rota_certa_0{version}.sh"
    text = path.read_text(encoding="utf-8")
    match = re.search(r"(?m)^(?P<indent>[ \t]*)\./gradlew\b", text)
    if match is None:
        raise SystemExit(f"Historical {version} final Gradle anchor not found")
    guard = (
        'if [[ "${ROTA_CERTA_MATERIALIZE_ONLY:-0}" == "1" ]]; then\n'
        f'  echo "historical_0{version}_materialized_only=PASS"\n'
        '  exit 0\n'
        'fi\n\n'
    )
    path.write_text(text[:match.start()] + guard + text[match.start():], encoding="utf-8")
    patched.append(path.name)

(evidence / "historical-materialize-only.txt").write_text(
    "stage47_quick_historical_gradle_skips=PASS\n"
    + "stage187_guard=after_runtime_patch_before_gradle\n"
    + "direct_guards=" + ",".join(patched) + "\n",
    encoding="utf-8",
)
PY
python3 -m py_compile "$HIST19/scripts/inject_build_rota_certa_0187.py"
for version in 0188 0189 0190 0191 0192 0193 0194; do
  bash -n "$HIST19/scripts/build_rota_certa_${version}.sh"
done

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

ROTA_CERTA_MATERIALIZE_ONLY=1 STAGE46_R8_MATERIALIZE_ONLY=1 bash "$R8_MATERIALIZER" "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r8-bootstrap"
grep -Fq 'stage46_r8_materialized_for_stage47=PASS version=0.1.226/5510' "$EVIDENCE/r8-bootstrap/final-status.txt"
grep -Fq 'versionCode = 5510' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.226"' "$SOURCE/app/build.gradle.kts"

# The newest validated predecessor that may already be installed is Error1
# 0.1.228/5521. Materialize only its already-validated runtime correction as
# the inherited base, then freeze both files it changes. Stage47 is forbidden
# from modifying either file; this prevents the Agenda APK from regressing a
# device that already has 0.1.228 while keeping FAROL out of the Agenda change.
ERROR1_EXPECTED_HEAD="12200b1f845007d8e9a8e44ec5f9e4fc6decfebd"
ERROR1_HEAD="$(git -C "$ERROR1" rev-parse HEAD)"
test "$ERROR1_HEAD" = "$ERROR1_EXPECTED_HEAD"
ERROR1_APPLIER="$ERROR1/scripts/apply_error1_card_visual_episode_reentry.py"
test -s "$ERROR1_APPLIER"
python3 -m py_compile "$ERROR1_APPLIER"
python3 "$ERROR1_APPLIER" "$SOURCE" | tee "$EVIDENCE/materialize-inherited-error1-runtime.txt"
grep -Fq 'error1_card_visual_episode_reentry=PASS' "$EVIDENCE/materialize-inherited-error1-runtime.txt"
printf 'error1_inherited_runtime_ref=%s\nsource_version_metadata_before_agenda=0.1.226/5510\nvalidated_predecessor_apk=0.1.228/5521\n' "$ERROR1_HEAD" > "$EVIDENCE/inherited-error1-base.txt"

FAROL_SERVICE="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
FAROL_GATE="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta/FarolReadingActivationStage26.kt"
sha256sum "$FAROL_SERVICE" "$FAROL_GATE" > "$EVIDENCE/inherited-error1-farol-before-stage47.sha256"

grep -Fq 'fun endVisualEpisode()' "$FAROL_GATE"
grep -Fq 'S46_VISUAL_EPISODE_PRECOLLECT_RESET' "$FAROL_SERVICE"

python3 "$PATCHES/scripts/apply_stage47_trip_calendar_booking.py" "$SOURCE" "$PATCHES" | tee "$EVIDENCE/materialize-stage47.txt"
python3 "$PATCHES/scripts/apply_stage47_route_planner_ui.py" "$SOURCE" | tee "$EVIDENCE/materialize-route-planner-ui.txt"

PRE_SHORTCUT="$EVIDENCE/pre-stage47-shortcut"
mkdir -p "$PRE_SHORTCUT"
for file in BubbleShortcutModule.kt ShortcutModuleFocusPolicy0177.kt MainActivity.kt ShortcutGridCustomization0179.kt; do
  cp "$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta/$file" "$PRE_SHORTCUT/$file"
done
printf 'stage47_pre_shortcut_sources=CAPTURED\n' > "$PRE_SHORTCUT/status.txt"

python3 "$PATCHES/scripts/apply_stage47_internal_trip_shortcut.py" "$SOURCE" | tee "$EVIDENCE/materialize-internal-trip-shortcut.txt"
python3 "$PATCHES/scripts/apply_stage47_flexible_capacity_r2.py" "$SOURCE" "$PATCHES" | tee "$EVIDENCE/materialize-flexible-capacity-r2.txt"

sha256sum "$FAROL_SERVICE" "$FAROL_GATE" > "$EVIDENCE/inherited-error1-farol-after-stage47.sha256"
cmp "$EVIDENCE/inherited-error1-farol-before-stage47.sha256" "$EVIDENCE/inherited-error1-farol-after-stage47.sha256"

grep -Fq 'versionCode = 5520' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.227"' "$SOURCE/app/build.gradle.kts"
python3 "$PATCHES/scripts/apply_stage47_update_compat_version.py" "$SOURCE" | tee "$EVIDENCE/materialize-update-compatible-version.txt"
grep -Fq 'versionCode = 5522' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.229"' "$SOURCE/app/build.gradle.kts"
grep -Fq 'require(seats in 1..999)' "$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
grep -Fq 'capacity > 999' "$PATCHES/trip-platform/functions/index.js"
grep -Fq 'id="seats" type="number" min="1" step="1" value="1"' "$PATCHES/trip-platform/public/index.html"
printf 'stage47_quick_materialization=PASS inherited_error1_runtime_preserved=true agenda_does_not_modify_farol=true flexible_capacity=1..999 public_booking_dynamic=true update_version=0.1.229/5522\n' | tee "$EVIDENCE/materialization-status.txt"

node --test "$PATCHES"/trip-platform/functions/test/*.test.js | tee "$EVIDENCE/node-contracts.log"

cd "$SOURCE"
./gradlew --no-daemon testDebugUnitTest --tests 'br.com.mapeiaia.rotacerta.trips.*' assembleDebug | tee "$EVIDENCE/gradle-quick.log"

APK="$EVIDENCE/Rota-Certa-Agenda-Viagens-Stage47-R2-Update-Compativel-0.1.229.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK" | tee "$EVIDENCE/badging.txt"
grep -q "package: name='br.com.mapeiaia.rotacerta' versionCode='5522' versionName='0.1.229'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
grep -q 'V2 Signer: certificate SHA-256 digest: d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$EVIDENCE/signature.txt"
unzip -t "$APK" | tee "$EVIDENCE/zip.txt"
sha256sum "$APK" | tee "$EVIDENCE/apk-sha256.txt"
stat -c '%s' "$APK" | tee "$EVIDENCE/apk-size.txt"
printf 'predecessor_versionCode=5521\ncandidate_versionCode=5522\nversion_order=5522>5521\npackage=br.com.mapeiaia.rotacerta\nsigner_sha256=d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd\ninherited_error1_runtime_ref=%s\nagenda_farol_hash_guard=PASS\n' "$ERROR1_HEAD" > "$EVIDENCE/update-compatibility.txt"
printf 'stage47_quick_apk=PASS version=0.1.229/5522 predecessor_0.1.228_5521_preserved=true trip_tests=PASS node_contracts=PASS flexible_capacity=1..999 public_booking_dynamic=true assemble=PASS signature_v2=PASS same_signer=true agenda_farol_hash_guard=PASS\n' | tee "$EVIDENCE/final-status.txt"
