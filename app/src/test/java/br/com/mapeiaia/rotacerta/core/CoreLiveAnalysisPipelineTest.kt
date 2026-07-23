package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CoreLiveAnalysisPipelineTest {
    @Test
    fun interleavedSnapshotsKeepIndependentTransactions() {
        var now = 1_000L
        val pipeline = CoreLiveAnalysisPipeline(nowMillis = { now++ })

        val firstBegin = pipeline.begin(
            packageName = "sinet.startup.indriver",
            source = "Accessibility",
            rawLength = 120,
            allowPopupCandidate = false,
        )
        val firstRead = pipeline.readReady(firstBegin, snapshotHash = 101, textLength = 110)

        val secondBegin = pipeline.begin(
            packageName = "sinet.startup.indriver",
            source = "Ocr",
            rawLength = 130,
            allowPopupCandidate = false,
        )
        val secondRead = pipeline.readReady(secondBegin, snapshotHash = 202, textLength = 125)

        val firstVisible = pipeline.visibleCard(
            transaction = firstRead,
            action = CoreVisibleCardAction.Entered,
            visibleCardSignature = "card-101",
        )

        assertEquals(101, firstVisible.snapshotHash)
        assertEquals("card-101", pipeline.transactionFor(101)?.visibleCardSignature)
        assertEquals(secondRead.id, pipeline.currentTransaction()?.id)
        assertEquals(202, pipeline.currentTransaction()?.snapshotHash)
    }

    @Test
    fun acceptedCardCanContinueFromItsOwnSnapshotTransaction() {
        val pipeline = CoreLiveAnalysisPipeline(nowMillis = { 2_000L })
        val begin = pipeline.begin("sinet.startup.indriver", "Accessibility", 100, false)
        val read = pipeline.readReady(begin, snapshotHash = 303, textLength = 90)
        val visible = pipeline.visibleCard(read, CoreVisibleCardAction.Entered, "card-303")
        val accepted = pipeline.cardAccepted(visible, contractName = "inDrive", cardTemplateName = "Card salvo")

        val recovered = pipeline.transactionFor(303)

        assertNotNull(recovered)
        assertEquals(CoreLivePipelineStage.Card, recovered?.stage)
        assertEquals("inDrive", recovered?.contractName)
        assertEquals("Card salvo", recovered?.cardTemplateName)
        assertEquals("card-303", recovered?.visibleCardSignature)
    }
}
