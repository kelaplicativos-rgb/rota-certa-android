#!/usr/bin/env python3
"""Stage18: isolate explicit ride-app identity from stale selected roots/sessions."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
HELPER16 = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisibleCardPriorityStage16.kt')
HELPER18 = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolAppIdentityIsolationStage18.kt')
TEST18 = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolAppIdentityIsolationStage18Test.kt')
MAPS = Path('app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt')
DECISION = Path('app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt')
BUILD = Path('app/build.gradle.kts')
MARKER = 'EXPLICIT_SELECTED_APP_IDENTITY_ISOLATION_STAGE18'
MAPS_SHA = 'c84d1e8bfa5f22ccbeb2f0e38615c9702bb763054c8c6a00c6021bd9320b29bf'

HELPER_SOURCE = r'''package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Stage 18 isolates app identity before any selected root is allowed to override
 * an accessibility event. Selected apps authorize reading; they do not authorize
 * cross-app reuse of roots, sessions, cards, OCR, cache entries or route results.
 */
object FarolAppIdentityIsolationStage18 {
    const val CONTRACT_MARKER = "EXPLICIT_SELECTED_APP_IDENTITY_ISOLATION_STAGE18"

    enum class Outcome {
        EXPLICIT_SELECTED_APP_MATCH,
        PASSIVE_EVENT_VISIBLE_SELECTED_APP,
        FAIL_CLOSED_CROSS_APP_ROOT,
        FAIL_CLOSED_EXPLICIT_APP_WITHOUT_COMPATIBLE_ROOT,
        NO_SELECTED_VISUAL_AUTHORITY,
    }

    data class Resolution(
        val outcome: Outcome,
        val authorityPackageName: String?,
        val explicitSelectedPackageName: String?,
        val allowVisibleRootOverride: Boolean,
        val failClosed: Boolean,
        val confirmedAppSwitch: Boolean,
        val preserveCurrentSession: Boolean,
    )

    data class IdentityBinding(
        val packageName: String,
        val sessionGeneration: Long,
        val windowId: Int,
        val screenGeneration: Long,
        val windowGeneration: Long,
        val screenHash: Int,
        val addressSignature: String,
    )

    fun resolve(
        eventPackageName: String?,
        visibleSelectedPackageName: String?,
        selectedPackages: Set<String>,
        activeSessionPackageName: String?,
    ): Resolution {
        val selected = selectedPackages.mapNotNull(::normalizePackage).toSet()
        val event = normalizePackage(eventPackageName)
        val visible = normalizePackage(visibleSelectedPackageName)?.takeIf { it in selected }
        val active = normalizePackage(activeSessionPackageName)
        val explicitSelected = event?.takeIf { it in selected }

        if (explicitSelected != null) {
            if (visible == null) {
                return Resolution(
                    outcome = Outcome.FAIL_CLOSED_EXPLICIT_APP_WITHOUT_COMPATIBLE_ROOT,
                    authorityPackageName = null,
                    explicitSelectedPackageName = explicitSelected,
                    allowVisibleRootOverride = false,
                    failClosed = true,
                    confirmedAppSwitch = false,
                    preserveCurrentSession = true,
                )
            }
            if (visible != explicitSelected) {
                return Resolution(
                    outcome = Outcome.FAIL_CLOSED_CROSS_APP_ROOT,
                    authorityPackageName = null,
                    explicitSelectedPackageName = explicitSelected,
                    allowVisibleRootOverride = false,
                    failClosed = true,
                    confirmedAppSwitch = false,
                    preserveCurrentSession = true,
                )
            }
            return Resolution(
                outcome = Outcome.EXPLICIT_SELECTED_APP_MATCH,
                authorityPackageName = explicitSelected,
                explicitSelectedPackageName = explicitSelected,
                allowVisibleRootOverride = true,
                failClosed = false,
                confirmedAppSwitch = active != null && active != explicitSelected,
                preserveCurrentSession = false,
            )
        }

        if (visible != null) {
            return Resolution(
                outcome = Outcome.PASSIVE_EVENT_VISIBLE_SELECTED_APP,
                authorityPackageName = visible,
                explicitSelectedPackageName = null,
                allowVisibleRootOverride = true,
                failClosed = false,
                confirmedAppSwitch = active != null && active != visible,
                preserveCurrentSession = false,
            )
        }

        return Resolution(
            outcome = Outcome.NO_SELECTED_VISUAL_AUTHORITY,
            authorityPackageName = null,
            explicitSelectedPackageName = null,
            allowVisibleRootOverride = false,
            failClosed = false,
            confirmedAppSwitch = false,
            preserveCurrentSession = true,
        )
    }

    fun bindingMatchesCurrent(bound: IdentityBinding, current: IdentityBinding): Boolean =
        normalizePackage(bound.packageName) == normalizePackage(current.packageName) &&
            bound.sessionGeneration == current.sessionGeneration &&
            bound.windowId == current.windowId &&
            bound.screenGeneration == current.screenGeneration &&
            bound.windowGeneration == current.windowGeneration &&
            bound.screenHash == current.screenHash &&
            bound.addressSignature == current.addressSignature

    fun blocksBelongToSingleAuthority(authorityPackageName: String, blockPackages: List<String>): Boolean {
        val authority = normalizePackage(authorityPackageName) ?: return false
        if (blockPackages.isEmpty()) return false
        return blockPackages.all { normalizePackage(it) == authority }
    }

    private fun normalizePackage(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
'''

TEST_SOURCE = r'''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolAppIdentityIsolationStage18Test {
    private val uber = "com.ubercab.driver"
    private val app99 = "com.app99.driver"
    private val inDrive = "sinet.startup.indriver"
    private val selected = setOf(uber, app99, inDrive)

    private fun resolution(event: String?, visible: String?, active: String? = null) =
        FarolAppIdentityIsolationStage18.resolve(event, visible, selected, active)

    private fun binding(
        packageName: String = uber,
        session: Long = 10,
        window: Int = 42,
        screen: Long = 20,
        windowGeneration: Long = 4,
        hash: Int = 123,
        signature: String = "uber|rua b 20",
    ) = FarolAppIdentityIsolationStage18.IdentityBinding(
        packageName, session, window, screen, windowGeneration, hash, signature,
    )

    private fun assertCrossAppFails(event: String, visible: String) {
        val result = resolution(event, visible, active = visible)
        assertEquals(FarolAppIdentityIsolationStage18.Outcome.FAIL_CLOSED_CROSS_APP_ROOT, result.outcome)
        assertTrue(result.failClosed)
        assertFalse(result.allowVisibleRootOverride)
        assertFalse(result.confirmedAppSwitch)
        assertTrue(result.preserveCurrentSession)
        assertNull(result.authorityPackageName)
        assertEquals(event, result.explicitSelectedPackageName)
    }

    private fun assertSwitch(event: String, previous: String) {
        val result = resolution(event, event, active = previous)
        assertEquals(FarolAppIdentityIsolationStage18.Outcome.EXPLICIT_SELECTED_APP_MATCH, result.outcome)
        assertFalse(result.failClosed)
        assertTrue(result.allowVisibleRootOverride)
        assertTrue(result.confirmedAppSwitch)
        assertFalse(result.preserveCurrentSession)
        assertEquals(event, result.authorityPackageName)
    }

    @Test fun eventUberRoot99FailsClosed() = assertCrossAppFails(uber, app99)
    @Test fun eventUberRootInDriveFailsClosed() = assertCrossAppFails(uber, inDrive)
    @Test fun event99RootUberFailsClosed() = assertCrossAppFails(app99, uber)
    @Test fun event99RootInDriveFailsClosed() = assertCrossAppFails(app99, inDrive)
    @Test fun eventInDriveRootUberFailsClosed() = assertCrossAppFails(inDrive, uber)
    @Test fun eventInDriveRoot99FailsClosed() = assertCrossAppFails(inDrive, app99)

    @Test fun switchUberTo99RequiresIdentityReset() = assertSwitch(app99, uber)
    @Test fun switch99ToUberRequiresIdentityReset() = assertSwitch(uber, app99)
    @Test fun switchUberToInDriveRequiresIdentityReset() = assertSwitch(inDrive, uber)
    @Test fun switchInDriveToUberRequiresIdentityReset() = assertSwitch(uber, inDrive)
    @Test fun switch99ToInDriveRequiresIdentityReset() = assertSwitch(inDrive, app99)
    @Test fun switchInDriveTo99RequiresIdentityReset() = assertSwitch(app99, inDrive)

    @Test fun uberPopupOverLauncherKeepsVisibleUberAuthority() {
        val r = resolution("com.sec.android.app.launcher", uber)
        assertEquals(FarolAppIdentityIsolationStage18.Outcome.PASSIVE_EVENT_VISIBLE_SELECTED_APP, r.outcome)
        assertEquals(uber, r.authorityPackageName)
        assertTrue(r.allowVisibleRootOverride)
    }

    @Test fun uberPopupOverMapsKeepsVisibleUberAuthority() {
        val r = resolution("com.google.android.apps.maps", uber)
        assertEquals(uber, r.authorityPackageName)
        assertFalse(r.failClosed)
    }

    @Test fun app99PopupOverLauncherKeepsVisible99Authority() {
        val r = resolution("com.sec.android.app.launcher", app99)
        assertEquals(app99, r.authorityPackageName)
        assertTrue(r.allowVisibleRootOverride)
    }

    @Test fun app99PopupOverMapsKeepsVisible99Authority() {
        val r = resolution("com.google.android.apps.maps", app99)
        assertEquals(app99, r.authorityPackageName)
        assertFalse(r.failClosed)
    }

    @Test fun inDrivePopupOverLauncherKeepsVisibleInDriveAuthority() {
        val r = resolution("com.sec.android.app.launcher", inDrive)
        assertEquals(inDrive, r.authorityPackageName)
        assertTrue(r.allowVisibleRootOverride)
    }

    @Test fun inDrivePopupOverMapsKeepsVisibleInDriveAuthority() {
        val r = resolution("com.google.android.apps.maps", inDrive)
        assertEquals(inDrive, r.authorityPackageName)
        assertFalse(r.failClosed)
    }

    @Test fun systemUiTransientDoesNotBlockValidCard() {
        val r = resolution("com.android.systemui", uber, active = uber)
        assertEquals(FarolAppIdentityIsolationStage18.Outcome.PASSIVE_EVENT_VISIBLE_SELECTED_APP, r.outcome)
        assertEquals(uber, r.authorityPackageName)
        assertFalse(r.confirmedAppSwitch)
    }

    @Test fun oldSelectedRootNeverOverridesExplicitDifferentSelectedEvent() {
        assertCrossAppFails(uber, app99)
    }

    @Test fun backgroundSelectedEventDoesNotAutomaticallyDisplaceCurrentVisibleCard() {
        val r = resolution(uber, app99, active = app99)
        assertTrue(r.failClosed)
        assertTrue(r.preserveCurrentSession)
        assertFalse(r.confirmedAppSwitch)
        assertNull(r.authorityPackageName)
    }

    @Test fun explicitSelectedEventWithoutCompatibleRootWaitsFailClosed() {
        val r = resolution(uber, null, active = app99)
        assertEquals(FarolAppIdentityIsolationStage18.Outcome.FAIL_CLOSED_EXPLICIT_APP_WITHOUT_COMPATIBLE_ROOT, r.outcome)
        assertTrue(r.failClosed)
        assertTrue(r.preserveCurrentSession)
    }

    @Test fun sameAppExplicitEventAndRootRemainEligible() {
        val r = resolution(uber, uber, active = uber)
        assertEquals(FarolAppIdentityIsolationStage18.Outcome.EXPLICIT_SELECTED_APP_MATCH, r.outcome)
        assertFalse(r.failClosed)
        assertFalse(r.confirmedAppSwitch)
        assertEquals(uber, r.authorityPackageName)
    }

    @Test fun oldOcrBindingCannotApplyAfterAppSwitch() {
        val old = binding(packageName = uber, session = 10)
        val current = binding(packageName = app99, session = 11, signature = "99|rua c 30")
        assertFalse(FarolAppIdentityIsolationStage18.bindingMatchesCurrent(old, current))
    }

    @Test fun oldRouteBindingCannotPaintAfterAppSwitch() {
        val old = binding(packageName = app99, session = 20)
        val current = binding(packageName = inDrive, session = 21, signature = "indrive|rua d 40")
        assertFalse(FarolAppIdentityIsolationStage18.bindingMatchesCurrent(old, current))
    }

    @Test fun oldCacheBindingCannotFastPathAfterAppSwitch() {
        val old = binding(packageName = inDrive, session = 30)
        val current = binding(packageName = uber, session = 31, signature = "uber|rua e 50")
        assertFalse(FarolAppIdentityIsolationStage18.bindingMatchesCurrent(old, current))
    }

    @Test fun sameOfferTransientEmptyIdentityRemainsCurrent() {
        val current = binding()
        assertTrue(FarolAppIdentityIsolationStage18.bindingMatchesCurrent(current, current.copy()))
    }

    @Test fun confirmedDisappearanceCannotReusePreviousWindowGeneration() {
        val old = binding(windowGeneration = 4)
        val current = binding(windowGeneration = 5)
        assertFalse(FarolAppIdentityIsolationStage18.bindingMatchesCurrent(old, current))
    }

    @Test fun samePackageNewSessionCannotReusePreviousResults() {
        val old = binding(session = 7)
        val current = binding(session = 8)
        assertFalse(FarolAppIdentityIsolationStage18.bindingMatchesCurrent(old, current))
    }

    @Test fun samePackageNewScreenGenerationCannotReusePreviousResults() {
        val old = binding(screen = 99)
        val current = binding(screen = 100)
        assertFalse(FarolAppIdentityIsolationStage18.bindingMatchesCurrent(old, current))
    }

    @Test fun mixedUberAnd99BlocksAreRejected() {
        assertFalse(FarolAppIdentityIsolationStage18.blocksBelongToSingleAuthority(uber, listOf(uber, app99)))
    }

    @Test fun mixed99AndInDriveBlocksAreRejected() {
        assertFalse(FarolAppIdentityIsolationStage18.blocksBelongToSingleAuthority(app99, listOf(app99, inDrive)))
    }

    @Test fun coherentSingleAppBlocksAreAccepted() {
        assertTrue(FarolAppIdentityIsolationStage18.blocksBelongToSingleAuthority(inDrive, listOf(inDrive, inDrive)))
    }

    @Test fun emptyBlocksCannotEstablishAuthority() {
        assertFalse(FarolAppIdentityIsolationStage18.blocksBelongToSingleAuthority(uber, emptyList()))
    }
}
'''


def fail(message: str) -> None:
    raise SystemExit(message)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f'âncora Stage18 inesperada ({label}): {count}')
    return text.replace(old, new, 1)


def require_stage16(root: Path) -> tuple[str, str, str]:
    for rel in (SERVICE, HELPER16, MAPS, DECISION, BUILD):
        if not (root / rel).is_file(): fail(f'arquivo Stage16 ausente: {rel}')
    build = (root / BUILD).read_text(encoding='utf-8')
    if 'versionCode = 5480' not in build or 'versionName = "0.1.196"' not in build:
        fail('Stage18 exige Stage16 materializada 0.1.196/5480')
    service = (root / SERVICE).read_text(encoding='utf-8')
    helper16 = (root / HELPER16).read_text(encoding='utf-8')
    if 'VISIBLE_CARD_PRIORITY_AND_TRANSIENT_EMPTY_STAGE16' not in helper16:
        fail('contrato Stage16 ausente')
    for required in (
        'visualAuthorityOverridesEventStage16',
        'resolveVisibleAuthorizedRootStage16',
        'BUBBLE_ROUTE_GATE_CACHE_HIT_STAGE16',
        'routeResultMayPaint',
        'clearStage16VisualProof()',
    ):
        if required not in service: fail(f'contrato Stage16 ausente na service: {required}')
    if sha(root / MAPS) != MAPS_SHA:
        fail('GoogleMapsService divergiu antes da Stage18')
    if (root / HELPER18).exists() or (root / TEST18).exists():
        fail('Stage18 já parece aplicada')
    return service, sha(root / MAPS), sha(root / DECISION)


def transform_service(service: str) -> str:
    old_identity = '''        val visibleRootResolutionStage16 = resolveVisibleAuthorizedRootStage16(selectedPackages156)\n        val visibleSelectedRootStage16 = visibleRootResolutionStage16.rootHandle\n        val visualAuthorityOverridesEventStage16 = visibleSelectedRootStage16 != null &&\n            (normalizePackageName(eventPackage) != normalizePackageName(visibleSelectedRootStage16.packageName) ||\n                eventWindowId0187 != visibleSelectedRootStage16.windowId)\n        if (!visualAuthorityOverridesEventStage16 && ExplicitPackageTransitionPolicy0185.shouldReject(\n'''
    new_identity = '''        val visibleRootResolutionStage16 = resolveVisibleAuthorizedRootStage16(selectedPackages156)\n        val visibleSelectedRootStage16 = visibleRootResolutionStage16.rootHandle\n        val activeSessionBeforeIdentityStage18 = driverCardSessionGate0162.current()\n        val identityResolutionStage18 = FarolAppIdentityIsolationStage18.resolve(\n            eventPackageName = eventPackage,\n            visibleSelectedPackageName = visibleSelectedRootStage16?.packageName,\n            selectedPackages = selectedPackages156,\n            activeSessionPackageName = activeSessionBeforeIdentityStage18?.packageName,\n        )\n        if (identityResolutionStage18.failClosed) {\n            UnifiedDebugEventStore.record(\n                "BUBBLE_IDENTITY_FAIL_CLOSED_STAGE18",\n                identityResolutionStage18.explicitSelectedPackageName ?: eventPackage.orEmpty(),\n                "outcome=${identityResolutionStage18.outcome}; eventPackage=${eventPackage.orEmpty()}; visibleSelectedPackage=${visibleSelectedRootStage16?.packageName.orEmpty()}; activeSession=${activeSessionBeforeIdentityStage18?.packageName.orEmpty()}",\n            )\n            identityResolutionStage18.explicitSelectedPackageName?.let(::scheduleScreenshotFallback127)\n            return\n        }\n        if (identityResolutionStage18.confirmedAppSwitch) {\n            UnifiedDebugEventStore.record(\n                "BUBBLE_APP_IDENTITY_SWITCH_STAGE18",\n                identityResolutionStage18.authorityPackageName.orEmpty(),\n                "from=${activeSessionBeforeIdentityStage18?.packageName.orEmpty()}; to=${identityResolutionStage18.authorityPackageName.orEmpty()}; old session/card/OCR/cache/route invalidated before new authority",\n            )\n            driverCardSessionGate0162.invalidate()\n            clearStage16VisualProof()\n            universalRouteJob?.cancel()\n            hardClearUniversalTwoAddress(\n                reason = "Troca confirmada de aplicativo de corrida; autoridade anterior invalidada antes da nova leitura.",\n                keepWaitingYellow = true,\n            )\n        }\n        val visualAuthorityOverridesEventStage16 = identityResolutionStage18.allowVisibleRootOverride &&\n            visibleSelectedRootStage16 != null &&\n            (normalizePackageName(eventPackage) != normalizePackageName(visibleSelectedRootStage16.packageName) ||\n                eventWindowId0187 != visibleSelectedRootStage16.windowId)\n        if (!visualAuthorityOverridesEventStage16 && ExplicitPackageTransitionPolicy0185.shouldReject(\n'''
    service = replace_once(service, old_identity, new_identity, 'identity resolution before visual override')

    old_gate = '''        val gateSnapshotStage16 = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(\n            packageName = packageName0188,\n            sessionGeneration = readBinding0187?.sessionGeneration ?: session0188.generation,\n            expectedWindowId = expectedWindow0188,\n            screenGeneration = readBinding0187?.screenGeneration ?: universalScreenGeneration,\n            windowGeneration = readBinding0187?.windowGeneration ?: universalWindowGeneration,\n            blocks = blocks0188.map(::toStage16BlockEvidence),\n        )\n'''
    new_gate = '''        if (!FarolAppIdentityIsolationStage18.blocksBelongToSingleAuthority(\n                authorityPackageName = packageName0188,\n                blockPackages = blocks0188.map { it.packageName },\n            )\n        ) {\n            UnifiedDebugEventStore.record(\n                "BUBBLE_MIXED_APP_BLOCKS_REJECTED_STAGE18", packageName0188,\n                "blocos de pacotes distintos não podem formar um único card/snapshot",\n            )\n            return null\n        }\n        val gateSnapshotStage16 = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(\n            packageName = packageName0188,\n            sessionGeneration = readBinding0187?.sessionGeneration ?: session0188.generation,\n            expectedWindowId = expectedWindow0188,\n            screenGeneration = readBinding0187?.screenGeneration ?: universalScreenGeneration,\n            windowGeneration = readBinding0187?.windowGeneration ?: universalWindowGeneration,\n            blocks = blocks0188.map(::toStage16BlockEvidence),\n        )\n'''
    service = replace_once(service, old_gate, new_gate, 'single-app block gate')
    return service


def audit(root: Path, maps_before: str, decision_before: str) -> None:
    if sha(root / MAPS) != maps_before or maps_before != MAPS_SHA:
        fail('GoogleMapsService foi alterado na Stage18')
    if sha(root / DECISION) != decision_before:
        fail('DecisionEngine foi alterado na Stage18')
    service = (root / SERVICE).read_text(encoding='utf-8')
    helper = (root / HELPER18).read_text(encoding='utf-8')
    test = (root / TEST18).read_text(encoding='utf-8')
    for required in (
        'BUBBLE_IDENTITY_FAIL_CLOSED_STAGE18',
        'BUBBLE_APP_IDENTITY_SWITCH_STAGE18',
        'BUBBLE_MIXED_APP_BLOCKS_REJECTED_STAGE18',
        'clearStage16VisualProof()',
        'universalRouteJob?.cancel()',
        'routeResultMayPaint',
        'BUBBLE_ROUTE_GATE_CACHE_HIT_STAGE16',
    ):
        if required not in service: fail(f'integração Stage18 ausente: {required}')
    for required in (
        MARKER,
        'FAIL_CLOSED_CROSS_APP_ROOT',
        'FAIL_CLOSED_EXPLICIT_APP_WITHOUT_COMPATIBLE_ROOT',
        'bindingMatchesCurrent',
        'blocksBelongToSingleAuthority',
    ):
        if required not in helper: fail(f'helper Stage18 incompleto: {required}')
    if test.count('@Test') < 30:
        fail(f'esperados >=30 testes Stage18, encontrados {test.count("@Test")}')
    for forbidden in ('Thread.sleep(', 'SystemClock.sleep(', 'Timer(', 'scheduleAtFixedRate('):
        if forbidden in helper: fail(f'atraso proibido Stage18: {forbidden}')


def self_test() -> None:
    if MARKER not in HELPER_SOURCE: fail('marker Stage18 ausente')
    count = TEST_SOURCE.count('@Test')
    if count < 30: fail(f'self-test exige >=30 testes Stage18, encontrados {count}')
    for required in (
        'eventUberRoot99FailsClosed',
        'eventUberRootInDriveFailsClosed',
        'event99RootUberFailsClosed',
        'event99RootInDriveFailsClosed',
        'eventInDriveRootUberFailsClosed',
        'eventInDriveRoot99FailsClosed',
        'oldOcrBindingCannotApplyAfterAppSwitch',
        'oldRouteBindingCannotPaintAfterAppSwitch',
        'oldCacheBindingCannotFastPathAfterAppSwitch',
        'backgroundSelectedEventDoesNotAutomaticallyDisplaceCurrentVisibleCard',
        'systemUiTransientDoesNotBlockValidCard',
        'sameOfferTransientEmptyIdentityRemainsCurrent',
    ):
        if required not in TEST_SOURCE: fail(f'regressão Stage18 ausente: {required}')
    print('app_identity_isolation_stage18_self_test=passed')
    print(f'stage18_test_methods={count}')
    print('foreground_requirement_added=false')
    print('selected_app_role=read_authorization_only')
    print('cross_app_root_override=false')
    print('background_event_displaces_visible_card=false')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_root', nargs='?', type=Path)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        self_test()
        if args.source_root is None: return
    if args.source_root is None: fail('source_root obrigatório')
    root = args.source_root.resolve()
    service, maps_before, decision_before = require_stage16(root)
    transformed = transform_service(service)
    if args.check:
        for required in ('BUBBLE_IDENTITY_FAIL_CLOSED_STAGE18', 'BUBBLE_MIXED_APP_BLOCKS_REJECTED_STAGE18'):
            if required not in transformed: fail(f'dry-run Stage18 incompleto: {required}')
        print('app_identity_isolation_stage18_check=passed')
        print('stage16_contract_preserved=true')
        print('google_maps_service_unchanged=true')
        print('decision_engine_unchanged=true')
        return
    (root / SERVICE).write_text(transformed, encoding='utf-8')
    (root / HELPER18).write_text(HELPER_SOURCE, encoding='utf-8')
    (root / TEST18).write_text(TEST_SOURCE, encoding='utf-8')
    build_path = root / BUILD
    build = build_path.read_text(encoding='utf-8')
    build = replace_once(build, 'versionCode = 5480', 'versionCode = 5482', 'versionCode Stage18')
    build = replace_once(build, 'versionName = "0.1.196"', 'versionName = "0.1.198"', 'versionName Stage18')
    build_path.write_text(build, encoding='utf-8')
    audit(root, maps_before, decision_before)
    print('app_identity_isolation_stage18_apply=passed')
    print('versionName=0.1.198')
    print('versionCode=5482')
    print('cross_app_identity_contamination_blocked=true')
    print('selected_app_role=read_authorization_only')
    print('foreground_requirement_added=false')
    print('google_maps_service_unchanged=true')
    print('decision_engine_unchanged=true')


if __name__ == '__main__':
    main()
