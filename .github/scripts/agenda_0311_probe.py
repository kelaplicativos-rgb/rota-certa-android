#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def once(path, old, new, label):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected 1 marker, found {n}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")
