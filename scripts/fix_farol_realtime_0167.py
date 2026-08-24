from base64 import b64decode
from pathlib import Path
from zlib import decompress

payload = Path(__file__).with_name("farol_realtime_0167.payload")
if not payload.exists():
    raise SystemExit("payload do núcleo em tempo real 0.1.167 não encontrado")
source = decompress(b64decode(payload.read_text(encoding="ascii").strip())).decode("utf-8")
exec(compile(source, __file__, "exec"))
