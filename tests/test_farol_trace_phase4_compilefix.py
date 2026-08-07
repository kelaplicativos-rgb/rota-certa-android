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
    def test_named_argument_value_can_use_declared_destination_signature(self):
        source = """
private fun createBinding(destinationSignature: String) {
    val token = Token(addressSignature = addressSignature)
}
"""
        repaired = MODULE.repair_source(source)
        self.assertIn("addressSignature = destinationSignature", repaired)

    def test_result_persistence_uses_immutable_phase4_binding(self):
        source = """
private fun applyResult(
    binding0187Phase4: FarolDecisionBinding0187Phase4,
) {
    val signature = listOf(addressSignature, "green").joinToString("|")
}
"""
        repaired = MODULE.repair_source(source)
        self.assertIn("binding0187Phase4.addressSignature", repaired)
        self.assertNotRegex(repaired, MODULE.BARE_ADDRESS_VALUE)

    def test_declared_address_signature_is_never_rewritten(self):
        source = """
private fun applyResult(addressSignature: String) {
    persist(addressSignature)
}
"""
        with self.assertRaises(SystemExit):
            MODULE.repair_source(source)

    def test_two_bindings_fail_closed(self):
        source = """
private fun applyResult(
    first: FarolDecisionBinding0187Phase4,
    second: FarolDecisionBinding0187Phase4,
) {
    persist(addressSignature)
}
"""
        with self.assertRaises(SystemExit):
            MODULE.repair_source(source)


if __name__ == "__main__":
    unittest.main()
