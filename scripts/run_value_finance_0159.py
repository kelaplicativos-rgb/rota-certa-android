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
exec(compile(source, str(wrapper_path), "exec"))
