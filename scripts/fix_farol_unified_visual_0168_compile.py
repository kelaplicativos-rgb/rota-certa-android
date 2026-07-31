#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
MAIN = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta"
SERVICE = MAIN / "LiveRideAccessibilityService.kt"
ANDROID_SERVICES = MAIN / "AndroidServices.kt"
TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/FarolUnifiedVisualCriticalPath0168Test.kt"
MARKER = "farol_visual_blocks_integrated_0_1_168"
FLAT_RESULT = "recognizer.process(image).await().text"
SPATIAL_RESULT = "FarolUnifiedVisual0168.fromVisionText(recognizer.process(image).await())"


def fail(message: str) -> None:
    raise SystemExit(message)


def find_matching_brace(text: str, opening: int) -> int:
    depth = 0
    quote: str | None = None
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
    fail("classe OcrService sem fechamento")


def ocr_service_range(source: str) -> tuple[int, int]:
    declaration = re.search(r"\bclass\s+OcrService\b[^\{]*\{", source)
    if declaration is None:
        fail("classe OcrService não encontrada em AndroidServices.kt")
    opening = source.find("{", declaration.start(), declaration.end())
    return declaration.start(), find_matching_brace(source, opening) + 1


if not SERVICE.exists() or not ANDROID_SERVICES.exists() or not TEST.exists():
    fail("a correção principal 0.1.168 precisa ser aplicada antes da integração OCR")

# O teste de contrato é executado a partir da raiz do repositório materializado.
contract = TEST.read_text(encoding="utf-8")
contract = contract.replace(
    'File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")',
    'File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")',
)
TEST.write_text(contract, encoding="utf-8")

android_services = ANDROID_SERVICES.read_text(encoding="utf-8")
start, end = ocr_service_range(android_services)
ocr_service = android_services[start:end]

if MARKER not in ocr_service:
    flat_count = ocr_service.count(FLAT_RESULT)
    if flat_count != 2:
        fail(
            "conversões OCR achatadas inesperadas em OcrService: "
            f"esperadas=2 encontradas={flat_count}"
        )
    ocr_service = ocr_service.replace(FLAT_RESULT, SPATIAL_RESULT)
    class_open = ocr_service.find("{")
    ocr_service = (
        ocr_service[: class_open + 1]
        + f"\n    // {MARKER}: mesma leitura ML Kit, preservando ordem espacial dos blocos."
        + ocr_service[class_open + 1 :]
    )
    android_services = android_services[:start] + ocr_service + android_services[end:]
    ANDROID_SERVICES.write_text(android_services, encoding="utf-8")
else:
    if ocr_service.count(SPATIAL_RESULT) != 2:
        fail("integração OCR 0.1.168 marcada, porém incompleta ou duplicada")

# Falha fechada: nenhuma das duas entradas públicas do OCR pode voltar a achatar
# o resultado antes de aplicar a ordenação espacial da visão unificada.
updated = ANDROID_SERVICES.read_text(encoding="utf-8")
start, end = ocr_service_range(updated)
updated_ocr_service = updated[start:end]
if updated_ocr_service.count(SPATIAL_RESULT) != 2:
    fail("as duas entradas de OcrService não usam a visão espacial unificada")
if FLAT_RESULT in updated_ocr_service:
    fail("OcrService ainda contém conversão OCR achatada")
if updated_ocr_service.count(MARKER) != 1:
    fail("marcador da integração OCR deve existir exatamente uma vez")

print("Integração espacial do OcrService e contrato 0.1.168 concluídos")
