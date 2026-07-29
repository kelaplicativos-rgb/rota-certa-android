from __future__ import annotations

import base64
import re
import zlib
from pathlib import Path

wrapper_path = Path(__file__).with_name("fix_value_finance_0159.py")
wrapper = wrapper_path.read_text(encoding="utf-8")
match = re.search(r'b64decode\("([A-Za-z0-9+/=]+)"\)', wrapper, flags=re.S)
if match is None:
    raise SystemExit("0.1.159 payload not found")

source = zlib.decompress(base64.b64decode(match.group(1))).decode("utf-8")
source = source.replace(
    '        assertFalse("Coletor não pode voltar", "BlaBlaCarCollector" in catalog || "OpenCollector" in catalog)\n',
    "",
)
source = source.replace("import java.text.NumberFormat\n", "")
old_currency = r'''    fun formatCurrency(amountCents: Long): String = NumberFormat
        .getCurrencyInstance(Locale("pt", "BR"))
        .format(amountCents / 100.0)
        .replace('\u00A0', ' ')'''
new_currency = r'''    fun formatCurrency(amountCents: Long): String {
        val absolute = kotlin.math.abs(amountCents)
        val reais = absolute / 100L
        val cents = (absolute % 100L).toString().padStart(2, '0')
        val grouped = reais.toString().reversed().chunked(3).joinToString(".").reversed()
        val sign = if (amountCents < 0L) "-" else ""
        return "${sign}R$ $grouped,$cents"
    }'''
if old_currency not in source:
    raise SystemExit("0.1.159 currency formatter anchor not found")
source = source.replace(old_currency, new_currency)
exec(compile(source, str(wrapper_path), "exec"))

root = wrapper_path.resolve().parents[1]
for relative in (
    "app/src/main/java/br/com/mapeiaia/rotacerta/PassengerValueFormatter.kt",
    "app/src/test/java/br/com/mapeiaia/rotacerta/PassengerValueFormatterTest.kt",
):
    path = root / relative
    print(f"===== BEGIN {relative} =====")
    print(path.read_text(encoding="utf-8"))
    print(f"===== END {relative} =====")
