#!/usr/bin/env bash
set -euo pipefail

# Preserve the previously reviewed 0.1.186 build script byte-for-byte and
# adjust only the auxiliary whitespace cleanup so retries are idempotent.
# The hardening patch can already contain the cleanup; in that case the
# reverse check proves the intended result is present and the build proceeds.
PATCH_REPOSITORY="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
ORIGINAL_SCRIPT="$(mktemp --suffix=.build-rota-certa-0186.sh)"
cleanup_wrapper() {
  rm -f "$ORIGINAL_SCRIPT"
}
trap cleanup_wrapper EXIT

git -C "$PATCH_REPOSITORY" cat-file blob d84be03702ff5c75e753b448cde4ccd4b66a3222 > "$ORIGINAL_SCRIPT"
chmod +x "$ORIGINAL_SCRIPT"

python3 - "$ORIGINAL_SCRIPT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = '''git apply --check "$WHITESPACE_FIX_PATCH"
git apply "$WHITESPACE_FIX_PATCH"'''
new = '''if git apply --check "$WHITESPACE_FIX_PATCH"; then
  git apply "$WHITESPACE_FIX_PATCH"
elif git apply --reverse --check "$WHITESPACE_FIX_PATCH"; then
  echo "Auxiliary whitespace fix already present after hardening patch"
else
  echo "Auxiliary whitespace fix is neither applicable nor already present" >&2
  exit 1
fi'''
if text.count(old) != 1:
    raise SystemExit("Expected whitespace-fix block not found exactly once")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
PY

bash "$ORIGINAL_SCRIPT" "$@"
