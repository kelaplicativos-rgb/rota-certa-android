#!/usr/bin/env python3
"""Endurecimento final da captura 0.1.188 antes do Gradle.

A palavra isolada "online" é comum em ofertas e não pode, sozinha, tornar uma
tela passiva. Permanecem bloqueadas frases de login/status/segurança e o gate
estrutural continua exigindo card coerente com dois ou mais endereços.
"""
from __future__ import annotations

import argparse
from pathlib import Path

SERVICE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
TEST = Path("app/src/test/java/br/com/mapeiaia/rotacerta/FarolRealDevice0188Test.kt")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_root", type=Path)
    args = parser.parse_args()
    root = args.source_root.resolve()
    service_path = root / SERVICE
    test_path = root / TEST
    if not service_path.is_file():
        raise SystemExit(f"Serviço materializado não encontrado: {service_path}")
    if not test_path.is_file():
        raise SystemExit(f"Teste 0.1.188 não encontrado: {test_path}")

    text = service_path.read_text(encoding="utf-8")
    broad_token = '            "online",\n'
    count = text.count(broad_token)
    if count == 1:
        text = text.replace(broad_token, "", 1)
        service_path.write_text(text, encoding="utf-8")
        print("farol_0188_broad_online_token=removed")
    elif count == 0:
        print("farol_0188_broad_online_token=already_absent")
    else:
        raise SystemExit(f"Quantidade inesperada do token online isolado: {count}")

    required = (
        "fun authorizeRoute0188(",
        "candidateCount < 2",
        "BUBBLE_ROUTE_GATE_REJECTED_0188",
        "BUBBLE_FAILED_CARD_EVIDENCE_ONLY_0188",
        "flagRetrieveInteractiveWindows",
        '"go online"',
        '"conecte-se"',
        '"reconhecimento facial"',
    )
    current = service_path.read_text(encoding="utf-8")
    missing = [marker for marker in required if marker not in current]
    if missing:
        raise SystemExit("Marcadores obrigatórios ausentes: " + ", ".join(missing))
    if broad_token in current:
        raise SystemExit("Token online isolado permaneceu após endurecimento")

    tests = test_path.read_text(encoding="utf-8")
    for marker in (
        "facialRecognitionDoesNotAuthorizeRoute",
        "twoAddressesInSameCardAuthorizeLastDestination",
        "addressesFromDifferentCardsNeverCombine",
        "popupWindowCanBeAuthorizedWhenPackageAndWindowStayBound",
    ):
        if marker not in tests:
            raise SystemExit(f"Regressão obrigatória ausente: {marker}")

    print("farol_real_device_0188_hardening=passed")


if __name__ == "__main__":
    main()
