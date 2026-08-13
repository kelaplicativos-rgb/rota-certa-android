#!/usr/bin/env python3
import gzip
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
PARTS = sorted((ROOT / 'stage32' / 'applier_gz').glob('chunk-*'))
if not PARTS:
    raise SystemExit('Stage32 compressed applier chunks missing')
SOURCE = gzip.decompress(b''.join(path.read_bytes() for path in PARTS)).decode('utf-8')
exec(compile(SOURCE, 'stage32/full_applier_stage32.py', 'exec'), globals(), globals())
