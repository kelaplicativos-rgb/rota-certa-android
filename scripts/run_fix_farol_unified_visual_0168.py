#!/usr/bin/env python3
from __future__ import annotations

import re
import runpy
import sys
import tempfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("fix_farol_unified_visual_0168.py")

TEXT_PARAM_OLD = 'params = list(re.finditer(r"\\b([A-Za-z_][A-Za-z0-9_]*text[A-Za-z0-9_]*)\\s*:\\s*String\\b", signature, re.I))'
TEXT_PARAM_NEW = '''string_params = list(
    re.finditer(r"\\b([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*String\\b", signature, re.I)
)
params = [match for match in string_params if "text" in match.group(1).lower()]'''

SEMANTIC_HASH_OLD = '''service, semantic_replacements = re.subn(
    r"\\b([A-Za-z_][A-Za-z0-9_]*(?:text|Text))\\.hashCode\\(\\)",
    r"FarolUnifiedVisual0168.semanticHash(\\1)",
    service,
)'''
SEMANTIC_HASH_NEW = '''service, semantic_replacements = re.subn(
    r"(?im)(\\bval\\s+[A-Za-z_][A-Za-z0-9_]*analysisHash[A-Za-z0-9_]*\\s*=\\s*)([A-Za-z_][A-Za-z0-9_]*text[A-Za-z0-9_]*)\\.hashCode\\(\\)",
    r"\\1FarolUnifiedVisual0168.semanticHash(\\2)",
    service,
)'''


def replace_required(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count == 1:
        return source.replace(old, new, 1)
    if new in source:
        return source
    raise SystemExit(
        f"aplicador 0.1.168 mudou: {label} não foi localizado com segurança; ocorrências={count}"
    )


def corrected_source() -> str:
    source = SCRIPT.read_text(encoding="utf-8")
    source = replace_required(
        source,
        TEXT_PARAM_OLD,
        TEXT_PARAM_NEW,
        "parâmetro textual",
    )
    source = replace_required(
        source,
        SEMANTIC_HASH_OLD,
        SEMANTIC_HASH_NEW,
        "hash semântico da análise",
    )
    return source


def validate_runtime_patch(source: str) -> None:
    namespace: dict[str, object] = {"re": re}
    sample_signature = '''private suspend fun processRideText(
        text: String,
        source: UniversalRideSource,
        packageHint152: String? = null,
    )'''
    string_params = list(
        re.finditer(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*:\s*String\b", sample_signature, re.I)
    )
    textual = [match.group(1) for match in string_params if "text" in match.group(1).lower()]
    if textual != ["text"]:
        raise SystemExit(f"regressão do parâmetro textual: {textual}")

    sample_service = '''
        val analysisHash143 = immediateTextChecklist13.hashCode()
        val diagnosticHash = immediateTextChecklist13.hashCode()
        lastFailedCardAccessibilityHash0161 = snapshotTextChecklist13.hashCode()
    '''
    stabilized, count = re.subn(
        r"(?im)(\bval\s+[A-Za-z_][A-Za-z0-9_]*analysisHash[A-Za-z0-9_]*\s*=\s*)([A-Za-z_][A-Za-z0-9_]*text[A-Za-z0-9_]*)\.hashCode\(\)",
        r"\1FarolUnifiedVisual0168.semanticHash(\2)",
        sample_service,
    )
    if count != 1:
        raise SystemExit(f"regressão do hash semântico: substituições={count}")
    if "val analysisHash143 = FarolUnifiedVisual0168.semanticHash(immediateTextChecklist13)" not in stabilized:
        raise SystemExit("hash de análise não foi estabilizado")
    if "val diagnosticHash = immediateTextChecklist13.hashCode()" not in stabilized:
        raise SystemExit("hash de diagnóstico foi alterado indevidamente")
    if "snapshotTextChecklist13.hashCode()" not in stabilized:
        raise SystemExit("hash de recuperação foi alterado indevidamente")

    if TEXT_PARAM_NEW not in source or SEMANTIC_HASH_NEW not in source:
        raise SystemExit("correções esperadas não estão no script materializado em memória")


def main() -> None:
    source_root = sys.argv[1] if len(sys.argv) > 1 else "."
    runtime_path: Path | None = None
    source = corrected_source()
    validate_runtime_patch(source)
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            suffix="_fix_farol_unified_visual_0168.py",
            prefix="rota_certa_",
            delete=False,
        ) as runtime:
            runtime.write(source)
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
