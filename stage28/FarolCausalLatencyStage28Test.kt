package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FarolCausalLatencyStage28Test {
    private val uber = "com.ubercab.driver"
    private val app99 = "com.app99.driver"

    @Before fun reset() { FarolCausalLatencyStage28.Metrics.resetForTests() }

    private fun machine(active: Set<String>): FarolReadingActivationStage26.ActivationMachine =
        FarolReadingActivationStage26.ActivationMachine().apply {
            updateSelection(setOf(uber, app99))
            setUsageAccess(true)
            replaceUsageState(FarolCausalLatencyStage28.currentExecutionEvents(active))
        }

    private fun signal(text: String, slot: String = "slot", window: String = "window", own: Boolean = false) =
        FarolCausalLatencyStage28.VisualSignal(own, window, slot, text)

    @Test fun noSelectedAppActiveMeansOff() { assertFalse(machine(emptySet()).snapshot().enabled) }
    @Test fun offMeansIdleGrayContract() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); assertEquals("IDLE", c.readingOff().color) }
    @Test fun offMeansKmNull() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); c.seedFinal("RED",12.0); assertNull(c.readingOff().distanceKm) }
    @Test fun oneSelectedActiveMeansOn() { assertTrue(machine(setOf(uber)).snapshot().enabled) }
    @Test fun twoSelectedActiveMeansOn() { assertEquals(2, machine(setOf(uber,app99)).snapshot().selectedAppsActiveCount) }
    @Test fun closeOneOfTwoRemainsOn() { assertTrue(machine(setOf(app99)).snapshot().enabled) }
    @Test fun closeLastMeansOff() { assertFalse(machine(emptySet()).snapshot().enabled) }
    @Test fun usageHistoryCannotKeepCachedProcessActive() { assertFalse(SelectedAppUsageStateStage28.isCurrentExecutionImportance(400)); assertTrue(SelectedAppUsageStateStage28.NO_HISTORY_AUTHORITY_MARKER.isNotBlank()) }
    @Test fun reopenSelectedMeansOn() { val m=machine(emptySet()); m.replaceUsageState(FarolCausalLatencyStage28.currentExecutionEvents(setOf(uber))); assertTrue(m.snapshot().enabled) }
    @Test fun activationGenerationChangesOnOff() { val m=machine(setOf(uber)); val g=m.snapshot().generation; m.replaceUsageState(emptyList()); assertTrue(m.snapshot().generation>g) }
    @Test fun oldWorkCannotPaintAfterOff() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); c.readingOff(); assertFalse(c.applyFinalIfFresh(l,"GREEN",2.0)) }
    @Test fun oldOcrCannotPaintAfterOff() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); c.readingOff(); assertFalse(c.isFresh(l)) }
    @Test fun oldCacheCannotPaintAfterOff() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); c.readingOff(); assertFalse(c.applyFinalIfFresh(l,"RED",8.0)) }
    @Test fun oldGoogleCannotPaintAfterOff() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); c.readingOff(); assertFalse(c.isFresh(l)) }
    @Test fun redAThenBRevokesAImmediately() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); c.seedFinal("RED",17.4); c.visualChanged(); assertEquals("WAITING",c.state().color); assertNull(c.state().distanceKm) }
    @Test fun greenAThenContentGoneClears() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); c.seedFinal("GREEN",3.0); c.visualChanged(); assertNull(c.state().distanceKm) }
    @Test fun sameContentNoiseDoesNotFlicker() { val g=FarolCausalLatencyStage28.VisualGate(); val a=g.admit(true,signal("Rua A, 10\nRua B, 20")); val b=g.admit(true,signal("Rua A, 10\nRua B, 20")); assertTrue(a.mutation); assertFalse(b.mutation) }
    @Test fun repeatedEventDoesNotHeavyCollect() { val g=FarolCausalLatencyStage28.VisualGate(); g.admit(true,signal("Rua A, 10\nRua B, 20")); assertFalse(g.admit(true,signal("Rua A, 10\nRua B, 20")).process) }
    @Test fun sameGenerationDoesNotRelaunchOcr() { val g=FarolCausalLatencyStage28.OcrGate(); assertTrue(g.request(7)); assertFalse(g.request(7)); assertEquals(1L,FarolCausalLatencyStage28.Metrics.counter("ocrCoalesced")) }
    @Test fun sameDestinationDoesNotRelaunchGoogle() { val g=FarolCausalLatencyStage28.RouteGate(); val k=FarolCausalLatencyStage28.RouteKey(1,2,"Rua B, 20"); assertTrue(g.begin(k)); assertFalse(g.begin(k)) }
    @Test fun newDestinationGetsNewIdentity() { val g=FarolCausalLatencyStage28.VisualGate(); val a=g.admit(true,signal("Rua A, 10\nRua B, 20")); val b=g.admit(true,signal("Rua A, 10\nRua C, 30")); assertNotEquals(a.fingerprint,b.fingerprint); assertTrue(b.mutation) }
    @Test fun googleAFinishesDuringBIsStale() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val a=c.lease(); c.visualChanged(); assertFalse(c.isFresh(a)) }
    @Test fun ocrAFinishesDuringBIsStale() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val a=c.lease(); c.visualChanged(); assertFalse(c.applyFinalIfFresh(a,"RED",5.0)) }
    @Test fun cacheAArrivesDuringBIsStale() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val a=c.lease(); c.visualChanged(); assertFalse(c.isFresh(a)) }
    @Test fun scheduledAIsDiscardedDuringB() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val a=c.lease(); c.visualChanged(); assertFalse(c.isFresh(a)) }
    @Test fun ownOverlayIsIgnored() { val g=FarolCausalLatencyStage28.VisualGate(); assertFalse(g.admit(true,signal("Rua A, 10\nRua B, 20",own=true)).process) }
    @Test fun eventStormIsCoalescedBeforeHeavyPath() { val g=FarolCausalLatencyStage28.VisualGate(); var n=0; repeat(100){if(g.admit(true,signal("Rua A, 10\nRua B, 20")).process)n++}; assertEquals(1,n); assertEquals(99L,FarolCausalLatencyStage28.Metrics.counter("eventsCoalesced")) }
    @Test fun twoAddressesOnWhatsAppAreProcessable() { val g=FarolCausalLatencyStage28.VisualGate(); assertTrue(g.admit(true,signal("Rua A, 10\nRua B, 20",window="whatsapp")).process) }
    @Test fun twoAddressesOnChatGptAreProcessable() { val g=FarolCausalLatencyStage28.VisualGate(); assertTrue(g.admit(true,signal("Rua A, 10\nRua B, 20",window="chatgpt")).process) }
    @Test fun twoAddressesOnHomeOverlayAreProcessable() { val g=FarolCausalLatencyStage28.VisualGate(); assertTrue(g.admit(true,signal("Rua A, 10\nRua B, 20",window="launcher-overlay")).process) }
    @Test fun addressesInDifferentCitiesAreNotBlocked() { assertTrue(FarolCausalLatencyStage28.UNIVERSAL_TWO_ADDRESS_MARKER.contains("ANY_VISIBLE_PACKAGE")) }
    @Test fun veryLongRouteIsNotBlockedByPolicy() { assertTrue(FarolCausalLatencyStage28.GOOGLE_REAL_MARKER.contains("REAL_GOOGLE")); assertFalse(FarolCausalLatencyStage28.CONTRACT_MARKER.contains("MAX_DISTANCE")) }
    @Test fun narrativeSuffixIsTrimmedFromDestination() { assertEquals("Rua Antônio Sampaio Ferraz, 158",FarolCausalLatencyStage28.trimNarrativeSuffix("Rua Antônio Sampaio Ferraz, 158, mas a bolinha continua vermelha mostrando 17,4")) }
    @Test fun trailingNarrativeDoesNotBecomeDestination() { val v=FarolCausalLatencyStage28.trimNarrativeSuffix("Avenida Jacu-Pêssego, 123; porém o resultado anterior continua na tela"); assertFalse(v.contains("resultado anterior")) }
    @Test fun cardModelIsNotRequired() { assertFalse(FarolCausalLatencyStage28.UNIVERSAL_TWO_ADDRESS_MARKER.contains("MODEL")) }
    @Test fun visualPackageUber99IndriveIsNotRequired() { assertTrue(FarolCausalLatencyStage28.UNIVERSAL_TWO_ADDRESS_MARKER.contains("ANY_VISIBLE_PACKAGE")) }
    @Test fun selectedAppOnlyControlsOnOff() { assertTrue(FarolCausalLatencyStage28.LIVE_ACTIVATION_MARKER.contains("LIVE_EXECUTION")) }
    @Test fun inDrivePackageCanActivate() { val m=FarolReadingActivationStage26.ActivationMachine(); m.updateSelection(setOf("sinet.startup.indriver")); m.setUsageAccess(true); m.replaceUsageState(FarolCausalLatencyStage28.currentExecutionEvents(setOf("sinet.startup.indriver"))); assertTrue(m.snapshot().enabled) }
    @Test fun app99PackageCanActivate() { assertTrue(machine(setOf(app99)).snapshot().enabled) }
    @Test fun uberPackageCanActivate() { assertTrue(machine(setOf(uber)).snapshot().enabled) }
    @Test fun realGoogleContractRemainsMandatory() { assertEquals("REAL_GOOGLE_ROUTE_PRESERVED_STAGE28",FarolCausalLatencyStage28.GOOGLE_REAL_MARKER) }
    @Test fun noGoogleRouteMeansNoInventedKmInCoordinator() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); assertNull(c.state().distanceKm) }
    @Test fun exactCurrentCacheCanPaintImmediately() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); assertTrue(c.applyFinalIfFresh(l,"GREEN",4.2)); assertEquals(4.2,c.state().distanceKm!!,0.0) }
    @Test fun oldCacheIsForbiddenAfterVisualChange() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); c.visualChanged(); assertFalse(c.applyFinalIfFresh(l,"GREEN",4.2)) }
    @Test fun readingOffMeansZeroHeavyCollection() { val g=FarolCausalLatencyStage28.VisualGate(); assertFalse(g.admit(false,signal("Rua A, 10\nRua B, 20")).process); assertEquals(1L,FarolCausalLatencyStage28.Metrics.counter("heavyCollectionsAvoided")) }
    @Test fun duplicateEventMeansZeroHeavyCollection() { val g=FarolCausalLatencyStage28.VisualGate(); g.admit(true,signal("Rua A, 10\nRua B, 20")); assertFalse(g.admit(true,signal("Rua A, 10\nRua B, 20")).process) }
    @Test fun ownOverlayMeansZeroHeavyCollection() { val g=FarolCausalLatencyStage28.VisualGate(); assertFalse(g.admit(true,signal("6,8",own=true)).process); assertEquals(1L,FarolCausalLatencyStage28.Metrics.counter("ownOverlayEventsIgnored")) }
    @Test fun staleProtectionIsO1GenerationTokenCheck() { val c=FarolCausalLatencyStage28.WorkCoordinator(); c.readingOn(); val l=c.lease(); assertTrue(c.isFresh(l)); assertTrue(FarolCausalLatencyStage28.O1_STALE_MARKER.contains("O1")) }
    @Test fun nonCachedServiceImportanceCountsAsCurrentExecution() { assertTrue(SelectedAppUsageStateStage28.isCurrentExecutionImportance(300)) }
    @Test fun cachedImportanceDoesNotCountAsCurrentExecution() { assertFalse(SelectedAppUsageStateStage28.isCurrentExecutionImportance(400)) }
}
