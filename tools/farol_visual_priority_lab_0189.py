#!/usr/bin/env python3
"""Deterministic oracle for Rota Certa 0.1.189 visual authority.

This model is test-only. It encodes the runtime contract before the Android
implementation is changed:
- highest interactive window layer wins;
- inside that window, the uppermost coherent card wins;
- a new authoritative card invalidates the previous one immediately;
- yellow exists only after final destination confirmation and before route result;
- different cards are never merged merely to reach two addresses.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class VisualBlock:
    package_name: str
    window_id: int
    window_layer: int
    block_id: str
    top: int
    bottom: int
    addresses: tuple[str, ...]
    coherent: bool = True
    visible: bool = True

    @property
    def destination(self) -> str | None:
        if not self.coherent or len(self.addresses) < 2:
            return None
        return self.addresses[-1]


@dataclass(frozen=True)
class Authority:
    key: tuple[str, int, str]
    destination: str


@dataclass(frozen=True)
class Transition:
    previous: Authority | None
    current: Authority | None
    invalidate_previous: bool
    color: str


def select_authority(selected_package: str, blocks: list[VisualBlock]) -> Authority | None:
    candidates = [
        block
        for block in blocks
        if block.visible
        and block.coherent
        and block.package_name == selected_package
        and block.destination is not None
    ]
    if not candidates:
        return None
    highest_layer = max(block.window_layer for block in candidates)
    in_front_window = [block for block in candidates if block.window_layer == highest_layer]
    # Android coordinates grow downward: smaller top means visually higher.
    winner = min(in_front_window, key=lambda block: (block.top, block.bottom, block.block_id))
    return Authority(
        key=(winner.package_name, winner.window_id, winner.block_id),
        destination=winner.destination or "",
    )


def transition(previous: Authority | None, current: Authority | None) -> Transition:
    changed = previous is not None and (current is None or previous.key != current.key)
    # New user contract: before final destination confirmation the bubble is gray.
    color = "yellow" if current is not None else "gray"
    return Transition(previous, current, changed, color)
