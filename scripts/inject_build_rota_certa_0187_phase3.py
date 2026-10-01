#!/usr/bin/env python3
from pathlib import Path
import sys

wrapper = Path(sys.argv[1])
text = wrapper.read_text(encoding="utf-8")
marker = "grep -Fq 'versionCode = 5471' app/build.gradle.kts"

apply_block = r'''PATCH_REPOSITORY_0187_PHASE3="${1:?Informe o repositório cumulativo}"
PATCH_0187_PHASE3="$PATCH_REPOSITORY_0187_PHASE3/patches/farol-runtime-0187-phase3.patch"
test "$(sha256sum "$PATCH_0187_PHASE3" | awk '{print $1}')" = "93c134b33e94e3352379b27404f3f2ac5432fa95cf3bd3c1d0b41b368223e5bb"
git apply --check "$PATCH_0187_PHASE3"
git apply "$PATCH_0187_PHASE3"

grep -Fq 'REJECTED_SNAPSHOT_HAS_NO_VISUAL_SIDE_EFFECT_0187_PHASE3' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt
grep -Fq 'BUBBLE_ROOT_SNAPSHOT_DISCARDED_0187_PHASE3' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'BUBBLE_ROOT_SNAPSHOT_READ_INVALIDATED_0187_PHASE3' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
python3 - <<'PY_PHASE3_CONTRACT'
from pathlib import Path
service = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt').read_text(encoding='utf-8')
start = service.index('if (!rootAdmission0187.accepted || rootHandle0187 == null)')
end = service.index('lastRejectedForegroundPackage0162 = null', start)
block = service[start:end]
if 'hardClearUniversalTwoAddress' in block or 'showOverlay' in block:
    raise SystemExit('Snapshot rejeitado ainda altera o visual da bolinha')
if 'FarolRejectedSnapshotPolicy0187Phase3.effect' not in block:
    raise SystemExit('Política tipada da fase 3 ausente do caminho crítico')
PY_PHASE3_CONTRACT
'''

if text.count(marker) != 1:
    raise SystemExit("0.1.187 validation marker not found exactly once for phase 3")
wrapper.write_text(text.replace(marker, apply_block + marker, 1), encoding="utf-8")
