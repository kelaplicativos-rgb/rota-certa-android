"""Exercise build failures and stale artifacts using an isolated Git repository."""

import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "build_validated_apk", Path(__file__).resolve().parents[1] / "build_validated_apk.py")
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)


class ValidatedApkTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "repo"
        self.root.mkdir()
        (self.root / "app").mkdir()
        (self.root / ".gitignore").write_text("build/\n")
        (self.root / "app/build.gradle.kts").write_text(
            'applicationId = "br.com.mapeiaia.rotacerta"\nversionCode = 37\nversionName = "0.1.36"\n')
        (self.root / "gradlew").write_text('''#!/bin/sh
exec python3 - "$@" <<'PY'
import os, pathlib, shutil, sys, zipfile
root = pathlib.Path.cwd()
mode = os.environ.get("FAKE_BUILD_MODE", "success")
assert sys.argv[1:5] == ["clean", "testDebugUnitTest", "lintDebug", "assembleDebug"]
if mode == "failed-build":
    print("simulated Gradle failure")
    sys.exit(7)
shutil.rmtree(root / "app/build", ignore_errors=True)
shutil.rmtree(root / "build", ignore_errors=True)
if mode == "changed-source":
    (root / "app/build.gradle.kts").write_text("changed during build")
elif mode == "changed-commit":
    import subprocess
    subprocess.run(["git", "commit", "--allow-empty", "-m", "moved"], check=True, stdout=subprocess.DEVNULL)
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
apk.parent.mkdir(parents=True)
if mode == "corrupt-apk":
    apk.write_bytes(b"not a zip")
else:
    with zipfile.ZipFile(apk, "w") as archive:
        archive.writestr("AndroidManifest.xml", b"manifest")
        archive.writestr("classes.dex", b"dex")
if mode != "missing-reports":
    report = root / "app/build/test-results/testDebugUnitTest/TEST-example.xml"
    report.parent.mkdir(parents=True)
    failures = "1" if mode == "failed-tests" else "0"
    report.write_text(f'<testsuite tests="2" failures="{failures}" errors="0" skipped="0"/>')
    lint = root / "app/build/reports/lint-results-debug.xml"
    lint.parent.mkdir(parents=True)
    issue = '<issue severity="Error"/>' if mode == "failed-lint" else ''
    lint.write_text(f'<issues>{issue}</issues>')
PY
''')
        self.git("init", "-q")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test")
        self.git("add", ".")
        self.git("commit", "-qm", "fixture")
        self.sha = self.git("rev-parse", "HEAD")
        sdk = Path(self.temp.name) / "sdk"
        tools = sdk / "build-tools/35.0.0"
        tools.mkdir(parents=True)
        (tools / "aapt").write_text('''#!/bin/sh
printf "package: name='br.com.mapeiaia.rotacerta' versionCode='37' versionName='%s'\\n" "${FAKE_APK_VERSION:-0.1.36}"
''')
        (tools / "apksigner").write_text('''#!/bin/sh
[ "${FAKE_BUILD_MODE:-}" != "invalid-signature" ] || exit 1
echo "Verified signature"
''')
        for tool in tools.iterdir():
            tool.chmod(0o755)
        environment = patch.dict(os.environ, {
            "ANDROID_HOME": str(sdk), "EXPECTED_SOURCE_SHA": self.sha,
            "FAKE_BUILD_MODE": "success", "FAKE_APK_VERSION": "0.1.36",
        })
        environment.start()
        self.addCleanup(environment.stop)

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()

    def run_build(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return builder.build(self.root)

    def assert_rejected(self, mode, exception=RuntimeError):
        old = self.root / "app/build/validated-apk"
        old.mkdir(parents=True)
        (old / "app-debug.apk").write_bytes(b"stale artifact")
        os.environ["FAKE_BUILD_MODE"] = mode
        with self.assertRaises(exception):
            self.run_build()
        self.assertFalse(old.exists(), "A failed retry must not expose a stale validated APK")

    def test_success_binds_exact_apk_to_commit_and_keeps_reports(self):
        manifest = self.run_build()
        destination = self.root / "app/build/validated-apk"
        self.assertEqual(self.sha, manifest["commit"])
        self.assertEqual(2, manifest["unit_tests"])
        self.assertEqual(hashlib.sha256((destination / "app-debug.apk").read_bytes()).hexdigest(),
                         manifest["sha256"])
        self.assertEqual(manifest, json.loads((destination / "validation.json").read_text()))
        self.assertTrue((self.root / "app/build/test-results/testDebugUnitTest/TEST-example.xml").exists())

    def test_gradle_failure_is_not_masked_by_log_capture(self):
        self.assert_rejected("failed-build")
        self.assertIn("simulated Gradle failure", (self.root / "build/validated-apk.log").read_text())

    def test_source_edit_during_build_is_rejected(self):
        self.assert_rejected("changed-source")

    def test_commit_switch_during_build_is_rejected(self):
        self.assert_rejected("changed-commit")

    def test_wrong_checkout_is_rejected(self):
        os.environ["EXPECTED_SOURCE_SHA"] = "0" * 40
        self.assert_rejected("success")

    def test_stale_apk_version_is_rejected(self):
        os.environ["FAKE_APK_VERSION"] = "0.1.146"
        self.assert_rejected("success")

    def test_missing_reports_are_rejected(self):
        self.assert_rejected("missing-reports")

    def test_failed_unit_tests_are_rejected(self):
        self.assert_rejected("failed-tests")

    def test_lint_errors_are_rejected(self):
        self.assert_rejected("failed-lint")

    def test_invalid_signature_is_rejected(self):
        self.assert_rejected("invalid-signature", subprocess.CalledProcessError)

    def test_corrupt_apk_is_rejected(self):
        import zipfile
        self.assert_rejected("corrupt-apk", zipfile.BadZipFile)


if __name__ == "__main__":
    unittest.main()
