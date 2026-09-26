from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from tools.farol_visual_priority_lab_0189 import (  # noqa: E402
    VisualBlock,
    select_authority,
    transition,
)


class FarolVisualPriority0189Test(unittest.TestCase):
    def test_topmost_card_in_highest_window_wins(self) -> None:
        blocks = [
            VisualBlock("com.example.driver", 10, 0, "feed-a", 120, 380, ("A1", "A2")),
            VisualBlock("com.example.driver", 11, 3, "popup", 520, 900, ("P1", "P2")),
        ]
        authority = select_authority("com.example.driver", blocks)
        self.assertIsNotNone(authority)
        self.assertEqual(("com.example.driver", 11, "popup"), authority.key)
        self.assertEqual("P2", authority.destination)

    def test_upper_card_wins_when_two_coherent_cards_share_window(self) -> None:
        blocks = [
            VisualBlock("regional.driver", 20, 1, "upper", 100, 420, ("U1", "U2")),
            VisualBlock("regional.driver", 20, 1, "lower", 500, 880, ("L1", "L2")),
        ]
        authority = select_authority("regional.driver", blocks)
        self.assertEqual(("regional.driver", 20, "upper"), authority.key)
        self.assertEqual("U2", authority.destination)

    def test_addresses_from_different_cards_never_merge(self) -> None:
        blocks = [
            VisualBlock("unknown.driver", 30, 1, "one", 100, 300, ("A1",)),
            VisualBlock("unknown.driver", 30, 1, "two", 350, 600, ("B1",)),
        ]
        self.assertIsNone(select_authority("unknown.driver", blocks))

    def test_new_top_block_invalidates_previous_and_turns_orange(self) -> None:
        first = select_authority(
            "com.example.driver",
            [VisualBlock("com.example.driver", 40, 1, "old", 100, 500, ("O1", "O2"))],
        )
        second = select_authority(
            "com.example.driver",
            [VisualBlock("com.example.driver", 41, 4, "new-popup", 450, 850, ("N1", "N2"))],
        )
        change = transition(first, second, selected_package_active=True)
        self.assertTrue(change.invalidate_previous)
        self.assertEqual("orange", change.color)
        self.assertEqual("N2", change.current.destination)

    def test_selected_package_without_final_destination_is_yellow(self) -> None:
        current = select_authority(
            "com.example.driver",
            [VisualBlock("com.example.driver", 50, 2, "partial", 100, 500, ("ONLY_ONE",))],
        )
        change = transition(None, current, selected_package_active=True)
        self.assertIsNone(current)
        self.assertEqual("yellow", change.color)

    def test_external_or_inactive_screen_is_gray(self) -> None:
        change = transition(None, None, selected_package_active=False)
        self.assertEqual("gray", change.color)

    def test_three_addresses_use_last_inside_same_authoritative_block(self) -> None:
        authority = select_authority(
            "com.example.driver",
            [VisualBlock("com.example.driver", 60, 2, "ride", 100, 700, ("ORIGIN", "STOP", "FINAL"))],
        )
        self.assertEqual("FINAL", authority.destination)
        self.assertEqual("orange", transition(None, authority, selected_package_active=True).color)

    def test_other_package_cannot_take_authority(self) -> None:
        blocks = [
            VisualBlock("com.android.systemui", 6, 99, "system", 0, 200, ("S1", "S2")),
            VisualBlock("local.driver", 70, 1, "ride", 100, 600, ("R1", "R2")),
        ]
        authority = select_authority("local.driver", blocks)
        self.assertEqual(("local.driver", 70, "ride"), authority.key)


if __name__ == "__main__":
    unittest.main()
