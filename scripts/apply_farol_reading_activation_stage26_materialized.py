#!/usr/bin/env python3
"""Stage26 materialization entry point.

Preserves the Stage23 OCR freshness helper while replacing only the Stage23
accessibility admission/collector section. The original Stage26 transformer is
kept as the implementation source; this entry point narrows that one section
boundary before invoking it, so the compiled chain remains exactly:
Stage18 -> Stage19 -> Stage20 -> Stage21 -> Stage23 -> Stage26.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys

MODULE_PATH = Path(__file__).with_name("apply_farol_reading_activation_stage26.py")
spec = importlib.util.spec_from_file_location("stage26_impl", MODULE_PATH)
if spec is None or spec.loader is None:
    raise SystemExit("Stage26 materializer could not load implementation")
impl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(impl)

_original_replace_section = impl.replace_section


def _replace_section_preserving_stage23_ocr(text: str, start: str, end: str, replacement: str, label: str) -> str:
    if label == "Stage26 gate/precollect/collector":
        expected_old_end = "    private fun requestUniversalScreenshotStage19("
        if end != expected_old_end:
            raise SystemExit(f"Stage26 unexpected original boundary: {end!r}")
        end = "    private fun isStage23OcrDemandFresh("
        if text.count(end) != 1:
            raise SystemExit(f"Stage26 must preserve exactly one Stage23 OCR freshness helper, found {text.count(end)}")
    return _original_replace_section(text, start, end, replacement, label)


impl.replace_section = _replace_section_preserving_stage23_ocr

if __name__ == "__main__":
    impl.main()
