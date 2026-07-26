from pathlib import Path

p = Path("scripts/apply_staged_manual_contract.py")
text = p.read_text(encoding="utf-8")

old_escape = r'.split(Regex("[,;\\s]+"))'
new_escape = r'.split(Regex("[,;\\\\s]+"))'
if old_escape not in text:
    raise SystemExit("Escape alvo não encontrado no gerador")
text = text.replace(old_escape, new_escape, 1)

old_tests = '''        "InDriveMarkerlessLiveCardTest.kt",
        "core/CoreRideCardContractsTest.kt",'''
new_tests = '''        "InDriveMarkerlessLiveCardTest.kt",
        "FinalIntegrationChecklist9Test.kt",
        "InAppBubbleImmediateStateContractTest.kt",
        "core/CoreRideCardContractsTest.kt",'''
if old_tests not in text:
    raise SystemExit("Lista de testes antigos não encontrada no gerador")
text = text.replace(old_tests, new_tests, 1)

p.write_text(text, encoding="utf-8")
Path(__file__).unlink()
