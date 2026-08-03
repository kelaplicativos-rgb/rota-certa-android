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
service.write_text(text, encoding="utf-8")
