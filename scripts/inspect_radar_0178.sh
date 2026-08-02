#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"

bash "$PATCHES/scripts/build_rota_certa_0177.sh" "$PATCHES"

OUT="inspection-radar-0178"
rm -rf "$OUT"
mkdir -p "$OUT/files/app/src"

cp -R app/src/main "$OUT/files/app/src/main"
cp -R app/src/test "$OUT/files/app/src/test"
cp app/build.gradle.kts "$OUT/build.gradle.kts"
cp app/src/main/AndroidManifest.xml "$OUT/AndroidManifest.xml"
find "$OUT/files/app/src" -type f -name '*.kt' -print0 | sort -z | xargs -0 sha256sum > "$OUT/source-sha256.txt"

tar -czf inspection-radar-0178.tar.gz "$OUT"
