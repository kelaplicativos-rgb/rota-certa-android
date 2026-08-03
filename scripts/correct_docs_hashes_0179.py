from pathlib import Path

path = Path("docs/PROJECT_STATUS.md")
text = path.read_text(encoding="utf-8")
replacements = {
    "081c9651f0733213c364d6f9502788c6fc4e6c443c62c1d02439b2bd58215713":
        "081cffc8837b8544c69d1422f21286ab0da3ffb483f378810d5feb70d565f52c",
    "71d8e08bbb41261cdd16a9a90ee3106276867c99e25a92254663a01747f94a26":
        "71d8a6c52c5bc7f0a1a6eb0ca7484c292f4574da90838c061ba9e1e98fb80ca8",
}
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f"expected documented hash not found: {old}")
    text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("documented hashes corrected")
