#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"

test -n "${GOOGLE_MAPS_API_KEY:-}"

python scripts/fix_99_overlay_state_0151.py
python scripts/fix_selected_package_state_0152.py
python scripts/fix_transient_launcher_clear_0154.py
python scripts/fix_bounded_empty_read_preservation_0155.py
python scripts/fix_passive_churn_and_launcher_race_0156.py
python scripts/fix_subsecond_farol_0157.py
python scripts/remove_collector_completely_0158.py
python scripts/run_value_finance_0159.py
python scripts/fix_value_finance_0160.py
python scripts/fix_value_finance_0160_compile.py
python "$PATCHES/scripts/fix_failed_card_autocapture_0161.py" .
python "$PATCHES/scripts/fix_failed_card_autocapture_0161_rerun.py" .
python "$PATCHES/scripts/fix_gigu_work_mode_session_0162.py" .
python "$PATCHES/scripts/fix_gigu_work_mode_session_0162_rerun.py" .
python "$PATCHES/scripts/fix_farol_flight_recorder_0163.py" .
python "$PATCHES/scripts/fix_farol_flight_recorder_0163_rerun.py" .
python "$PATCHES/scripts/fix_diagnostic_session_summary_0164.py" .
python "$PATCHES/scripts/fix_diagnostic_session_summary_0164_rerun.py" .
python "$PATCHES/scripts/fix_diagnostic_attempt_timeline_0165.py" .
python "$PATCHES/scripts/fix_diagnostic_attempt_timeline_0165_rerun.py" .
python "$PATCHES/scripts/fix_farol_selected_apps_0166.py" .
python "$PATCHES/scripts/fix_farol_selected_apps_0166_rerun.py" .
python "$PATCHES/scripts/fix_farol_realtime_0167.py" .
python "$PATCHES/scripts/fix_farol_realtime_0167_rerun.py" .
python "$PATCHES/scripts/run_fix_farol_unified_visual_0168.py" .
python "$PATCHES/scripts/fix_farol_unified_visual_0168_compile.py" .
python "$PATCHES/scripts/fix_uber_notification_wakeup_0169.py" .
python "$PATCHES/scripts/fix_notification_wakeup_crash_containment_0170.py" .
cat "$PATCHES"/scripts/fix_home_modules_long_press_0171.py.part0{0,1,2,3,4} > /tmp/fix0171.py
python -m py_compile /tmp/fix0171.py
python /tmp/fix0171.py .

cat "$PATCHES"/scripts/fix_accessibility_resilience_tools_0172.patch.gz.b64.part0{0,1,2,3,4} > /tmp/fix0172.b64
base64 --decode /tmp/fix0172.b64 | gzip --decompress > /tmp/fix0172.patch
echo 'c22d3884779f17f9fff7b42dd6f3450cf3c416c62f33ac219e4611d55d345ae7  /tmp/fix0172.patch' | sha256sum --check
git apply --check /tmp/fix0172.patch
git apply /tmp/fix0172.patch
python "$PATCHES/scripts/fix_accessibility_resilience_tools_0172_test_contract.py" .

base64 --decode "$PATCHES/scripts/fix_deterministic_shortcut_grid_0173.patch.gz.b64" | gzip --decompress > /tmp/fix0173.patch
echo 'acb4ddf9d31d942c9cff86f40965fe2b2910ffee6090cd8c2b804a7530317a6c  /tmp/fix0173.patch' | sha256sum --check
git apply --check /tmp/fix0173.patch
git apply /tmp/fix0173.patch
python "$PATCHES/scripts/fix_deterministic_shortcut_grid_0173_test_contract.py" .

base64 --decode "$PATCHES/scripts/fix_home_inline_modules_0174.patch.gz.b64" | gzip --decompress > /tmp/fix0174.patch
echo '1a0622e82db7aa3468851a7fcfab324d885b7c614567de0f4485e8553784e154  /tmp/fix0174.patch' | sha256sum --check
git apply --check /tmp/fix0174.patch
git apply /tmp/fix0174.patch
python "$PATCHES/scripts/fix_home_inline_modules_0174_test_contract.py" .

python "$PATCHES/scripts/fix_home_bubble_grid_0175.py" .
python "$PATCHES/scripts/fix_shortcut_single_tap_0176.py" .
python "$PATCHES/scripts/fix_shortcut_single_tap_0176_test_contract.py" .

sha256sum \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt \
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt \
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt \
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt \
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt \
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutLongPressPolicy0171.kt \
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActivityLaunchPolicy0176.kt \
  > /tmp/protected-0177.sha256

python "$PATCHES/scripts/fix_shortcut_module_focus_0177.py" .
sha256sum --check /tmp/protected-0177.sha256

grep -q 'versionName = "0.1.177"' app/build.gradle.kts
grep -q 'versionCode = 5380' app/build.gradle.kts
grep -q 'SHORTCUT_MODULE_IDENTITY_FOCUS_0177' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutModuleFocusPolicy0177.kt
grep -q 'openShortcutModule0171(spec) // shortcut_module_identity_focus_0177' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
! grep -q 'openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -q 'navigationRequestKey0177 = System.identityHashCode(launchIntent)' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -q 'Modifier.bringIntoViewRequester(rowFocusRequester0177)' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -q 'moduleFocusRequesters0177\[requestedModuleId0177\]?.bringIntoView()' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt

./gradlew testDebugUnitTest --no-daemon --stacktrace
./gradlew lintDebug --no-daemon --stacktrace
./gradlew clean assembleDebug --no-daemon --stacktrace

APK=app/build/outputs/apk/debug/app-debug.apk
OUT=rota-certa-0.1.177-modulos-grade-foco-validado.apk
test -s "$APK"
unzip -t "$APK" >/dev/null
APKSIGNER=$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -1)
AAPT=$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -1)
"$APKSIGNER" verify --verbose --print-certs "$APK" | tee rota-certa-0.1.177-assinatura.txt
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' rota-certa-0.1.177-assinatura.txt
BADGING=$($AAPT dump badging "$APK" | head -1)
echo "$BADGING" | tee rota-certa-0.1.177-badging.txt
echo "$BADGING" | grep -q "name='br.com.mapeiaia.rotacerta'"
echo "$BADGING" | grep -q "versionCode='5380'"
echo "$BADGING" | grep -q "versionName='0.1.177'"
for dex in $(zipinfo -1 "$APK" | grep -E '^classes([0-9]+)?\.dex$'); do unzip -p "$APK" "$dex"; done | strings > dex-strings.txt
grep -q 'SHORTCUT_ACTIVITY_LAUNCH_0176' dex-strings.txt
grep -q 'SHORTCUT_MODULE_IDENTITY_FOCUS_0177' dex-strings.txt
grep -q 'ShortcutModuleFocusPolicy0177' dex-strings.txt
cp "$APK" "$OUT"
sha256sum "$OUT" | tee rota-certa-0.1.177-sha256.txt
stat -c '%n %s bytes' "$OUT" | tee rota-certa-0.1.177-tamanho.txt
printf 'Versao: 0.1.177 (5380)\nPacote: br.com.mapeiaia.rotacerta\nEscopo: atalhos inline enviam ID do modulo e a Home faz foco orientado ao evento\nFarol, parser, OCR, rota, Manifest e toque longo: preservados por hash\n' > rota-certa-0.1.177-validacao.txt
