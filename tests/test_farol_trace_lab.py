from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from tools.farol_trace_lab import load_fixture, run_fixture  # noqa: E402


FIXTURE = ROOT / "tests" / "fixtures" / "farol_trace_20260806_sanitized.json"


class FarolTraceReplayLabTest(unittest.TestCase):
    def setUp(self) -> None:
        self.result, self.expectation_failures = run_fixture(FIXTURE)
        self.summary = self.result.summary()

    def test_report_trace_matches_declared_oracle(self) -> None:
        self.assertEqual([], self.expectation_failures)
        self.assertEqual([], self.result.invariant_failures)

    def test_external_packages_never_read_stale_monitored_root(self) -> None:
        external_records = [
            record
            for record in self.result.records
            if record.outcome == "external_package_rejected"
        ]
        self.assertEqual(1443, len(external_records))
        # The entire 1,440-event SystemUI storm happens after one legitimate
        # card-root read and before the next accepted monitored-app root.
        storm = external_records[:1440]
        self.assertTrue(storm)
        self.assertTrue(all(record.root_read_count == 1 for record in storm))

    def test_old_route_result_never_repaints_new_card(self) -> None:
        delayed = next(
            record for record in self.result.records if record.source_id == "delayed-route-card-a"
        )
        self.assertEqual("stale_generation_rejected", delayed.outcome)
        self.assertEqual(("yellow", "DESTINO_MASCARADO_B", None), delayed.visual)

    def test_event_and_clear_storms_are_bounded(self) -> None:
        self.assertEqual(454, self.summary["outcomes"]["event_coalesced"])
        self.assertEqual(562, self.summary["clear_requests"])
        self.assertEqual(1, self.summary["effective_clears"])
        self.assertLess(self.summary["renders"], 20)

    def test_containment_fails_closed_without_leaking_distance(self) -> None:
        contained = [
            record for record in self.result.records if record.outcome == "failure_contained"
        ]
        self.assertEqual(3, len(contained))
        self.assertTrue(all(record.visual == ("yellow", None, None) for record in contained))

    def test_card_disappearance_removes_decision_immediately(self) -> None:
        disappeared = next(
            record for record in self.result.records if record.source_id == "card-c-disappeared"
        )
        self.assertEqual("card_disappeared", disappeared.outcome)
        self.assertEqual(("yellow", None, None), disappeared.visual)

    def test_fixture_keeps_report_evidence_without_personal_addresses(self) -> None:
        metadata, _, _ = load_fixture(FIXTURE)
        observed = metadata["observed_report_counts"]
        self.assertEqual(2294, observed["current_recorder_dropped"])
        self.assertEqual(37848, observed["recovered_recorder_dropped"])
        text = FIXTURE.read_text(encoding="utf-8")
        self.assertNotIn("Terminal São Mateus", text)
        self.assertNotIn("Rua ", text)


if __name__ == "__main__":
    unittest.main()
