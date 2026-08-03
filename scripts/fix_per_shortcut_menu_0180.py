from pathlib import Path

parts = [
    Path(__file__).with_name(f"fix_per_shortcut_menu_0180.py.part{index:02d}")
    for index in range(6)
]
missing = [str(path) for path in parts if not path.is_file()]
if missing:
    raise FileNotFoundError(f"Missing 0.1.180 transformation parts: {missing}")
source = "".join(path.read_text(encoding="utf-8") for path in parts)
exec(compile(source, str(parts[0]), "exec"), globals(), globals())
