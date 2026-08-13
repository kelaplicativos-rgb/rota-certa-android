#!/usr/bin/env python3
import base64, gzip
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
PARTS = sorted((ROOT / "stage34" / "applier_b64").glob("chunk-*"))
if not PARTS:
    raise SystemExit("Stage34 compressed applier chunks missing")
SOURCE = gzip.decompress(base64.b64decode("".join(p.read_text().strip() for p in PARTS))).decode("utf-8")
exec(compile(SOURCE, "stage34/full_applier_stage34.py", "exec"), globals(), globals())
