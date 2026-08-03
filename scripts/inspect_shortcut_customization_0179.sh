#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0178.sh"
PATCH_PARTS=(
  "$PATCHES/patches/customizable-shortcut-grid-0179.patch.part00"
  "$PATCHES/patches/customizable-shortcut-grid-0179.patch.part01"
  "$PATCHES/patches/customizable-shortcut-grid-0179.patch.part02"
  "$PATCHES/patches/customizable-shortcut-grid-0179.patch.part03"
)
PATCH_SHA256="4167d17dc9cde54d2ae3962c3480bdc8211d43f3b7e969ef9b252cb829a3aa8c"
CONTRACT_PATCH="$PATCHES/patches/customizable-shortcut-grid-0179-contracts.patch"
CONTRACT_PATCH_SHA256="181bb7a9036db57a95d56234cf0859bbb2f8c9cf45a79d0fe5570fa800f4d721"

patch_file="$(mktemp --suffix=.patch)"
trap 'rm -f "$patch_file"' EXIT

bash "$BASE_BUILD" "$PATCHES"

cat "${PATCH_PARTS[@]}" > "$patch_file"
echo "$PATCH_SHA256  $patch_file" | sha256sum --check
git apply --check "$patch_file"
git apply "$patch_file"

echo "$CONTRACT_PATCH_SHA256  $CONTRACT_PATCH" | sha256sum --check
git apply --check "$CONTRACT_PATCH"
git apply "$CONTRACT_PATCH"

OUT="inspection-shortcut-customization-0179"
rm -rf "$OUT"
mkdir -p "$OUT/app/src/main/java/br/com/mapeiaia/rotacerta" "$OUT/app/src/test/java/br/com/mapeiaia/rotacerta"

MAIN_FILES=(
  BubbleShortcutCatalog.kt
  BubbleShortcutModule.kt
  BubbleShortcutOverlayController.kt
  ShortcutGridCustomization0179.kt
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

find app/src/test/java/br/com/mapeiaia/rotacerta -maxdepth 1 -type f \
  \( -iname '*Shortcut*' -o -iname '*Bubble*' -o -iname '*Catalog*' \) \
  -exec cp {} "$OUT/app/src/test/java/br/com/mapeiaia/rotacerta/" \;

# Inclui contratos legados que validam diretamente o despacho da grade, mesmo
# quando o nome do arquivo não contém Shortcut/Bubble/Catalog.
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
