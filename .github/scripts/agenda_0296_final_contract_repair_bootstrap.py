#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / ".github/scripts/agenda_0296_final_contract_repair.py"
text = TARGET.read_text(encoding="utf-8")

patches = [
    (
        "'''        seats: pendingBooking.seats,\\n      };\\n'''",
        "'''      seats: pendingBooking.seats,\\n    };\\n'''",
        "confirmed booking object indentation",
    ),
    (
        "'''      $(\"confirmationText\").textContent = body.replayed\\n        ? \"✅ Esta reserva já estava confirmada. Nenhuma duplicata foi criada.\"\\n        : `✅ Reserva confirmada para ${pendingBooking.seats} lugar(es).`;\\n'''",
        "'''    $(\"confirmationText\").textContent = body.replayed\\n      ? \"✅ Esta reserva já estava confirmada. Nenhuma duplicata foi criada.\"\\n      : `✅ Reserva confirmada para ${pendingBooking.seats} lugar(es).`;\\n'''",
        "confirmation message indentation",
    ),
]

for old, new, label in patches:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one source marker, got {count}")
    text = text.replace(old, new, 1)

TARGET.write_text(text, encoding="utf-8")
print("agenda_0296_final_contract_repair_bootstrap=PASS")
subprocess.run([sys.executable, str(TARGET)], cwd=ROOT, check=True)
