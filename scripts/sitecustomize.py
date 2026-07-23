from __future__ import annotations

import sys
from pathlib import Path


def _repair_behavior_fix_source() -> None:
    if not sys.argv:
        return
    target = Path(sys.argv[0])
    if target.name != "apply_0128_behavior_fixes.py" or not target.exists():
        return

    text = target.read_text()
    old = 'Regex("^\\\\d{1,6}(?:[-/][\\\\p{L}\\\\d]+|[\\\\p{L}])?(?:\\\\s|,|\\\\()", RegexOption.IGNORE_CASE)'
    new = 'Regex("""^\\\\d{1,6}(?:[-/][\\\\p{L}\\\\d]+|[\\\\p{L}])?(?:\\\\s|,|\\\\()""", RegexOption.IGNORE_CASE)'
    repaired = text.replace(old, new)
    if repaired == text:
        raise SystemExit("Regex Kotlin do materializador 0.1.128 nao encontrada")
    target.write_text(repaired)


_repair_behavior_fix_source()
