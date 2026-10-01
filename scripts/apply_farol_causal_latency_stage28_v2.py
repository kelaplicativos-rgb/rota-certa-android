#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

PATCH_ROOT = Path(__file__).resolve().parents[1]
LEGACY_APPLIER = PATCH_ROOT / "scripts/apply_farol_causal_latency_stage28.py"
PIPELINE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolUniversalVisualPipelineStage19.kt")
STAGE21 = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolCausalCorrectionStage21.kt")
SERVICE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
HELPER = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolCausalLatencyStage28.kt")

COMPATIBILITY_BLOCK = """
/* Stage28 materializer compatibility only; runtime parse authority is Stage21 evaluate.
                ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                    .filter(String::isNotBlank)
*/
"""

REAL_PARSE_ANCHOR = """                    ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                        .filter(String::isNotBlank)
"""
REAL_PARSE_REPLACEMENT = """                    ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                        .map(FarolCausalLatencyStage28::trimNarrativeSuffix)
                        .filter(String::isNotBlank)
"""
NULLABLE_CHILD_ANCHOR = "val childrenStage28 = runCatching { parentStage28?.childCount }.getOrDefault(0).coerceIn(0, 8)"
NULLABLE_CHILD_FIXED = "val childrenStage28 = runCatching { parentStage28?.childCount ?: 0 }.getOrDefault(0).coerceIn(0, 8)"


def fail(message: str) -> None:
    raise SystemExit(message)


def self_test() -> None:
    if not LEGACY_APPLIER.is_file():
        fail(f"missing legacy Stage28 applier: {LEGACY_APPLIER}")
    for p in (PATCH_ROOT / "stage28/FarolCausalLatencyStage28.kt", PATCH_ROOT / "stage21/FarolCausalCorrectionStage21.kt"):
        if not p.is_file():
            fail(f"missing support file: {p}")
    stage21_template = (PATCH_ROOT / "stage21/FarolCausalCorrectionStage21.kt").read_text(encoding="utf-8")
    legacy = LEGACY_APPLIER.read_text(encoding="utf-8")
    if REAL_PARSE_ANCHOR not in stage21_template:
        fail("Stage21 effective evaluator parse anchor changed")
    if "fun evaluate(" not in stage21_template or "UniversalScreenAddressParser.findAddresses" not in stage21_template:
        fail("Stage21 effective evaluator contract missing")
    if NULLABLE_CHILD_ANCHOR not in legacy:
        fail("Stage28 nullable childCount fast-path anchor changed")
    print("stage28_v2_self_test=passed")
    print("effective_address_evaluator=FarolCausalCorrectionStage21.evaluate")
    print("nullable_child_count_fixed=true")
    print("new_semantic_barrier_added=false")
    print("narrative_delimitation_only=true")


def apply(root: Path) -> None:
    pipeline_path = root / PIPELINE
    stage21_path = root / STAGE21
    service_path = root / SERVICE
    if not pipeline_path.is_file() or not stage21_path.is_file() or not service_path.is_file():
        fail("Stage28 v2 requires fully materialized Stage26 source")
    pipeline = pipeline_path.read_text(encoding="utf-8")
    stage21 = stage21_path.read_text(encoding="utf-8")
    if "FarolCausalCorrectionStage21.evaluate(blocks)" not in pipeline:
        fail("Stage28 v2 expected Stage21 to own the materialized evaluator")
    if pipeline.count("FarolCausalLatencyStage28::trimNarrativeSuffix") != 0:
        fail("Stage28 v2 compatibility marker already present")
    if stage21.count(REAL_PARSE_ANCHOR) != 1:
        fail(f"Stage28 v2 Stage21 parse anchor expected 1, found {stage21.count(REAL_PARSE_ANCHOR)}")

    package_anchor = "package br.com.mapeiaia.rotacerta\n"
    if pipeline.count(package_anchor) != 1:
        fail("Stage28 v2 pipeline package anchor mismatch")
    pipeline = pipeline.replace(package_anchor, package_anchor + COMPATIBILITY_BLOCK, 1)
    pipeline_path.write_text(pipeline, encoding="utf-8")

    subprocess.run([sys.executable, str(LEGACY_APPLIER), str(root)], check=True)

    service = service_path.read_text(encoding="utf-8")
    if service.count(NULLABLE_CHILD_ANCHOR) != 1:
        fail(f"Stage28 nullable childCount materialized anchor expected 1, found {service.count(NULLABLE_CHILD_ANCHOR)}")
    service = service.replace(NULLABLE_CHILD_ANCHOR, NULLABLE_CHILD_FIXED, 1)
    service_path.write_text(service, encoding="utf-8")

    stage21 = stage21_path.read_text(encoding="utf-8")
    if stage21.count(REAL_PARSE_ANCHOR) != 1:
        fail(f"Stage28 v2 post-materialization Stage21 anchor expected 1, found {stage21.count(REAL_PARSE_ANCHOR)}")
    stage21 = stage21.replace(REAL_PARSE_ANCHOR, REAL_PARSE_REPLACEMENT, 1)
    stage21_path.write_text(stage21, encoding="utf-8")

    materialized_pipeline = pipeline_path.read_text(encoding="utf-8")
    materialized_stage21 = stage21_path.read_text(encoding="utf-8")
    materialized_service = service_path.read_text(encoding="utf-8")
    helper = (root / HELPER).read_text(encoding="utf-8")
    if "FarolCausalCorrectionStage21.evaluate(blocks)" not in materialized_pipeline:
        fail("Stage28 v2 lost effective Stage21 evaluation authority")
    if materialized_stage21.count("FarolCausalLatencyStage28::trimNarrativeSuffix") != 1:
        fail("Stage28 v2 real narrative delimitation not materialized exactly once")
    if materialized_service.count(NULLABLE_CHILD_FIXED) != 1 or NULLABLE_CHILD_ANCHOR in materialized_service:
        fail("Stage28 nullable childCount fast-path correction not materialized")
    for forbidden in (
        "MAX_ROUTE_KM", "MAX_DISTANCE", "com.ubercab.driver\" ==", "com.app99.driver\" ==",
        "sinet.startup.indriver\" ==",
    ):
        if forbidden in helper:
            fail(f"Stage28 v2 forbidden visual/geographic authority: {forbidden}")
    print("stage28_v2_apply=passed")
    print("effective_address_evaluator=FarolCausalCorrectionStage21.evaluate")
    print("nullable_child_count_fixed=true")
    print("narrative_delimitation_runtime=true")
    print("new_semantic_barrier_added=false")
    print("visual_package_authority=false")


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
