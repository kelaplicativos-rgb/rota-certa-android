#!/usr/bin/env bash
set -euo pipefail

readonly APP_APK='app/build/outputs/apk/debug/app-debug.apk'
readonly TEST_APK='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
readonly RUNNER='br.com.mapeiaia.rotacerta.test/androidx.test.runner.AndroidJUnitRunner'
readonly TEST_CLASS='br.com.mapeiaia.rotacerta.trips.AgendaPullRefreshGesture0388InstrumentedTest'

adb wait-for-device
package_service_ready=0
for _attempt in $(seq 1 120); do
  if adb shell service check package 2>/dev/null | grep -Fq 'Service package: found'; then
    package_service_ready=1
    break
  fi
  sleep 2
done
test "$package_service_ready" -eq 1

test -s "$APP_APK"
test -s "$TEST_APK"
adb install -r "$APP_APK"
adb install -r "$TEST_APK"

adb shell pm list instrumentation | tee instrumentation-list.txt
grep -Fq "$RUNNER" instrumentation-list.txt
adb logcat -c

set +e
adb shell am instrument -w -r -e class "$TEST_CLASS" "$RUNNER" \
  > instrumentation-output.txt 2>&1
instrumentation_status=$?
set -e

cat instrumentation-output.txt
adb logcat -d -v threadtime '*:W' > instrumentation-logcat.txt 2>&1 || true
cat instrumentation-logcat.txt || true

test "$instrumentation_status" -eq 0
grep -Fq 'OK (9 tests)' instrumentation-output.txt
