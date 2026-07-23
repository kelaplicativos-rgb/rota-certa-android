from __future__ import annotations

import sys
from pathlib import Path


def _repair_behavior_fix_source() -> None:
    if not sys.argv:
        return
    target = Path(sys.argv[0])
    if target.name != "apply_0128_behavior_fixes.py" or not target.exists():
        return

    root = target.resolve().parents[1]
    report = root / "validation-failure-0128-recognized.txt"
    text = target.read_text()
    old = 'Regex("^\\\\d{1,6}(?:[-/][\\\\p{L}\\\\d]+|[\\\\p{L}])?(?:\\\\s|,|\\\\()", RegexOption.IGNORE_CASE)'
    new = 'Regex("""^\\\\d{1,6}(?:[-/][\\\\p{L}\\\\d]+|[\\\\p{L}])?(?:\\\\s|,|\\\\()""", RegexOption.IGNORE_CASE)'
    occurrences = text.count(old)
    repaired = text.replace(old, new)
    target.write_text(repaired)
    report.write_text(
        "SITECUSTOMIZE_REGEX_REPAIR\n"
        f"target={target}\n"
        f"occurrences={occurrences}\n"
        f"changed={repaired != text}\n"
    )


_repair_behavior_fix_source()
