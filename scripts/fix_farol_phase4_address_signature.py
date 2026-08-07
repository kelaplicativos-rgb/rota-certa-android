#!/usr/bin/env python3
"""Corrige somente a referência de assinatura fora de escopo da fase 4.

O reparo é intencionalmente fail-closed: exige um único método que declare
`destinationSignature`, não declare `addressSignature` e contenha exatamente
uma utilização de `addressSignature` que não seja o rótulo de argumento
nomeado. Qualquer divergência interrompe a materialização.
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

FUNCTION_START = re.compile(
    r"(?m)^[ \t]*(?:private\s+|internal\s+|public\s+|protected\s+)?"
    r"(?:suspend\s+)?fun\s+[A-Za-z_][A-Za-z0-9_]*\s*\("
)
DESTINATION_DECLARATION = re.compile(
    r"(?:\bdestinationSignature\s*:\s*String\b|\b(?:val|var)\s+destinationSignature\b)"
)
ADDRESS_DECLARATION = re.compile(
    r"(?:\baddressSignature\s*:\s*String\b|\b(?:val|var)\s+addressSignature\b)"
)
ADDRESS_VALUE = re.compile(r"\baddressSignature\b(?!\s*=)")


def function_blocks(source: str) -> list[tuple[int, int]]:
    starts = [match.start() for match in FUNCTION_START.finditer(source)]
    return [
        (start, starts[index + 1] if index + 1 < len(starts) else len(source))
        for index, start in enumerate(starts)
    ]


def repair_source(source: str) -> str:
    candidates: list[tuple[int, int, str]] = []
    for start, end in function_blocks(source):
        block = source[start:end]
        if "addressSignature" not in block:
            continue
        if not DESTINATION_DECLARATION.search(block):
            continue
        if ADDRESS_DECLARATION.search(block):
            continue
        value_uses = list(ADDRESS_VALUE.finditer(block))
        if len(value_uses) != 1:
            continue
        candidates.append((start, end, block))

    if len(candidates) != 1:
        raise SystemExit(
            "phase4 addressSignature scope candidate count=" + str(len(candidates))
        )

    start, end, block = candidates[0]
    repaired_block, replacements = ADDRESS_VALUE.subn("destinationSignature", block)
    if replacements != 1:
        raise SystemExit(f"phase4 addressSignature replacement count={replacements}")
    if not DESTINATION_DECLARATION.search(repaired_block):
        raise SystemExit("phase4 destinationSignature declaration lost after repair")
    if ADDRESS_VALUE.search(repaired_block):
        raise SystemExit("phase4 unresolved addressSignature value remains")
    return source[:start] + repaired_block + source[end:]


def self_test() -> None:
    named = """
private fun binding(destinationSignature: String) {
    val binding = Token(
        addressSignature = addressSignature,
    )
}
"""
    repaired_named = repair_source(named)
    assert "addressSignature = destinationSignature" in repaired_named

    positional = """
private fun binding(destinationSignature: String) {
    val binding = Token(
        destinationSignature,
        addressSignature,
    )
}
"""
    repaired_positional = repair_source(positional)
    assert repaired_positional.count("destinationSignature") == 3

    declared = """
private fun binding(destinationSignature: String, addressSignature: String) {
    val binding = Token(addressSignature)
}
"""
    try:
        repair_source(declared)
    except SystemExit:
        pass
    else:
        raise AssertionError("declared addressSignature must not be rewritten")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        print("phase4_address_signature_self_test=passed")
        return
    if args.path is None:
        parser.error("path is required unless --self-test is used")

    original = args.path.read_text(encoding="utf-8")
    repaired = repair_source(original)
    if repaired == original:
        raise SystemExit("phase4 addressSignature repair produced no change")
    args.path.write_text(repaired, encoding="utf-8")
    print("phase4_address_signature_scope_fix=applied")


if __name__ == "__main__":
    main()
