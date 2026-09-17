#!/usr/bin/env python3
"""Deterministic replay laboratory for Rota Certa farol traces.

This is a test-only oracle. It does not import Android classes and does not
change runtime behavior. Its job is to turn a sanitized diagnostic trace into
repeatable invariants that the Android implementation must satisfy.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Iterable


DECISION_COLORS = {"green", "red"}
NO_DISTANCE_COLORS = {"gray", "yellow"}
ROOT_REQUIRED_KINDS = {
    "accessibility",
    "card_confirmed",
    "card_disappeared",
    "failure_probe",
}


@dataclass(frozen=True)
class TraceEvent:
    sequence: int
    at_ms: int
    kind: str
    event_package: str
    root_package: str | None
    window_id: int
    generation: int
    fingerprint: str = ""
    card_signature: str | None = None
    destination: str | None = None
    distance_km: float | None = None
    within_radius: bool | None = None
    source_id: str = ""


@dataclass
class ReplayState:
    selected_package: str
    generation: int = 0
    window_id: int = -1
    card_signature: str | None = None
    destination_confirmed: bool = False
    color: str = "gray"
    destination: str | None = None
    distance_km: float | None = None
    render_count: int = 0
    root_read_count: int = 0
    clear_requests: int = 0
    effective_clears: int = 0
    last_fingerprint: str | None = None
    last_fingerprint_at_ms: int = -1
    counters: Counter[str] = field(default_factory=Counter)

    @property
    def visual(self) -> tuple[str, str | None, float | None]:
        return (self.color, self.destination, self.distance_km)


@dataclass(frozen=True)
class ReplayRecord:
    sequence: int
    source_id: str
    outcome: str
    visual: tuple[str, str | None, float | None]
    generation: int
    window_id: int
    root_read_count: int
    render_count: int


@dataclass
class ReplayResult:
    metadata: dict[str, Any]
    final_state: ReplayState
    records: list[ReplayRecord]
    invariant_failures: list[str]

    def summary(self) -> dict[str, Any]:
        state = self.final_state
        return {
            "source_report": self.metadata.get("source_report"),
            "expanded_events": len(self.records),
            "outcomes": dict(sorted(state.counters.items())),
            "root_reads": state.root_read_count,
            "renders": state.render_count,
            "clear_requests": state.clear_requests,
            "effective_clears": state.effective_clears,
            "contained_failures": state.counters["failure_contained"],
            "final_visual": {
                "color": state.color,
                "destination": state.destination,
                "distance_km": state.distance_km,
            },
            "invariant_failures": self.invariant_failures,
        }


class TraceReplayLab:
    """Small deterministic oracle for package/generation/visual invariants."""

    def __init__(self, selected_package: str, coalesce_window_ms: int = 250) -> None:
        if not selected_package:
            raise ValueError("selected_package must not be empty")
        if coalesce_window_ms < 0:
            raise ValueError("coalesce_window_ms must be non-negative")
        self.state = ReplayState(selected_package=selected_package)
        self.coalesce_window_ms = coalesce_window_ms
        self.records: list[ReplayRecord] = []
        self.invariant_failures: list[str] = []

    def replay(self, metadata: dict[str, Any], events: Iterable[TraceEvent]) -> ReplayResult:
        for event in events:
            outcome = self._process_safely(event)
            self.state.counters[outcome] += 1
            self._assert_invariants(event, outcome)
            self.records.append(
                ReplayRecord(
                    sequence=event.sequence,
                    source_id=event.source_id,
                    outcome=outcome,
                    visual=self.state.visual,
                    generation=self.state.generation,
                    window_id=self.state.window_id,
                    root_read_count=self.state.root_read_count,
                    render_count=self.state.render_count,
                )
            )
        return ReplayResult(
            metadata=metadata,
            final_state=self.state,
            records=self.records,
            invariant_failures=self.invariant_failures,
        )

    def _process_safely(self, event: TraceEvent) -> str:
        try:
            return self._process(event)
        except Exception:  # Deliberate containment boundary exercised by failure_probe.
            self._forget_card()
            self._render("yellow", None, None)
            return "failure_contained"

    def _process(self, event: TraceEvent) -> str:
        # Package gate is intentionally first. An external event may carry a
        # stale root from the monitored app, but that root must never be read.
        if event.event_package != self.state.selected_package:
            self._forget_card()
            self._render("gray", None, None)
            return "external_package_rejected"

        if event.generation < self.state.generation:
            return "stale_generation_rejected"

        if event.generation > self.state.generation:
            self.state.generation = event.generation
            self.state.window_id = event.window_id
            self.state.last_fingerprint = None
            self.state.last_fingerprint_at_ms = -1
            self._forget_card()
            self._render("yellow", None, None)
        elif self.state.window_id not in {-1, event.window_id}:
            return "window_mismatch_rejected"

        if event.kind in ROOT_REQUIRED_KINDS:
            self.state.root_read_count += 1
            if event.root_package is None:
                self._forget_card()
                self._render("yellow", None, None)
                return "root_unavailable"
            if event.root_package != self.state.selected_package:
                self._forget_card()
                self._render("yellow", None, None)
                return "stale_root_rejected"

        if event.kind == "accessibility":
            if self._is_duplicate(event):
                return "event_coalesced"
            self.state.last_fingerprint = event.fingerprint
            self.state.last_fingerprint_at_ms = event.at_ms
            self._render("yellow", None, None)
            return "accessibility_accepted"

        if event.kind == "card_confirmed":
            if not event.card_signature or not event.destination:
                self._forget_card()
                self._render("yellow", None, None)
                return "card_confirmation_rejected"
            self.state.card_signature = event.card_signature
            self.state.destination = event.destination
            self.state.destination_confirmed = True
            self.state.distance_km = None
            self._render("yellow", event.destination, None)
            return "card_confirmed"

        if event.kind == "route_result":
            if (
                not self.state.destination_confirmed
                or event.card_signature != self.state.card_signature
                or event.destination != self.state.destination
            ):
                return "route_result_identity_rejected"
            if event.distance_km is None or event.within_radius is None:
                return "route_result_incomplete_rejected"
            color = "green" if event.within_radius else "red"
            self._render(color, self.state.destination, event.distance_km)
            return "route_result_applied"

        if event.kind == "card_disappeared":
            self._forget_card()
            self._render("yellow", None, None)
            return "card_disappeared"

        if event.kind == "clear_request":
            self.state.clear_requests += 1
            changed = self._clear_to_waiting()
            return "clear_applied" if changed else "clear_idempotent"

        if event.kind == "failure_probe":
            raise RuntimeError("synthetic replay failure")

        return "unknown_event_rejected"

    def _is_duplicate(self, event: TraceEvent) -> bool:
        return (
            bool(event.fingerprint)
            and event.fingerprint == self.state.last_fingerprint
            and self.state.last_fingerprint_at_ms >= 0
            and event.at_ms - self.state.last_fingerprint_at_ms <= self.coalesce_window_ms
        )

    def _forget_card(self) -> None:
        self.state.card_signature = None
        self.state.destination_confirmed = False
        self.state.destination = None
        self.state.distance_km = None

    def _clear_to_waiting(self) -> bool:
        before = self.state.visual
        self._forget_card()
        self._render("yellow", None, None)
        changed = before != self.state.visual
        if changed:
            self.state.effective_clears += 1
        return changed

    def _render(self, color: str, destination: str | None, distance_km: float | None) -> None:
        new_visual = (color, destination, distance_km)
        if new_visual == self.state.visual:
            return
        self.state.color = color
        self.state.destination = destination
        self.state.distance_km = distance_km
        self.state.render_count += 1

    def _assert_invariants(self, event: TraceEvent, outcome: str) -> None:
        state = self.state
        prefix = f"seq={event.sequence} source={event.source_id or '-'} outcome={outcome}: "

        if state.color in DECISION_COLORS:
            if not state.destination_confirmed:
                self.invariant_failures.append(prefix + "decision color without confirmed destination")
            if state.distance_km is None:
                self.invariant_failures.append(prefix + "decision color without real distance")
        if state.color in NO_DISTANCE_COLORS and state.distance_km is not None:
            self.invariant_failures.append(prefix + "non-decision color retained distance")
        if state.distance_km is not None and state.destination is None:
            self.invariant_failures.append(prefix + "distance retained without destination")
        if outcome == "external_package_rejected" and event.root_package is not None:
            if event.event_package == state.selected_package:
                self.invariant_failures.append(prefix + "selected package misclassified as external")
        if outcome in {
            "stale_generation_rejected",
            "route_result_identity_rejected",
            "route_result_incomplete_rejected",
        } and event.kind == "route_result":
            if state.card_signature == event.card_signature and state.distance_km == event.distance_km:
                self.invariant_failures.append(prefix + "rejected route result changed visual decision")


def expand_fixture_events(raw_events: list[dict[str, Any]]) -> list[TraceEvent]:
    expanded: list[TraceEvent] = []
    sequence = 0
    for raw in raw_events:
        repeat = int(raw.get("repeat", 1))
        interval_ms = int(raw.get("repeat_interval_ms", 0))
        if repeat < 1:
            raise ValueError("repeat must be at least 1")
        for index in range(repeat):
            sequence += 1
            expanded.append(
                TraceEvent(
                    sequence=sequence,
                    at_ms=int(raw["at_ms"]) + index * interval_ms,
                    kind=str(raw["kind"]),
                    event_package=str(raw["event_package"]),
                    root_package=raw.get("root_package"),
                    window_id=int(raw.get("window_id", -1)),
                    generation=int(raw.get("generation", 0)),
                    fingerprint=str(raw.get("fingerprint", "")),
                    card_signature=raw.get("card_signature"),
                    destination=raw.get("destination"),
                    distance_km=(
                        float(raw["distance_km"]) if raw.get("distance_km") is not None else None
                    ),
                    within_radius=raw.get("within_radius"),
                    source_id=str(raw.get("id", "")),
                )
            )
    return expanded


def load_fixture(path: Path) -> tuple[dict[str, Any], list[TraceEvent], dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    metadata = dict(payload.get("metadata", {}))
    selected_package = str(payload["selected_package"])
    metadata["selected_package"] = selected_package
    metadata["coalesce_window_ms"] = int(payload.get("coalesce_window_ms", 250))
    events = expand_fixture_events(list(payload["events"]))
    expected = dict(payload.get("expected", {}))
    return metadata, events, expected


def validate_expected(summary: dict[str, Any], expected: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    for key, value in expected.items():
        if key == "outcomes":
            actual_outcomes = summary["outcomes"]
            for outcome, expected_count in value.items():
                actual_count = int(actual_outcomes.get(outcome, 0))
                if actual_count != int(expected_count):
                    failures.append(
                        f"outcome {outcome}: expected {expected_count}, got {actual_count}"
                    )
            continue
        if summary.get(key) != value:
            failures.append(f"{key}: expected {value!r}, got {summary.get(key)!r}")
    return failures


def run_fixture(path: Path) -> tuple[ReplayResult, list[str]]:
    metadata, events, expected = load_fixture(path)
    lab = TraceReplayLab(
        selected_package=metadata["selected_package"],
        coalesce_window_ms=metadata["coalesce_window_ms"],
    )
    result = lab.replay(metadata, events)
    expectation_failures = validate_expected(result.summary(), expected)
    return result, expectation_failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--timeline", action="store_true")
    args = parser.parse_args()

    result, expectation_failures = run_fixture(args.fixture)
    output = result.summary()
    output["expectation_failures"] = expectation_failures
    print(json.dumps(output, ensure_ascii=False, indent=2, sort_keys=True))

    if args.timeline:
        print(json.dumps([asdict(record) for record in result.records], ensure_ascii=False, indent=2))

    if args.strict and (result.invariant_failures or expectation_failures):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
