#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
SOURCE_REPOSITORY="$(git rev-parse --show-toplevel)"
TEMP_BUILD="$PATCH_REPOSITORY/scripts/.build-rota-certa-0188-canonical-$$.sh"
ORIGINAL_GRADLEW="$SOURCE_REPOSITORY/gradlew.real-0188-canonical"
STAGED_TEST_RESULTS="${RUNNER_TEMP:-/tmp}/rota-certa-0188-canonical-test-results"

cleanup() {
  rm -f "$TEMP_BUILD"
  if [[ -f "$ORIGINAL_GRADLEW" ]]; then
    rm -f "$SOURCE_REPOSITORY/gradlew"
    mv "$ORIGINAL_GRADLEW" "$SOURCE_REPOSITORY/gradlew"
    chmod +x "$SOURCE_REPOSITORY/gradlew"
  fi
  rm -rf "$STAGED_TEST_RESULTS"
}
trap cleanup EXIT

cp "$PATCH_REPOSITORY/scripts/build_rota_certa_0188.sh" "$TEMP_BUILD"
python3 - "$TEMP_BUILD" "$PATCH_REPOSITORY" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
patch_repo = Path(sys.argv[2])
text = path.read_text(encoding="utf-8")
marker = "farol_real_device_0188_hardening"
if marker not in text:
    match = re.search(r"(?m)^[ \t]*\./gradlew\b", text)
    if match is None:
        raise SystemExit("Não encontrei a primeira validação Gradle no build 0.1.188")
    command = (
        'python3 "' + str(patch_repo / "scripts/harden_farol_real_device_0188.py") + '" "$PWD"\n'
    )
    text = text[: match.start()] + command + text[match.start() :]
    path.write_text(text, encoding="utf-8")
PY
bash -n "$TEMP_BUILD"

if [[ ! -x "$SOURCE_REPOSITORY/gradlew" ]]; then
  echo "Gradle wrapper não encontrado ou sem permissão" >&2
  exit 1
fi
mv "$SOURCE_REPOSITORY/gradlew" "$ORIGINAL_GRADLEW"
cat > "$SOURCE_REPOSITORY/gradlew" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail
SOURCE_REPOSITORY="$(cd "$(dirname "$0")" && pwd)"
ORIGINAL_GRADLEW="$SOURCE_REPOSITORY/gradlew.real-0188-canonical"
STAGED_TEST_RESULTS="${RUNNER_TEMP:-/tmp}/rota-certa-0188-canonical-test-results"

"$ORIGINAL_GRADLEW" "$@"
status=$?
if [[ $status -ne 0 ]]; then
  exit $status
fi
arguments=" $* "
if [[ "$arguments" == *" testDebugUnitTest "* ]]; then
  rm -rf "$STAGED_TEST_RESULTS"
  mkdir -p "$STAGED_TEST_RESULTS"
  compgen -G "$SOURCE_REPOSITORY/app/build/test-results/testDebugUnitTest/*.xml" >/dev/null || {
    echo "Nenhum XML de teste produzido" >&2
    exit 1
  }
  cp "$SOURCE_REPOSITORY"/app/build/test-results/testDebugUnitTest/*.xml "$STAGED_TEST_RESULTS"/
fi
if [[ "$arguments" == *" clean "* ]] && compgen -G "$STAGED_TEST_RESULTS/*.xml" >/dev/null; then
  mkdir -p "$SOURCE_REPOSITORY/app/build/test-results/testDebugUnitTest"
  cp "$STAGED_TEST_RESULTS"/*.xml "$SOURCE_REPOSITORY/app/build/test-results/testDebugUnitTest"/
fi
WRAPPER
chmod +x "$SOURCE_REPOSITORY/gradlew"

bash "$TEMP_BUILD" "$PATCH_REPOSITORY"

OUTPUT_DIR="$SOURCE_REPOSITORY/artifact-0.1.188"
APK="$OUTPUT_DIR/rota-certa-0.1.188-farol-real-device-validado-em-ci.apk"
for required in "$APK" "$OUTPUT_DIR/test-count.txt" "$OUTPUT_DIR/sha256.txt" "$OUTPUT_DIR/signature.txt" "$OUTPUT_DIR/package.txt" "$OUTPUT_DIR/version.txt"; do
  test -s "$required" || { echo "Arquivo obrigatório ausente: $required" >&2; exit 1; }
done

grep -Fxq 'failures=0' "$OUTPUT_DIR/test-count.txt"
python3 - "$OUTPUT_DIR/test-count.txt" <<'PY'
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8")
match = re.search(r"(?m)^tests=(\d+)$", text)
if match is None or int(match.group(1)) <= 0:
    raise SystemExit("Contagem de testes inválida")
print("canonical_test_count=" + match.group(1))
PY

grep -Fxq 'br.com.mapeiaia.rotacerta' "$OUTPUT_DIR/package.txt"
grep -Fq '0.1.188' "$OUTPUT_DIR/version.txt"
grep -Fq '5472' "$OUTPUT_DIR/version.txt"
EXPECTED_SHA="$(awk '{print $1}' "$OUTPUT_DIR/sha256.txt")"
ACTUAL_SHA="$(sha256sum "$APK" | awk '{print $1}')"
test "$EXPECTED_SHA" = "$ACTUAL_SHA"

echo "rota_certa_0188_canonical_validation=passed"
