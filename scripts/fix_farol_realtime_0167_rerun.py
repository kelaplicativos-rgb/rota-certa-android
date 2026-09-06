from pathlib import Path
import subprocess
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
subprocess.run(
    [sys.executable, str(Path(__file__).with_name("fix_farol_realtime_0167.py")), str(root)],
    check=True,
)

service = (root / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").read_text(encoding="utf-8")
recorder = (root / "app/src/main/java/br/com/mapeiaia/rotacerta/FarolFlightRecorder0163.kt").read_text(encoding="utf-8")
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")

assert service.count("farolRealtimeEventGate0167.shouldCollect") == 1
assert service.count("bounded_allocation_light_accessibility_tree_0_1_167") == 1
assert service.count("MAX_ACCESSIBILITY_NODES_0167 = 768") == 1
assert recorder.count("checkpoint_snapshot_fully_off_main_0_1_167") == 1
assert 'versionName = "0.1.167"' in build
assert "versionCode = 5280" in build
print("Rerun 0.1.167 aprovado: aplicação idempotente e marcadores únicos")
