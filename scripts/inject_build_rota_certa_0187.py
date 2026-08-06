#!/usr/bin/env python3
from pathlib import Path
import sys

wrapper = Path(sys.argv[1])
text = wrapper.read_text(encoding="utf-8")
marker = 'bash -n "$ORIGINAL_SCRIPT"'

injection = r"""python3 - "$ORIGINAL_SCRIPT" "$PATCH_REPOSITORY" <<'PY0187'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
patch_repository = Path(sys.argv[2])
text = path.read_text(encoding="utf-8")

apply_block = r'''PATCH_0187_B64="$PATCH_REPOSITORY/patches/farol-runtime-0187.patch.gz.b64"
PATCH_0187="$(mktemp --suffix=.farol-runtime-0187.patch)"
base64 --decode "$PATCH_0187_B64" | gzip --decompress > "$PATCH_0187"
test "$(sha256sum "$PATCH_0187" | awk '{print $1}')" = "9d04b2f3b26808676b7dfedbf82bbe0c68e79aff8e3e2fb968decf56fcb44d9d"
git apply --check "$PATCH_0187"
git apply "$PATCH_0187"
rm -f "$PATCH_0187"

grep -Fq 'versionCode = 5471' app/build.gradle.kts
grep -Fq 'versionName = "0.1.187"' app/build.gradle.kts
grep -Fq 'SAME_CARD_RECOVERY_BINDING_0187' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt
grep -Fq 'MONOTONIC_FAROL_TIME_0187' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt
grep -Fq 'BUBBLE_FAILED_CARD_RECOVERY_DISCARDED_0187' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'FarolExternalPackageEventGate0187' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
if grep -Fq 'System.currentTimeMillis() - universalLastActiveReadAtMillis' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt; then
  echo 'Relógio civil ainda presente na idade crítica do farol' >&2
  exit 1
fi
echo 'farol_runtime_patch_0187=applied_and_verified'
'''
pattern = re.compile(
    r'(?m)^(?P<indent>[ \t]*)(?P<command>(?:if ! )?\./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace(?:; then)?)$'
)
matches = list(pattern.finditer(text))
if len(matches) != 1:
    raise SystemExit(f"Final Gradle validation command count was {len(matches)}, expected exactly one")
match = matches[0]
replacement = apply_block + match.group('indent') + match.group('command')
text = text[:match.start()] + replacement + text[match.end():]
text = text.replace('0.1.186', '0.1.187')
text = text.replace('5470', '5471')
text = text.replace(
    'rota-certa-0.1.187-grade-audio-links-corretor-validado.apk',
    'rota-certa-0.1.187-farol-runtime-validado.apk',
)
path.write_text(text, encoding="utf-8")
PY0187

bash -n "$ORIGINAL_SCRIPT"
"""

if text.count(marker) != 1:
    raise SystemExit("0.1.186 wrapper validation marker not found exactly once")
wrapper.write_text(text.replace(marker, injection, 1), encoding="utf-8")
