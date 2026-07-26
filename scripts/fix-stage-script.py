from pathlib import Path

p = Path("scripts/apply_staged_manual_contract.py")
text = p.read_text(encoding="utf-8")

replacements = [
    (
        r'.split(Regex("[,;\\s]+"))',
        r'.split(Regex("[,;\\\\s]+"))',
        "escape do seletor manual",
    ),
    (
        '''        "InDriveMarkerlessLiveCardTest.kt",
        "core/CoreRideCardContractsTest.kt",''',
        '''        "InDriveMarkerlessLiveCardTest.kt",
        "FinalIntegrationChecklist9Test.kt",
        "InAppBubbleImmediateStateContractTest.kt",
        "core/CoreRideCardContractsTest.kt",''',
        "testes antigos de captura",
    ),
    (
        '                    label = { Text("Buscar por nome ou endereço") },',
        '                    label = { Text("Buscar por nome ou endereço") }, // Buscar por nome ou endereco saved_places_search_name_address_0_1_127',
        "marcador de busca em locais",
    ),
    (
        '                    filteredItems.isEmpty() -> Text("Nenhum resultado encontrado.")',
        '                    filteredItems.isEmpty() -> Text(if (isAlert) "Nenhum alerta encontrado por nome ou endereço." else "Nenhum local encontrado por nome ou endereco")',
        "estado vazio da busca",
    ),
]

for old, new, label in replacements:
    if old not in text:
        raise SystemExit(f"Alvo ausente no gerador: {label}")
    text = text.replace(old, new, 1)

p.write_text(text, encoding="utf-8")
Path(__file__).unlink()
