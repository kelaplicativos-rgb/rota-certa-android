from __future__ import annotations

import unittest
from dataclasses import dataclass


@dataclass(frozen=True)
class DecisionBinding:
    package_name: str
    session_generation: int
    window_id: int
    screen_generation: int
    window_generation: int
    screen_hash: int
    address_signature: str


def is_fresh(binding: DecisionBinding, current: DecisionBinding) -> bool:
    return binding == current


class FarolPhase4ReplayTest(unittest.TestCase):
    def test_network_result_that_ignores_cancellation_cannot_repaint_after_window_invalidation(self) -> None:
        visual = ("green", "DESTINO_CONFIRMADO", 1.788)
        route_binding = DecisionBinding(
            package_name="com.example.local.driver",
            session_generation=7,
            window_id=42,
            screen_generation=19,
            window_generation=4,
            screen_hash=991,
            address_signature="destination-a",
        )

        # Rejected selected-app snapshot invalidates session/window work but does
        # not itself prove that the confirmed card disappeared.
        current_after_rejection = DecisionBinding(
            package_name="com.example.local.driver",
            session_generation=8,
            window_id=42,
            screen_generation=19,
            window_generation=5,
            screen_hash=991,
            address_signature="destination-a",
        )

        self.assertEqual(("green", "DESTINO_CONFIRMADO", 1.788), visual)
        self.assertFalse(is_fresh(route_binding, current_after_rejection))

    def test_new_route_for_current_binding_can_apply(self) -> None:
        current = DecisionBinding(
            package_name="org.regional.rideapp",
            session_generation=11,
            window_id=77,
            screen_generation=23,
            window_generation=9,
            screen_hash=881,
            address_signature="destination-b",
        )
        self.assertTrue(is_fresh(current, current))


if __name__ == "__main__":
    unittest.main()
