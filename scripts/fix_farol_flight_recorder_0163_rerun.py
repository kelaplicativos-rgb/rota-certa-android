from pathlib import Path
import runpy
import sys

root = sys.argv[1] if len(sys.argv) > 1 else "."
main_script = Path(__file__).with_name("fix_farol_flight_recorder_0163.py")
sys.argv = [str(main_script), root]
runpy.run_path(str(main_script), run_name="__main__")
