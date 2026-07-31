#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/FarolUnifiedVisualCriticalPath0168Test.kt"
MARKER = "farol_visual_blocks_integrated_0_1_168"


def fail(message: str) -> None:
    raise SystemExit(message)


def matching_brace(text: str, opening: int) -> int:
    depth = 0
    quote = None
    escaped = False
    for index in range(opening, len(text)):
        char = text[index]
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ('"', "'"):
            quote = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return index
    fail("lambda OCR sem fechamento")


if not SERVICE.exists() or not TEST.exists():
    fail("a correção principal 0.1.168 precisa ser aplicada primeiro")

contract = TEST.read_text(encoding="utf-8")
contract = contract.replace(
    'File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")',
    'File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")',
)
TEST.write_text(contract, encoding="utf-8")

service = SERVICE.read_text(encoding="utf-8")
if MARKER not in service:
    integrated = False
    pattern = re.compile(
        r"\.process\s*\([^)]*\)\s*\.addOnSuccessListener\s*\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*->",
        re.S,
    )
    for match in pattern.finditer(service):
        result_name = match.group(1)
        opening = service.find("{", match.start(), match.end())
        if opening < 0:
            continue
        closing = matching_brace(service, opening)
        body = service[opening : closing + 1]
        target = f"{result_name}.text"
        if target not in body:
            continue
        replacement = f"FarolUnifiedVisual0168.fromVisionText({result_name})"
        body = body.replace(target, replacement, 1)
        body = body[:-1] + f"\n            // {MARKER}\n" + body[-1]
        service = service[:opening] + body + service[closing + 1 :]
        integrated = True
        break
    if not integrated:
        # Some versions assign the ML Kit task before attaching the listener.
        candidates = list(re.finditer(r"addOnSuccessListener\s*\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*->", service))
        for match in candidates:
            result_name = match.group(1)
            opening = service.find("{", match.start(), match.end())
            closing = matching_brace(service, opening)
            body = service[opening : closing + 1]
            target = f"{result_name}.text"
            if target in body and "OCR" in service[max(0, match.start() - 5_000) : match.start()].upper():
                body = body.replace(target, f"FarolUnifiedVisual0168.fromVisionText({result_name})", 1)
                body = body[:-1] + f"\n            // {MARKER}\n" + body[-1]
                service = service[:opening] + body + service[closing + 1 :]
                integrated = True
                break
    if not integrated:
        fail("resultado textual do ML Kit OCR não localizado")
    SERVICE.write_text(service, encoding="utf-8")

print("Integração espacial e contrato 0.1.168 concluídos")
