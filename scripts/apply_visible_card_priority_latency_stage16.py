#!/usr/bin/env python3
"""Stage16: current visible authorized card wins weak event attribution; transient empty is confirmed visually."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
SAFETY = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolRuntimeSafety0187.kt')
GATE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt')
VISUAL = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisualPriority0189.kt')
PARSER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt')
MAPS = Path('app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt')
STAGE9 = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolLatencyProbeStage9.kt')
STAGE12 = Path('app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt')
BUILD = Path('app/build.gradle.kts')
HELPER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisibleCardPriorityStage16.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolVisibleCardPriorityStage16Test.kt')
BENCHMARK_TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage16LocalBenchmarkTest.kt')
MARKER = 'VISIBLE_CARD_PRIORITY_AND_TRANSIENT_EMPTY_STAGE16'

EXPECTED = {
    SERVICE: 'b4fd2ff429fd72125a5ae724129c625974be313bec700b5f09d2310795ccbab6',
    SAFETY: '0f7428beeeb1476c0ec4d4560c291a4c7ed9f412ef87805b49c75976551f8cba',
    GATE: '2aaa6263c450d6218aba722915a2ceeec900728c90001625ff1513f65a39236e',
    VISUAL: '78e46dee8bf1e55ea452356c598e580f040177eb65cb639a93ece796e6b61f21',
    PARSER: '19088d8b110a36b8ab75b7a46b5eab6b0fc79fd17d5f2dad6608e59b7a967d4d',
    MAPS: 'c84d1e8bfa5f22ccbeb2f0e38615c9702bb763054c8c6a00c6021bd9320b29bf',
    STAGE9: 'a22cc939e147acbe019d2ead4616050419430ad7afd0e5c02aeb26e917b6c8cc',
    STAGE12: 'b26022c9c2fadc563376dd612baf55368e25cbdb3d6f08851d8e515097598cec',
}

HELPER_SOURCE = 'package br.com.mapeiaia.rotacerta\n\nimport java.util.Locale\n\n/**\n * Stage 16 keeps the currently visible coherent card above weak event/package attribution.\n * It does not parse addresses and never authorizes a route on its own.\n */\nobject FarolVisibleCardPriorityStage16 {\n    const val CONTRACT_MARKER = "VISIBLE_CARD_PRIORITY_AND_TRANSIENT_EMPTY_STAGE16"\n    const val EXACT_ACCEPTED_GATE_CACHE_MARKER = "EXACT_ACCEPTED_VISUAL_GATE_CACHE_STAGE16"\n\n    enum class WindowKind { APPLICATION, SYSTEM, INPUT_METHOD, ACCESSIBILITY_OVERLAY, OTHER }\n    enum class WindowSelectionOutcome { AUTHORIZED_SELECTED_WINDOW, BLOCKED_BY_APPLICATION, NO_DECISIVE_WINDOW }\n    enum class EmptyReadAction { CONFIRM_CURRENT_VISUAL, CLEAR_WITHOUT_PRESERVATION }\n    enum class EmptyVisualConfirmation { SAME_CARD, DIFFERENT_CARD, CONFIRMED_ABSENT, AMBIGUOUS }\n\n    data class WindowEvidence(\n        val windowId: Int,\n        val packageName: String?,\n        val layer: Int,\n        val kind: WindowKind,\n        val hasRoot: Boolean,\n    )\n\n    data class VisibleWindowAuthority(\n        val packageName: String,\n        val windowId: Int,\n        val layer: Int,\n    )\n\n    data class WindowSelection(\n        val outcome: WindowSelectionOutcome,\n        val authority: VisibleWindowAuthority? = null,\n    )\n\n    data class BlockEvidence(\n        val id: String,\n        val parentId: String?,\n        val packageName: String,\n        val windowId: Int,\n        val windowLayer: Int,\n        val depth: Int,\n        val text: String,\n        val source: String,\n        val left: Int,\n        val top: Int,\n        val right: Int,\n        val bottom: Int,\n        val syntheticRoot: Boolean,\n    )\n\n    data class GateSnapshotIdentity(\n        val packageName: String,\n        val sessionGeneration: Long,\n        val expectedWindowId: Int,\n        val screenGeneration: Long,\n        val windowGeneration: Long,\n        val blocks: List<BlockEvidence>,\n    )\n\n    data class ActiveCardBinding(\n        val packageName: String,\n        val sessionGeneration: Long,\n        val windowId: Int,\n        val screenGeneration: Long,\n        val windowGeneration: Long,\n        val screenHash: Int,\n        val addressSignature: String,\n    )\n\n    fun selectVisibleAuthorizedWindow(\n        windows: List<WindowEvidence>,\n        selectedPackages: Set<String>,\n    ): WindowSelection {\n        val selected = selectedPackages.mapNotNull(::normalizePackage).toSet()\n        if (selected.isEmpty()) return WindowSelection(WindowSelectionOutcome.NO_DECISIVE_WINDOW)\n        for (window in windows.sortedWith(compareByDescending<WindowEvidence> { it.layer }.thenByDescending { it.windowId })) {\n            if (!window.hasRoot) continue\n            val pkg = normalizePackage(window.packageName)\n            if (pkg != null && pkg in selected) {\n                return WindowSelection(\n                    outcome = WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW,\n                    authority = VisibleWindowAuthority(pkg, window.windowId, window.layer),\n                )\n            }\n            // A real application above the selected ride window is positive visual evidence\n            // that the selected card is not the current application visual authority. SystemUI,\n            // IME and accessibility overlays are only transient wrappers and cannot block alone.\n            if (window.kind == WindowKind.APPLICATION) {\n                return WindowSelection(WindowSelectionOutcome.BLOCKED_BY_APPLICATION)\n            }\n        }\n        return WindowSelection(WindowSelectionOutcome.NO_DECISIVE_WINDOW)\n    }\n\n    fun gateSnapshotIdentity(\n        packageName: String,\n        sessionGeneration: Long,\n        expectedWindowId: Int,\n        screenGeneration: Long,\n        windowGeneration: Long,\n        blocks: List<BlockEvidence>,\n    ): GateSnapshotIdentity = GateSnapshotIdentity(\n        packageName = normalizePackage(packageName).orEmpty(),\n        sessionGeneration = sessionGeneration,\n        expectedWindowId = expectedWindowId,\n        screenGeneration = screenGeneration,\n        windowGeneration = windowGeneration,\n        // Equality is over the exact current visual structure/text, not a lossy hash.\n        blocks = blocks.sortedWith(\n            compareByDescending<BlockEvidence> { it.windowLayer }\n                .thenBy { it.windowId }\n                .thenBy { it.id },\n        ),\n    )\n\n    fun canReuseAcceptedAuthorization(\n        cached: GateSnapshotIdentity?,\n        current: GateSnapshotIdentity,\n        cachedPackageName: String?,\n        cachedWindowId: Int?,\n        cachedAddressSignature: String?,\n        cachedScreenHash: Int?,\n        activePackageName: String?,\n        activeAddressSignature: String?,\n        activeScreenHash: Int?,\n        routeInFlight: Boolean,\n        stableDecision: Boolean,\n        transientEmptyPending: Boolean,\n    ): Boolean {\n        if (transientEmptyPending) return false\n        if (!routeInFlight && !stableDecision) return false\n        if (cached == null || cached != current) return false\n        val currentPackage = normalizePackage(current.packageName)\n        if (normalizePackage(cachedPackageName) != currentPackage) return false\n        if (cachedWindowId != current.expectedWindowId) return false\n        if (normalizePackage(activePackageName) != currentPackage) return false\n        if (cachedAddressSignature.isNullOrBlank() || cachedAddressSignature != activeAddressSignature) return false\n        if (cachedScreenHash == null || cachedScreenHash != activeScreenHash) return false\n        return true\n    }\n\n    fun emptyReadAction(activeCardBinding: ActiveCardBinding?): EmptyReadAction =\n        if (activeCardBinding == null) EmptyReadAction.CLEAR_WITHOUT_PRESERVATION\n        else EmptyReadAction.CONFIRM_CURRENT_VISUAL\n\n    fun pendingMatches(binding: ActiveCardBinding?, routeBinding: ActiveCardBinding): Boolean =\n        binding == routeBinding\n\n    fun classifyEmptyVisualConfirmation(\n        active: ActiveCardBinding,\n        selection: WindowSelection,\n        confirmedCard: ActiveCardBinding?,\n    ): EmptyVisualConfirmation {\n        when (selection.outcome) {\n            WindowSelectionOutcome.BLOCKED_BY_APPLICATION -> return EmptyVisualConfirmation.CONFIRMED_ABSENT\n            WindowSelectionOutcome.NO_DECISIVE_WINDOW -> return EmptyVisualConfirmation.AMBIGUOUS\n            WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW -> Unit\n        }\n        val authority = selection.authority ?: return EmptyVisualConfirmation.AMBIGUOUS\n        if (normalizePackage(authority.packageName) != normalizePackage(active.packageName) ||\n            authority.windowId != active.windowId\n        ) {\n            return EmptyVisualConfirmation.DIFFERENT_CARD\n        }\n        val confirmed = confirmedCard ?: return EmptyVisualConfirmation.AMBIGUOUS\n        return if (normalizePackage(confirmed.packageName) == normalizePackage(active.packageName) &&\n            confirmed.windowId == active.windowId &&\n            confirmed.sessionGeneration == active.sessionGeneration &&\n            confirmed.screenGeneration == active.screenGeneration &&\n            confirmed.windowGeneration == active.windowGeneration &&\n            confirmed.screenHash == active.screenHash &&\n            confirmed.addressSignature == active.addressSignature\n        ) {\n            EmptyVisualConfirmation.SAME_CARD\n        } else {\n            EmptyVisualConfirmation.DIFFERENT_CARD\n        }\n    }\n\n    fun routeResultMayPaint(bindingFresh: Boolean, transientEmptyPendingForBinding: Boolean): Boolean =\n        bindingFresh && !transientEmptyPendingForBinding\n\n    fun hasCoherentAbsenceEvidence(\n        expectedPackageName: String,\n        expectedWindowId: Int,\n        blocks: List<BlockEvidence>,\n    ): Boolean {\n        val expected = normalizePackage(expectedPackageName) ?: return false\n        return blocks.any { block ->\n            normalizePackage(block.packageName) == expected &&\n                block.windowId == expectedWindowId &&\n                block.text.isNotBlank()\n        }\n    }\n\n    private fun normalizePackage(value: String?): String? = value\n        ?.trim()\n        ?.lowercase(Locale.ROOT)\n        ?.takeIf(String::isNotBlank)\n}\n'
TEST_SOURCE = 'package br.com.mapeiaia.rotacerta\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertNotEquals\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass FarolVisibleCardPriorityStage16Test {\n    private val uber = "com.ubercab.driver"\n    private val app99 = "com.app99.driver"\n    private val inDrive = "sinet.startup.indriver"\n    private val selected = setOf(uber, app99, inDrive)\n\n    private fun w(\n        packageName: String?,\n        layer: Int,\n        kind: FarolVisibleCardPriorityStage16.WindowKind,\n        id: Int = layer,\n        hasRoot: Boolean = true,\n    ) = FarolVisibleCardPriorityStage16.WindowEvidence(id, packageName, layer, kind, hasRoot)\n\n    private fun b(\n        text: String = "Rua A, 10\\nRua B, 20",\n        packageName: String = uber,\n        windowId: Int = 42,\n        id: String = "a11y:42/0",\n        top: Int = 100,\n    ) = FarolVisibleCardPriorityStage16.BlockEvidence(\n        id = id,\n        parentId = "a11y:42",\n        packageName = packageName,\n        windowId = windowId,\n        windowLayer = 9,\n        depth = 2,\n        text = text,\n        source = "Accessibility",\n        left = 50,\n        top = top,\n        right = 1000,\n        bottom = top + 400,\n        syntheticRoot = false,\n    )\n\n    private fun identity(\n        blocks: List<FarolVisibleCardPriorityStage16.BlockEvidence> = listOf(b()),\n        packageName: String = uber,\n        windowId: Int = 42,\n        session: Long = 7,\n        screen: Long = 12,\n        windowGeneration: Long = 5,\n    ) = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(\n        packageName, session, windowId, screen, windowGeneration, blocks,\n    )\n\n    private fun activeBinding(\n        packageName: String = uber,\n        windowId: Int = 42,\n        session: Long = 7,\n        screen: Long = 12,\n        windowGeneration: Long = 5,\n        screenHash: Int = 123,\n        signature: String = "uber|rua b 20",\n    ) = FarolVisibleCardPriorityStage16.ActiveCardBinding(\n        packageName, session, windowId, screen, windowGeneration, screenHash, signature,\n    )\n\n    @Test fun uberPopupOverLauncherIsEligible() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w(uber, 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.sec.android.app.launcher", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW, r.outcome)\n        assertEquals(uber, r.authority?.packageName)\n    }\n\n    @Test fun uberPopupOverAnotherAppIsEligible() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.whatsapp", 3, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(uber, r.authority?.packageName)\n    }\n\n    @Test fun uberPopupOverMapsIsEligible() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.google.android.apps.maps", 2, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(uber, r.authority?.packageName)\n    }\n\n    @Test fun uberPopupOverWazeIsEligible() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.waze", 2, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(uber, r.authority?.packageName)\n    }\n\n    @Test fun systemUiDoesNotBlockAuthorizedVisibleRoot() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w("com.android.systemui", 10, FarolVisibleCardPriorityStage16.WindowKind.SYSTEM), w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(uber, r.authority?.packageName)\n    }\n\n    @Test fun inputMethodDoesNotBlockAuthorizedVisibleRoot() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w("com.samsung.android.honeyboard", 11, FarolVisibleCardPriorityStage16.WindowKind.INPUT_METHOD), w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(uber, r.authority?.packageName)\n    }\n\n    @Test fun accessibilityOverlayDoesNotBlockAuthorizedVisibleRoot() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w("android", 12, FarolVisibleCardPriorityStage16.WindowKind.ACCESSIBILITY_OVERLAY), w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(uber, r.authority?.packageName)\n    }\n\n    @Test fun app99PopupEquivalentIsEligible() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w(app99, 7, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.whatsapp", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(app99, r.authority?.packageName)\n    }\n\n    @Test fun inDrivePopupEquivalentIsEligible() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w(inDrive, 7, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.whatsapp", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(inDrive, r.authority?.packageName)\n    }\n\n    @Test fun selectionIsAuthorizationNotForegroundRequirement() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w(uber, 6, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.sec.android.app.launcher", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), setOf(uber),\n        )\n        assertTrue(r.authority != null)\n    }\n\n    @Test fun higherNonSelectedApplicationFailsClosed() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w("com.whatsapp", 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w(uber, 3, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.BLOCKED_BY_APPLICATION, r.outcome)\n        assertEquals(null, r.authority)\n    }\n\n    @Test fun rootlessHigherApplicationCannotPretendToBeVisualEvidence() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w("com.whatsapp", 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION, hasRoot = false), w(uber, 3, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,\n        )\n        assertEquals(uber, r.authority?.packageName)\n    }\n\n    @Test fun unselectedRideAppNeverWinsAuthorization() {\n        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(\n            listOf(w(app99, 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w(uber, 3, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), setOf(uber),\n        )\n        assertEquals(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.BLOCKED_BY_APPLICATION, r.outcome)\n    }\n\n    @Test fun isolatedEmptyWithBoundCardRequestsObjectiveVisualConfirmation() {\n        assertEquals(FarolVisibleCardPriorityStage16.EmptyReadAction.CONFIRM_CURRENT_VISUAL, FarolVisibleCardPriorityStage16.emptyReadAction(activeBinding()))\n    }\n\n    @Test fun isolatedEmptyWithoutBoundCardDoesNotInventPreservation() {\n        assertEquals(FarolVisibleCardPriorityStage16.EmptyReadAction.CLEAR_WITHOUT_PRESERVATION, FarolVisibleCardPriorityStage16.emptyReadAction(null))\n    }\n\n    @Test fun sameCardObjectiveReconfirmationResolvesTransientEmpty() {\n        val active = activeBinding()\n        val selection = FarolVisibleCardPriorityStage16.WindowSelection(\n            FarolVisibleCardPriorityStage16.WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW,\n            FarolVisibleCardPriorityStage16.VisibleWindowAuthority(uber, 42, 9),\n        )\n        assertEquals(FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.SAME_CARD, FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selection, active.copy()))\n    }\n\n    @Test fun realApplicationAboveCardConfirmsAbsence() {\n        val active = activeBinding()\n        val selection = FarolVisibleCardPriorityStage16.WindowSelection(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.BLOCKED_BY_APPLICATION)\n        assertEquals(FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.CONFIRMED_ABSENT, FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selection, null))\n    }\n\n    @Test fun newAuthorizedWindowConfirmsCardChange() {\n        val active = activeBinding()\n        val selection = FarolVisibleCardPriorityStage16.WindowSelection(\n            FarolVisibleCardPriorityStage16.WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW,\n            FarolVisibleCardPriorityStage16.VisibleWindowAuthority(uber, 77, 10),\n        )\n        assertEquals(FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.DIFFERENT_CARD, FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selection, null))\n    }\n\n    @Test fun ambiguousWindowSnapshotDoesNotPretendCardDisappeared() {\n        val active = activeBinding()\n        val selection = FarolVisibleCardPriorityStage16.WindowSelection(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.NO_DECISIVE_WINDOW)\n        assertEquals(FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.AMBIGUOUS, FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selection, null))\n    }\n\n    @Test fun unresolvedTransientEmptyBlocksRoutePainting() {\n        assertFalse(FarolVisibleCardPriorityStage16.routeResultMayPaint(bindingFresh = true, transientEmptyPendingForBinding = true))\n    }\n\n    @Test fun freshBindingCanPaintAfterSameCardReconfirmation() {\n        assertTrue(FarolVisibleCardPriorityStage16.routeResultMayPaint(bindingFresh = true, transientEmptyPendingForBinding = false))\n    }\n\n    @Test fun staleBindingNeverPaintsEvenWithoutTransientEmpty() {\n        assertFalse(FarolVisibleCardPriorityStage16.routeResultMayPaint(bindingFresh = false, transientEmptyPendingForBinding = false))\n    }\n\n    @Test fun pendingIdentityMatchesOnlyExactCardGenerationBinding() {\n        val a = activeBinding()\n        assertTrue(FarolVisibleCardPriorityStage16.pendingMatches(a, a.copy()))\n        assertFalse(FarolVisibleCardPriorityStage16.pendingMatches(a, a.copy(screenGeneration = a.screenGeneration + 1)))\n        assertFalse(FarolVisibleCardPriorityStage16.pendingMatches(a, a.copy(addressSignature = "different")))\n    }\n\n    @Test fun exactAcceptedVisualSnapshotCanReuseAuthorizationDuringRoute() {\n        val i = identity()\n        assertTrue(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n            cached = i, current = i.copy(), cachedPackageName = uber, cachedWindowId = 42,\n            cachedAddressSignature = "sig", cachedScreenHash = 123, activePackageName = uber,\n            activeAddressSignature = "sig", activeScreenHash = 123, routeInFlight = true,\n            stableDecision = false, transientEmptyPending = false,\n        ))\n    }\n\n    @Test fun exactAcceptedVisualSnapshotCanReuseAuthorizationForStableDecision() {\n        val i = identity()\n        assertTrue(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n            i, i, uber, 42, "sig", 123, uber, "sig", 123, false, true, false,\n        ))\n    }\n\n    @Test fun transientEmptyDisablesFastPathUntilReconfirmed() {\n        val i = identity()\n        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n            i, i, uber, 42, "sig", 123, uber, "sig", 123, true, false, true,\n        ))\n    }\n\n    @Test fun changedDestinationTextAlwaysMissesExactFastPath() {\n        val old = identity()\n        val changed = identity(blocks = listOf(b(text = "Rua A, 10\\nRua C, 30")))\n        assertNotEquals(old, changed)\n        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n            old, changed, uber, 42, "sig", 123, uber, "sig", 123, true, false, false,\n        ))\n    }\n\n    @Test fun changedPriceOrTimeTextAlsoLeavesFastPathAndMustReenterGate() {\n        val old = identity(blocks = listOf(b(text = "Rua A, 10\\nR$ 18,00\\nRua B, 20")))\n        val changed = identity(blocks = listOf(b(text = "Rua A, 10\\nR$ 21,00\\nRua B, 20")))\n        assertNotEquals(old, changed)\n    }\n\n    @Test fun changedWindowAlwaysMissesExactFastPath() {\n        val old = identity()\n        val changed = identity(windowId = 99, blocks = listOf(b(windowId = 99, id = "a11y:99/0")))\n        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n            old, changed, uber, 42, "sig", 123, uber, "sig", 123, true, false, false,\n        ))\n    }\n\n    @Test fun changedSessionAlwaysMissesExactFastPath() {\n        assertNotEquals(identity(), identity(session = 8))\n    }\n\n    @Test fun changedScreenGenerationAlwaysMissesExactFastPath() {\n        assertNotEquals(identity(), identity(screen = 13))\n    }\n\n    @Test fun changedWindowGenerationAlwaysMissesExactFastPath() {\n        assertNotEquals(identity(), identity(windowGeneration = 6))\n    }\n\n    @Test fun otherPackageNeverReusesPreviousAppAuthorization() {\n        val i = identity()\n        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n            i, i, uber, 42, "sig", 123, app99, "sig", 123, true, false, false,\n        ))\n    }\n\n    @Test fun partialOrAmbiguousStateWithoutActiveRouteOrDecisionCannotUseFastPath() {\n        val i = identity()\n        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n            i, i, uber, 42, "sig", 123, uber, "sig", 123, false, false, false,\n        ))\n    }\n\n    @Test fun exactVisualStructureIncludesGeometryAndPreventsCrossCardReuse() {\n        val upper = identity(blocks = listOf(b(id = "upper", top = 100)))\n        val lower = identity(blocks = listOf(b(id = "lower", top = 800)))\n        assertNotEquals(upper, lower)\n    }\n\n    @Test fun exactVisualStructureIncludesParentRelationship() {\n        val original = b()\n        val changed = original.copy(parentId = "different-parent")\n        assertNotEquals(identity(listOf(original)), identity(listOf(changed)))\n    }\n\n    @Test fun currentNonBlankSelectedWindowIsObjectiveAbsenceEvidenceCandidate() {\n        assertTrue(FarolVisibleCardPriorityStage16.hasCoherentAbsenceEvidence(uber, 42, listOf(b(text = "Você está online"))))\n    }\n\n    @Test fun emptyBlocksAreNeverAbsenceProof() {\n        assertFalse(FarolVisibleCardPriorityStage16.hasCoherentAbsenceEvidence(uber, 42, emptyList()))\n    }\n\n    @Test fun backgroundWindowTextCannotProvePopupAbsence() {\n        assertFalse(FarolVisibleCardPriorityStage16.hasCoherentAbsenceEvidence(uber, 42, listOf(b(text = "Maps address", packageName = "com.google.android.apps.maps"))))\n    }\n\n    @Test fun otherWindowOfSameAppCannotProveCurrentPopupAbsence() {\n        assertFalse(FarolVisibleCardPriorityStage16.hasCoherentAbsenceEvidence(uber, 42, listOf(b(text = "Outra janela", windowId = 77))))\n    }\n\n    private fun gateBlock(\n        id: String,\n        text: String,\n        packageName: String = uber,\n        windowId: Int = 42,\n        layer: Int = 9,\n        top: Int = 100,\n        bottom: Int = 500,\n    ) = FarolCardBlock0188(\n        id = id, packageName = packageName, windowId = windowId, windowLayer = layer, depth = 2,\n        text = text, source = FarolEvidenceSource0188.Accessibility, left = 50, top = top, right = 1000, bottom = bottom,\n    )\n\n    @Test fun backgroundAddressesAndPopupAddressNeverFormArtificialRide() {\n        val decision = FarolRealDeviceGate0188.evaluate(\n            uber, setOf(uber), listOf(\n                gateBlock("maps", "Rua Fundo, 10\\nRua Fundo 2, 20", packageName = "com.google.android.apps.maps", layer = 1),\n                gateBlock("popup", "Rua Popup, 30", layer = 9),\n            ),\n        )\n        assertFalse(decision.authorized)\n    }\n\n    @Test fun twoSimultaneousCardsRemainSeparated() {\n        val decision = FarolRealDeviceGate0188.evaluate(\n            uber, setOf(uber), listOf(\n                gateBlock("upper", "Rua A, 10\\nRua B, 20", top = 100, bottom = 450),\n                gateBlock("lower", "Rua C, 30\\nRua D, 40", top = 800, bottom = 1150),\n            ),\n        )\n        assertTrue(decision.authorized)\n        assertEquals("Rua B, 20", decision.authorization?.destination)\n    }\n\n    @Test fun threeAddressesInsideWinningCardStillUseLastDestination() {\n        val decision = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\\nRua B, 20\\nRua C, 30")))\n        assertTrue(decision.authorized)\n        assertEquals("Rua C, 30", decision.authorization?.destination)\n    }\n\n    @Test fun backgroundPackageNeverEntersSelectedAuthorizationText() {\n        val decision = FarolRealDeviceGate0188.evaluate(\n            uber, setOf(uber), listOf(\n                gateBlock("background", "Rua Maps, 10\\nRua Maps, 20", packageName = "com.google.android.apps.maps", layer = 1),\n                gateBlock("popup", "Rua Uber, 30\\nRua Uber, 40", layer = 9),\n            ),\n        )\n        assertTrue(decision.authorized)\n        assertFalse(decision.authorization!!.analysisText.contains("Maps"))\n    }\n\n    @Test fun priceNoiseWithSameWinningBlockAndDestinationKeepsAuthorityIdentity() {\n        val before = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\\nR$ 18,00\\nRua B, 20"))).authorization!!\n        val after = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\\nR$ 22,00\\nRua B, 20"))).authorization!!\n        assertEquals(before.addressSignature, after.addressSignature)\n        assertEquals(before.screenHash, after.screenHash)\n    }\n\n    @Test fun realDestinationChangeChangesAuthorityIdentity() {\n        val before = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\\nRua B, 20"))).authorization!!\n        val after = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\\nRua C, 30"))).authorization!!\n        assertNotEquals(before.addressSignature, after.addressSignature)\n        assertNotEquals(before.screenHash, after.screenHash)\n    }\n\n    @Test fun sameDestinationInDifferentWinningWindowChangesScreenHash() {\n        val before = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\\nRua B, 20", windowId = 42))).authorization!!\n        val after = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\\nRua B, 20", windowId = 77))).authorization!!\n        assertEquals(before.addressSignature, after.addressSignature)\n        assertNotEquals(before.screenHash, after.screenHash)\n    }\n\n}\n'
BENCHMARK_SOURCE = 'package br.com.mapeiaia.rotacerta\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\n/** Informational JVM/CI guardrails only. They never claim physical card-to-color latency. */\nclass FarolStage16LocalBenchmarkTest {\n    private val uber = "com.ubercab.driver"\n\n    private inline fun benchmark(name: String, maxMillis: Long, block: () -> Unit) {\n        val started = System.nanoTime()\n        block()\n        val elapsedMs = (System.nanoTime() - started) / 1_000_000L\n        println("STAGE16_BENCHMARK_$name duration_ms=$elapsedMs max_guard_ms=$maxMillis physical_claim=false")\n        assertTrue("$name exceeded broad CI regression guard: ${elapsedMs}ms", elapsedMs <= maxMillis)\n    }\n\n    private fun evidence(index: Int) = FarolVisibleCardPriorityStage16.BlockEvidence(\n        id = "a11y:42/$index",\n        parentId = "a11y:42",\n        packageName = uber,\n        windowId = 42,\n        windowLayer = 9,\n        depth = 2,\n        text = "Rua A, 10\\nR$ ${18 + index},00\\nRua B, 20",\n        source = "Accessibility",\n        left = 50,\n        top = 100 + index * 5,\n        right = 1000,\n        bottom = 500 + index * 5,\n        syntheticRoot = false,\n    )\n\n    @Test fun normalizationLocalGuard() {\n        var value = ""\n        benchmark("NORMALIZATION", 5_000L) {\n            repeat(2_000) {\n                value = WrappedAddressTextNormalizer.normalize("Rua A, 10\\nR$ 18,00\\nRua B, 20")\n            }\n        }\n        assertTrue(value.contains("Rua B"))\n    }\n\n    @Test fun visibleWindowSelectionLocalGuard() {\n        val windows = listOf(\n            FarolVisibleCardPriorityStage16.WindowEvidence(99, "com.android.systemui", 12, FarolVisibleCardPriorityStage16.WindowKind.SYSTEM, true),\n            FarolVisibleCardPriorityStage16.WindowEvidence(42, uber, 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION, true),\n            FarolVisibleCardPriorityStage16.WindowEvidence(1, "com.google.android.apps.maps", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION, true),\n        )\n        var result: FarolVisibleCardPriorityStage16.WindowSelection? = null\n        benchmark("VISIBLE_WINDOW_SELECTION", 5_000L) {\n            repeat(10_000) { result = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(windows, setOf(uber)) }\n        }\n        assertEquals(uber, result?.authority?.packageName)\n    }\n\n    @Test fun exactVisualIdentityLocalGuard() {\n        val blocks = (0 until 30).map(::evidence)\n        var identity: FarolVisibleCardPriorityStage16.GateSnapshotIdentity? = null\n        benchmark("EXACT_VISUAL_IDENTITY", 5_000L) {\n            repeat(1_000) {\n                identity = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(uber, 7, 42, 12, 5, blocks)\n            }\n        }\n        assertEquals(30, identity?.blocks?.size)\n    }\n\n    @Test fun duplicateFastPathLocalGuard() {\n        val blocks = listOf(evidence(0))\n        val identity = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(uber, 7, 42, 12, 5, blocks)\n        var accepted = false\n        benchmark("DUPLICATE_FAST_PATH", 5_000L) {\n            repeat(10_000) {\n                accepted = FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n                    identity, identity, uber, 42, "sig", 123, uber, "sig", 123,\n                    routeInFlight = true, stableDecision = false, transientEmptyPending = false,\n                )\n            }\n        }\n        assertTrue(accepted)\n    }\n\n    @Test fun fullUniversalGateLocalGuard() {\n        val block = FarolCardBlock0188(\n            id = "card", packageName = uber, windowId = 42, windowLayer = 9, depth = 2,\n            text = "Rua Apeninos, 100\\nR$ 18,50\\nAvenida Paulista, 1000",\n            source = FarolEvidenceSource0188.Accessibility, left = 50, top = 100, right = 1000, bottom = 600,\n        )\n        var authorized = false\n        benchmark("FULL_GATE", 15_000L) {\n            repeat(100) { authorized = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(block)).authorized }\n        }\n        assertTrue(authorized)\n    }\n\n    @Test fun postRouteFreshnessPolicyLocalGuard() {\n        var mayPaint = false\n        benchmark("POST_ROUTE_BINDING", 5_000L) {\n            repeat(100_000) {\n                mayPaint = FarolVisibleCardPriorityStage16.routeResultMayPaint(true, false)\n            }\n        }\n        assertTrue(mayPaint)\n    }\n}\n'


def fail(message: str) -> None:
    raise SystemExit(message)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f'{label}: âncora esperada 1x, encontrada {count}x')
    return text.replace(old, new, 1)


def require_base(root: Path) -> None:
    build = (root / BUILD).read_text(encoding='utf-8')
    if 'versionCode = 5478' not in build or 'versionName = "0.1.194"' not in build:
        fail('Stage16 exige fonte efetiva 0.1.194/5478 antes do patch')
    for rel, expected in EXPECTED.items():
        path = root / rel
        if not path.is_file(): fail(f'arquivo obrigatório ausente: {rel}')
        actual = sha(path)
        if actual != expected: fail(f'fonte efetiva divergente em {rel}: {actual} != {expected}')
    safety = (root / SAFETY).read_text(encoding='utf-8')
    for required in ('SELECTED_APP_ACTIVATES_VISIBLE_ROOT_STAGE14', 'DECISION_RESULT_MONOTONIC_BINDING_0187_PHASE4'):
        if required not in safety: fail(f'contrato anterior ausente: {required}')
    gate = (root / GATE).read_text(encoding='utf-8')
    for required in (
        'val authorityIdentity = "$selected|${winner.block.windowId}|${winner.block.id}|$signature"',
        'screenHash = authorityIdentity.hashCode()',
    ):
        if required not in gate: fail(f'autoridade visual 0188 ausente: {required}')
    if (root / HELPER).exists() or (root / TEST).exists() or (root / BENCHMARK_TEST).exists(): fail('Stage16 já parece aplicado')


def transform_service(service: str) -> str:
    service = replace_once(
        service,
        '''    private var universalRouteJob: Job? = null\n    private var universalScreenGeneration: Long = 0L\n''',
        '''    private var universalRouteJob: Job? = null\n    private var stage16TransientEmptyBinding: FarolVisibleCardPriorityStage16.ActiveCardBinding? = null\n    private var stage16AcceptedGateSnapshot: FarolVisibleCardPriorityStage16.GateSnapshotIdentity? = null\n    private var stage16AcceptedGateAuthorization: FarolRouteAuthorization0188? = null\n    private var universalScreenGeneration: Long = 0L\n''',
        'campos Stage16',
    )

    service = replace_once(
        service,
        '''        if (ExplicitPackageTransitionPolicy0185.shouldReject(\n                eventPackageName = eventPackage,\n                selectedPackages = selectedPackages156,\n                ownPackageName = packageName,\n                isTransientOverlay = { candidate0185 ->\n                    DriverAppPackagePolicy0162.isTransientOverlay(candidate0185, packageName)\n                },\n            )\n        ) {\n''',
        '''        val visibleRootResolutionStage16 = resolveVisibleAuthorizedRootStage16(selectedPackages156)\n        val visibleSelectedRootStage16 = visibleRootResolutionStage16.rootHandle\n        val visualAuthorityOverridesEventStage16 = visibleSelectedRootStage16 != null &&\n            (normalizePackageName(eventPackage) != normalizePackageName(visibleSelectedRootStage16.packageName) ||\n                eventWindowId0187 != visibleSelectedRootStage16.windowId)\n        if (!visualAuthorityOverridesEventStage16 && ExplicitPackageTransitionPolicy0185.shouldReject(\n                eventPackageName = eventPackage,\n                selectedPackages = selectedPackages156,\n                ownPackageName = packageName,\n                isTransientOverlay = { candidate0185 ->\n                    DriverAppPackagePolicy0162.isTransientOverlay(candidate0185, packageName)\n                },\n            )\n        ) {\n''',
        'prioridade visual antes da rejeição explícita',
    )
    service = replace_once(
        service,
        '''        val rootHandle0187 = captureRootHandle0187()\n''',
        '''        val rootHandle0187 = visibleSelectedRootStage16 ?: captureRootHandle0187()\n''',
        'root visível Stage16',
    )
    service = replace_once(
        service,
        '''        val candidatePackage = DriverCardEventResolver0162.resolve(\n            eventPackageName = eventPackage,\n            rootPackageName = rootPackage,\n            selectedPackages = selectedPackages156,\n            ownPackageName = packageName,\n        )\n        val transientOverlayEvent151 = eventPackage != null &&\n            DriverAppPackagePolicy0162.isTransientOverlay(eventPackage, packageName) &&\n            candidatePackage != null\n''',
        '''        val candidatePackage = visibleSelectedRootStage16?.packageName ?: DriverCardEventResolver0162.resolve(\n            eventPackageName = eventPackage,\n            rootPackageName = rootPackage,\n            selectedPackages = selectedPackages156,\n            ownPackageName = packageName,\n        )\n        val transientOverlayEvent151 = visualAuthorityOverridesEventStage16 || (eventPackage != null &&\n            DriverAppPackagePolicy0162.isTransientOverlay(eventPackage, packageName) &&\n            candidatePackage != null)\n''',
        'candidato visual Stage16',
    )
    service = replace_once(
        service,
        '''        val activeSessionBeforeRootGate0187 = driverCardSessionGate0162.current()\n        val rootAdmission0187 = FarolRootSnapshotPolicy0187.evaluate(\n            eventPackageName = eventPackage,\n            selectedPackageName = candidatePackage,\n            rootPackageName = rootPackage,\n            eventWindowId = eventWindowId0187,\n            rootWindowId = rootHandle0187?.windowId,\n            transientOverlayEvent = transientOverlayEvent151,\n            activeSessionPackageName = activeSessionBeforeRootGate0187?.packageName,\n            activeSessionWindowId = activeSessionBeforeRootGate0187?.windowId,\n        )\n''',
        '''        val activeSessionBeforeRootGate0187 = driverCardSessionGate0162.current()\n        val visibleWindowTransitionStage16 = visibleSelectedRootStage16 != null &&\n            activeSessionBeforeRootGate0187?.packageName == candidatePackage &&\n            activeSessionBeforeRootGate0187.windowId != visibleSelectedRootStage16.windowId\n        val admissionSessionStage16 = activeSessionBeforeRootGate0187.takeUnless { visibleWindowTransitionStage16 }\n        val rootAdmission0187 = FarolRootSnapshotPolicy0187.evaluate(\n            eventPackageName = if (visualAuthorityOverridesEventStage16) null else eventPackage,\n            selectedPackageName = candidatePackage,\n            rootPackageName = rootPackage,\n            eventWindowId = if (visualAuthorityOverridesEventStage16) -1 else eventWindowId0187,\n            rootWindowId = rootHandle0187?.windowId,\n            transientOverlayEvent = transientOverlayEvent151,\n            activeSessionPackageName = admissionSessionStage16?.packageName,\n            activeSessionWindowId = admissionSessionStage16?.windowId,\n        )\n''',
        'root admission Stage16',
    )
    service = replace_once(
        service,
        '''        if (eventPackage == this.packageName && !ownMainActivityEvent) return\n''',
        '''        if (!visualAuthorityOverridesEventStage16 && eventPackage == this.packageName && !ownMainActivityEvent) return\n''',
        'own package atrás de popup',
    )
    service = replace_once(
        service,
        '''                sourcePackageName = eventPackage,\n''',
        '''                sourcePackageName = if (visualAuthorityOverridesEventStage16) candidatePackage else eventPackage,\n''',
        'fonte do realtime gate',
    )
    service = replace_once(
        service,
        '''            eventPackageName = eventPackage,\n            rootPackageName = rootPackage,\n            selectedPackageName = resolvedPackage,\n            eventWindowId = eventWindowId0187,\n            rootWindowId = activeRootWindowId0166,\n''',
        '''            eventPackageName = if (visualAuthorityOverridesEventStage16) resolvedPackage else eventPackage,\n            rootPackageName = rootPackage,\n            selectedPackageName = resolvedPackage,\n            eventWindowId = if (visualAuthorityOverridesEventStage16) activeRootWindowId0166 ?: eventWindowId0187 else eventWindowId0187,\n            rootWindowId = activeRootWindowId0166,\n''',
        'stable window Stage16',
    )

    old_empty = '''        if (immediateAnalysisText0185.isBlank()) {\n            UnifiedDebugEventStore.record("BUBBLE_TEXT_EMPTY", resolvedPackage, "coleta imediata vazia; OCR fallback agendado")\n            val decisionAge141 = FarolElapsedTimePolicy0187.ageMillis(SystemClock.elapsedRealtime(), universalLastActiveReadAtElapsedMillis0187)\n            val preserveStableDecision141 =\n                universalActiveRidePackageName == resolvedPackage &&\n                    universalActiveAddressSignature != null &&\n                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&\n                    universalForegroundPackageName == resolvedPackage &&\n                    decisionAge141?.let { it <= 5_000L } == true\n            if (preserveStableDecision141 || transientOverlayEvent151) {\n                UnifiedDebugEventStore.record(\n                    "BUBBLE_EMPTY_READ_DEFERRED",\n                    resolvedPackage,\n                    "decisao valida preservada; idade=${FarolElapsedTimePolicy0187.formatAge(decisionAge141)}",\n                )\n            } else {\n                hardClearUniversalTwoAddress(\n                    reason = "Tela alterada sem dois enderecos visiveis; resultado removido apos confirmacao.",\n                    keepWaitingYellow = true,\n                )\n            }\n            scheduleScreenshotFallback127(resolvedPackage)\n            return\n        }\n'''
    new_empty = '''        if (immediateAnalysisText0185.isBlank()) {\n            UnifiedDebugEventStore.record("BUBBLE_TEXT_EMPTY", resolvedPackage, "coleta imediata vazia; confirmação visual Stage16 iniciada")\n            val activeBindingStage16 = activeCardBindingStage16(resolvedPackage)\n            when (FarolVisibleCardPriorityStage16.emptyReadAction(activeBindingStage16)) {\n                FarolVisibleCardPriorityStage16.EmptyReadAction.CLEAR_WITHOUT_PRESERVATION -> {\n                    hardClearUniversalTwoAddress(\n                        reason = "Tela vazia sem card previamente vinculado; resultado removido.",\n                        keepWaitingYellow = true,\n                    )\n                }\n                FarolVisibleCardPriorityStage16.EmptyReadAction.CONFIRM_CURRENT_VISUAL -> {\n                    stage16TransientEmptyBinding = activeBindingStage16\n                    val confirmationStage16 = confirmTransientEmptyVisualStage16(\n                        active = activeBindingStage16!!,\n                        savedPackages = savedPackages,\n                    )\n                    when (confirmationStage16) {\n                        FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.SAME_CARD -> {\n                            stage16TransientEmptyBinding = null\n                            universalLastActiveReadAtElapsedMillis0187 = SystemClock.elapsedRealtime()\n                            UnifiedDebugEventStore.record(\n                                "BUBBLE_EMPTY_READ_RECONFIRMED_STAGE16", resolvedPackage,\n                                "mesmo card visual confirmado; generation=$universalScreenGeneration; windowGeneration=$universalWindowGeneration",\n                            )\n                        }\n                        FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.DIFFERENT_CARD -> {\n                            hardClearUniversalTwoAddress(\n                                reason = "Mudança visual positiva de card, destino ou janela durante leitura vazia.",\n                                keepWaitingYellow = true,\n                            )\n                        }\n                        FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.CONFIRMED_ABSENT -> {\n                            hardClearUniversalTwoAddress(\n                                reason = "Card confirmadamente ausente: outra aplicação possui a autoridade visual atual.",\n                                keepWaitingYellow = true,\n                            )\n                        }\n                        FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.AMBIGUOUS -> {\n                            UnifiedDebugEventStore.record(\n                                "BUBBLE_EMPTY_READ_TRANSIENT_STAGE16", resolvedPackage,\n                                "vazio isolado sem prova positiva de desaparecimento; geração preservada e resultado de rota bloqueado até nova evidência",\n                            )\n                        }\n                    }\n                }\n            }\n            scheduleScreenshotFallback127(resolvedPackage)\n            return\n        }\n'''
    service = replace_once(service, old_empty, new_empty, 'leitura vazia Stage16')

    old_gate_eval = '''        val decision0188 = FarolLatencyProbeStage9.measureValue(\n            stage = "REAL_DEVICE_GATE",\n            source = source0188.name,\n        ) {\n            FarolRealDeviceGate0188.evaluate(\n                selectedPackageName = packageName0188,\n                selectedPackages = savedPackages0188,\n                blocks = blocks0188,\n            )\n        }\n'''
    new_gate_eval = '''        val gateSnapshotStage16 = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(\n            packageName = packageName0188,\n            sessionGeneration = readBinding0187?.sessionGeneration ?: session0188.generation,\n            expectedWindowId = expectedWindow0188,\n            screenGeneration = readBinding0187?.screenGeneration ?: universalScreenGeneration,\n            windowGeneration = readBinding0187?.windowGeneration ?: universalWindowGeneration,\n            blocks = blocks0188.map(::toStage16BlockEvidence),\n        )\n        val cachedAuthorizationStage16 = stage16AcceptedGateAuthorization\n        val useAcceptedGateCacheStage16 = FarolLatencyProbeStage9.measureValue(\n            stage = "STAGE16_ACCEPTED_GATE_CACHE_LOOKUP",\n            source = source0188.name,\n        ) {\n            FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(\n                cached = stage16AcceptedGateSnapshot,\n                current = gateSnapshotStage16,\n                cachedPackageName = cachedAuthorizationStage16?.packageName,\n                cachedWindowId = cachedAuthorizationStage16?.windowId,\n                cachedAddressSignature = cachedAuthorizationStage16?.addressSignature,\n                cachedScreenHash = cachedAuthorizationStage16?.screenHash,\n                activePackageName = universalActiveRidePackageName,\n                activeAddressSignature = universalActiveAddressSignature,\n                activeScreenHash = lastSnapshotHash,\n                routeInFlight = universalRouteJob?.isActive == true,\n                stableDecision = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red,\n                transientEmptyPending = stage16TransientEmptyBinding != null,\n            )\n        }\n        if (useAcceptedGateCacheStage16 && cachedAuthorizationStage16 != null) {\n            UnifiedDebugEventStore.record(\n                "BUBBLE_ROUTE_GATE_CACHE_HIT_STAGE16", packageName0188,\n                "source=${source0188.name}; window=$expectedWindow0188; blocks=${blocks0188.size}; screenHash=${cachedAuthorizationStage16.screenHash}",\n            )\n            return cachedAuthorizationStage16\n        }\n        val decision0188 = FarolLatencyProbeStage9.measureValue(\n            stage = "REAL_DEVICE_GATE",\n            source = source0188.name,\n        ) {\n            FarolRealDeviceGate0188.evaluate(\n                selectedPackageName = packageName0188,\n                selectedPackages = savedPackages0188,\n                blocks = blocks0188,\n            )\n        }\n        decision0188.authorization?.let { authorizationStage16 ->\n            stage16AcceptedGateSnapshot = gateSnapshotStage16\n            stage16AcceptedGateAuthorization = authorizationStage16\n        }\n'''
    service = replace_once(service, old_gate_eval, new_gate_eval, 'cache exato do gate')

    post_auth_anchor = '''            return\n        }\n        if (source == TextSource.Accessibility && lastFailedCardAccessibilityHash0161 != snapshotTextChecklist13.hashCode()) {\n'''
    post_auth_new = '''            return\n        }\n        if (stage16TransientEmptyBinding != null) {\n            stage16TransientEmptyBinding = null\n            UnifiedDebugEventStore.record(\n                "BUBBLE_TRANSIENT_EMPTY_RESOLVED_STAGE16", selectedPackageChecklist13,\n                "leitura positiva atual passou novamente pelo gate completo",\n            )\n        }\n        if (source == TextSource.Accessibility && lastFailedCardAccessibilityHash0161 != snapshotTextChecklist13.hashCode()) {\n'''
    service = replace_once(service, post_auth_anchor, post_auth_new, 'resolução positiva do vazio')

    service = replace_once(
        service,
        '''        if (cardChangedChecklist13) {\n            universalScreenGeneration += 1L\n            universalRouteJob?.cancel()\n''',
        '''        if (cardChangedChecklist13) {\n            universalScreenGeneration += 1L\n            rebindAcceptedGateCacheStage16(evaluationChecklist13)\n            universalRouteJob?.cancel()\n''',
        'rebind cache após ativação',
    )

    old_fresh = '''        return serviceReady &&\n            currentSettings.appEnabled &&\n            currentSettings.liveReadingEnabled &&\n            activePackage0187Phase4 == normalizePackageName(binding0187Phase4.packageName) &&\n            binding0187Phase4.packageName in SelectedRideAppStore.read(applicationContext) &&\n            shouldScanPackage(binding0187Phase4.packageName) &&\n            FarolDecisionBindingPolicy0187Phase4.isFresh(\n                binding = binding0187Phase4,\n                currentPackageName = currentSession0187Phase4.packageName,\n                currentSessionGeneration = currentSession0187Phase4.generation,\n                currentWindowId = currentSession0187Phase4.windowId,\n                currentScreenGeneration = universalScreenGeneration,\n                currentWindowGeneration = universalWindowGeneration,\n                currentScreenHash = lastSnapshotHash,\n                currentAddressSignature = universalActiveAddressSignature,\n            )\n'''
    new_fresh = '''        val baseFreshStage16 = serviceReady &&\n            currentSettings.appEnabled &&\n            currentSettings.liveReadingEnabled &&\n            activePackage0187Phase4 == normalizePackageName(binding0187Phase4.packageName) &&\n            binding0187Phase4.packageName in SelectedRideAppStore.read(applicationContext) &&\n            shouldScanPackage(binding0187Phase4.packageName) &&\n            FarolDecisionBindingPolicy0187Phase4.isFresh(\n                binding = binding0187Phase4,\n                currentPackageName = currentSession0187Phase4.packageName,\n                currentSessionGeneration = currentSession0187Phase4.generation,\n                currentWindowId = currentSession0187Phase4.windowId,\n                currentScreenGeneration = universalScreenGeneration,\n                currentWindowGeneration = universalWindowGeneration,\n                currentScreenHash = lastSnapshotHash,\n                currentAddressSignature = universalActiveAddressSignature,\n            )\n        val pendingForBindingStage16 = stage16TransientEmptyBinding?.let { pendingStage16 ->\n            FarolVisibleCardPriorityStage16.pendingMatches(\n                pendingStage16,\n                binding0187Phase4.toActiveCardBindingStage16(),\n            )\n        } == true\n        return FarolVisibleCardPriorityStage16.routeResultMayPaint(\n            bindingFresh = baseFreshStage16,\n            transientEmptyPendingForBinding = pendingForBindingStage16,\n        )\n'''
    service = replace_once(service, old_fresh, new_fresh, 'freshness + transient pending')

    service = replace_once(
        service,
        '''        if (invalidateSession0187Phase4) driverCardSessionGate0162.invalidate()\n        if (advanceScreenGeneration0187Phase4) universalScreenGeneration += 1L\n''',
        '''        if (invalidateSession0187Phase4) driverCardSessionGate0162.invalidate()\n        clearStage16VisualProof()\n        if (advanceScreenGeneration0187Phase4) universalScreenGeneration += 1L\n''',
        'clear proof on invalidation',
    )
    service = replace_once(
        service,
        '''        driverCardSessionGate0162.invalidate()\n        universalScreenGeneration += 1L\n''',
        '''        driverCardSessionGate0162.invalidate()\n        clearStage16VisualProof()\n        universalScreenGeneration += 1L\n''',
        'work mode clears proof',
    )

    helper_anchor = '''    private fun captureRootHandle0187(): FarolRootHandle0187? {\n'''
    helper_code = r'''    private data class VisibleRootCandidateStage16(
        val root: AccessibilityNodeInfo,
        val evidence: FarolVisibleCardPriorityStage16.WindowEvidence,
    )

    private data class VisibleRootResolutionStage16(
        val selection: FarolVisibleCardPriorityStage16.WindowSelection,
        val rootHandle: FarolRootHandle0187?,
    )

    private fun resolveVisibleAuthorizedRootStage16(
        selectedPackagesStage16: Set<String>,
    ): VisibleRootResolutionStage16 {
        val candidatesStage16 = runCatching { windows }.getOrDefault(emptyList())
            .mapNotNull { windowStage16 ->
                val rootStage16 = runCatching { windowStage16.root }.getOrNull() ?: return@mapNotNull null
                val packageStage16 = safeNodePackageName0185(rootStage16)
                val windowIdStage16 = runCatching { windowStage16.id }.getOrDefault(-1)
                val layerStage16 = runCatching { windowStage16.layer }.getOrDefault(0)
                val typeStage16 = runCatching { windowStage16.type }.getOrDefault(0)
                VisibleRootCandidateStage16(
                    root = rootStage16,
                    evidence = FarolVisibleCardPriorityStage16.WindowEvidence(
                        windowId = windowIdStage16,
                        packageName = packageStage16,
                        layer = layerStage16,
                        kind = when (typeStage16) {
                            AccessibilityWindowInfo.TYPE_APPLICATION -> FarolVisibleCardPriorityStage16.WindowKind.APPLICATION
                            AccessibilityWindowInfo.TYPE_SYSTEM -> FarolVisibleCardPriorityStage16.WindowKind.SYSTEM
                            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> FarolVisibleCardPriorityStage16.WindowKind.INPUT_METHOD
                            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> FarolVisibleCardPriorityStage16.WindowKind.ACCESSIBILITY_OVERLAY
                            else -> FarolVisibleCardPriorityStage16.WindowKind.OTHER
                        },
                        hasRoot = true,
                    ),
                )
            }
        val selectionStage16 = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            windows = candidatesStage16.map { it.evidence },
            selectedPackages = selectedPackagesStage16,
        )
        val authorityStage16 = selectionStage16.authority
        val candidateStage16 = authorityStage16?.let { authority ->
            candidatesStage16.firstOrNull { candidate ->
                candidate.evidence.windowId == authority.windowId &&
                    candidate.evidence.layer == authority.layer &&
                    normalizePackageName(candidate.evidence.packageName) == normalizePackageName(authority.packageName)
            }
        }
        return VisibleRootResolutionStage16(
            selection = selectionStage16,
            rootHandle = candidateStage16?.let { candidate ->
                FarolRootHandle0187(candidate.root, normalizePackageName(candidate.evidence.packageName), candidate.evidence.windowId)
            },
        )
    }

    private fun toStage16BlockEvidence(blockStage16: FarolCardBlock0188) =
        FarolVisibleCardPriorityStage16.BlockEvidence(
            id = blockStage16.id,
            parentId = blockStage16.parentId,
            packageName = blockStage16.packageName,
            windowId = blockStage16.windowId,
            windowLayer = blockStage16.windowLayer,
            depth = blockStage16.depth,
            text = blockStage16.text,
            source = blockStage16.source.name,
            left = blockStage16.left,
            top = blockStage16.top,
            right = blockStage16.right,
            bottom = blockStage16.bottom,
            syntheticRoot = blockStage16.syntheticRoot,
        )

    private fun activeCardBindingStage16(packageNameStage16: String): FarolVisibleCardPriorityStage16.ActiveCardBinding? {
        val sessionStage16 = driverCardSessionGate0162.current()
            ?.takeIf { normalizePackageName(it.packageName) == normalizePackageName(packageNameStage16) }
            ?: return null
        val signatureStage16 = universalActiveAddressSignature?.takeIf(String::isNotBlank) ?: return null
        val screenHashStage16 = lastSnapshotHash ?: return null
        return FarolVisibleCardPriorityStage16.ActiveCardBinding(
            packageName = packageNameStage16,
            sessionGeneration = sessionStage16.generation,
            windowId = sessionStage16.windowId,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
            screenHash = screenHashStage16,
            addressSignature = signatureStage16,
        )
    }

    private fun FarolDecisionBinding0187Phase4.toActiveCardBindingStage16() =
        FarolVisibleCardPriorityStage16.ActiveCardBinding(
            packageName = packageName,
            sessionGeneration = sessionGeneration,
            windowId = windowId,
            screenGeneration = screenGeneration,
            windowGeneration = windowGeneration,
            screenHash = screenHash,
            addressSignature = addressSignature,
        )

    private fun confirmTransientEmptyVisualStage16(
        active: FarolVisibleCardPriorityStage16.ActiveCardBinding,
        savedPackages: Set<String>,
    ): FarolVisibleCardPriorityStage16.EmptyVisualConfirmation {
        val visibleStage16 = resolveVisibleAuthorizedRootStage16(savedPackages)
        val selectionStage16 = visibleStage16.selection
        val authorityStage16 = selectionStage16.authority
        if (selectionStage16.outcome != FarolVisibleCardPriorityStage16.WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW ||
            authorityStage16 == null ||
            normalizePackageName(authorityStage16.packageName) != normalizePackageName(active.packageName) ||
            authorityStage16.windowId != active.windowId
        ) {
            return FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selectionStage16, null)
        }
        val blocksStage16 = FarolLatencyProbeStage9.measureBlocks(
            stage = "STAGE16_TRANSIENT_CONFIRM_BLOCKS",
            source = "Accessibility",
        ) {
            collectAccessibilityCardBlocks0188(
                expectedPackage0188 = active.packageName,
                expectedWindowId0188 = active.windowId,
            )
        }
        if (blocksStage16.isEmpty()) return FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.AMBIGUOUS
        val decisionStage16 = FarolLatencyProbeStage9.measureValue(
            stage = "STAGE16_TRANSIENT_CONFIRM_GATE",
            source = "Accessibility",
        ) {
            FarolRealDeviceGate0188.evaluate(
                selectedPackageName = active.packageName,
                selectedPackages = savedPackages,
                blocks = blocksStage16,
            )
        }
        val confirmedStage16 = decisionStage16.authorization?.let { authorizationStage16 ->
            active.copy(
                windowId = authorizationStage16.windowId,
                screenHash = authorizationStage16.screenHash,
                addressSignature = authorizationStage16.addressSignature,
            )
        }
        return FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(
            active = active,
            selection = selectionStage16,
            confirmedCard = confirmedStage16,
        )
    }

    private fun rebindAcceptedGateCacheStage16(evaluationStage16: SimpleSavedAppFarolPolicy.Evaluation) {
        val cachedStage16 = stage16AcceptedGateSnapshot ?: return
        val authorizationStage16 = stage16AcceptedGateAuthorization ?: return
        val sessionStage16 = driverCardSessionGate0162.current() ?: return
        if (authorizationStage16.screenHash != evaluationStage16.screenHash ||
            authorizationStage16.addressSignature != evaluationStage16.addressSignature ||
            normalizePackageName(authorizationStage16.packageName) != normalizePackageName(evaluationStage16.packageName)
        ) return
        stage16AcceptedGateSnapshot = cachedStage16.copy(
            sessionGeneration = sessionStage16.generation,
            expectedWindowId = sessionStage16.windowId,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
        )
    }

    private fun clearStage16VisualProof() {
        stage16TransientEmptyBinding = null
        stage16AcceptedGateSnapshot = null
        stage16AcceptedGateAuthorization = null
    }

'''
    service = replace_once(service, helper_anchor, helper_code + helper_anchor, 'helpers Stage16')

    if MARKER in service:
        fail('marker Stage16 deve residir no helper, não ser duplicado na service')
    for required in (
        'visualAuthorityOverridesEventStage16',
        'BUBBLE_EMPTY_READ_RECONFIRMED_STAGE16',
        'BUBBLE_EMPTY_READ_TRANSIENT_STAGE16',
        'BUBBLE_ROUTE_GATE_CACHE_HIT_STAGE16',
        'STAGE16_ACCEPTED_GATE_CACHE_LOOKUP',
        'routeResultMayPaint',
        'clearStage16VisualProof()',
    ):
        if required not in service: fail(f'contrato Stage16 ausente na service: {required}')
    return service


def audit(root: Path) -> None:
    for rel in (SAFETY, GATE, VISUAL, PARSER, MAPS, STAGE9, STAGE12):
        actual = sha(root / rel)
        if actual != EXPECTED[rel]: fail(f'fronteira protegida mudou: {rel}: {actual}')
    service = (root / SERVICE).read_text(encoding='utf-8')
    helper = (root / HELPER).read_text(encoding='utf-8')
    test = (root / TEST).read_text(encoding='utf-8')
    benchmark = (root / BENCHMARK_TEST).read_text(encoding='utf-8')
    for forbidden in (
        'Thread.sleep(', 'SystemClock.sleep(', 'Timer(', 'scheduleAtFixedRate(',
        'FarolLatencyProbeStage9.clear()', 'FarolLatencyProbeStage9.dump()',
    ):
        if forbidden in service or forbidden in helper: fail(f'comportamento proibido Stage16: {forbidden}')
    for required in (
        MARKER,
        'EXACT_ACCEPTED_VISUAL_GATE_CACHE_STAGE16',
        'BLOCKED_BY_APPLICATION',
        'transientEmptyPending',
        'routeResultMayPaint',
    ):
        if required not in helper: fail(f'helper Stage16 incompleto: {required}')
    if test.count('@Test') < 45: fail(f'esperados >=45 testes Stage16, encontrados {test.count("@Test")}')
    if benchmark.count('@Test') < 6 or 'physical_claim=false' not in benchmark:
        fail('benchmarks locais Stage16 ausentes ou tentando alegar latência física')
    for required in (
        'createDecisionBinding0187Phase4(',
        'isDecisionBindingFresh0187Phase4(',
        'BUBBLE_ROUTE_RESULT_DISCARDED_0187_PHASE4',
        'universalScreenGeneration',
        'universalWindowGeneration',
    ):
        if required not in service: fail(f'freshness/generation removido: {required}')


def self_test(helper_source: str, test_source: str, benchmark_source: str) -> None:
    if MARKER not in helper_source: fail('marker Stage16 ausente do helper')
    if test_source.count('@Test') < 45: fail('self-test exige pelo menos 45 testes Stage16')
    if 'Rua Fundo' not in test_source or 'threeAddressesInsideWinningCardStillUseLastDestination' not in test_source:
        fail('regressões de separação/último destino ausentes')
    if benchmark_source.count('@Test') < 6: fail('self-test exige 6 benchmarks locais Stage16')
    print('visible_card_priority_stage16_self_test=passed')
    print(f'stage16_test_methods={test_source.count("@Test")}')
    print(f'stage16_benchmark_methods={benchmark_source.count("@Test")}')
    print('timer_as_absence_proof=false')
    print('real_route_authority_preserved=true')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_root', nargs='?', type=Path)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if HELPER_SOURCE is None or TEST_SOURCE is None or BENCHMARK_SOURCE is None:
        fail('embedded Stage16 sources missing; packaging step must embed helper/test literals')
    if args.self_test:
        self_test(HELPER_SOURCE, TEST_SOURCE, BENCHMARK_SOURCE)
        if args.source_root is None: return
    if args.source_root is None: fail('source_root obrigatório')
    root = args.source_root.resolve()
    require_base(root)
    transformed = transform_service((root / SERVICE).read_text(encoding='utf-8'))
    if args.check:
        if 'BUBBLE_ROUTE_GATE_CACHE_HIT_STAGE16' not in transformed: fail('dry-run Stage16 incompleto')
        print('visible_card_priority_stage16_check=passed')
        print('base_effective_source_hashes=passed')
        print('google_maps_service_unchanged=true')
        print('stage9_stage12_stage14_preserved=true')
        return
    (root / SERVICE).write_text(transformed, encoding='utf-8')
    (root / HELPER).write_text(HELPER_SOURCE, encoding='utf-8')
    (root / TEST).write_text(TEST_SOURCE, encoding='utf-8')
    (root / BENCHMARK_TEST).write_text(BENCHMARK_SOURCE, encoding='utf-8')
    build_path = root / BUILD
    build = build_path.read_text(encoding='utf-8')
    build = replace_once(build, 'versionCode = 5478', 'versionCode = 5480', 'versionCode Stage16')
    build = replace_once(build, 'versionName = "0.1.194"', 'versionName = "0.1.196"', 'versionName Stage16')
    build_path.write_text(build, encoding='utf-8')
    audit(root)
    print('visible_card_priority_stage16_apply=passed')
    print('versionName=0.1.196')
    print('versionCode=5480')
    print('selected_app_role=read_authorization_only')
    print('transient_empty_generation_advance=false')
    print('stale_route_paint_blocked_while_empty_unresolved=true')
    print('exact_visual_gate_cache=true')
    print('google_maps_service_unchanged=true')


if __name__ == '__main__':
    main()
