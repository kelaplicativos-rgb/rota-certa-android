#!/usr/bin/env python3
from pathlib import Path
import sys

wrapper = Path(sys.argv[1])
text = wrapper.read_text(encoding="utf-8")
marker = "grep -Fq 'versionCode = 5471' app/build.gradle.kts"

apply_block = r'''PATCH_REPOSITORY_0187_PHASE2_CONTRACT="${1:?Informe o repositório cumulativo}"
PATCH_0187_PHASE2_CONTRACT_B64="$PATCH_REPOSITORY_0187_PHASE2_CONTRACT/patches/farol-runtime-0187-phase2-legacy-contract.patch.gz.b64"
PATCH_0187_PHASE2_CONTRACT="$(mktemp --suffix=.farol-runtime-0187-phase2-legacy-contract.patch)"
base64 --decode "$PATCH_0187_PHASE2_CONTRACT_B64" | gzip --decompress > "$PATCH_0187_PHASE2_CONTRACT"
test "$(sha256sum "$PATCH_0187_PHASE2_CONTRACT" | awk '{print $1}')" = "1022a32820a22e8592634d34ccb3c9153ee6c5bb381bbef1582ff41caf055cb2"
git apply --check "$PATCH_0187_PHASE2_CONTRACT"
git apply "$PATCH_0187_PHASE2_CONTRACT"
rm -f "$PATCH_0187_PHASE2_CONTRACT"
grep -Fq 'eventMethod.indexOf("collectImmediateVisibleTextChecklist13(")' app/src/test/java/br/com/mapeiaia/rotacerta/FarolRealtimeCriticalPathContract0167Test.kt
'''

if text.count(marker) != 1:
    raise SystemExit("0.1.187 validation marker not found exactly once")
wrapper.write_text(text.replace(marker, apply_block + marker, 1), encoding="utf-8")
