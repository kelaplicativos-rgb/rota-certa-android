package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolVisibleCardPriorityStage16Test {
    private val uber = "com.ubercab.driver"
    private val app99 = "com.app99.driver"
    private val inDrive = "sinet.startup.indriver"
    private val selected = setOf(uber, app99, inDrive)

    private fun w(
        packageName: String?,
        layer: Int,
        kind: FarolVisibleCardPriorityStage16.WindowKind,
        id: Int = layer,
        hasRoot: Boolean = true,
    ) = FarolVisibleCardPriorityStage16.WindowEvidence(id, packageName, layer, kind, hasRoot)

    private fun b(
        text: String = "Rua A, 10\nRua B, 20",
        packageName: String = uber,
        windowId: Int = 42,
        id: String = "a11y:42/0",
        top: Int = 100,
    ) = FarolVisibleCardPriorityStage16.BlockEvidence(
        id = id,
        parentId = "a11y:42",
        packageName = packageName,
        windowId = windowId,
        windowLayer = 9,
        depth = 2,
        text = text,
        source = "Accessibility",
        left = 50,
        top = top,
        right = 1000,
        bottom = top + 400,
        syntheticRoot = false,
    )

    private fun identity(
        blocks: List<FarolVisibleCardPriorityStage16.BlockEvidence> = listOf(b()),
        packageName: String = uber,
        windowId: Int = 42,
        session: Long = 7,
        screen: Long = 12,
        windowGeneration: Long = 5,
    ) = FarolVisibleCardPriorityStage16.gateSnapshotIdentity(
        packageName, session, windowId, screen, windowGeneration, blocks,
    )

    private fun activeBinding(
        packageName: String = uber,
        windowId: Int = 42,
        session: Long = 7,
        screen: Long = 12,
        windowGeneration: Long = 5,
        screenHash: Int = 123,
        signature: String = "uber|rua b 20",
    ) = FarolVisibleCardPriorityStage16.ActiveCardBinding(
        packageName, session, windowId, screen, windowGeneration, screenHash, signature,
    )

    @Test fun uberPopupOverLauncherIsEligible() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w(uber, 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.sec.android.app.launcher", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW, r.outcome)
        assertEquals(uber, r.authority?.packageName)
    }

    @Test fun uberPopupOverAnotherAppIsEligible() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.whatsapp", 3, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(uber, r.authority?.packageName)
    }

    @Test fun uberPopupOverMapsIsEligible() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.google.android.apps.maps", 2, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(uber, r.authority?.packageName)
    }

    @Test fun uberPopupOverWazeIsEligible() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.waze", 2, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(uber, r.authority?.packageName)
    }

    @Test fun systemUiDoesNotBlockAuthorizedVisibleRoot() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w("com.android.systemui", 10, FarolVisibleCardPriorityStage16.WindowKind.SYSTEM), w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(uber, r.authority?.packageName)
    }

    @Test fun inputMethodDoesNotBlockAuthorizedVisibleRoot() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w("com.samsung.android.honeyboard", 11, FarolVisibleCardPriorityStage16.WindowKind.INPUT_METHOD), w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(uber, r.authority?.packageName)
    }

    @Test fun accessibilityOverlayDoesNotBlockAuthorizedVisibleRoot() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w("android", 12, FarolVisibleCardPriorityStage16.WindowKind.ACCESSIBILITY_OVERLAY), w(uber, 8, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(uber, r.authority?.packageName)
    }

    @Test fun app99PopupEquivalentIsEligible() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w(app99, 7, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.whatsapp", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(app99, r.authority?.packageName)
    }

    @Test fun inDrivePopupEquivalentIsEligible() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w(inDrive, 7, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.whatsapp", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(inDrive, r.authority?.packageName)
    }

    @Test fun selectionIsAuthorizationNotForegroundRequirement() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w(uber, 6, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w("com.sec.android.app.launcher", 1, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), setOf(uber),
        )
        assertTrue(r.authority != null)
    }

    @Test fun higherNonSelectedApplicationFailsClosed() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w("com.whatsapp", 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w(uber, 3, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.BLOCKED_BY_APPLICATION, r.outcome)
        assertEquals(null, r.authority)
    }

    @Test fun rootlessHigherApplicationCannotPretendToBeVisualEvidence() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w("com.whatsapp", 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION, hasRoot = false), w(uber, 3, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), selected,
        )
        assertEquals(uber, r.authority?.packageName)
    }

    @Test fun unselectedRideAppNeverWinsAuthorization() {
        val r = FarolVisibleCardPriorityStage16.selectVisibleAuthorizedWindow(
            listOf(w(app99, 9, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION), w(uber, 3, FarolVisibleCardPriorityStage16.WindowKind.APPLICATION)), setOf(uber),
        )
        assertEquals(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.BLOCKED_BY_APPLICATION, r.outcome)
    }

    @Test fun isolatedEmptyWithBoundCardRequestsObjectiveVisualConfirmation() {
        assertEquals(FarolVisibleCardPriorityStage16.EmptyReadAction.CONFIRM_CURRENT_VISUAL, FarolVisibleCardPriorityStage16.emptyReadAction(activeBinding()))
    }

    @Test fun isolatedEmptyWithoutBoundCardDoesNotInventPreservation() {
        assertEquals(FarolVisibleCardPriorityStage16.EmptyReadAction.CLEAR_WITHOUT_PRESERVATION, FarolVisibleCardPriorityStage16.emptyReadAction(null))
    }

    @Test fun sameCardObjectiveReconfirmationResolvesTransientEmpty() {
        val active = activeBinding()
        val selection = FarolVisibleCardPriorityStage16.WindowSelection(
            FarolVisibleCardPriorityStage16.WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW,
            FarolVisibleCardPriorityStage16.VisibleWindowAuthority(uber, 42, 9),
        )
        assertEquals(FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.SAME_CARD, FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selection, active.copy()))
    }

    @Test fun realApplicationAboveCardConfirmsAbsence() {
        val active = activeBinding()
        val selection = FarolVisibleCardPriorityStage16.WindowSelection(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.BLOCKED_BY_APPLICATION)
        assertEquals(FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.CONFIRMED_ABSENT, FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selection, null))
    }

    @Test fun newAuthorizedWindowConfirmsCardChange() {
        val active = activeBinding()
        val selection = FarolVisibleCardPriorityStage16.WindowSelection(
            FarolVisibleCardPriorityStage16.WindowSelectionOutcome.AUTHORIZED_SELECTED_WINDOW,
            FarolVisibleCardPriorityStage16.VisibleWindowAuthority(uber, 77, 10),
        )
        assertEquals(FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.DIFFERENT_CARD, FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selection, null))
    }

    @Test fun ambiguousWindowSnapshotDoesNotPretendCardDisappeared() {
        val active = activeBinding()
        val selection = FarolVisibleCardPriorityStage16.WindowSelection(FarolVisibleCardPriorityStage16.WindowSelectionOutcome.NO_DECISIVE_WINDOW)
        assertEquals(FarolVisibleCardPriorityStage16.EmptyVisualConfirmation.AMBIGUOUS, FarolVisibleCardPriorityStage16.classifyEmptyVisualConfirmation(active, selection, null))
    }

    @Test fun unresolvedTransientEmptyBlocksRoutePainting() {
        assertFalse(FarolVisibleCardPriorityStage16.routeResultMayPaint(bindingFresh = true, transientEmptyPendingForBinding = true))
    }

    @Test fun freshBindingCanPaintAfterSameCardReconfirmation() {
        assertTrue(FarolVisibleCardPriorityStage16.routeResultMayPaint(bindingFresh = true, transientEmptyPendingForBinding = false))
    }

    @Test fun staleBindingNeverPaintsEvenWithoutTransientEmpty() {
        assertFalse(FarolVisibleCardPriorityStage16.routeResultMayPaint(bindingFresh = false, transientEmptyPendingForBinding = false))
    }

    @Test fun pendingIdentityMatchesOnlyExactCardGenerationBinding() {
        val a = activeBinding()
        assertTrue(FarolVisibleCardPriorityStage16.pendingMatches(a, a.copy()))
        assertFalse(FarolVisibleCardPriorityStage16.pendingMatches(a, a.copy(screenGeneration = a.screenGeneration + 1)))
        assertFalse(FarolVisibleCardPriorityStage16.pendingMatches(a, a.copy(addressSignature = "different")))
    }

    @Test fun exactAcceptedVisualSnapshotCanReuseAuthorizationDuringRoute() {
        val i = identity()
        assertTrue(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
            cached = i, current = i.copy(), cachedPackageName = uber, cachedWindowId = 42,
            cachedAddressSignature = "sig", cachedScreenHash = 123, activePackageName = uber,
            activeAddressSignature = "sig", activeScreenHash = 123, routeInFlight = true,
            stableDecision = false, transientEmptyPending = false,
        ))
    }

    @Test fun exactAcceptedVisualSnapshotCanReuseAuthorizationForStableDecision() {
        val i = identity()
        assertTrue(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
            i, i, uber, 42, "sig", 123, uber, "sig", 123, false, true, false,
        ))
    }

    @Test fun transientEmptyDisablesFastPathUntilReconfirmed() {
        val i = identity()
        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
            i, i, uber, 42, "sig", 123, uber, "sig", 123, true, false, true,
        ))
    }

    @Test fun changedDestinationTextAlwaysMissesExactFastPath() {
        val old = identity()
        val changed = identity(blocks = listOf(b(text = "Rua A, 10\nRua C, 30")))
        assertNotEquals(old, changed)
        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
            old, changed, uber, 42, "sig", 123, uber, "sig", 123, true, false, false,
        ))
    }

    @Test fun changedPriceOrTimeTextAlsoLeavesFastPathAndMustReenterGate() {
        val old = identity(blocks = listOf(b(text = "Rua A, 10\nR$ 18,00\nRua B, 20")))
        val changed = identity(blocks = listOf(b(text = "Rua A, 10\nR$ 21,00\nRua B, 20")))
        assertNotEquals(old, changed)
    }

    @Test fun changedWindowAlwaysMissesExactFastPath() {
        val old = identity()
        val changed = identity(windowId = 99, blocks = listOf(b(windowId = 99, id = "a11y:99/0")))
        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
            old, changed, uber, 42, "sig", 123, uber, "sig", 123, true, false, false,
        ))
    }

    @Test fun changedSessionAlwaysMissesExactFastPath() {
        assertNotEquals(identity(), identity(session = 8))
    }

    @Test fun changedScreenGenerationAlwaysMissesExactFastPath() {
        assertNotEquals(identity(), identity(screen = 13))
    }

    @Test fun changedWindowGenerationAlwaysMissesExactFastPath() {
        assertNotEquals(identity(), identity(windowGeneration = 6))
    }

    @Test fun otherPackageNeverReusesPreviousAppAuthorization() {
        val i = identity()
        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
            i, i, uber, 42, "sig", 123, app99, "sig", 123, true, false, false,
        ))
    }

    @Test fun partialOrAmbiguousStateWithoutActiveRouteOrDecisionCannotUseFastPath() {
        val i = identity()
        assertFalse(FarolVisibleCardPriorityStage16.canReuseAcceptedAuthorization(
            i, i, uber, 42, "sig", 123, uber, "sig", 123, false, false, false,
        ))
    }

    @Test fun exactVisualStructureIncludesGeometryAndPreventsCrossCardReuse() {
        val upper = identity(blocks = listOf(b(id = "upper", top = 100)))
        val lower = identity(blocks = listOf(b(id = "lower", top = 800)))
        assertNotEquals(upper, lower)
    }

    @Test fun exactVisualStructureIncludesParentRelationship() {
        val original = b()
        val changed = original.copy(parentId = "different-parent")
        assertNotEquals(identity(listOf(original)), identity(listOf(changed)))
    }

    @Test fun currentNonBlankSelectedWindowIsObjectiveAbsenceEvidenceCandidate() {
        assertTrue(FarolVisibleCardPriorityStage16.hasCoherentAbsenceEvidence(uber, 42, listOf(b(text = "Você está online"))))
    }

    @Test fun emptyBlocksAreNeverAbsenceProof() {
        assertFalse(FarolVisibleCardPriorityStage16.hasCoherentAbsenceEvidence(uber, 42, emptyList()))
    }

    @Test fun backgroundWindowTextCannotProvePopupAbsence() {
        assertFalse(FarolVisibleCardPriorityStage16.hasCoherentAbsenceEvidence(uber, 42, listOf(b(text = "Maps address", packageName = "com.google.android.apps.maps"))))
    }

    @Test fun otherWindowOfSameAppCannotProveCurrentPopupAbsence() {
        assertFalse(FarolVisibleCardPriorityStage16.hasCoherentAbsenceEvidence(uber, 42, listOf(b(text = "Outra janela", windowId = 77))))
    }

    private fun gateBlock(
        id: String,
        text: String,
        packageName: String = uber,
        windowId: Int = 42,
        layer: Int = 9,
        top: Int = 100,
        bottom: Int = 500,
    ) = FarolCardBlock0188(
        id = id, packageName = packageName, windowId = windowId, windowLayer = layer, depth = 2,
        text = text, source = FarolEvidenceSource0188.Accessibility, left = 50, top = top, right = 1000, bottom = bottom,
    )

    @Test fun backgroundAddressesAndPopupAddressNeverFormArtificialRide() {
        val decision = FarolRealDeviceGate0188.evaluate(
            uber, setOf(uber), listOf(
                gateBlock("maps", "Rua Fundo, 10\nRua Fundo 2, 20", packageName = "com.google.android.apps.maps", layer = 1),
                gateBlock("popup", "Rua Popup, 30", layer = 9),
            ),
        )
        assertFalse(decision.authorized)
    }

    @Test fun twoSimultaneousCardsRemainSeparated() {
        val decision = FarolRealDeviceGate0188.evaluate(
            uber, setOf(uber), listOf(
                gateBlock("upper", "Rua A, 10\nRua B, 20", top = 100, bottom = 450),
                gateBlock("lower", "Rua C, 30\nRua D, 40", top = 800, bottom = 1150),
            ),
        )
        assertTrue(decision.authorized)
        assertEquals("Rua B, 20", decision.authorization?.destination)
    }

    @Test fun threeAddressesInsideWinningCardStillUseLastDestination() {
        val decision = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\nRua B, 20\nRua C, 30")))
        assertTrue(decision.authorized)
        assertEquals("Rua C, 30", decision.authorization?.destination)
    }

    @Test fun backgroundPackageNeverEntersSelectedAuthorizationText() {
        val decision = FarolRealDeviceGate0188.evaluate(
            uber, setOf(uber), listOf(
                gateBlock("background", "Rua Maps, 10\nRua Maps, 20", packageName = "com.google.android.apps.maps", layer = 1),
                gateBlock("popup", "Rua Uber, 30\nRua Uber, 40", layer = 9),
            ),
        )
        assertTrue(decision.authorized)
        assertFalse(decision.authorization!!.analysisText.contains("Maps"))
    }

    @Test fun priceNoiseWithSameWinningBlockAndDestinationKeepsAuthorityIdentity() {
        val before = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\nR$ 18,00\nRua B, 20"))).authorization!!
        val after = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\nR$ 22,00\nRua B, 20"))).authorization!!
        assertEquals(before.addressSignature, after.addressSignature)
        assertEquals(before.screenHash, after.screenHash)
    }

    @Test fun realDestinationChangeChangesAuthorityIdentity() {
        val before = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\nRua B, 20"))).authorization!!
        val after = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\nRua C, 30"))).authorization!!
        assertNotEquals(before.addressSignature, after.addressSignature)
        assertNotEquals(before.screenHash, after.screenHash)
    }

    @Test fun sameDestinationInDifferentWinningWindowChangesScreenHash() {
        val before = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\nRua B, 20", windowId = 42))).authorization!!
        val after = FarolRealDeviceGate0188.evaluate(uber, setOf(uber), listOf(gateBlock("card", "Rua A, 10\nRua B, 20", windowId = 77))).authorization!!
        assertEquals(before.addressSignature, after.addressSignature)
        assertNotEquals(before.screenHash, after.screenHash)
    }

}
