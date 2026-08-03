from pathlib import Path

wrapper_path = Path(__file__).with_name("fix_per_shortcut_menu_0180.py")
wrapper_source = wrapper_path.read_text(encoding="utf-8")
exec_anchor = 'exec(compile(source, str(parts[0]), "exec"), globals(), globals())'
if wrapper_source.count(exec_anchor) != 1:
    raise RuntimeError("Unexpected 0.1.180 wrapper execution anchor")

injection = r'''
# The inherited 0.1.179 shortcut touch listener differs slightly across the
# cumulative source chain. Replace only the listener inside shortcutBubble by
# structural anchors instead of requiring the whole old block byte-for-byte.
import re as _wrapper_re0180

_touch_helper_source_0180 = r"""def replace_shortcut_touch_0180(relative: str, new: str) -> None:
    text = read(relative)
    function_marker = "    private fun shortcutBubble("
    function_start = text.find(function_marker)
    if function_start < 0:
        raise RuntimeError(f"{relative}: shortcutBubble function not found")
    start_token = "        var downX = 0f\n        var downY = 0f\n"
    start = text.find(start_token, function_start)
    if start < 0:
        raise RuntimeError(f"{relative}: shortcut touch start not found")
    end_token = "                else -> true\n            }\n        }"
    end = text.find(end_token, start)
    if end < 0:
        raise RuntimeError(f"{relative}: shortcut touch end not found")
    end += len(end_token)
    write(relative, text[:start] + new + text[end:])
"""
_helper_anchor_0180 = "\n\n# Version.\n"
if source.count(_helper_anchor_0180) != 1:
    raise RuntimeError("Unexpected 0.1.180 helper insertion anchor")
source = source.replace(
    _helper_anchor_0180,
    "\n\n" + _touch_helper_source_0180 + _helper_anchor_0180,
    1,
)

_touch_statement_pattern_0180 = _wrapper_re0180.compile(
    r'replace_once\(\n'
    r'    overlay,\n'
    r'    (?P<old>"""        var downX = 0f\n        var downY = 0f\n        var longPressTriggered = false.*?"""),\n'
    r'    (?P<new>"""        var downX = 0f\n        var downY = 0f\n        var downEventTime = 0L.*?"""),\n'
    r'\)\n',
    _wrapper_re0180.DOTALL,
)
_touch_matches_0180 = list(_touch_statement_pattern_0180.finditer(source))
if len(_touch_matches_0180) != 1:
    raise RuntimeError(
        f"Unexpected 0.1.180 shortcut touch transform count: {len(_touch_matches_0180)}"
    )
_touch_match_0180 = _touch_matches_0180[0]
_touch_call_0180 = (
    "replace_shortcut_touch_0180(\n"
    "    overlay,\n"
    f"    {_touch_match_0180.group('new')},\n"
    ")\n"
)
source = (
    source[: _touch_match_0180.start()]
    + _touch_call_0180
    + source[_touch_match_0180.end() :]
)
'''

wrapper_source = wrapper_source.replace(
    exec_anchor,
    injection + "\n" + exec_anchor,
    1,
)
exec(compile(wrapper_source, str(wrapper_path), "exec"), globals(), globals())

# The 0.1.179 contract still looked for a delayed 1.5-second Runnable. In
# 0.1.180 the hold is intentionally classified only on ACTION_UP so it cannot
# conflict with the bounded triple-tap sequence.
contract_path = Path.cwd() / "app/src/test/java/br/com/mapeiaia/rotacerta/AuthorizedAppsCards146ContractTest.kt"
contract_text = contract_path.read_text(encoding="utf-8")
old_contract = 'assertTrue(overlay.contains("postDelayed(longPressAction, ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS)"))'
new_contract = 'assertTrue(overlay.contains("heldMillis >= ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS"))'
if contract_text.count(old_contract) != 1:
    raise RuntimeError("Unexpected AuthorizedAppsCards146 hold contract")
contract_path.write_text(contract_text.replace(old_contract, new_contract, 1), encoding="utf-8")
