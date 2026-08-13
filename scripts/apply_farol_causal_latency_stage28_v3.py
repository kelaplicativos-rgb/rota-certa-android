#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

PATCH_ROOT = Path(__file__).resolve().parents[1]
V2 = PATCH_ROOT / "scripts/apply_farol_causal_latency_stage28_v2.py"
SERVICE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")

OLD = "val childrenStage28 = runCatching { parentStage28?.childCount }.getOrDefault(0).coerceIn(0, 8)"
NEW = "val childrenStage28 = runCatching { parentStage28?.childCount ?: 0 }.getOrDefault(0).coerceIn(0, 8)"


def fail(message: str) -> None:
    raise SystemExit(message)


def self_test() -> None:
    if not V2.is_file():
        fail(f"missing Stage28 v2 materializer: {V2}")
    v2 = V2.read_text(encoding="utf-8")
    if OLD not in (PATCH_ROOT / "scripts/apply_farol_causal_latency_stage28.py").read_text(encoding="utf-8"):
        fail("Stage28 nullable childCount anchor changed")
    if "stage28_v2_apply=passed" not in v2:
        fail("Stage28 v2 contract marker missing")
    print("stage28_v3_self_test=passed")
    print("nullable_child_count_fixed=true")
    print("behavior_change=none_except_null_to_zero")


def apply(root: Path) -> None:
    subprocess.run([sys.executable, str(V2), str(root)], check=True)
    service_path = root / SERVICE
    text = service_path.read_text(encoding="utf-8")
    if text.count(OLD) != 1:
        fail(f"Stage28 nullable childCount materialized anchor expected 1, found {text.count(OLD)}")
    text = text.replace(OLD, NEW, 1)
    service_path.write_text(text, encoding="utf-8")
    verify = service_path.read_text(encoding="utf-8")
    if verify.count(NEW) != 1 or OLD in verify:
        fail("Stage28 nullable childCount correction verification failed")
    print("stage28_v3_apply=passed")
    print("nullable_child_count_fixed=true")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_root", nargs="?", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    self_test()
    if args.self_test:
        return
    if args.source_root is None:
        fail("source_root required")
    apply(args.source_root.resolve())


if __name__ == "__main__":
    main()
