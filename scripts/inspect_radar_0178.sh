#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"

bash "$PATCHES/scripts/build_rota_certa_0177.sh" "$PATCHES"

OUT="inspection-radar-0178"
rm -rf "$OUT"
mkdir -p "$OUT/files"

ROOT="app/src/main/java/br/com/mapeiaia/rotacerta"
find "$ROOT" -type f -name '*.kt' -print0 | while IFS= read -r -d '' file; do
  if grep -Eiq 'radar|proximity|notification|popup|dialog|SavedPlace|ImportedRadar' "$file"; then
    mkdir -p "$OUT/files/$(dirname "$file")"
    cp "$file" "$OUT/files/$file"
    printf '%s\n' "$file" >> "$OUT/matched-files.txt"
  fi
done

cp app/build.gradle.kts "$OUT/build.gradle.kts"
cp app/src/main/AndroidManifest.xml "$OUT/AndroidManifest.xml"
sha256sum "$OUT"/files/app/src/main/java/br/com/mapeiaia/rotacerta/*.kt > "$OUT/source-sha256.txt" || true

tar -czf inspection-radar-0178.tar.gz "$OUT"
