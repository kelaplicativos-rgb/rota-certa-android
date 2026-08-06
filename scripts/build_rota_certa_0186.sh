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
fi'''
if text.count(old_whitespace) != 1:
    raise SystemExit("Expected whitespace-fix block not found exactly once")
text = text.replace(old_whitespace, new_whitespace, 1)

old_base_call = 'bash "$BASE_BUILD" "$PATCHES"'
new_base_call = 'ROTA_CERTA_MATERIALIZE_ONLY=1 bash "$BASE_BUILD" "$PATCHES"'
if text.count(old_base_call) != 1:
    raise SystemExit("Expected cumulative base-build call not found exactly once")
text = text.replace(old_base_call, new_base_call, 1)
path.write_text(text, encoding="utf-8")
PY

bash -n "$ORIGINAL_SCRIPT"
for materialized_script in "$MATERIALIZE_REPOSITORY"/scripts/build_rota_certa_*.sh; do
  bash -n "$materialized_script"
done

echo "pipeline_mode=single_final_gradle_validation"
bash "$ORIGINAL_SCRIPT" "$MATERIALIZE_REPOSITORY"
