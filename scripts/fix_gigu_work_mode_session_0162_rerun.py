from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/br/com/mapeiaia/rotacerta"
service_path = main / "LiveRideAccessibilityService.kt"
work_mode_path = main / "WorkModeSession0162.kt"

service = service_path.read_text(encoding="utf-8")
scan_anchor = """    private fun startContinuousScan() {
        // event_driven_farol_0_1_162
"""
scan_replacement = """    private fun startContinuousScan() {
        // event_driven_farol_0_1_162
        // bubble_drag_scan_pause_0_1_116 — sem loop, portanto nenhum scan compete com o gesto.
"""
if service.count(scan_anchor) != 1:
    raise SystemExit(f"0.1.162 rerun scan anchor expected once, found {service.count(scan_anchor)}")
service = service.replace(scan_anchor, scan_replacement, 1)

resolver_anchor = """        val resolvedPackage0162 = DriverCardEventResolver0162.resolve(
"""
resolver_replacement = """        // Compatibilidade funcional: TransientOverlayPackagePolicy0161.shouldPreferSelectedRoot
        // agora é aplicada pelo resolvedor estrito da sessão imutável 0.1.162.
        val resolvedPackage0162 = DriverCardEventResolver0162.resolve(
"""
if service.count(resolver_anchor) != 1:
    raise SystemExit(f"0.1.162 rerun resolver anchor expected once, found {service.count(resolver_anchor)}")
service = service.replace(resolver_anchor, resolver_replacement, 1)
service_path.write_text(service, encoding="utf-8")

work_mode = work_mode_path.read_text(encoding="utf-8")n