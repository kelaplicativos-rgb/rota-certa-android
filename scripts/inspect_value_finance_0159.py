from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "app/src/main/java/br/com/mapeiaia/rotacerta"

for name in (
    "PassengerValueFormatter.kt",
    "FinancialRepository.kt",
    "FinancialActivity.kt",
    "BubbleShortcutModule.kt",
):
    path = base / name
    print(f"===== BEGIN {name} =====")
    print(path.read_text(encoding="utf-8"))
    print(f"===== END {name} =====")

service = (base / "LiveRideAccessibilityService.kt").read_text(encoding="utf-8")
markers = (
    "copyPassengerValue159",
    "requestPassengerValueOcr159",
    "showPassengerValue",
    "lastPassengerValue",
    "CopyPassengerValue",
    "OpenFinance",
)
lines = service.splitlines()
for marker in markers:
    hits = [i for i, line in enumerate(lines) if marker in line]
    for hit in hits:
        start = max(0, hit - 35)
        end = min(len(lines), hit + 110)
        print(f"===== SERVICE SNIPPET {marker} line {hit + 1} =====")
        for index in range(start, end):
            print(f"{index + 1:05d}: {lines[index]}")
        print(f"===== END SERVICE SNIPPET {marker} =====")
