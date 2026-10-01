from pathlib import Path

original = Path(__file__).with_name("fix_saved_place_popup_0181.py")
source = original.read_text(encoding="utf-8")

old_widget_imports = '''replace_once(
    service,
    "import android.widget.LinearLayout\\nimport android.widget.TextView\\n",
    "import android.widget.Button\\nimport android.widget.EditText\\nimport android.widget.LinearLayout\\nimport android.widget.TextView\\n",
)
'''
new_widget_imports = '''replace_once(
    service,
    "import android.widget.LinearLayout\\n",
    "import android.widget.Button\\nimport android.widget.EditText\\nimport android.widget.LinearLayout\\n",
)
'''
if source.count(old_widget_imports) != 1:
    raise RuntimeError("Unexpected 0.1.181 widget import transform structure")
source = source.replace(old_widget_imports, new_widget_imports, 1)

old_field_insert = '''replace_once(
    service,
    "    private var overlayMenuView: LinearLayout? = null\\n    private var overlayMenuParams: WindowManager.LayoutParams? = null\\n",
    "    private var overlayMenuView: LinearLayout? = null\\n    private var overlayMenuParams: WindowManager.LayoutParams? = null\\n    private var savedPlacePopupView: LinearLayout? = null\\n",
)
'''
new_field_insert = '''service_text = read(service)
saved_popup_field = "    private var savedPlacePopupView: LinearLayout? = null\\n"
if saved_popup_field not in service_text:
    overlay_field = "    private var overlayView: TextView? = null\\n"
    if service_text.count(overlay_field) != 1:
        raise RuntimeError("LiveRideAccessibilityService.kt: missing unique overlayView field")
    service_text = service_text.replace(overlay_field, overlay_field + saved_popup_field, 1)
    write(service, service_text)
'''
if source.count(old_field_insert) != 1:
    raise RuntimeError("Unexpected 0.1.181 popup field transform structure")
source = source.replace(old_field_insert, new_field_insert, 1)

old_function_apply = "replace_once(service, old_save, new_save)"
new_function_apply = '''service_text = read(service)
save_start_marker = "    private fun saveCurrentPlaceFromBubble("
save_start = service_text.find(save_start_marker)
if save_start < 0 or service_text.find(save_start_marker, save_start + 1) >= 0:
    raise RuntimeError("LiveRideAccessibilityService.kt: expected one saveCurrentPlaceFromBubble function")
save_end = service_text.find("\\n    private fun ", save_start + len(save_start_marker))
if save_end < 0:
    raise RuntimeError("LiveRideAccessibilityService.kt: could not locate end of saveCurrentPlaceFromBubble")
write(service, service_text[:save_start] + new_save + service_text[save_end:])'''
if source.count(old_function_apply) != 1:
    raise RuntimeError("Unexpected 0.1.181 save function transform structure")
source = source.replace(old_function_apply, new_function_apply, 1)

exec(compile(source, str(original), "exec"), globals(), globals())
