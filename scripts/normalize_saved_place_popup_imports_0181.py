from pathlib import Path

service = Path.cwd() / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
text = service.read_text(encoding="utf-8")
linear = "import android.widget.LinearLayout\n"
text_view = "import android.widget.TextView\n"

if text.count(linear) != 1 or text.count(text_view) != 1:
    raise RuntimeError("Unexpected widget import structure before 0.1.181 popup transform")

# The cumulative source may insert other widget imports between LinearLayout and
# TextView. Keep the imports deterministic so the strict 0.1.181 transformer
# can add Button/EditText without depending on those unrelated imports.
text = text.replace(text_view, "", 1)
text = text.replace(linear, linear + text_view, 1)

menu_view = "    private var overlayMenuView: LinearLayout? = null\n"
menu_params = "    private var overlayMenuParams: WindowManager.LayoutParams? = null\n"
menu_anchor = menu_view + menu_params

if text.count(menu_anchor) != 1:
    if text.count(menu_view) > 1 or text.count(menu_params) > 1:
        raise RuntimeError("Unexpected duplicated overlay menu fields before 0.1.181 popup transform")
    text = text.replace(menu_view, "", 1)
    text = text.replace(menu_params, "", 1)
    field_anchor = "    private var overlayView: TextView? = null\n"
    if text.count(field_anchor) != 1:
        raise RuntimeError("Missing overlayView field for deterministic popup field insertion")
    text = text.replace(field_anchor, field_anchor + menu_anchor, 1)

service.write_text(text, encoding="utf-8")

# Bounded structural evidence in the workflow log. This is emitted only during
# build validation and does not add runtime logging to the Android application.
lines = text.splitlines()
for needle in (
    "private var overlayView",
    "private var overlayMenuView",
    "private var overlayMenuParams",
    "private fun saveCurrentPlaceFromBubble",
    "private fun removeOverlay",
):
    for index, line in enumerate(lines):
        if needle in line:
            start = max(0, index - 2)
            end = min(len(lines), index + 8)
            print(f"--- 0.1.181 source context: {needle} ---")
            for number in range(start, end):
                print(f"{number + 1}: {lines[number]}")
            break
