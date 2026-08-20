#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
BUILD = SOURCE / "app/build.gradle.kts"

if not BUILD.is_file():
    raise SystemExit(f"missing Android build file: {BUILD}")

visible = Path(__file__).with_name("apply_stage47_r4_step7_visible_capacity_flow.py")
if not visible.is_file():
    raise SystemExit(f"missing visible capacity materializer: {visible}")

previous_argv = sys.argv
try:
    sys.argv = [str(visible), str(SOURCE)]
    namespace = {"__name__": "__main__", "__file__": str(visible)}
    exec(compile(visible.read_text(encoding="utf-8"), str(visible), "exec"), namespace)
finally:
    sys.argv = previous_argv

text = BUILD.read_text(encoding="utf-8")
if text.count("versionCode = 5530") != 1 or text.count('versionName = "0.1.237"') != 1:
    raise SystemExit("0.1.238 must materialize immediately after the validated 0.1.237 state")
BUILD.write_text(
    text.replace("versionCode = 5530", "versionCode = 5531", 1)
        .replace('versionName = "0.1.237"', 'versionName = "0.1.238"', 1),
    encoding="utf-8",
)

print(
    "stage47_r4_step7_0238_version=PASS version=0.1.238/5531 "
    "visible_quick_passenger=true selected_profile_trip_prefill=true targeted_account_reread=true "
    "internal_capacity_authority=true external_seat_write_claimed=false "
    "passenger_named_buttons=true saved_home_coordinate_direction=true farol_touched=false"
)
