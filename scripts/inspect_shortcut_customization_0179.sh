#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
bash "$PATCHES/scripts/build_rota_certa_0178.sh" "$PATCHES"

OUT="inspection-shortcut-customization-0179"
rm -rf "$OUT"
mkdir -p "$OUT/app/src/main/java/br/com/mapeiaia/rotacerta" "$OUT/app/src/test/java/br/com/mapeiaia/rotacerta"

MAIN_FILES=(
  BubbleShortcutCatalog.kt
  BubbleShortcutModule.kt
  BubbleShortcutOverlayController.kt
  ShortcutLongPressPolicy0171.kt
  ShortcutActivityLaunchPolicy0176.kt
  LiveRideAccessibilityService.kt
  MainActivity.kt
  Models.kt
  Repositories.kt
  SettingsRepository.kt
)

for name in "${MAIN_FILES[@]}"; do
  path="app/src/main/java/br/com/mapeiaia/rotacerta/$name"
  if [[ -f "$path" ]]; then
    cp "$path" "$OUT/app/src/main/java/br/com/mapeiaia/rotacerta/$name"
  fi
done

find app/src/test/java/br/com/mapeiaia/rotacerta -maxdepth 1 -type f \( -iname '*Shortcut*' -o -iname '*Bubble*' -o -iname '*Catalog*' \) -exec cp {} "$OUT/app/src/test/java/br/com/mapeiaia/rotacerta/" \;

# Inclui contratos legados que validam diretamente o despacho da grade, mesmo
# quando o nome do arquivo não contém Shortcut/Bubble/Catalog. Isso permite
# comparar com segurança a personalização atual sem ampliar o bundle inteiro.
for name in AuthorizedAppsCards146ContractTest.kt; do
  path="app/src/test/java/br/com/mapeiaia/rotacerta/$name"
  if [[ -f "$path" ]]; then
    cp "$path" "$OUT/app/src/test/java/br/com/mapeiaia/rotacerta/$name"
  fi
done

cp app/build.gradle.kts "$OUT/app/build.gradle.kts"
cp app/src/main/AndroidManifest.xml "$OUT/app/src/main/AndroidManifest.xml"
find "$OUT" -type f -print0 | sort -z | xargs -0 sha256sum > "$OUT/source-sha256.txt"
tar -czf inspection-shortcut-customization-0179.tar.gz "$OUT"
