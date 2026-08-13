#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
PARTS = sorted((ROOT / "stage34" / "applier_text").glob("chunk-*"))
if not PARTS:
    raise SystemExit("Stage34 transparent applier chunks missing")
SOURCE = "".join(path.read_text() for path in PARTS)
exec(compile(SOURCE, "stage34/full_applier_stage34.py", "exec"), globals(), globals())
