#!/usr/bin/env python3
"""Stage 14: selected-app choice activates reading; visible selected root may bootstrap a transient popup session."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

SAFETY = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt')
SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
GATE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt')
PARSER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt')
BUILD = Path('app/build.gradle.kts')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/VisibleOfferActivationStage14Test.kt')
MARKER = 'SELECTED_APP_ACTIVATES_VISIBLE_ROOT_STAGE14'

OLD = '''        if (transientOverlayEvent) {
            if (session != selected) return FarolRootAdmission0187(false, "transient_without_selected_session")
            if (activeSessionWindowId != null && activeSessionWindowId >= 0 && activeSessionWindowId != rootWindowId) {
                return FarolRootAdmission0187(false, "transient_root_window_mismatch")
            }
            return FarolRootAdmission0187(true, "selected_root_behind_transient")
        }

        if (event.isBlank()) {
            val sameSession = session == selected && activeSessionWindowId != null && activeSessionWindowId >= 0
            if (!sameSession || activeSessionWindowId != rootWindowId) {
                return FarolRootAdmission0187(false, "event_package_missing_without_same_session")
            }
            return FarolRootAdmission0187(true, "same_session_root_continuation")
        }
'''

NEW = '''        if (transientOverlayEvent) {
            // Stage 14: the user's selected-app list authorizes observation. A selected
            // app root that is actually visible behind a transient SystemUI/overlay event
            // may start the immutable card session instead of requiring a session first.
            // Package/root coherence is still mandatory above, and the existing 0.1.188
            // card/block gate remains the only route authority.
            val sameSelectedSessionStage14 = session == selected
            if (sameSelectedSessionStage14 && activeSessionWindowId != null && activeSessionWindowId >= 0 && activeSessionWindowId != rootWindowId) {
                return FarolRootAdmission0187(false, "transient_root_window_mismatch")
            }
            return FarolRootAdmission0187(
                accepted = true,
                reason = if (sameSelectedSessionStage14) {
                    "selected_root_behind_transient"
                } else {
                    "selected_root_behind_transient_session_bootstrap_stage14"
                },
            )
        }

        if (event.isBlank()) {
            val sameSelectedSessionStage14 = session == selected &&
                activeSessionWindowId != null && activeSessionWindowId >= 0
            if (sameSelectedSessionStage14 && activeSessionWindowId != rootWindowId) {
                return FarolRootAdmission0187(false, "event_package_missing_root_window_mismatch_stage14")
            }
            return FarolRootAdmission0187(
                accepted = true,
                reason = if (sameSelectedSessionStage14) {
                    "same_session_root_continuation"
                } else {
                    "selected_root_without_event_package_session_bootstrap_stage14"
                },
            )
        }
'''

TEST_SOURCE = r'''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleOfferActivationStage14Test {
    private fun admission(
        eventPackage: String? = "com.android.systemui",
        selectedPackage: String = "com.ubercab.driver",
        rootPackage: String? = "com.ubercab.driver",
        eventWindow: Int = 9,
        rootWindow: Int? = 201,
        transient: Boolean = true,
        sessionPackage: String? = null,
        sessionWindow: Int? = null,
    ): FarolRootAdmission0187 = FarolRootSnapshotPolicy0187.evaluate(
        eventPackageName = eventPackage,
        selectedPackageName = selectedPackage,
        rootPackageName = rootPackage,
        eventWindowId = eventWindow,
        rootWindowId = rootWindow,
        transientOverlayEvent = transient,
        activeSessionPackageName = sessionPackage,
        activeSessionWindowId = sessionWindow,
    )

    @Test
    fun selectedVisibleRootBehindSystemUiCanBootstrapWithoutPriorSession() {
        val result = admission()
        assertTrue(result.accepted)
        assertEquals("selected_root_behind_transient_session_bootstrap_stage14", result.reason)
    }

    @Test
    fun selectedVisibleRootCanReplaceDifferentPreviousDriverSession() {
        val result = admission(
            sessionPackage = "com.app99.driver",
            sessionWindow = 180,
        )
        assertTrue(result.accepted)
        assertEquals("selected_root_behind_transient_session_bootstrap_stage14", result.reason)
    }

    @Test
    fun sameSelectedSessionStillRejectsWrongRootWindow() {
        val result = admission(
            sessionPackage = "com.ubercab.driver",
            sessionWindow = 199,
        )
        assertFalse(result.accepted)
        assertEquals("transient_root_window_mismatch", result.reason)
    }

    @Test
    fun selectedVisibleRootWithMissingEventPackageCanBootstrapWithoutPriorSession() {
        val result = admission(eventPackage = null, transient = false)
        assertTrue(result.accepted)
        assertEquals("selected_root_without_event_package_session_bootstrap_stage14", result.reason)
    }

    @Test
    fun missingEventPackageStillRejectsWrongWindowInsideSameSelectedSession() {
        val result = admission(
            eventPackage = null,
            transient = false,
            sessionPackage = "com.ubercab.driver",
            sessionWindow = 199,
        )
        assertFalse(result.accepted)
        assertEquals("event_package_missing_root_window_mismatch_stage14", result.reason)
    }

    @Test
    fun externalRootStillFailsClosedEvenDuringSystemUiTransient() {
        val result = admission(rootPackage = "com.sec.android.app.launcher")
        assertFalse(result.accepted)
        assertEquals("root_package_mismatch", result.reason)
    }

    @Test
    fun nonTransientExternalEventStillCannotAuthorizeSelectedRoot() {
        val result = admission(
            eventPackage = "com.sec.android.app.launcher",
            transient = false,
        )
        assertFalse(result.accepted)
        assertEquals("event_package_mismatch", result.reason)
    }

    @Test
    fun sameSelectedSessionAndWindowPreservesExistingTransientContract() {
        val result = admission(
            sessionPackage = "com.ubercab.driver",
            sessionWindow = 201,
        )
        assertTrue(result.accepted)
        assertEquals("selected_root_behind_transient", result.reason)
    }
}
'''


def fail(message: str) -> None:
    raise SystemExit(message)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require_base(root: Path) -> tuple[str, dict[str, str]]:
    build = (root / BUILD).read_text(encoding='utf-8')
    if 'versionCode = 5478' not in build or 'versionName = "0.1.194"' not in build:
        fail('Stage14 exige fonte materializada 0.1.194/5478')
    safety_path = root / SAFETY
    service_path = root / SERVICE
    gate_path = root / GATE
    parser_path = root / PARSER
    for p in (safety_path, service_path, gate_path, parser_path):
        if not p.is_file(): fail(f'arquivo obrigatório ausente: {p.relative_to(root)}')
    safety = safety_path.read_text(encoding='utf-8')
    if MARKER in safety:
        fail('Stage14 já aplicado')
    if safety.count(OLD) != 1:
        fail(f'âncora transient 0187 inesperada: {safety.count(OLD)}')
    for required in (
        'ATOMIC_ROOT_SNAPSHOT_GATE_0187',
        'transient_without_selected_session',
        'transient_root_window_mismatch',
        'selected_root_behind_transient',
        'DECISION_RESULT_MONOTONIC_BINDING_0187_PHASE4',
    ):
        if required not in safety: fail(f'contrato 0187 ausente: {required}')
    gate = gate_path.read_text(encoding='utf-8')
    for exact in (
        'val authorityIdentity = "$selected|${winner.block.windowId}|${winner.block.id}|$signature"',
        'screenHash = authorityIdentity.hashCode()',
    ):
        if exact not in gate: fail(f'autoridade 0188 ausente: {exact}')
    protected = {str(p): sha(root / p) for p in (SERVICE, GATE, PARSER)}
    return safety, protected


def transformed(safety: str) -> str:
    updated = safety.replace(OLD, NEW, 1)
    marker_anchor = '    const val CONTRACT_MARKER = "ATOMIC_ROOT_SNAPSHOT_GATE_0187"\n'
    if updated.count(marker_anchor) != 1:
        fail('âncora de marcador 0187 inesperada')
    updated = updated.replace(
        marker_anchor,
        marker_anchor + f'    const val STAGE14_CONTRACT_MARKER = "{MARKER}"\n',
        1,
    )
    return updated


def audit(root: Path, protected: dict[str, str], updated: str) -> None:
    for rel, before in protected.items():
        now = sha(root / Path(rel))
        if now != before: fail(f'arquivo protegido mudou: {rel}: {before} -> {now}')
    for forbidden in (
        'delay(', 'Timer(', 'scheduleAtFixedRate', 'takeScreenshot(',
        'FarolLatencyProbeStage9.clear()', 'FarolLatencyProbeStage9.dump()',
    ):
        if forbidden in NEW: fail(f'comportamento proibido adicionado: {forbidden}')
    for required in (
        MARKER,
        'root != selected',
        'transient_root_window_mismatch',
        'selected_root_behind_transient_session_bootstrap_stage14',
        'selected_root_without_event_package_session_bootstrap_stage14',
        'event_package_missing_root_window_mismatch_stage14',
    ):
        if required not in updated: fail(f'contrato Stage14 ausente: {required}')


def self_test() -> None:
    synthetic = 'prefix\n    const val CONTRACT_MARKER = "ATOMIC_ROOT_SNAPSHOT_GATE_0187"\n' + OLD + 'suffix\n'
    out = transformed(synthetic)
    if MARKER not in out or OLD in out or NEW not in out:
        fail('self-test de transformação falhou')
    if TEST_SOURCE.count('@Test') != 8:
        fail('self-test esperava 8 testes Stage14')
    print('visible_offer_activation_stage14_self_test=passed')


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('source_root', nargs='?', type=Path)
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--self-test', action='store_true')
    args = ap.parse_args()
    if args.self_test:
        self_test()
        if args.source_root is None:
            return
    if args.source_root is None:
        fail('source_root obrigatório fora de --self-test isolado')
    root = args.source_root.resolve()
    safety, protected = require_base(root)
    updated = transformed(safety)
    audit(root, protected, updated)
    if args.check:
        print('visible_offer_activation_stage14_check=passed')
        print('selected_app_role=activation_only')
        print('transient_selected_root_session_bootstrap=true')
        print('route_authority_0188=unchanged')
        return
    (root / SAFETY).write_text(updated, encoding='utf-8')
    test_path = root / TEST
    if test_path.exists():
        fail(f'teste Stage14 já existe: {TEST}')
    test_path.write_text(TEST_SOURCE, encoding='utf-8')
    audit(root, protected, (root / SAFETY).read_text(encoding='utf-8'))
    print('visible_offer_activation_stage14_apply=passed')
    print('selected_app_role=activation_only')
    print('transient_selected_root_session_bootstrap=true')
    print('route_authority_0188=unchanged')
    print('artificial_delay_added=false')


if __name__ == '__main__':
    main()
