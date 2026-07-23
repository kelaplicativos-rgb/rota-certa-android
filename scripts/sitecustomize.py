from __future__ import annotations

from pathlib import Path

_original_write_text = Path.write_text
_invalid = 'Regex("^\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?(?:\\s|,|\\()", RegexOption.IGNORE_CASE)'
_valid = 'Regex("""^\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?(?:\\s|,|\\()""", RegexOption.IGNORE_CASE)'


def _write_text(self: Path, data: str, *args, **kwargs):
    if isinstance(data, str) and _invalid in data:
        data = data.replace(_invalid, _valid)
    return _original_write_text(self, data, *args, **kwargs)


Path.write_text = _write_text
