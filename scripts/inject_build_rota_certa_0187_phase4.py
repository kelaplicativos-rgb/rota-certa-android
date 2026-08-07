#!/usr/bin/env python3
from pathlib import Path
import sys

wrapper = Path(sys.argv[1])
text = wrapper.read_text(encoding="utf-8")
marker = "grep -Fq 'versionCode = 5471' app/build.gradle.kts"

apply_block = r'''PATCH_REPOSITORY_0187_PHASE4="${1:?Informe o repositório cumulativo}"
PATCH_0187_PHASE4_B64="$PATCH_REPOSITORY_0187_PHASE4/patches/farol-runtime-0187-phase4.patch.gz.b64"
PATCH_0187_PHASE4="$(mktemp --suffix=.farol-runtime-0187-phase4.patch)"
base64 --decode "$PATCH_0187_PHASE4_B64" | gzip --decompress > "$PATCH_0187_PHASE4"
test "$(sha256sum "$PATCH_0187_PHASE4" | awk '{print $1}')" = "f1cd8fbee993b93da81f97e313143f96ddd0a821e8c1e1ce7176e3b2e524843f"
git apply --check "$PATCH_0187_PHASE4"
git apply "$PATCH_0187_PHASE4"
rm -f "$PATCH_0187_PHASE4"

PHASE4_SIGNATURE_FIX="$PATCH_REPOSITORY_0187_PHASE4/scripts/fix_farol_phase4_address_signature.py"
PHASE4_SERVICE="app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
python3 "$PHASE4_SIGNATURE_FIX" --self-test
python3 "$PHASE4_SIGNATURE_FIX" "$PHASE4_SERVICE"

grep -Fq 'DECISION_RESULT_MONOTONIC_BINDING_0187_PHASE4' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt
grep -Fq 'BUBBLE_ASYNC_WORK_INVALIDATED_0187_PHASE4' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'BUBBLE_ROUTE_RESULT_DISCARDED_0187_PHASE4' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
python3 - <<'PY_PHASE4_CONTRACT'
from pathlib import Path
service = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt').read_text(encoding='utf-8')
safety = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt').read_text(encoding='utf-8')
if 'private fun isUniversalResultFresh(' in service:
    raise SystemExit('Verificação antiga de rota sem sessão/janela ainda existe')
if 'FarolDecisionBindingPolicy0187Phase4.isFresh' not in service:
    raise SystemExit('Resultado da rota não usa o vínculo monotônico da fase 4')
if 'DECISION_RESULT_MONOTONIC_BINDING_0187_PHASE4' not in safety:
    raise SystemExit('Contrato de vínculo monotônico ausente')
if 'addressSignature = addressSignature' in service:
    raise SystemExit('Referência addressSignature fora de escopo permanece após reparo')
start = service.index('private fun invalidateFarolAsyncWork0187Phase4')
end = service.index('private fun invalidateRejectedSnapshotRead0187Phase3', start)
block = service[start:end]
for required in (
    'universalRouteJob?.cancel()',
    'analyzeJob?.cancel()',
    'screenshotFallbackJob127?.cancel()',
    'partialReadConfirmationJobChecklist14?.cancel()',
    'liveAnalysisJob?.cancel()',
):
    if required not in block:
        raise SystemExit(f'Cancelamento central incompleto: {required}')
PY_PHASE4_CONTRACT
'''

if text.count(marker) != 1:
    raise SystemExit("0.1.187 validation marker not found exactly once for phase 4")
wrapper.write_text(text.replace(marker, apply_block + marker, 1), encoding="utf-8")
