#!/usr/bin/env python3
from pathlib import Path
import sys

wrapper = Path(sys.argv[1])
text = wrapper.read_text(encoding="utf-8")
needle = 'bash -n "$ORIGINAL_SCRIPT"'

injected = r"""python3 - "$ORIGINAL_SCRIPT" <<'PYFAROL'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = 'if ! ./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace; then'
diagnostic = r'''echo "--- FAROL SOURCE SNAPSHOT 0.1.187 ---"
SERVICE="app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
test -f "$SERVICE"

echo "--- onAccessibilityEvent / containment / package transition ---"
grep -n -C 45 -E 'onAccessibilityEvent|accessibility_event_0172|UNEXPECTED_FAILURE_CONTAINED_0172|ExplicitPackageTransitionPolicy0185|rootInActiveWindow|safeRoot|safeNode|eventPackage' "$SERVICE" | head -n 1400 || true

echo "--- card confirmation / capture recovery / route generation ---"
grep -n -C 55 -E 'BUBBLE_FAILED_CARD_CAPTURE|failedCard|recovered|recovery|acessibilidade_mais_ocr|BUBBLE_ROUTE_REQUESTED|BUBBLE_ROUTE_CALL_START|generation|routeJob|lastAnalyzedHash|lastSnapshotHash' "$SERVICE" | head -n 1800 || true

echo "--- clear/render/event throttling ---"
grep -n -C 35 -E 'EXPLICIT_EXTERNAL_PACKAGE_REJECTED_0185|BUBBLE_CLEAR_REQUEST|clearBubble|renderBubble|OVERLAY_RENDER|duplicate|thrott|deboun|lastExternal' "$SERVICE" | head -n 1200 || true

for POLICY in \
  app/src/main/java/br/com/mapeiaia/rotacerta/RideCardConfirmationPolicy0185.kt \
  app/src/main/java/br/com/mapeiaia/rotacerta/ExplicitPackageTransitionPolicy0185.kt; do
  if [[ -f "$POLICY" ]]; then
    echo "--- $POLICY ---"
    nl -ba "$POLICY"
  fi
done

exit 91
'''
if text.count(needle) != 1:
    raise SystemExit("Final Gradle test entry not found exactly once")
path.write_text(text.replace(needle, diagnostic + needle, 1), encoding="utf-8")
PYFAROL

bash -n "${ORIGINAL_SCRIPT}"
# END_FAROL_DIAGNOSTIC_INJECTION
"""

if text.count(needle) != 1:
    raise SystemExit("Wrapper bash -n marker not found exactly once")
wrapper.write_text(text.replace(needle, injected, 1), encoding="utf-8")
