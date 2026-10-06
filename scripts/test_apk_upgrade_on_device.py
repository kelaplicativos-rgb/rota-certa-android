#!/usr/bin/env python3
"""Install the real APKs on a fresh emulator and verify private data survives -r."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import sqlite3
import subprocess
import tempfile


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--proof", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    args.report.unlink(missing_ok=True)
    proof = json.loads(args.proof.read_text())
    assert proof["update_compatible"]
    for path, metadata in ((args.baseline, proof["baseline"]), (args.candidate, proof["candidate"])):
        assert hashlib.sha256(path.read_bytes()).hexdigest() == metadata["sha256"], "APK changed after validation"
    # This integration test never touches an existing user installation.
    assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Use a fresh test emulator"
    package = proof["candidate"]["application_id"]
    assert re.fullmatch(r"[A-Za-z0-9_.]+", package)
    installed = subprocess.run(["adb", "shell", "pm", "path", package], capture_output=True, text=True)
    assert "package:" not in installed.stdout, "Test requires a fresh emulator; do not uninstall existing data"
    assert "Success" in adb("install", "--no-streaming", str(args.baseline))
    uid_before = adb("shell", "run-as", package, "id", "-u")
    token = "rota-certa-update-preservation-" + proof["source_commit"]
    fixtures = {
        "files/update-preservation-probe": token.encode(),
        "shared_prefs/update-preservation-probe.xml":
            ('<map><string name="probe">' + token + '</string></map>').encode(),
    }
    with tempfile.TemporaryDirectory() as temp:
        database = Path(temp) / "probe.db"
        connection = sqlite3.connect(database)
        connection.execute("CREATE TABLE preserved_data (value TEXT NOT NULL)")
        connection.execute("INSERT INTO preserved_data VALUES (?)", (token,))
        connection.commit()
        connection.close()
        fixtures["databases/update-preservation-probe.db"] = database.read_bytes()
    for path, value in fixtures.items():
        command = f"mkdir -p {shlex.quote(path.split('/')[0])} && cat > {shlex.quote(path)}"
        subprocess.run(["adb", "shell", "-T", "run-as", package, "sh", "-c", shlex.quote(command)],
                       input=value, check=True)
        assert subprocess.check_output(["adb", "exec-out", "run-as", package, "cat", path]) == value
    # No uninstall, clear-data, downgrade, or replacement signing key is used.
    assert "Success" in adb("install", "--no-streaming", "-r", str(args.candidate))
    uid_after = adb("shell", "run-as", package, "id", "-u")
    assert uid_before == uid_after, "Android app UID changed during update"
    for path, value in fixtures.items():
        assert subprocess.check_output(["adb", "exec-out", "run-as", package, "cat", path]) == value, f"Private data lost: {path}"
    version = re.search(r"versionCode=(\d+)", adb("shell", "dumpsys", "package", package))
    assert version and int(version.group(1)) == proof["candidate"]["version_code"]
    report = {
        "source_commit": proof["source_commit"], "apk_sha256": proof["candidate"]["sha256"],
        "baseline_version_code": proof["baseline"]["version_code"],
        "installed_version_code": proof["candidate"]["version_code"],
        "api_level": adb("shell", "getprop", "ro.build.version.sdk"),
        "uid_preserved": True, "private_files_preserved": list(fixtures),
        "install_command": ["adb", "install", "--no-streaming", "-r"],
        "uninstall_used": False, "clear_data_used": False,
        "scope": "fresh emulator; app not launched; storage/migrations unchanged",
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print("PASS: real APK upgrade preserved app UID, private files, preferences and database file.")


if __name__ == "__main__":
    main()
