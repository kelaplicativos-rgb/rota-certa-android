#!/usr/bin/env python3
"""Corrige somente a referência de assinatura fora de escopo da fase 4.

A fase 4 substituiu parâmetros soltos por um `FarolDecisionBinding0187Phase4`.
O reparo é fail-closed: localiza exatamente um identificador nu
`addressSignature` sem declaração no método e o vincula à única fonte válida
no mesmo escopo — `destinationSignature` declarado ou a propriedade do único
vínculo imutável da fase 4. Qualquer ambiguidade interrompe a materialização.
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
BARE_ADDRESS_VALUE = re.compile(
    r"(?<![A-Za-z0-9_.])\baddressSignature\b(?!\s*=)"
)
BINDING_PARAMETER = re.compile(
    r"\b([A-Za-z_][A-Za-z0-9_]*)\s*:\s*FarolDecisionBinding0187Phase4\b"
)
BINDING_LOCAL = re.compile(
    r"\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*createDecisionBinding0187Phase4\s*\("
)


def function_blocks(source: str) -> list[tuple[int, int]]:
    starts = [match.start() for match in FUNCTION_START.finditer(source)]
    return [
        (start, starts[index + 1] if index + 1 < len(starts) else len(source))
        for index, start in enumerate(starts)
    ]


def print_address_context(source: str) -> None:
    lines = source.splitlines()
    occurrences = [index for index, line in enumerate(lines) if BARE_ADDRESS_VALUE.search(line)]
    print(f"phase4_bare_address_signature_occurrences={len(occurrences)}")
    for index in occurrences:
        start = max(0, index - 60)
        end = min(len(lines), index + 13)
        print(f"--- bare addressSignature context line {index + 1} ---")
        for line_index in range(start, end):
            print(f"{line_index + 1:05d}: {lines[line_index]}")


def replacement_for_block(block: str) -> str | None:
    replacements: list[str] = []
    if DESTINATION_DECLARATION.search(block):
        replacements.append("destinationSignature")
    binding_names = set(BINDING_PARAMETER.findall(block)) | set(BINDING_LOCAL.findall(block))
    replacements.extend(f"{name}.addressSignature" for name in sorted(binding_names))
    unique = list(dict.fromkeys(replacements))
    return unique[0] if len(unique) == 1 else None


def repair_source(source: str) -> str:
    candidates: list[tuple[int, int, str, str]] = []
    for start, end in function_blocks(source):
        block = source[start:end]
        if not BARE_ADDRESS_VALUE.search(block):
            continue
        if ADDRESS_DECLARATION.search(block):
            continue
        value_uses = list(BARE_ADDRESS_VALUE.finditer(block))
        if len(value_uses) != 1:
            continue
        replacement = replacement_for_block(block)
        if replacement is None:
            continue
        candidates.append((start, end, block, replacement))

    if len(candidates) != 1:
        print_address_context(source)
        raise SystemExit(
            "phase4 addressSignature scope candidate count=" + str(len(candidates))
        )

    start, end, block, replacement = candidates[0]
    repaired_block, replacements = BARE_ADDRESS_VALUE.subn(replacement, block)
    if replacements != 1:
        print_address_context(source)
        raise SystemExit(f"phase4 addressSignature replacement count={replacements}")
    if BARE_ADDRESS_VALUE.search(repaired_block):
        print_address_context(repaired_block)
        raise SystemExit("phase4 unresolved bare addressSignature remains")
    return source[:start] + repaired_block + source[end:]


def self_test() -> None:
    destination = """
private fun binding(destinationSignature: String) {
    val token = Token(addressSignature = addressSignature)
}
"""
    assert "addressSignature = destinationSignature" in repair_source(destination)

    immutable_binding = """
private fun applyResult(
    binding0187Phase4: FarolDecisionBinding0187Phase4,
) {
    persist(addressSignature)
}
"""
    assert "persist(binding0187Phase4.addressSignature)" in repair_source(immutable_binding)

    declared = """
private fun binding(destinationSignature: String, addressSignature: String) {
    persist(addressSignature)
}
"""
    try:
        repair_source(declared)
    except SystemExit:
        pass
    else:
        raise AssertionError("declared addressSignature must not be rewritten")

    ambiguous = """
private fun applyResult(
    first: FarolDecisionBinding0187Phase4,
    second: FarolDecisionBinding0187Phase4,
) {
    persist(addressSignature)
}
"""
    try:
        repair_source(ambiguous)
    except SystemExit:
        pass
    else:
        raise AssertionError("ambiguous bindings must fail closed")


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
    print("phase4_address_signature_scope_fix=immutable_binding")


if __name__ == "__main__":
    main()
