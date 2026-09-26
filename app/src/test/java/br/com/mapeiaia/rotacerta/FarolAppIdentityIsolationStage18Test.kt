package br.com.mapeiaia.rotacerta

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
