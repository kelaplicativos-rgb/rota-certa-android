import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "verify_apk_upgrade.py"
SPEC = importlib.util.spec_from_file_location("verify_apk_upgrade", SCRIPT)
upgrade = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(upgrade)
CONTRACT = json.loads((SCRIPT.parents[1] / "app/update-baseline.json").read_text())


class SamsungUpgradeRegressionTest(unittest.TestCase):
    def setUp(self):
        self.baseline = {
            "application_id": CONTRACT["application_id"], "version_name": "0.1.737",
            "version_code": 6028, "signers": [CONTRACT["signer_sha256"]],
            "min_sdk": 26, "native_files": ["lib/arm64-v8a/liborganicmaps.so"],
            "sha256": CONTRACT["apk_sha256"], "size_bytes": 1,
        }
        self.candidate = copy.deepcopy(self.baseline)
        self.candidate.update(version_name="0.1.738", version_code=6029)

    def verify(self):
        upgrade.verify_metadata(self.baseline, self.candidate, CONTRACT, "0.1.738", 6029)

    def test_next_version_keeps_package_signer_and_device_support(self):
        self.verify()

    def test_actual_wrong_0136_delivery_cannot_replace_0737(self):
        self.candidate.update(version_name="0.1.36", version_code=37,
                              signers=["b13258d97bdf30e74de98bad78cb53e5df4f798bf646da5e9b3203d33ad6f182"])
        with self.assertRaisesRegex(ValueError, "Signature changed"):
            self.verify()

    def test_same_signer_does_not_allow_downgrade(self):
        self.candidate["version_code"] = 37
        with self.assertRaisesRegex(ValueError, "increase versionCode"):
            self.verify()

    def test_new_name_does_not_allow_same_version_code(self):
        self.candidate["version_code"] = 6028
        with self.assertRaisesRegex(ValueError, "increase versionCode"):
            self.verify()

    def test_higher_version_does_not_allow_rotated_debug_key(self):
        self.candidate["signers"] = ["b" * 64]
        with self.assertRaisesRegex(ValueError, "Signature changed"):
            self.verify()

    def test_different_package_is_not_an_update(self):
        self.candidate["application_id"] += ".debug"
        with self.assertRaisesRegex(ValueError, "Package changed"):
            self.verify()

    def test_reference_download_cannot_be_silently_replaced(self):
        self.baseline["sha256"] = "b" * 64
        with self.assertRaisesRegex(ValueError, "reference APK hash"):
            self.verify()

    def test_wrong_baseline_version_is_rejected(self):
        self.baseline["version_code"] = 6027
        with self.assertRaisesRegex(ValueError, "baseline identity"):
            self.verify()

    def test_reference_signer_is_pinned(self):
        self.baseline["signers"] = ["b" * 64]
        with self.assertRaisesRegex(ValueError, "Reference signing"):
            self.verify()

    def test_samsung_android_16_must_remain_supported(self):
        self.candidate["min_sdk"] = 37
        with self.assertRaisesRegex(ValueError, "Android version"):
            self.verify()

    def test_samsung_native_map_library_cannot_disappear(self):
        self.candidate["native_files"] = []
        with self.assertRaisesRegex(ValueError, "ARM64 map library"):
            self.verify()

    def test_renamed_apk_does_not_change_its_actual_version(self):
        self.candidate["version_name"] = "0.1.737"
        with self.assertRaisesRegex(ValueError, "intended release"):
            self.verify()

    def test_old_dex_commit_is_rejected_even_if_version_and_signer_match(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "renamed.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", CONTRACT["source_commit"])
            with patch.object(upgrade, "inspect_apk", side_effect=[self.baseline, self.candidate]):
                with self.assertRaisesRegex(ValueError, "validated source commit"):
                    upgrade.verify(apk, apk, CONTRACT, Path(temp), "0.1.738", 6029, "b" * 40)

    def test_failed_retry_removes_an_old_success_report(self):
        with tempfile.TemporaryDirectory() as temp:
            report = Path(temp) / "proof.json"
            report.write_text('{"update_compatible":true}')
            argv = [str(SCRIPT), "--baseline", "base.apk", "--candidate", "new.apk",
                    "--build-tools", temp, "--version", "0.1.738", "--version-code", "6029",
                    "--commit", "not-a-commit", "--report", str(report)]
            with patch("sys.argv", argv):
                with self.assertRaisesRegex(ValueError, "full source commit"):
                    upgrade.main()
            self.assertFalse(report.exists())


if __name__ == "__main__":
    unittest.main()
