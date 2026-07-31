from base64 import b64decode
from pathlib import Path
from zlib import decompress

parts = sorted(Path(__file__).parent.glob("farol_visual_0168.payload.part*"))
if not parts:
    raise SystemExit("payload visual 0.1.168 nao encontrado")
encoded = "".join(part.read_text(encoding="ascii") for part in parts)
source = decompress(b64decode(encoded)).decode("utf-8")
exec(compile(source, __file__, "exec"))
