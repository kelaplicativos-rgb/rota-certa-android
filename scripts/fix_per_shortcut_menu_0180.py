from pathlib import Path

parts = [
    Path(__file__).with_name(f"fix_per_shortcut_menu_0180.py.part{index:02d}")
    for index in range(6)
]
missing = [str(path) for path in parts if not path.is_file()]
if missing:
    raise FileNotFoundError(f"Missing 0.1.180 transformation parts: {missing}")
source = "".join(path.read_text(encoding="utf-8") for path in parts)

# The cumulative 0.1.179 source may expose the two overlay callbacks either
# with BubbleShortcutSpec or with the already-resolved entry type. Keep the
# transformation strict about names/count while accepting both safe forms.
source = source.replace("from pathlib import Path\n", "from pathlib import Path\nimport re\n", 1)
old_helper = '''def replace_all_exact(relative: str, old: str, new: str, expected: int) -> None:
    text = read(relative)
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{relative}: expected {expected} occurrences, found {count}: {old[:140]!r}")
    write(relative, text.replace(old, new))
'''
new_helper = '''def replace_all_exact(relative: str, old: str, new: str, expected: int) -> None:
    text = read(relative)
    count = text.count(old)
    if count == 0 and "onShortcut:" in old and "onShortcutLongPress:" in old:
        callback_pair = re.compile(
            r"(?m)^        onShortcut: \\([^\\n]+\\) -> Unit,\\n"
            r"        onShortcutLongPress: \\([^\\n]+\\) -> Unit,"
        )
        updated, regex_count = callback_pair.subn(new, text)
        if regex_count == expected:
            write(relative, updated)
            return
        count = regex_count
    if count != expected:
        raise RuntimeError(f"{relative}: expected {expected} occurrences, found {count}: {old[:140]!r}")
    write(relative, text.replace(old, new))
'''
if source.count(old_helper) != 1:
    raise RuntimeError("Unexpected 0.1.180 transformer helper structure")
source = source.replace(old_helper, new_helper, 1)
exec(compile(source, str(parts[0]), "exec"), globals(), globals())
