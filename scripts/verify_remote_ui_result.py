#!/usr/bin/env python3
"""Require successful on-device button regressions before APK publication."""
import argparse
import json
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--proof", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    args.report.unlink(missing_ok=True)
    log = args.log.read_text()
    if not re.search(r"^OK \(4 tests\)\s*$", log, re.MULTILINE) or re.search(
        r"FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=", log,
    ):
        raise RuntimeError("Remote access UI instrumentation did not pass all four tests")
    proof = json.loads(args.proof.read_text())
    args.report.write_text(json.dumps({
        "source_commit": proof["source_commit"],
        "apk_sha256": proof["candidate"]["sha256"],
        "ui_tests_passed": 4,
        "scope": "Android 16 emulator; missing access, provisioning error, in-flight request, provisioned access",
        "physical_device_connection": "not_tested",
    }, indent=2) + "\n")
    print("PASS: all four remote access button tests passed on the emulator")


if __name__ == "__main__":
    main()
