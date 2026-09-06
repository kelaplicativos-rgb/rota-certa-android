#!/usr/bin/env python3
"""Validação fail-closed da arquitetura do farol 0.1.188."""
from __future__ import annotations

import argparse
from pathlib import Path

SERVICE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
GATE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt")
OCR = Path("app/src/main/java/br/com/mapeiaia/rotacerta/AndroidServices.kt")
TEST = Path("app/src/test/java/br/com/mapeiaia/rotacerta/FarolRealDevice0188Test.kt")


def require(path: Path, markers: tuple[str, ...]) -> None:
    if not path.is_file():
        raise SystemExit(f"Arquivo obrigatório ausente: {path}")
    text = path.read_text(encoding="utf-8")
    missing = [marker for marker in markers if marker not in text]
    if missing:
        raise SystemExit(f"Marcadores ausentes em {path}: " + ", ".join(missing))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_root", type=Path)
    args = parser.parse_args()
    root = args.source_root.resolve()

    require(root / SERVICE, (
        "fun authorizeRoute0188(",
        "BUBBLE_ROUTE_GATE_REJECTED_0188",
        "BUBBLE_ROUTE_GATE_ACCEPTED_0188",
        "BUBBLE_FAILED_CARD_EVIDENCE_ONLY_0188",
        "ocrService.extractStructuredText",
        "collectAccessibilityCardBlocks0188",
        "collectOcrCardBlocks0188",
    ))
    require(root / GATE, (
        "FAROL_REAL_DEVICE_GATE_0188",
        "selectedPackageName",
        "Endereços pertencem a blocos/cards diferentes.",
        "Tela passiva, segurança ou status não pode autorizar rota.",
        "reconhecimento facial",
        "go online",
    ))
    require(root / OCR, (
        "OcrStructuredText0188",
        "OcrTextBlock0188",
        "extractStructuredText",
        "result.textBlocks",
        "recognizer.close()",
    ))
    require(root / TEST, (
        "facialRecognitionDoesNotAuthorizeRoute",
        "twoAddressesInSameCardAuthorizeLastDestination",
        "addressesFromDifferentCardsNeverCombine",
        "popupWindowCanBeAuthorizedWhenPackageAndWindowStayBound",
        "multipleVisibleCardsWithDifferentDestinationsFailClosed",
        "unknownSelectedPackageUsesSameUniversalCore",
    ))

    service = (root / SERVICE).read_text(encoding="utf-8")
    if "applyRecoveredCard0161(" in service.replace("private suspend fun applyRecoveredCard0161(", ""):
        raise SystemExit("Recuperação antiga ainda possui chamada capaz de pintar o farol")

    print("farol_real_device_0188_hardening=passed")


if __name__ == "__main__":
    main()
