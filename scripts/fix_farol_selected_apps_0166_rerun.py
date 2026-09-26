from pathlib import Path
import subprocess
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
script = Path(__file__).with_name("fix_farol_selected_apps_0166.py")
subprocess.run([sys.executable, str(script), str(root)], check=True)
