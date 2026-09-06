#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
PART_DIR="$PATCH_REPOSITORY/patches/0189"
COMBINED="$(mktemp --suffix=.0189.patch)"
ARCHIVE="$PATCH_REPOSITORY/patches/farol-priority-latency-0189.patch.gz.b64"
trap 'rm -f "$COMBINED"' EXIT

PARTS=(
  '01-build-gradle.patch'
  '02-route-gate.patch'
  '03-visual-priority.patch'
  '04-live-service.patch'
  '05-route-gate-tests.patch'
  '06-visual-priority-test.patch'
)
EXPECTED=(
  '638b6d3489b1938dedfa2d13fa30938bce9ed0399e7adf762e779fbc55184a60'
  'f7daaf05b30fc58640337fe312defef148aaef12deefa3f6a2aa5d83370c2966'
  '1698b4beee511a106e622041a9b186ae47e0f83200ce16abe6dbf832770640e7'
  'b5b02966cd11a8549417628a02384917b654b648b6c57f5e4a880dcf374e15a2'
  '5c345c0c982b99bc2ea04d382f2bbd0e07f0fcf727546d98fd2862126a5195a8'
  'd8685c14806654e3eb3febdb5325f44a7fe3c881b2a7c6be540270e472bef4f6'
)
EXPECTED_COMBINED='a64ae94d050499efcba6bc1b8231fe111fcb38b57030327cde911f0a46a06493'

: > "$COMBINED"
for i in "${!PARTS[@]}"; do
  file="$PART_DIR/${PARTS[$i]}"
  test -s "$file" || { echo "Parte 0.1.189 ausente: $file" >&2; exit 1; }
  actual="$(sha256sum "$file" | awk '{print $1}')"
  echo "patch_part=${PARTS[$i]} sha256=$actual"
  test "$actual" = "${EXPECTED[$i]}" || {
    echo "Parte 0.1.189 corrompida: ${PARTS[$i]}" >&2
    exit 1
  }
  cat "$file" >> "$COMBINED"
done

combined_sha="$(sha256sum "$COMBINED" | awk '{print $1}')"
echo "patch_0189_reconstructed_sha256=$combined_sha"
test "$combined_sha" = "$EXPECTED_COMBINED" || {
  echo "Patch reconstruído 0.1.189 divergente" >&2
  exit 1
}

# Regera o transporte apenas dentro do runner; ele deixa de ser fonte de verdade.
gzip -n -c "$COMBINED" | base64 -w 76 > "$ARCHIVE"
base64 --decode "$ARCHIVE" | gzip --decompress | sha256sum | grep -Fq "$EXPECTED_COMBINED"

echo 'patch_0189_transport_reconstructed=passed'
bash "$PATCH_REPOSITORY/scripts/build_rota_certa_0189.sh" "$PATCH_REPOSITORY"
