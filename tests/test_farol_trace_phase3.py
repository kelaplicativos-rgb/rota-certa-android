from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from tools.farol_trace_lab import TraceEvent, TraceReplayLab  # noqa: E402


class FarolTracePhase3Test(unittest.TestCase):
    def test_rejected_read_preserves_decision_until_positive_evidence(self) -> None:
        """Sanitized regression extracted from report 30.

        A green decision must survive a rejected selected-app window read. A
        coherent card disappearance clears to yellow, and a confirmed external
        foreground transition clears to gray.
        """
        selected = "sinet.startup.indriver"
        lab = TraceReplayLab(selected_package=selected)
        result = lab.replay(
            {"source_report": "rota-certa-relatorio-depuracao (30).txt"},
            [
                TraceEvent(1, 0, "accessibility", selected, selected, 6453, 1, fingerprint="card-a"),
                TraceEvent(
                    2,
                    10,
                    "card_confirmed",
                    selected,
                    selected,
                    6453,
                    1,
                    card_signature="card-a",
                    destination="DESTINO_MASCARADO",
                ),
                TraceEvent(
                    3,
                    20,
                    "route_result",
                    selected,
                    None,
                    6453,
                    1,
                    card_signature="card-a",
                    destination="DESTINO_MASCARADO",
                    distance_km=1.788,
                    within_radius=True,
                ),
                TraceEvent(4, 30, "accessibility", selected, selected, 6444, 1, fingerprint="stale-window"),
                TraceEvent(5, 40, "card_disappeared", selected, selected, 6453, 1),
                TraceEvent(6, 50, "accessibility", "com.sec.android.app.launcher", selected, 5481, 1),
            ],
        )

        self.assertEqual([], result.invariant_failures)
        by_sequence = {record.sequence: record for record in result.records}
        self.assertEqual("route_result_applied", by_sequence[3].outcome)
        self.assertEqual(("green", "DESTINO_MASCARADO", 1.788), by_sequence[3].visual)
        self.assertEqual("window_mismatch_rejected", by_sequence[4].outcome)
        self.assertEqual(("green", "DESTINO_MASCARADO", 1.788), by_sequence[4].visual)
        self.assertEqual("card_disappeared", by_sequence[5].outcome)
        self.assertEqual(("yellow", None, None), by_sequence[5].visual)
        self.assertEqual("external_package_rejected", by_sequence[6].outcome)
        self.assertEqual(("gray", None, None), by_sequence[6].visual)


if __name__ == "__main__":
    unittest.main()
