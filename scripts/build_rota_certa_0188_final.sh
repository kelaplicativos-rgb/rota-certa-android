#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
SOURCE_REPOSITORY="$(git rev-parse --show-toplevel)"
ORIGINAL_GRADLEW="$SOURCE_REPOSITORY/gradlew.real-0188"
STAGED_TEST_RESULTS="${RUNNER_TEMP:-/tmp}/rota-certa-0188-test-results"

cleanup() {
  if [[ -f "$ORIGINAL_GRADLEW" ]]; then
    rm -f "$SOURCE_REPOSITORY/gradlew"
    mv "$ORIGINAL_GRADLEW" "$SOURCE_REPOSITORY/gradlew"
    chmod +x "$SOURCE_REPOSITORY/gradlew"
  fi
  rm -rf "$STAGED_TEST_RESULTS"
}
trap cleanup EXIT

if [[ ! -x "$SOURCE_REPOSITORY/gradlew" ]]; then
  echo "Gradle wrapper não encontrado ou sem permissão" >&2
  exit 1
fi
if [[ -e "$ORIGINAL_GRADLEW" ]]; then
  echo "Wrapper temporário 0.1.188 já existe" >&2
  exit 1
fi

mv "$SOURCE_REPOSITORY/gradlew" "$ORIGINAL_GRADLEW"
cat > "$SOURCE_REPOSITORY/gradlew" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail
SOURCE_REPOSITORY="$(cd "$(dirname "$0")" && pwd)"
ORIGINAL_GRADLEW="$SOURCE_REPOSITORY/gradlew.real-0188"
STAGED_TEST_RESULTS="${RUNNER_TEMP:-/tmp}/rota-certa-0188-test-results"

"$ORIGINAL_GRADLEW" "$@"
status=$?
if [[ $status -ne 0 ]]; then
  exit $status
fi

arguments=" $* "
if [[ "$arguments" == *" testDebugUnitTest "* ]]; then
  rm -rf "$STAGED_TEST_RESULTS"
  mkdir -p "$STAGED_TEST_RESULTS"
  if compgen -G "$SOURCE_REPOSITORY/app/build/test-results/testDebugUnitTest/*.xml" >/dev/null; then
    cp "$SOURCE_REPOSITORY"/app/build/test-results/testDebugUnitTest/*.xml "$STAGED_TEST_RESULTS"/
  else
    echo "Nenhum XML de teste foi produzido por testDebugUnitTest" >&2
    exit 1
  fi
fi

if [[ "$arguments" == *" clean "* ]] && compgen -G "$STAGED_TEST_RESULTS/*.xml" >/dev/null; then
  mkdir -p "$SOURCE_REPOSITORY/app/build/test-results/testDebugUnitTest"
  cp "$STAGED_TEST_RESULTS"/*.xml "$SOURCE_REPOSITORY/app/build/test-results/testDebugUnitTest"/
fi
WRAPPER
chmod +x "$SOURCE_REPOSITORY/gradlew"

bash "$PATCH_REPOSITORY/scripts/build_rota_certa_0188.sh" "$PATCH_REPOSITORY"

OUTPUT_DIR="$SOURCE_REPOSITORY/artifact-0.1.188"
test -d "$OUTPUT_DIR"
test -s "$OUTPUT_DIR/rota-certa-0.1.188-farol-real-device-validado-em-ci.apk"
test -s "$OUTPUT_DIR/test-count.txt"
test -s "$OUTPUT_DIR/sha256.txt"
test -s "$OUTPUT_DIR/signature.txt"
test -s "$OUTPUT_DIR/package.txt"
test -s "$OUTPUT_DIR/version.txt"

grep -Fxq 'failures=0' "$OUTPUT_DIR/test-count.txt"
python3 - "$OUTPUT_DIR/test-count.txt" <<'PY'
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8")
match = re.search(r"(?m)^tests=(\d+)$", text)
if match is None or int(match.group(1)) <= 0:
    raise SystemExit("Contagem de testes inválida na validação final 0.1.188")
print("final_test_count=" + match.group(1))
PY

grep -Fxq 'br.com.mapeiaia.rotacerta' "$OUTPUT_DIR/package.txt"
grep -Fq '0.1.188' "$OUTPUT_DIR/version.txt"
grep -Fq '5472' "$OUTPUT_DIR/version.txt"

echo "rota_certa_0188_final_validation=passed"
