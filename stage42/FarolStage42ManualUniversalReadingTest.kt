package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage42ManualUniversalReadingTest {
    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Stage42 source not found: $name; cwd=${cwd.absolutePath}")
    }

    @Test fun contractMarkersDescribeManualUniversalAuthority() {
        assertEquals("FAROL_MANUAL_READING_AUTHORITY_STAGE42", FarolManualReadingAuthorityStage42.CONTRACT_MARKER)
        assertEquals("USER_TOGGLE_ONLY_ARMS_READING_STAGE42", FarolManualReadingAuthorityStage42.USER_ONLY_MARKER)
        assertEquals("ANY_VISIBLE_APP_WINDOW_POPUP_TWO_ADDRESS_STAGE42", FarolManualReadingAuthorityStage42.UNIVERSAL_VISUAL_MARKER)
        assertEquals("NO_SELECTED_APP_PRESENCE_IN_CRITICAL_PATH_STAGE42", FarolManualReadingAuthorityStage42.NO_PRESENCE_GATE_MARKER)
        assertEquals("HOME_READING_MODULE_AND_FLOATING_SHORTCUT_STAGE42", FarolManualReadingAuthorityStage42.HOME_MODULE_MARKER)
    }

    @Test fun helperTurnsBothHistoricalWorkFlagsOnAndOffAtomically() {
        val base = AppSettings(appEnabled = false, liveReadingEnabled = false)
        val on = FarolManualReadingAuthorityStage42.setEnabled(base, true)
        assertTrue(on.appEnabled)
        assertTrue(on.liveReadingEnabled)
        assertTrue(FarolManualReadingAuthorityStage42.isEnabled(on))
        val off = FarolManualReadingAuthorityStage42.setEnabled(on, false)
        assertFalse(off.appEnabled)
        assertFalse(off.liveReadingEnabled)
        assertFalse(FarolManualReadingAuthorityStage42.isEnabled(off))
    }

    @Test fun manualAuthorityDoesNotRequireSelectedPackagesInStage26() {
        val machine = FarolReadingActivationStage26.ActivationMachine()
        val on = machine.setManualAuthority(true)
        assertTrue(on.enabled)
        assertTrue(on.usageAccessGranted)
        assertTrue(on.selectedPackages.isEmpty())
        assertTrue(on.activeSelectedPackages.isEmpty())
        assertEquals(0, on.selectedAppsActiveCount)
    }

    @Test fun stage26ManualOffInvalidatesActivationGeneration() {
        val machine = FarolReadingActivationStage26.ActivationMachine()
        val on = machine.setManualAuthority(true)
        val lease = machine.lease(7L, 11L)
        assertTrue(machine.isLeaseFresh(lease, 7L, 11L))
        val off = machine.setManualAuthority(false)
        assertFalse(off.enabled)
        assertTrue(off.generation > on.generation)
        assertFalse(machine.isLeaseFresh(lease, 7L, 11L))
    }

    @Test fun stage36ManualOnNeedsNoSelectedPackageUsageEventOrPresence() {
        val authority = FarolRuntimeAuthorityStage36.Authority(123L)
        val on = authority.setManualAuthority(true)
        assertTrue(on.enabled)
        assertTrue(on.usageAccessGranted)
        assertTrue(on.selectedPackages.isEmpty())
        assertTrue(on.authoritativeActivePackages.isEmpty())
        assertEquals("stage42_manual_user_on", on.reason)
    }

    @Test fun stage36ManualOnKeepsAsyncWorkTokenFresh() {
        val authority = FarolRuntimeAuthorityStage36.Authority(123L)
        authority.setManualAuthority(true)
        authority.bindDestination("visual|Rua A 10|Rua B 20")
        val token = authority.captureWorkToken()
        assertNotNull(token)
        assertTrue(authority.isFresh(token))
    }

    @Test fun stage36ManualOffInvalidatesAsyncWorkTokenAndLease() {
        val authority = FarolRuntimeAuthorityStage36.Authority(123L)
        authority.setManualAuthority(true)
        authority.bindDestination("visual|Rua A 10|Rua B 20")
        val token = authority.captureWorkToken()
        assertNotNull(token)
        authority.setManualAuthority(false)
        assertFalse(authority.isFresh(token))
        assertFalse(authority.snapshot().enabled)
        assertEquals(0L, authority.snapshot().leaseId)
        assertNull(authority.snapshot().destinationKey)
    }

    @Test fun readingModuleIsPromotedIntoHomeCatalog() {
        BubbleShortcutCatalog.requireValid()
        val spec = BubbleShortcutCatalog.modules.firstOrNull { it.spec.id == "reading" }?.spec
        assertNotNull(spec)
        assertEquals(BubbleShortcutAction.ToggleReading, spec?.action)
        assertEquals("Leitura do Farol", spec?.label)
    }

    @Test fun readingExecutableIsEligibleForFloatingShortcutGrid() {
        val actions = ShortcutActionCatalog0184.actionsForModule("reading")
        assertTrue(actions.any { it.id == "reading" && it.action == BubbleShortcutAction.ToggleReading })
        assertEquals("reading", ShortcutActionCatalog0184.moduleIdForAction("reading"))
    }

    @Test fun readingShortcutDescriptionStatesUniversalManualBehavior() {
        val spec = requireNotNull(BubbleShortcutCatalog.modules.firstOrNull { it.spec.id == "reading" }?.spec)
        val description = ShortcutGridPolicy0173.description(spec).lowercase()
        assertTrue(description.contains("manualmente"))
        assertTrue(description.contains("qualquer tela"))
        assertTrue(description.contains("pop-up"))
    }

    @Test fun runtimeRefreshHasNoSelectedAppPresenceAuthority() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun refreshReadingActivationStage26(")
        val b = s.indexOf("    private fun applyReadingOffStage26(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("FarolManualReadingAuthorityStage42.isEnabled(currentSettings)"))
        assertTrue(block.contains("stage36RuntimeAuthority.setManualAuthority(manualEnabledStage42)"))
        assertTrue(block.contains("stage26ReadingActivation.setManualAuthority(manualEnabledStage42)"))
        listOf(
            "SelectedRideAppStore.read",
            "hasUsageAccess",
            "readIncrementalUsage",
            "currentSelectedWindowPackagesStage40",
            "readProcessShadow",
            "selectedEventStage36",
        ).forEach { forbidden -> assertFalse("presence gate remains: $forbidden", block.contains(forbidden)) }
    }

    @Test fun homeHasDedicatedReadingModuleAndSameToggleAction() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("BubbleShortcutAction.ToggleReading -> ManualReadingHomeModuleStage42("))
        assertTrue(main.contains("private fun ManualReadingHomeModuleStage42("))
        assertTrue(main.contains("FarolManualReadingAuthorityStage42.setEnabled(settings, enabled)"))
        assertTrue(main.contains("O atalho Leitura deste módulo pode ser adicionado à grade flutuante"))
    }

    @Test fun manualOffKeepsGrayBubbleInsteadOfRemovingIt() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("    private fun applyWorkModeRuntime0162(")
        val b = s.indexOf("    private fun ensureDriverCardSession0162(", a)
        assertTrue(a >= 0 && b > a)
        val block = s.substring(a, b)
        assertTrue(block.contains("stage36RuntimeAuthority.setManualAuthority(enabled0162)"))
        assertTrue(block.contains("stage26ReadingActivation.setManualAuthority(enabled0162)"))
        assertTrue(block.contains("showOverlay(RadarColor.Idle, null)"))
        assertFalse(block.contains("removeOverlay()"))
    }

    @Test fun stage42IntroducesNoPollingSleepTimerOrContinuousOcr() {
        val helper = source("FarolManualReadingAuthorityStage42.kt")
        val service = source("LiveRideAccessibilityService.kt")
        listOf("Thread.sleep(", "SystemClock.sleep(", "Timer(", "scheduleAtFixedRate(", "fixedRateTimer(", "while (true)").forEach {
            assertFalse("forbidden Stage42 timing primitive: $it", helper.contains(it))
        }
        val a = service.indexOf("    private fun refreshReadingActivationStage26(")
        val b = service.indexOf("    private fun applyReadingOffStage26(", a)
        val refresh = service.substring(a, b)
        assertFalse(refresh.contains("delay("))
        assertFalse(refresh.contains("requestUniversalScreenshotStage19("))
        assertFalse(refresh.contains("ocrService"))
    }
}
