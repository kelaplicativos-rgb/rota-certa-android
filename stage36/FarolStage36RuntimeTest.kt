package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage36RuntimeTest {
    private fun root():File{val c=File(System.getProperty("user.dir"));return if(File(c,"app/src/main/java").isDirectory)c else if(c.name=="app"&&File(c,"src/main/java").isDirectory)c.parentFile else c}
    private fun src(name:String)=File(root(),"app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()
    private fun service()=src("LiveRideAccessibilityService.kt")

    @Test fun serviceUsesStage36Authority(){val s=service();assertTrue(s.contains("stage36RuntimeAuthority"));assertTrue(s.contains("stage36RuntimeAuthority.observeVisualEvidence()"));assertTrue(s.contains("stage36RuntimeAuthority.observeWindowBoundary"))}
    @Test fun legacyOcrFreshnessDemoted(){val s=service();assertFalse(s.contains("serialStage19 == stage19OcrSerial"));assertFalse(s.contains("private fun isStage23OcrDemandFresh("));assertTrue(s.contains("private fun isStage36WorkFresh("))}
    @Test fun rawMutationPreservesRoute(){val s=service();val a=s.indexOf("private fun invalidateOldVisualBeforeCollectStage26");val b=s.indexOf("private fun collectUniversalAccessibilityBlocksStage19",a);val f=s.substring(a,b);assertFalse(f.contains("universalRouteJob?.cancel"));assertTrue(f.contains("routePreservedAcrossRawMutation"))}
    @Test fun destinationChangeOnlyCancelsRoute(){val s=service();val a=s.indexOf("private suspend fun processUniversalVisualStage19");val b=s.indexOf("private fun stage20BindingSnapshot",a);val f=s.substring(a,b);assertTrue(f.contains("val visualChangedStage19 = universalActiveAddressSignature != evaluationStage19.addressSignature"));assertFalse(f.contains("lastSnapshotHash != evaluationStage19.screenHash"))}
    @Test fun paintRequiresVerificationComplete(){val s=service();val a=s.indexOf("private suspend fun applyUniversalTwoAddressResultStage19");val b=s.indexOf("private fun scheduleAccessibilityFallbackStage23",a);assertTrue(s.substring(a,b).contains("isStage19BindingFresh(bindingStage19) && !stage19VisualVerificationPending"))}
    @Test fun lastVisibleAddressIsDestination(){val s=src("FarolCausalCorrectionStage21.kt");assertTrue(s.contains("val destination = winner.addresses.last()"));assertTrue(s.contains("addresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES"))}
    @Test fun googleRoutePreserved(){assertTrue(service().contains("drivingDistancesFromAddressKm("))}
    @Test fun processShadowStillDiagnostic(){assertTrue(service().contains("updateProcessShadow"));assertTrue(src("FarolPresenceAuthorityStage30.kt").contains("RUNNING_APP_PROCESSES_SHADOW_ONLY_STAGE30"))}
    @Test fun reportExportsStage36(){assertTrue(src("ManualTechnicalReportBuilder.kt").contains("FarolRuntimeAuthorityStage36.Metrics.exportReport(null)"))}
    @Test fun versionStage36(){val b=File(root(),"app/build.gradle.kts").readText();assertTrue(b.contains("versionCode = 5493"));assertTrue(b.contains("versionName = \"0.1.209\""))}
}
