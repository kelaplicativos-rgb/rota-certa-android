package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage36FreshnessTest {
    private val uber="com.ubercab.driver"
    private fun a()=FarolRuntimeAuthorityStage36.Authority(1000L).also{it.updateSelection(setOf(uber));it.setUsageAccess(true);it.observeAccessibility(uber);it.observeVisualEvidence()}
    private fun e(signal:FarolPresenceAuthorityStage30.UsageSignal)=FarolPresenceAuthorityStage30.UsageEvidence(uber,signal,2000L)
    private fun src(name:String)=File(System.getProperty("user.dir"),"app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    @Test fun firstDestinationSameLease(){val x=a();val p=x.snapshot().leaseId;assertEquals(p,x.bindDestination("Rua A|Rua B 20").leaseId)}
    @Test fun sameDestinationSameLease(){val x=a();val p=x.bindDestination("Rua A|Rua B 20");val q=x.bindDestination("Outra|Rua B 20");assertEquals(p.leaseId,q.leaseId)}
    @Test fun accentSameDestination(){val x=a();val p=x.bindDestination("Rua A|Av. São João, 100");val q=x.bindDestination("Outra|Avenida Sao Joao 100");assertEquals(p.leaseId,q.leaseId)}
    @Test fun finalAddressWinsThree(){assertEquals("rua c 30",FarolRuntimeAuthorityStage36.destinationFromAddressSignature("Rua A 10|Rua B 20|Rua C 30"))}
    @Test fun tokenSurvivesRaw(){val x=a();val t=x.captureWorkToken();x.observeVisualEvidence();assertTrue(x.isFresh(t))}
    @Test fun tokenSurvivesHome(){val x=a();val t=x.captureWorkToken();x.observeWindowBoundary("com.sec.android.app.launcher");assertTrue(x.isFresh(t))}
    @Test fun tokenSurvivesPause(){val x=a();val t=x.captureWorkToken();x.applyUsageEvidence(listOf(e(FarolPresenceAuthorityStage30.UsageSignal.ACTIVITY_PAUSED)));assertTrue(x.isFresh(t))}
    @Test fun acquiringTokenSurvivesFirstCandidate(){val x=a();val t=x.captureWorkToken();x.bindDestination("Rua A|Rua B 20");assertTrue(x.isFresh(t))}
    @Test fun destinationTokenSurvivesSameDestination(){val x=a();val t=x.captureDestinationToken("Rua A|Rua B 20");x.bindDestination("Outra|Rua B 20");assertTrue(x.isFresh(t))}
    @Test fun tokenStaleDifferentDestination(){val x=a();val t=x.captureDestinationToken("Rua A|Rua B 20");x.bindDestination("Rua A|Rua C 30");assertFalse(x.isFresh(t))}
    @Test fun tokenStaleTrueOff(){val x=a();val t=x.captureWorkToken();x.setUsageAccess(false);assertFalse(x.isFresh(t))}
    @Test fun tokenStaleVisualClear(){val x=a();val t=x.captureWorkToken();x.clearVisualLease("card_disappeared");assertFalse(x.isFresh(t))}
    @Test fun oldTokenCannotPaintAfterReopen(){val x=a();val old=x.captureDestinationToken("Rua A|Rua B 20");x.setUsageAccess(false);x.setUsageAccess(true);x.observeAccessibility(uber);x.observeVisualEvidence();x.bindDestination("Rua A|Rua B 20");assertFalse(x.isFresh(old));assertNotNull(x.captureWorkToken())}
    @Test fun newLeaseAfterClear(){val x=a();val p=x.snapshot().leaseId;x.clearVisualLease("gone");val q=x.observeVisualEvidence().leaseId;assertNotEquals(p,q)}
    @Test fun helperNoTimerSleepPolling(){val s=src("FarolRuntimeAuthorityStage36.kt");listOf("Thread.sleep(","SystemClock.sleep(","Timer(","scheduleAtFixedRate(").forEach{assertFalse(s.contains(it))}}
}
