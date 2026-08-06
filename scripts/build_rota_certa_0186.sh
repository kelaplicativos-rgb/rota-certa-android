#!/usr/bin/env bash
set -euo pipefail

# Preserve the reviewed 0.1.186 validator and optimize only cumulative
# materialization: older versions apply their patches and structural checks,
# but Gradle tests/lint/build run once on the final 0.1.186 source tree.
PATCH_REPOSITORY="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
ORIGINAL_SCRIPT="$(mktemp --suffix=.build-rota-certa-0186.sh)"
MATERIALIZE_REPOSITORY="$(mktemp -d --suffix=.rota-certa-materialize-0186)"
cleanup_wrapper() {
  rm -f "$ORIGINAL_SCRIPT"
  rm -rf "$MATERIALIZE_REPOSITORY"
}
trap cleanup_wrapper EXIT

git -C "$PATCH_REPOSITORY" cat-file blob d84be03702ff5c75e753b448cde4ccd4b66a3222 > "$ORIGINAL_SCRIPT"
chmod +x "$ORIGINAL_SCRIPT"

mkdir -p "$MATERIALIZE_REPOSITORY/scripts"
cp -a "$PATCH_REPOSITORY/scripts/." "$MATERIALIZE_REPOSITORY/scripts/"
ln -s "$PATCH_REPOSITORY/patches" "$MATERIALIZE_REPOSITORY/patches"

python3 - "$MATERIALIZE_REPOSITORY/scripts" <<'PY'
from pathlib import Path
import re
import sys

scripts_dir = Path(sys.argv[1])
guard_marker = "ROTA_CERTA_MATERIALIZE_ONLY"
patched = []

for path in sorted(scripts_dir.glob("build_rota_certa_*.sh")):
    if path.name == "build_rota_certa_0186.sh":
        continue
    text = path.read_text(encoding="utf-8")
    if guard_marker in text:
        patched.append(path.name)
        continue
    match = re.search(r"(?m)^[ \t]*\./gradlew\b", text)
    if match is None:
        continue
    guard = '''if [[ "${ROTA_CERTA_MATERIALIZE_ONLY:-0}" == "1" ]]; then
  echo "Materialização cumulativa concluída em $(basename \"$0\"); validações Gradle reservadas ao estado final 0.1.186"
  exit 0
fi

'''
    path.write_text(text[:match.start()] + guard + text[match.start():], encoding="utf-8")
    patched.append(path.name)

if "build_rota_certa_0185.sh" not in patched:
    raise SystemExit("build_rota_certa_0185.sh não recebeu o modo de materialização")
if not patched:
    raise SystemExit("Nenhum script cumulativo recebeu o modo de materialização")
print("scripts_materialization_only=" + ",".join(patched))
PY

python3 - "$ORIGINAL_SCRIPT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old_whitespace = '''git apply --check "$WHITESPACE_FIX_PATCH"
git apply "$WHITESPACE_FIX_PATCH"'''
new_whitespace = '''if git apply --check "$WHITESPACE_FIX_PATCH"; then
  git apply "$WHITESPACE_FIX_PATCH"
elif git apply --reverse --check "$WHITESPACE_FIX_PATCH"; then
  echo "Auxiliary whitespace fix already present after hardening patch"
else
  echo "Auxiliary whitespace fix is neither applicable nor already present" >&2
  exit 1
fi

COMPOSE_WEIGHT_IMPORT_FIX_FILES=(
  app/src/main/java/br/com/mapeiaia/rotacerta/QuickLinksActivity.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/TextCorrectionModule0186.kt
)
for compose_file in "${COMPOSE_WEIGHT_IMPORT_FIX_FILES[@]}"; do
  import_count="$(grep -Fxc 'import androidx.compose.foundation.layout.weight' "$compose_file" || true)"
  if [[ "$import_count" != "1" ]]; then
    echo "Import Compose weight inesperado em $compose_file: $import_count" >&2
    exit 1
  fi
  sed -i '/^import androidx.compose.foundation.layout.weight$/d' "$compose_file"
done
echo "compose_weight_import_compatibility=applied"

AUTHORIZED_APPS_TEST="app/src/test/java/br/com/mapeiaia/rotacerta/AuthorizedAppsCards146ContractTest.kt"
old_marker_count="$(grep -Foc 'SHORTCUT_DIRECT_TAP_0182' "$AUTHORIZED_APPS_TEST" || true)"
if [[ "$old_marker_count" != "1" ]]; then
  echo "Marcador legado inesperado em $AUTHORIZED_APPS_TEST: $old_marker_count" >&2
  exit 1
fi
sed -i 's/SHORTCUT_DIRECT_TAP_0182/SHORTCUT_DIRECT_TAP_AND_HOLD_0186/' "$AUTHORIZED_APPS_TEST"

LEGACY_GESTURE_TEST="app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenu0180Test.kt"
legacy_hold_line_count="$(grep -Fxc '            holdAction0180 = ShortcutGestureAction0180.NONE,' "$LEGACY_GESTURE_TEST" || true)"
if [[ "$legacy_hold_line_count" != "1" ]]; then
  echo "Cenário legado inesperado em $LEGACY_GESTURE_TEST: $legacy_hold_line_count" >&2
  exit 1
fi
sed -i '/^            holdAction0180 = ShortcutGestureAction0180.NONE,$/a\            holdActionType0186 = null,' "$LEGACY_GESTURE_TEST"
echo "shortcut_contract_compatibility=applied"'''
if text.count(old_whitespace) != 1:
    raise SystemExit("Expected whitespace-fix block not found exactly once")
text = text.replace(old_whitespace, new_whitespace, 1)

old_base_call = 'bash "$BASE_BUILD" "$PATCHES"'
new_base_call = 'ROTA_CERTA_MATERIALIZE_ONLY=1 bash "$BASE_BUILD" "$PATCHES"'
if text.count(old_base_call) != 1:
    raise SystemExit("Expected cumulative base-build call not found exactly once")
text = text.replace(old_base_call, new_base_call, 1)

old_test_call = './gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace'
new_test_call = '''if ! ./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace; then
  echo "--- TEST CONTRACT DIAGNOSTIC 0.1.186 ---" >&2
  TEST_DIAGNOSTIC_FILES=(
    app/src/test/java/br/com/mapeiaia/rotacerta/AuthorizedAppsCards146ContractTest.kt
    app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenu0180Test.kt
    app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt
    app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
    app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
    app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutInteractionPolicy0186.kt
  )
  for diagnostic_file in "${TEST_DIAGNOSTIC_FILES[@]}"; do
    if [[ -f "$diagnostic_file" ]]; then
      echo "--- $diagnostic_file ---" >&2
      nl -ba "$diagnostic_file" >&2
    fi
  done
  exit 1
fi'''
if text.count(old_test_call) != 1:
    raise SystemExit("Expected final test call not found exactly once")
text = text.replace(old_test_call, new_test_call, 1)

path.write_text(text, encoding="utf-8")
PY

bash -n "$ORIGINAL_SCRIPT"
for materialized_script in "$MATERIALIZE_REPOSITORY"/scripts/build_rota_certa_*.sh; do
  bash -n "$materialized_script"
done

echo "pipeline_mode=single_final_gradle_validation"
bash "$ORIGINAL_SCRIPT" "$MATERIALIZE_REPOSITORY"
