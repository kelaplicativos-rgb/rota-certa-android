#!/usr/bin/env python3
from __future__ import annotations

import runpy
import sys
import tempfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("fix_farol_unified_visual_0168.py")

OLD = 'params = list(re.finditer(r"\\b([A-Za-z_][A-Za-z0-9_]*text[A-Za-z0-9_]*)\\s*:\\s*String\\b", signature, re.I))'
NEW = '''string_params = list(
    re.finditer(r"\\b([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*String\\b", signature, re.I)
)
params = [match for match in string_params if "text" in match.group(1).lower()]'''


def corrected_source() -> str:
    source = SCRIPT.read_text(encoding="utf-8")
    count = source.count(OLD)
    if count == 1:
        return source.replace(OLD, NEW, 1)
    if NEW in source:
        return source
    raise SystemExit(
        "aplicador 0.1.168 mudou: padrão textual esperado não foi localizado com segurança"
    )


def main() -> None:
    source_root = sys.argv[1] if len(sys.argv) > 1 else "."
    runtime_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            suffix="_fix_farol_unified_visual_0168.py",
            prefix="rota_certa_",
            delete=False,
        ) as runtime:
            runtime.write(corrected_source())
            runtime_path = Path(runtime.name)
        previous_argv = sys.argv[:]
        sys.argv = [str(runtime_path), source_root]
        try:
            runpy.run_path(str(runtime_path), run_name="__main__")
        finally:
            sys.argv = previous_argv
    finally:
        if runtime_path is not None:
            runtime_path.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
