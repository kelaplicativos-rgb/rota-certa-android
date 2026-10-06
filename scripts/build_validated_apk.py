#!/usr/bin/env python3
"""Build once, validate, and bind the debug APK to a clean source commit."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile


def output(command, root):
    return subprocess.check_output(command, cwd=root, text=True).strip()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def source_identity(root):
    require(not output(["git", "status", "--porcelain", "--untracked-files=all"], root),
            "Source must be committed and clean before and after validation.")
    return {
        "commit": output(["git", "rev-parse", "HEAD"], root),
        "tree": output(["git", "rev-parse", "HEAD^{tree}"], root),
    }


def build_tools():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    require(sdk, "Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK.")
    tools = Path(sdk) / "build-tools" / "35.0.0"
    require((tools / "aapt").is_file() and (tools / "apksigner").is_file(),
            "Install Android build-tools;35.0.0.")
    return tools


def validate_reports(root):
    reports = sorted((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    require(reports, "No unit-test results were generated.")
    count = 0
    for report in reports:
        suite = ET.parse(report).getroot()
        require(int(suite.get("failures", "0")) == 0 and int(suite.get("errors", "0")) == 0,
                f"Unit-test failures in {report.name}.")
        count += int(suite.get("tests", "0")) - int(suite.get("skipped", "0"))
    require(count > 0, "No unit tests were executed.")
    lint = root / "app/build/reports/lint-results-debug.xml"
    require(lint.is_file(), "No Android lint results were generated.")
    issues = ET.parse(lint).getroot().findall("issue")
    require(not any(issue.get("severity") in ("Error", "Fatal") for issue in issues),
            "Android lint reported errors.")
    return count, len(issues)


def build(root):
    # A failed retry must never leave a previously validated artifact behind.
    destination = root / "app/build/validated-apk"
    if destination.exists():
        shutil.rmtree(destination)
    identity = source_identity(root)
    expected = os.environ.get("EXPECTED_SOURCE_SHA")
    require(not expected or expected == identity["commit"],
            "Checked-out commit does not match the workflow's expected source SHA.")
    tools = build_tools()
    config = (root / "app/build.gradle.kts").read_text()
    package = re.search(r'applicationId\s*=\s*"([^"]+)"', config).group(1)
    version = re.search(r'versionName\s*=\s*"([^"]+)"', config).group(1)
    version_code = re.search(r'versionCode\s*=\s*(\d+)', config).group(1)
    log = root / "build/validated-apk.log"
    # Keep test and lint reports: never run clean between validation and assembly.
    command = ["sh", "./gradlew", "clean", "testDebugUnitTest", "lintDebug", "assembleDebug",
               "--no-daemon", "--stacktrace", "--console=plain"]
    # A root clean task may remove build/; keep the live log outside the checkout.
    with tempfile.TemporaryFile(mode="w+", encoding="utf-8") as stream:
        with subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True) as process:
            for line in process.stdout:
                print(line, end="", flush=True)
                stream.write(line)
            exit_code = process.wait()
        stream.seek(0)
        log.parent.mkdir(parents=True, exist_ok=True)
        with log.open("w") as saved_log:
            shutil.copyfileobj(stream, saved_log)
    require(exit_code == 0, "Gradle failed; no validated APK will be published.")
    require(source_identity(root) == identity, "Source commit changed during the build.")
    tests, lint_issues = validate_reports(root)
    apk = root / "app/build/outputs/apk/debug/app-debug.apk"
    require(apk.is_file() and apk.stat().st_size > 0, "The debug APK is missing or empty.")
    with zipfile.ZipFile(apk) as archive:
        require(archive.testzip() is None, "The APK ZIP is corrupt.")
        require("AndroidManifest.xml" in archive.namelist() and "classes.dex" in archive.namelist(),
                "The APK does not contain its manifest and executable code.")
    signature = output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], root)
    badging = output([str(tools / "aapt"), "dump", "badging", str(apk)], root)
    package_line = next((line for line in badging.splitlines() if line.startswith("package:")), "")
    attributes = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    require(attributes.get("name") == package and attributes.get("versionName") == version
            and attributes.get("versionCode") == version_code,
            "APK package/version does not match the validated Gradle configuration.")
    require(source_identity(root) == identity, "Source changed during APK verification.")
    destination.mkdir(parents=True)
    final_apk = destination / "app-debug.apk"
    shutil.copyfile(apk, final_apk)
    checksum = hashlib.sha256(final_apk.read_bytes()).hexdigest()
    require(checksum == hashlib.sha256(apk.read_bytes()).hexdigest(), "APK changed while copying.")
    (destination / "apk-sha256.txt").write_text(f"{checksum}  app-debug.apk\n")
    (destination / "validated-commit.txt").write_text(identity["commit"] + "\n")
    (destination / "aapt-badging.txt").write_text(badging + "\n")
    (destination / "apksigner.txt").write_text(signature + "\n")
    shutil.copyfile(log, destination / "build.log")
    manifest = {
        **identity, "apk": "app-debug.apk", "sha256": checksum,
        "size_bytes": final_apk.stat().st_size, "application_id": package,
        "version_name": version, "version_code": int(version_code),
        "unit_tests": tests, "lint_issues": lint_issues, "gradle_command": command,
    }
    (destination / "validation.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Validated APK: {final_apk}\nSource: {identity['commit']}\nSHA-256: {checksum}")
    return manifest


if __name__ == "__main__":
    try:
        build(Path(__file__).resolve().parents[1])
    except (RuntimeError, subprocess.CalledProcessError, OSError, ValueError,
            zipfile.BadZipFile, ET.ParseError) as error:
        print(f"Validation failed: {error}", file=sys.stderr)
        sys.exit(1)
