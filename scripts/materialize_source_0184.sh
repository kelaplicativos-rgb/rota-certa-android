#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"

# Materializa exatamente a fonte aprovada da 0.1.183. O script-base continua
# executando testes, lint, assemble e validações antes de expor qualquer fonte.
bash "$PATCHES/scripts/build_rota_certa_0183.sh" "$PATCHES"

OUT="materialized-source-0.1.183"
rm -rf "$OUT"
mkdir -p "$OUT/app/src/main/java/br/com/mapeiaia/rotacerta"
mkdir -p "$OUT/app/src/test/java/br/com/mapeiaia/rotacerta"
mkdir -p "$OUT/app/src/main"

cp app/build.gradle.kts "$OUT/app/build.gradle.kts"
cp app/src/main/AndroidManifest.xml "$OUT/app/src/main/AndroidManifest.xml"

MAIN="app/src/main/java/br/com/mapeiaia/rotacerta"
TEST="app/src/test/java/br/com/mapeiaia/rotacerta"
FILES=(
  MainActivity.kt
  LiveRideAccessibilityService.kt
  BubbleShortcutModule.kt
  BubbleShortcutOverlayController.kt
  ShortcutGridCustomization0179.kt
  ShortcutDirectTapPolicy0182.kt
  ShortcutContextMenuPolicy0183.kt
  DirectionalAlertPolicy.kt
  DirectionalProximityAlertEngine.kt
  DirectionalAlertOverlayController.kt
  RadarImport.kt
  Repositories.kt
  Models.kt
  WorkTrackingService.kt
  WorkTrackingActivity.kt
  QuickLinksActivity.kt
  FinancialActivity.kt
)
for file in "${FILES[@]}"; do
  if [[ -f "$MAIN/$file" ]]; then
    cp "$MAIN/$file" "$OUT/app/src/main/java/br/com/mapeiaia/rotacerta/$file"
  fi
done

find "$TEST" -maxdepth 1 -type f \
  \( -iname '*Shortcut*' -o -iname '*Directional*' -o -iname '*Radar*' -o -iname '*Backup*' \) \
  -exec cp {} "$OUT/app/src/test/java/br/com/mapeiaia/rotacerta/" \;

find "$OUT" -type f -print0 | sort -z | xargs -0 sha256sum > "$OUT/source-sha256.txt"
{
  echo 'base_version=0.1.183'
  echo 'base_version_code=5440'
  echo 'base_commit=eea3f1a1e11704c78b65a0a643851cd6518875cc'
  echo 'purpose=exact_source_before_0.1.184'
} > "$OUT/manifest.txt"
