#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
SOURCE_BRANCH="agent/per-shortcut-gesture-menu-0.1.180"
FILES=(
  scripts/build_rota_certa_0180.sh
  scripts/fix_per_shortcut_gestures_0180.py.part00
  scripts/fix_per_shortcut_gestures_0180.py.part01
  scripts/fix_per_shortcut_gestures_0180.py.part02
  scripts/fix_per_shortcut_gestures_0180.py.part03
)

# Executor temporário e explícito para validar a PR #50 usando um run antigo
# sem artifact. A branch 0.1.179 é restaurada após a conclusão.
git -C "$PATCHES" fetch --depth=1 origin "$SOURCE_BRANCH"
for file in "${FILES[@]}"; do
  mkdir -p "$PATCHES/$(dirname "$file")"
  git -C "$PATCHES" show "FETCH_HEAD:$file" > "$PATCHES/$file"
done

# A 0.1.180 materializa a 0.1.179 pelo script preservado, evitando recursão.
sed -i 's#scripts/build_rota_certa_0179.sh#scripts/build_rota_certa_0179_base_for_0180.sh#' \
  "$PATCHES/scripts/build_rota_certa_0180.sh"

bash "$PATCHES/scripts/build_rota_certa_0180.sh" "$PATCHES"

# O workflow antigo envia este diretório; o conteúdo continua identificado
# internamente como 0.1.180, versionCode 5410 e APK próprio.
rm -rf artifact-0.1.179
mv artifact-0.1.180 artifact-0.1.179
