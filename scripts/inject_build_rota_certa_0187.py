#!/usr/bin/env python3
from pathlib import Path
import sys

wrapper = Path(sys.argv[1])
text = wrapper.read_text(encoding="utf-8")
marker = 'bash -n "$ORIGINAL_SCRIPT"'

injection = r"""python3 - "$ORIGINAL_SCRIPT" <<'PY0187'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

apply_block = r'''PATCH_REPOSITORY_0187="${1:?Informe o repositório cumulativo}"
PATCH_0187_B64="$PATCH_REPOSITORY_0187/patches/farol-runtime-0187.patch.gz.b64"
PATCH_0187="$(mktemp --suffix=.farol-runtime-0187.patch)"
base64 --decode "$PATCH_0187_B64" | gzip --decompress > "$PATCH_0187"
test "$(sha256sum "$PATCH_0187" | awk '{print $1}')" = "9d04b2f3b26808676b7dfedbf82bbe0c68e79aff8e3e2fb968decf56fcb44d9d"
git apply --check "$PATCH_0187"
git apply "$PATCH_0187"
rm -f "$PATCH_0187"

PATCH_0187_TEST_B64="$PATCH_REPOSITORY_0187/patches/farol-runtime-0187-test-compat.patch.gz.b64"
PATCH_0187_TEST="$(mktemp --suffix=.farol-runtime-0187-test-compat.patch)"
base64 --decode "$PATCH_0187_TEST_B64" | gzip --decompress > "$PATCH_0187_TEST"
test "$(sha256sum "$PATCH_0187_TEST" | awk '{print $1}')" = "10873357628acf1e1e464e4d650fe3c7e67ad316773406ce4e1d13f594972769"
git apply --check "$PATCH_0187_TEST"
git apply "$PATCH_0187_TEST"
rm -f "$PATCH_0187_TEST"

PATCH_0187_PHASE2_B64="$PATCH_REPOSITORY_0187/patches/farol-runtime-0187-phase2.patch.gz.b64"
PATCH_0187_PHASE2="$(mktemp --suffix=.farol-runtime-0187-phase2.patch)"
base64 --decode "$PATCH_0187_PHASE2_B64" | gzip --decompress > "$PATCH_0187_PHASE2"
test "$(sha256sum "$PATCH_0187_PHASE2" | awk '{print $1}')" = "96e1d1fcdd6f238bc16ff2c325952c4fcbb53ed66527618bc9f6702b528d5ed5"
git apply --check "$PATCH_0187_PHASE2"
git apply "$PATCH_0187_PHASE2"
rm -f "$PATCH_0187_PHASE2"

PATCH_0187_PHASE2_TESTFIX_B64="$PATCH_REPOSITORY_0187/patches/farol-runtime-0187-phase2-testfix.patch.gz.b64"
PATCH_0187_PHASE2_TESTFIX="$(mktemp --suffix=.farol-runtime-0187-phase2-testfix.patch)"
base64 --decode "$PATCH_0187_PHASE2_TESTFIX_B64" | gzip --decompress > "$PATCH_0187_PHASE2_TESTFIX"
test "$(sha256sum "$PATCH_0187_PHASE2_TESTFIX" | awk '{print $1}')" = "58a9e41f9d917767dea1127125511212b5f459fbfdf9d2272eef0fe3be01d4af"
git apply --check "$PATCH_0187_PHASE2_TESTFIX"
git apply "$PATCH_0187_PHASE2_TESTFIX"
rm -f "$PATCH_0187_PHASE2_TESTFIX"

grep -Fq 'versionCode = 5471' app/build.gradle.kts
grep -Fq 'versionName = "0.1.187"' app/build.gradle.kts
grep -Fq 'SAME_CARD_RECOVERY_BINDING_0187' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt
grep -Fq 'MONOTONIC_FAROL_TIME_0187' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt
grep -Fq 'ATOMIC_ROOT_SNAPSHOT_GATE_0187' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt
grep -Fq 'ACCESSIBILITY_READ_BINDING_0187' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt
grep -Fq 'BUBBLE_FAILED_CARD_RECOVERY_DISCARDED_0187' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'BUBBLE_ROOT_SNAPSHOT_REJECTED_0187' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'BUBBLE_ACCESSIBILITY_READ_DISCARDED_0187' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'captureRootHandle0187()' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'FarolExternalPackageEventGate0187' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'universalLastActiveReadAtElapsedMillis0187 =' app/src/test/java/br/com/mapeiaia/rotacerta/SelectedAppWaitingYellow127Test.kt
if grep -Fq 'val rootPackage = currentRootPackageName()' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt; then
  echo 'Leitura crítica ainda consulta pacote fora do snapshot da raiz' >&2
  exit 1
fi
if grep -Fq 'val currentWindow0187 = safeRootWindowId0185()' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt; then
  echo 'Recuperação ainda consulta janela separada da raiz usada para texto' >&2
  exit 1
fi
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
prefix = text[:match.start()]
suffix = text[match.start():]

# Preserve every byte of the validated 0.1.186 materialization and its embedded
# patches. Only the final validation/output tail runs after 0.1.187 is applied.
replacements = {
    'OUTPUT_DIR="artifact-0.1.186"': 'OUTPUT_DIR="artifact-0.1.187"',
    'APK_NAME="rota-certa-0.1.186-grade-audio-links-corretor-validado.apk"': 'APK_NAME="rota-certa-0.1.187-farol-runtime-validado.apk"',
    "versionCode='5470' versionName='0.1.186'": "versionCode='5471' versionName='0.1.187'",
    'versionName=0.1.186': 'versionName=0.1.187',
    'versionCode=5470': 'versionCode=5471',
}
for old, new in replacements.items():
    count = suffix.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one final-tail marker {old!r}, found {count}")
    suffix = suffix.replace(old, new, 1)

contracts_marker = 'COMPILED_CONTRACTS=(\n'
if suffix.count(contracts_marker) != 1:
    raise SystemExit("Compiled-contract marker not found exactly once")
suffix = suffix.replace(
    contracts_marker,
    contracts_marker
    + '  FarolRuntimeSafety0187\n'
    + '  SAME_CARD_RECOVERY_BINDING_0187\n'
    + '  MONOTONIC_FAROL_TIME_0187\n'
    + '  ATOMIC_ROOT_SNAPSHOT_GATE_0187\n'
    + '  ACCESSIBILITY_READ_BINDING_0187\n',
    1,
)

replacement = apply_block + match.group('indent') + match.group('command')
suffix = replacement + suffix[match.end() - match.start():]
path.write_text(prefix + suffix, encoding="utf-8")
PY0187

bash -n "$ORIGINAL_SCRIPT"
"""

if text.count(marker) != 1:
    raise SystemExit("0.1.186 wrapper validation marker not found exactly once")
wrapper.write_text(text.replace(marker, injection, 1), encoding="utf-8")
