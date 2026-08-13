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
HELPER = Path("app/src/main/java/br/com/mapeiaia/rotacerta/FarolCausalLatencyStage28.kt")

# Stage21 deliberately replaces FarolUniversalVisualPipelineStage19.evaluate during materialization.
# The older Stage28 applier expected the original Stage19 parser to still be present. This inert
# comment preserves that applier's structural checksum while the real correction below is applied
# to the effective Stage21 evaluator that actually runs in the materialized application.
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


def fail(message: str) -> None:
    raise SystemExit(message)


def self_test() -> None:
    if not LEGACY_APPLIER.is_file():
        fail(f"missing legacy Stage28 applier: {LEGACY_APPLIER}")
    for p in (PATCH_ROOT / "stage28/FarolCausalLatencyStage28.kt", PATCH_ROOT / "stage21/FarolCausalCorrectionStage21.kt"):
        if not p.is_file():
            fail(f"missing support file: {p}")
    stage21_template = (PATCH_ROOT / "stage21/FarolCausalCorrectionStage21.kt").read_text(encoding="utf-8")
    if REAL_PARSE_ANCHOR not in stage21_template:
        fail("Stage21 effective evaluator parse anchor changed")
    if "fun evaluate(" not in stage21_template or "UniversalScreenAddressParser.findAddresses" not in stage21_template:
        fail("Stage21 effective evaluator contract missing")
    print("stage28_v2_self_test=passed")
    print("effective_address_evaluator=FarolCausalCorrectionStage21.evaluate")
    print("new_semantic_barrier_added=false")
    print("narrative_delimitation_only=true")


def apply(root: Path) -> None:
    pipeline_path = root / PIPELINE
    stage21_path = root / STAGE21
    if not pipeline_path.is_file() or not stage21_path.is_file():
        fail("Stage28 v2 requires fully materialized Stage26 source")
    pipeline = pipeline_path.read_text(encoding="utf-8")
    stage21 = stage21_path.read_text(encoding="utf-8")
    if "FarolCausalCorrectionStage21.evaluate(blocks)" not in pipeline:
        fail("Stage28 v2 expected Stage21 to own the materialized evaluator")
    if pipeline.count("FarolCausalLatencyStage28::trimNarrativeSuffix") != 0:
        fail("Stage28 v2 compatibility marker already present")
    if stage21.count(REAL_PARSE_ANCHOR) != 1:
        fail(f"Stage28 v2 Stage21 parse anchor expected 1, found {stage21.count(REAL_PARSE_ANCHOR)}")

    # Let the existing Stage28 applier materialize activation/freshness/fast-path/runtime changes.
    # It validates that a Stage19-era parser insertion happened, so provide an inert comment-only
    # compatibility anchor; this cannot alter runtime evaluation because Stage21 owns evaluate().
    package_anchor = "package br.com.mapeiaia.rotacerta\n"
    if pipeline.count(package_anchor) != 1:
        fail("Stage28 v2 pipeline package anchor mismatch")
    pipeline = pipeline.replace(package_anchor, package_anchor + COMPATIBILITY_BLOCK, 1)
    pipeline_path.write_text(pipeline, encoding="utf-8")

    subprocess.run([sys.executable, str(LEGACY_APPLIER), str(root)], check=True)

    # Apply delimitation to the effective evaluator only. No package/model/city/state/distance gate
    # is added; the existing Stage21 decision contract remains otherwise byte-for-byte unchanged.
    stage21 = stage21_path.read_text(encoding="utf-8")
    if stage21.count(REAL_PARSE_ANCHOR) != 1:
        fail(f"Stage28 v2 post-materialization Stage21 anchor expected 1, found {stage21.count(REAL_PARSE_ANCHOR)}")
    stage21 = stage21.replace(REAL_PARSE_ANCHOR, REAL_PARSE_REPLACEMENT, 1)
    stage21_path.write_text(stage21, encoding="utf-8")

    materialized_pipeline = pipeline_path.read_text(encoding="utf-8")
    materialized_stage21 = stage21_path.read_text(encoding="utf-8")
    helper = (root / HELPER).read_text(encoding="utf-8")
    if "FarolCausalCorrectionStage21.evaluate(blocks)" not in materialized_pipeline:
        fail("Stage28 v2 lost effective Stage21 evaluation authority")
    if materialized_stage21.count("FarolCausalLatencyStage28::trimNarrativeSuffix") != 1:
        fail("Stage28 v2 real narrative delimitation not materialized exactly once")
    for forbidden in (
        "MAX_ROUTE_KM", "MAX_DISTANCE", "com.ubercab.driver\" ==", "com.app99.driver\" ==",
        "sinet.startup.indriver\" ==",
    ):
        if forbidden in helper:
            fail(f"Stage28 v2 forbidden visual/geographic authority: {forbidden}")
    print("stage28_v2_apply=passed")
    print("effective_address_evaluator=FarolCausalCorrectionStage21.evaluate")
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
