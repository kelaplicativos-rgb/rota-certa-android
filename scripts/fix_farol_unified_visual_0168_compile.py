#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
MAIN = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta"
TEST_ROOT = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta"
SERVICE = MAIN / "LiveRideAccessibilityService.kt"
ANDROID_SERVICES = MAIN / "AndroidServices.kt"
TEST = TEST_ROOT / "FarolUnifiedVisualCriticalPath0168Test.kt"
PIPELINE_TEST = TEST_ROOT / "InstantPipeline127ContractTest.kt"
MARKER = "farol_visual_blocks_integrated_0_1_168"
FLAT_RESULT = "recognizer.process(image).await().text"
SPATIAL_RESULT = "FarolUnifiedVisual0168.fromVisionText(recognizer.process(image).await())"

CONTRACT_DIRECT_SRC = '    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()'
CONTRACT_DIRECT_APP = '    private val service = File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()'
CONTRACT_RESILIENT = '''    private val service = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("LiveRideAccessibilityService.kt não encontrado")'''
CONTRACT_DELAY_OLD = '        assertFalse(window.contains("postDelayed("))'
CONTRACT_DELAY_NEW = '''        assertFalse(window.contains("postDelayed("))
        assertFalse(window.contains("delay(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS)"))'''
PIPELINE_DELAY_OLD = '        assertTrue("Fallback precisa usar o orçamento centralizado", "delay(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS)" in service)'
PIPELINE_DELAY_NEW = '        assertFalse("Fallback OCR não pode aguardar artificialmente", "delay(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS)" in service)'


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once_or_accept(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count == 1:
        return text.replace(old, new, 1)
    if new in text:
        return text
    fail(f"{label}: esperado exatamente um contrato conhecido; ocorrências antigas={count}")


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


if not SERVICE.exists() or not ANDROID_SERVICES.exists() or not TEST.exists() or not PIPELINE_TEST.exists():
    fail("a correção principal 0.1.168 precisa ser aplicada antes da integração OCR")

# Contratos de arquivo devem funcionar tanto quando o Gradle parte da raiz do
# módulo app quanto quando parte da raiz do repositório.
contract = TEST.read_text(encoding="utf-8")
if CONTRACT_RESILIENT not in contract:
    if CONTRACT_DIRECT_SRC in contract:
        contract = contract.replace(CONTRACT_DIRECT_SRC, CONTRACT_RESILIENT, 1)
    elif CONTRACT_DIRECT_APP in contract:
        contract = contract.replace(CONTRACT_DIRECT_APP, CONTRACT_RESILIENT, 1)
    else:
        fail("caminho do contrato crítico 0.1.168 não reconhecido")
contract = replace_once_or_accept(
    contract,
    CONTRACT_DELAY_OLD,
    CONTRACT_DELAY_NEW,
    "contrato crítico sem atraso OCR",
)
TEST.write_text(contract, encoding="utf-8")

# A 0.1.168 removeu intencionalmente o atraso fixo; o contrato histórico deve
# continuar exigindo acessibilidade primeiro, fallback cancelável e ausência de
# screenshot direto, mas não pode exigir a espera removida.
pipeline_contract = PIPELINE_TEST.read_text(encoding="utf-8")
pipeline_contract = replace_once_or_accept(
    pipeline_contract,
    PIPELINE_DELAY_OLD,
    PIPELINE_DELAY_NEW,
    "contrato histórico do fallback OCR",
)
PIPELINE_TEST.write_text(pipeline_contract, encoding="utf-8")

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

final_contract = TEST.read_text(encoding="utf-8")
if CONTRACT_RESILIENT not in final_contract or CONTRACT_DELAY_NEW not in final_contract:
    fail("contrato crítico 0.1.168 não foi estabilizado")
if PIPELINE_DELAY_NEW not in PIPELINE_TEST.read_text(encoding="utf-8"):
    fail("contrato histórico do pipeline ainda exige atraso OCR")

print("Integração espacial do OcrService e contratos 0.1.168 concluídos")
