package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteVersionAudit0765ContractTest {
    @Test
    fun healthSnapshotIncludesBoundedReleaseAuditWithoutRawPrivateEvidence() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta")
        val source = File(root, "trips/RemoteHealthDiagnostic0747.kt").readText()
        assertTrue(source.contains("val recentReleases: List<RemoteReleaseAudit0765>"))
        assertTrue(source.contains("ReleaseHistoryStore.load(applicationContext)"))
        assertTrue(source.contains(".take(8)"))
        assertTrue(source.contains("sanitizeForExport"))
        assertTrue(source.contains("implemented = strings(release.implemented)"))
        assertTrue(source.contains("fixed = strings(release.fixed)"))
        assertTrue(source.contains("improved = strings(release.improved)"))
        assertFalse(source.contains("rawHtml ="))
    }

    @Test
    fun technicalZipContainsVersionAuditAndSourceRevision() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/monitoring/OperationalHealthTechnicalPackage0575.kt").readText()
        assertTrue(source.contains("\"release-audit.json\" to releaseAudit0765(appContext)"))
        assertTrue(source.contains("ReleaseHistoryStore.load(context)"))
        assertTrue(source.contains("BuildConfig.BUILD_GIT_SHA"))
        assertTrue(source.contains("sanitizeForExport"))
        assertTrue(source.contains("implemented"))
        assertTrue(source.contains("fixed"))
        assertTrue(source.contains("improved"))
    }

    @Test
    fun oneToggleProtectsAllRemoteTechnicalCollection() {
        val auth = File("src/main/java/br/com/mapeiaia/rotacerta/trips/RemoteSupportAutoAccess0763.kt").readText()
        val worker = File("src/main/java/br/com/mapeiaia/rotacerta/trips/RemoteHealthDiagnostic0747.kt").readText()
        assertTrue(auth.contains("\"remote_health_collect\""))
        assertTrue(worker.contains("RemoteSupportAutoAccess0763.enabled(applicationContext, \"remote_health_collect\")"))
    }
}
