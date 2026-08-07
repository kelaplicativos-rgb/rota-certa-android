#!/usr/bin/env python3
"""Verificação fail-closed dos contratos funcionais da 0.1.189."""
from __future__ import annotations

import argparse
from pathlib import Path

SERVICE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
GATE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt")
VISUAL = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisualPriority0189.kt")
GATE_TEST = Path("app/src/test/java/br/com/mapeiaia/rotacerta/FarolRealDevice0188Test.kt")
VISUAL_TEST = Path("app/src/test/java/br/com/mapeiaia/rotacerta/FarolVisualPriority0189Test.kt")
BUILD = Path("app/build.gradle.kts")


def require(path: Path, markers: tuple[str, ...]) -> str:
    if not path.is_file():
        raise SystemExit(f"Arquivo obrigatório ausente: {path}")
    text = path.read_text(encoding="utf-8")
    missing = [marker for marker in markers if marker not in text]
    if missing:
        raise SystemExit(f"Marcadores ausentes em {path}: " + ", ".join(missing))
    return text


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_root", type=Path)
    args = parser.parse_args()
    root = args.source_root.resolve()

    require(root / BUILD, (
        "versionCode = 5473",
        'versionName = "0.1.189"',
    ))

    service = require(root / SERVICE, (
        "BUBBLE_DESTINATION_CONFIRMED_ORANGE_0189",
        "OCR_FALLBACK_DEDUPED_0189",
        "scheduledOcrIdentity0189",
        "lastOcrAttemptIdentity0189",
        "FarolVisualPriority0189.cluster",
        "Orange(Color.rgb(243, 156, 18)",
        '"laranja"',
        "RadarColor.Orange",
        "collectAccessibilityCardBlocks0188",
        "collectOcrCardBlocks0188",
    ))

    gate = require(root / GATE, (
        "FAROL_TOP_BLOCK_AUTHORITY_0189",
        "windowLayer",
        "syntheticRoot",
        "val bestLayer = addressBearing.maxOf",
        "val destination = winner.addresses.last()",
        "Bloco visual superior ainda não contém dois endereços confirmados.",
        "Bloco visual superior confirmado; último endereço autorizado como destino final.",
    ))

    require(root / VISUAL, (
        "object FarolVisualPriority0189",
        "FarolSpatialFragment0189",
        "FarolVisualGroup0189",
        "fun cluster(",
    ))

    require(root / GATE_TEST, (
        "facialRecognitionDoesNotAuthorizeRoute",
        "twoAddressesInSameCardAuthorizeLastDestination",
        "addressesFromDifferentCardsNeverCombine",
        "popupWindowCanBeAuthorizedWhenPackageAndWindowStayBound",
        "upperCardWinsWhenTwoCompleteCardsShareWindow",
        "higherWindowLayerWinsEvenWhenItsCardIsLowerOnScreen",
        "upperPartialCardBlocksLowerCompleteCard",
        "threeAddressesInOneCardUseFinalAddress",
        "syntheticRootNeverCombinesMultipleCards",
        "unknownSelectedPackageUsesSameUniversalCore",
    ))

    require(root / VISUAL_TEST, (
        "separatedAddressLinesWithIntermediateContentStayInSameVisualCard",
        "largeGapSeparatesStackedCards",
    ))

    if "applyRecoveredCard0161(" in service.replace("private suspend fun applyRecoveredCard0161(", ""):
        raise SystemExit("Recuperação antiga voltou a possuir chamada capaz de pintar o farol")

    if "winner.addresses.first()" not in gate or "winner.addresses.last()" not in gate:
        raise SystemExit("Origem/destino não estão vinculados ao mesmo bloco vencedor")

    if "screenHash = authorityIdentity.hashCode()" not in gate:
        raise SystemExit("Identidade visual do bloco vencedor não participa da geração")

    print("farol_priority_latency_0189_hardening=passed")


if __name__ == "__main__":
    main()
