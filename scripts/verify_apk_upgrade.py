#!/usr/bin/env python3
"""Reject an APK that cannot replace the confirmed installed baseline."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inspect_apk(path, tools):
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, "Corrupt APK ZIP.")
        require("AndroidManifest.xml" in archive.namelist() and "classes.dex" in archive.namelist(),
                "Missing APK manifest or executable code.")
        native_files = [name for name in archive.namelist() if name.startswith("lib/")]
    badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(path)], text=True)
    package_line = next((line for line in badging.splitlines() if line.startswith("package:")), "")
    package = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    signature = subprocess.check_output(
        [str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(path)], text=True)
    signers = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})", signature)
    require(signers, "No verified APK signing certificate.")
    sdk = re.search(r"^sdkVersion:'(\d+)'", badging, re.MULTILINE)
    require(sdk, "APK minimum SDK is missing.")
    return {
        "application_id": package["name"], "version_name": package["versionName"],
        "version_code": int(package["versionCode"]), "signers": sorted(x.lower() for x in signers),
        "min_sdk": int(sdk.group(1)), "native_files": native_files,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "size_bytes": path.stat().st_size,
    }


def verify_metadata(baseline, candidate, contract, version, version_code):
    require(baseline["sha256"] == contract["apk_sha256"], "Wrong reference APK hash.")
    require(baseline["application_id"] == contract["application_id"]
            and baseline["version_code"] == contract["version_code"]
            and baseline["version_name"] == contract["version_name"], "Wrong installed baseline identity.")
    require(baseline["signers"] == [contract["signer_sha256"]], "Reference signing certificate changed.")
    require(candidate["application_id"] == baseline["application_id"], "Package changed; this would install a different app.")
    require(candidate["signers"] == baseline["signers"], "Signature changed; Android cannot update the installed app.")
    require(candidate["version_code"] > baseline["version_code"], "Update must increase versionCode; no downgrade or same-version delivery.")
    require(candidate["version_name"] == version and candidate["version_code"] == version_code,
            "APK differs from the intended release version.")
    require(candidate["min_sdk"] <= contract["device_api"], "Update excludes the user's Android version.")
    require("lib/arm64-v8a/liborganicmaps.so" in candidate["native_files"], "Samsung ARM64 map library is missing.")


def verify(baseline_path, candidate_path, contract, tools, version, version_code, commit):
    baseline = inspect_apk(baseline_path, tools)
    candidate = inspect_apk(candidate_path, tools)
    verify_metadata(baseline, candidate, contract, version, version_code)
    with zipfile.ZipFile(candidate_path) as archive:
        require(any(commit.encode() in archive.read(name) for name in archive.namelist() if name.endswith(".dex")),
                "APK does not contain the validated source commit.")
        history = json.loads(archive.read("assets/release_history.json"))
        require(sum(item.get("version") == version and item.get("build") == version_code
                    for item in history["releases"]) == 1, "APK release history does not match its version.")
        for asset in ("assets/World.mwm", "assets/WorldCoasts.mwm"):
            require(asset in archive.namelist(), f"Offline map asset missing: {asset}")
    return {
        "source_commit": commit, "baseline": baseline, "candidate": candidate,
        "update_compatible": True, "same_package": True, "same_signer": True,
        "version_code_increased": True, "uninstall_required": False,
        "device_test": "not_run",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--contract", type=Path, default=Path("app/update-baseline.json"))
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    # Never leave an old successful proof after a failed validation attempt.
    args.report.unlink(missing_ok=True)
    require(re.fullmatch(r"[0-9a-f]{40}", args.commit), "Expected full source commit SHA.")
    report = verify(args.baseline, args.candidate, json.loads(args.contract.read_text()),
                    args.build_tools, args.version, args.version_code, args.commit)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Update compatible: {report['candidate']['version_name']} ({report['candidate']['version_code']})")
    print(f"Source: {args.commit}\nAPK SHA-256: {report['candidate']['sha256']}")


if __name__ == "__main__":
    main()
