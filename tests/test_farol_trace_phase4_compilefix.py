import importlib.util
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "fix_farol_phase4_address_signature.py"
SPEC = importlib.util.spec_from_file_location("phase4_compilefix", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class Phase4AddressSignatureCompileFixTest(unittest.TestCase):
    def test_named_argument_value_is_rebound_to_destination_signature(self):
        source = """
private fun createBinding(destinationSignature: String) {
    val binding = Token(
        addressSignature = addressSignature,
    )
}
"""
        repaired = MODULE.repair_source(source)
        self.assertIn("addressSignature = destinationSignature", repaired)
        self.assertNotIn("addressSignature = addressSignature", repaired)

    def test_declared_address_signature_is_never_rewritten(self):
        source = """
private fun createBinding(destinationSignature: String, addressSignature: String) {
    val binding = Token(addressSignature)
}
"""
        with self.assertRaises(SystemExit):
            MODULE.repair_source(source)

    def test_ambiguous_candidates_fail_closed(self):
        source = """
private fun first(destinationSignature: String) {
    use(addressSignature)
}
private fun second(destinationSignature: String) {
    use(addressSignature)
}
"""
        with self.assertRaises(SystemExit):
            MODULE.repair_source(source)


if __name__ == "__main__":
    unittest.main()
