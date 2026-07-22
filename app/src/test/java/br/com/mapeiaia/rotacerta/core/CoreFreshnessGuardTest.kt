package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreFreshnessGuardTest {
    @Test
    fun nullTransactionIsAlwaysStale() {
        val decision = CoreFreshnessGuard.evaluate(
            transaction = null,
            currentPackageName = "sinet.startup.indriver",
            currentSnapshotHash = 10,
            currentVisibleCardSignature = "card-a",
        )

        assertFalse(decision.fresh)
        assertTrue(decision.reason.contains("Sem transacao ativa"))
    }

    @Test
    fun samePackageHashAndSignatureIsFresh() {
        val transaction = transaction(
            packageName = " SINET.STARTUP.INDRIVER ",
            snapshotHash = 123,
            signature = "card-a",
        )

        val decision = CoreFreshnessGuard.evaluate(
            transaction = transaction,
            currentPackageName = "sinet.startup.indriver",
            currentSnapshotHash = 123,
            currentVisibleCardSignature = "card-a",
        )

        assertTrue(decision.fresh)
    }

    @Test
    fun changedPackageIsStale() {
        val decision = CoreFreshnessGuard.evaluate(
            transaction = transaction("sinet.startup.indriver", 123, "card-a"),
            currentPackageName = "com.ubercab.driver",
            currentSnapshotHash = 123,
            currentVisibleCardSignature = "card-a",
        )

        assertFalse(decision.fresh)
        assertTrue(decision.reason.contains("Pacote mudou"))
    }

    @Test
    fun changedSnapshotHashIsStale() {
        val decision = CoreFreshnessGuard.evaluate(
            transaction = transaction("sinet.startup.indriver", 123, "card-a"),
            currentPackageName = "sinet.startup.indriver",
            currentSnapshotHash = 456,
            currentVisibleCardSignature = "card-a",
        )

        assertFalse(decision.fresh)
        assertTrue(decision.reason.contains("Snapshot mudou"))
    }

    @Test
    fun changedVisibleCardSignatureIsStale() {
        val decision = CoreFreshnessGuard.evaluate(
            transaction = transaction("sinet.startup.indriver", 123, "card-a"),
            currentPackageName = "sinet.startup.indriver",
            currentSnapshotHash = 123,
            currentVisibleCardSignature = "card-b",
        )

        assertFalse(decision.fresh)
        assertTrue(decision.reason.contains("Assinatura do card mudou"))
    }

    @Test
    fun missingCurrentHashDoesNotRejectOtherwiseFreshTransaction() {
        val decision = CoreFreshnessGuard.evaluate(
            transaction = transaction("sinet.startup.indriver", 123, "card-a"),
            currentPackageName = "sinet.startup.indriver",
            currentSnapshotHash = null,
            currentVisibleCardSignature = "card-a",
        )

        assertTrue(decision.fresh)
    }

    @Test
    fun blankCurrentSignatureDoesNotRejectOtherwiseFreshTransaction() {
        val decision = CoreFreshnessGuard.evaluate(
            transaction = transaction("sinet.startup.indriver", 123, "card-a"),
            currentPackageName = "sinet.startup.indriver",
            currentSnapshotHash = 123,
            currentVisibleCardSignature = "   ",
        )

        assertTrue(decision.fresh)
    }

    private fun transaction(
        packageName: String,
        snapshotHash: Int,
        signature: String,
    ): CoreLivePipelineTransaction = CoreLivePipelineTransaction(
        id = 1L,
        stage = CoreLivePipelineStage.Decision,
        packageName = packageName,
        source = "test",
        rawLength = 100,
        allowPopupCandidate = false,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        reason = "test",
        snapshotHash = snapshotHash,
        visibleCardSignature = signature,
    )
}
