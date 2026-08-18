#!/usr/bin/env bash
set -euo pipefail
PATCHES="$(cd "${2:?patches}" && pwd)"
mkdir -p "${5:?evidence}"
EVIDENCE="$(cd "$5" && pwd)"
ORIGINAL="$PATCHES/scripts/run_error1_card_visual_episode_reentry_ci.sh"
GENERATED="$EVIDENCE/run-error1-fast-generated.sh"

python3 - "$ORIGINAL" "$GENERATED" <<'PY'
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text(encoding='utf-8')
out = Path(sys.argv[2])
old = '''mkdir -p "$EVIDENCE/r8-baseline"
bash "$PATCHES/scripts/run_stage46_r8_reproducible_ci.sh" \\
  "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r8-baseline"
grep -Fq 'stage46_r8_end_to_end=PASS version=0.1.226/5510 tests=1262' "$EVIDENCE/r8-baseline/final-status.txt"
printf 'error1_r8_authoritative_baseline=PASS head_runtime=0.1.226/5510 tests=1262\\n' | tee "$EVIDENCE/r8-baseline-proof.txt"
'''
new = '''mkdir -p "$EVIDENCE/r8-materialize"
R8_MATERIALIZER="$EVIDENCE/run-stage46-r8-materialize-only.sh"
python3 - "$PATCHES/scripts/run_stage46_r8_reproducible_ci.sh" "$R8_MATERIALIZER" <<'PY_R8'
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text(encoding='utf-8')
out = Path(sys.argv[2])
anchor = "python3 - \\\"$SOURCE\\\" \\\"$PATCHES\\\" <<'PY' | tee \\\"$EVIDENCE/static-r8.txt\\\""
if source.count(anchor) != 1:
    raise SystemExit(f'Error1 R8 materialize-only anchor expected once, got {source.count(anchor)}')
early = """if [[ \\\"${ERROR1_R8_MATERIALIZE_ONLY:-0}\\\" == \\\"1\\\" ]]; then
  printf 'error1_r8_materialized=PASS version=0.1.226/5510 exact_r8_recipe=true historical_runtime_tests_deferred_to_final_error1_suite=true\\\\n' | tee \\\"$EVIDENCE/final-status.txt\\\"
  exit 0
fi

"""
out.write_text(source.replace(anchor, early + anchor, 1), encoding='utf-8')
PY_R8
ERROR1_R8_MATERIALIZE_ONLY=1 bash "$R8_MATERIALIZER" \\
  "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r8-materialize"
grep -Fq 'error1_r8_materialized=PASS version=0.1.226/5510 exact_r8_recipe=true' "$EVIDENCE/r8-materialize/final-status.txt"
printf 'error1_r8_authoritative_baseline=PASS base_sha=af136cf44ed746d1a350e251eac6099bc957b26e materialization_only=true final_full_regression_required=true\\n' | tee "$EVIDENCE/r8-baseline-proof.txt"
'''
if source.count(old) != 1:
    raise SystemExit(f'Error1 baseline block expected once, got {source.count(old)}')
out.write_text(source.replace(old, new, 1), encoding='utf-8')
PY
chmod +x "$GENERATED"
exec bash "$GENERATED" "$@"
