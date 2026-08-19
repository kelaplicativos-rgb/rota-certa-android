#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

PATCHES = Path(sys.argv[1]).resolve()
for script in (
    "apply_stage47_unified_capacity_backend_r4_step2.py",
    "fix_stage47_unified_capacity_backend_r4_step2_persistence.py",
):
    subprocess.run([sys.executable, str(PATCHES / "scripts" / script), str(PATCHES)], check=True)
print("stage47_unified_capacity_r4_step2=PASS backend_reconciler_materialized=true")
