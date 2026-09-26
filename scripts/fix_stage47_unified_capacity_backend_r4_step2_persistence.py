#!/usr/bin/env python3
from pathlib import Path
import sys

PATCHES = Path(sys.argv[1]).resolve()
BACKEND = PATCHES / "trip-platform/functions/index.js"
text = BACKEND.read_text(encoding="utf-8")

replacements = {
    '      tx.create(bookingRef, { ...candidate, id: undefined });\n': '      const candidatePersisted = { ...candidate };\n      delete candidatePersisted.id;\n      tx.create(bookingRef, candidatePersisted);\n',
    '      tx.set(bookingRef, { ...normalized, id: undefined }, { merge: true });\n': '      const normalizedPersisted = { ...normalized };\n      delete normalizedPersisted.id;\n      tx.set(bookingRef, normalizedPersisted, { merge: true });\n',
}
for old, new in replacements.items():
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"persistence marker expected once, got {count}: {old.strip()}")
    text = text.replace(old, new, 1)
BACKEND.write_text(text, encoding="utf-8")
print("stage47_unified_capacity_backend_r4_step2_persistence=PASS undefined_firestore_values=false")
