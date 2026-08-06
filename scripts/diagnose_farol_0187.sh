#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
WRAPPER="$PATCH_REPOSITORY/scripts/.build-rota-certa-0186-diagnostic-$$.sh"
cleanup() {
  rm -f "$WRAPPER"
}
trap cleanup EXIT

cp "$PATCH_REPOSITORY/scripts/build_rota_certa_0186.sh" "$WRAPPER"
# Primeiro materializa a correção 0.1.187 exatamente como o workflow de build.
# Depois injeta a parada diagnóstica antes do Gradle, preservando a árvore final
# para inspeção e para o artifact de fonte.
python3 "$PATCH_REPOSITORY/scripts/inject_build_rota_certa_0187.py" "$WRAPPER"
python3 "$PATCH_REPOSITORY/scripts/inject_farol_diagnostic_0187.py" "$WRAPPER"
bash -n "$WRAPPER"
PS4='+${BASH_SOURCE}:${LINENO}: ' bash -x "$WRAPPER" "$PATCH_REPOSITORY"
