from pathlib import Path

path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCarSessionKeeper0552.kt")
text = path.read_text()

replacements = {
    "if (current?.state.isStickyFailure()) {": "if (current != null && current.state.isStickyFailure()) {",
    "current?.state.isStickyFailure() -> current.copy(": "current != null && current.state.isStickyFailure() -> current.copy(",
    "val observation = if (current?.state.isStickyFailure()) {": "val observation = if (current != null && current.state.isStickyFailure()) {",
}

changed = False
for old, new in replacements.items():
    if old in text:
        text = text.replace(old, new)
        changed = True

if "current?.state.isStickyFailure()" in text:
    raise SystemExit("nullable sticky guard remains; refusing to continue")

required = [
    "if (current != null && current.state.isStickyFailure()) {",
    "current != null && current.state.isStickyFailure() -> current.copy(",
]
missing = [marker for marker in required if marker not in text]
if missing:
    raise SystemExit("required smart-cast markers missing: " + ", ".join(missing))

if changed:
    path.write_text(text)
    print("SessionKeeper sticky guards fixed")
else:
    print("SessionKeeper sticky guards already fixed")
