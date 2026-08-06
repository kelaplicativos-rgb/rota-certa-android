#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
WRAPPER="$PATCH_REPOSITORY/scripts/.build-rota-certa-0187-$$.sh"
cleanup() {
  rm -f "$WRAPPER"
}
trap cleanup EXIT

cp "$PATCH_REPOSITORY/scripts/build_rota_certa_0186.sh" "$WRAPPER"
python3 "$PATCH_REPOSITORY/scripts/inject_build_rota_certa_0187.py" "$WRAPPER"
python3 "$PATCH_REPOSITORY/scripts/inject_build_rota_certa_0187_phase2_contract.py" "$WRAPPER"
python3 "$PATCH_REPOSITORY/scripts/inject_build_rota_certa_0187_phase3.py" "$WRAPPER"
bash -n "$WRAPPER"
bash "$WRAPPER" "$PATCH_REPOSITORY"
