package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.Recommendation
import java.util.concurrent.atomic.AtomicLong

/**
 * Linha de montagem oficial da analise ao vivo.
 * O servico Android coleta/renderiza; o Core registra e controla a ordem profissional:
 * pacote -> leitura -> card -> rota -> decisao -> visual.
 */
class CoreLiveAnalysisPipeline(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val sequence = AtomicLong(0L)
    private var current: CoreLivePipelineTransaction? = null
    private val transactionsBySnapshotHash = LinkedHashMap<Int, CoreLivePipelineTransaction>()

    @Synchronized
    fun begin(
        packageName: String?,
        source: String,
        rawLength: Int,
        allowPopupCandidate: Boolean,
    ): CoreLivePipelineTransaction {
        val transaction = CoreLivePipelineTransaction(
            id = sequence.incrementAndGet(),
            stage = CoreLivePipelineStage.Package,
            packageName = CorePackageMonitor.normalize(packageName),
            source = source,
            rawLength = rawLength,
            allowPopupCandidate = allowPopupCandidate,
            createdAtMillis = nowMillis(),
            updatedAtMillis = nowMillis(),
            reason = "Pipeline iniciado pelo Core.",
        )
        current = transaction
        return transaction
    }

    @Synchronized
    fun readReady(
        transaction: CoreLivePipelineTransaction,
        snapshotHash: Int,
        textLength: Int,
    ): CoreLivePipelineTransaction = update(
        transaction = transaction,
        stage = CoreLivePipelineStage.Read,
        snapshotHash = snapshotHash,
        textLength = textLength,
        reason = "Leitura preparada e pronta para classificar card.",
    )

    @Synchronized
    fun visibleCard(
        transaction: CoreLivePipelineTransaction,
        action: CoreVisibleCardAction,
        visibleCardSignature: String?,
    ): CoreLivePipelineTransaction = update(
        transaction = transaction,
        stage = CoreLivePipelineStage.VisibleCard,
        visibleCardSignature = visibleCardSignature,
        reason = "Ciclo do card visivel: $action.",
    )

    @Synchronized
    fun cardAccepted(
        transaction: CoreLivePipelineTransaction,
        contractName: String,
        cardTemplateName: String?,
    ): CoreLivePipelineTransaction = update(
        transaction = transaction,
        stage = CoreLivePipelineStage.Card,
        contractName = contractName,
        cardTemplateName = cardTemplateName,
        reason = "Card cadastrado aprovado pelo contrato $contractName.",
    )

    @Synchronized
    fun routeReady(
        transaction: CoreLivePipelineTransaction,
        fromCache: Boolean,
    ): CoreLivePipelineTransaction = update(
        transaction = transaction,
        stage = CoreLivePipelineStage.Route,
        routeFromCache = fromCache,
        reason = if (fromCache) "Rota obtida do cache do Core." else "Rota calculada para o card atual.",
    )

    @Synchronized
    fun decisionReady(
        transaction: CoreLivePipelineTransaction,
        recommendation: Recommendation,
        distanceKm: Double?,
    ): CoreLivePipelineTransaction = update(
        transaction = transaction,
        stage = CoreLivePipelineStage.Decision,
        recommendation = recommendation,
        distanceKm = distanceKm,
        reason = "Decisao pronta para refletir no visual.",
    )

    @Synchronized
    fun visualApplied(
        transaction: CoreLivePipelineTransaction,
        mode: CoreBubbleMode,
    ): CoreLivePipelineTransaction = update(
        transaction = transaction,
        stage = CoreLivePipelineStage.Visual,
        visualMode = mode,
        reason = "Visual aplicado pela bolinha.",
    )

    @Synchronized
    fun abort(
        transaction: CoreLivePipelineTransaction?,
        stage: CoreLivePipelineStage,
        reason: String,
    ): CoreLivePipelineTransaction {
        val base = transaction ?: CoreLivePipelineTransaction(
            id = sequence.incrementAndGet(),
            stage = stage,
            packageName = null,
            source = "unknown",
            rawLength = 0,
            allowPopupCandidate = false,
            createdAtMillis = nowMillis(),
            updatedAtMillis = nowMillis(),
            reason = reason,
        )
        val next = base.copy(
            stage = CoreLivePipelineStage.Aborted,
            abortedAtStage = stage,
            reason = reason,
            updatedAtMillis = nowMillis(),
        )
        remember(next)
        if (current == null || current?.id == base.id) current = next
        return next
    }

    @Synchronized
    fun currentTransaction(): CoreLivePipelineTransaction? = current

    @Synchronized
    fun transactionFor(snapshotHash: Int): CoreLivePipelineTransaction? =
        transactionsBySnapshotHash[snapshotHash]

    private fun update(
        transaction: CoreLivePipelineTransaction,
        stage: CoreLivePipelineStage,
        snapshotHash: Int? = transaction.snapshotHash,
        textLength: Int = transaction.textLength,
        visibleCardSignature: String? = transaction.visibleCardSignature,
        contractName: String? = transaction.contractName,
        cardTemplateName: String? = transaction.cardTemplateName,
        routeFromCache: Boolean? = transaction.routeFromCache,
        recommendation: Recommendation? = transaction.recommendation,
        distanceKm: Double? = transaction.distanceKm,
        visualMode: CoreBubbleMode? = transaction.visualMode,
        reason: String,
    ): CoreLivePipelineTransaction {
        val next = transaction.copy(
            stage = stage,
            snapshotHash = snapshotHash,
            textLength = textLength,
            visibleCardSignature = visibleCardSignature,
            contractName = contractName,
            cardTemplateName = cardTemplateName,
            routeFromCache = routeFromCache,
            recommendation = recommendation,
            distanceKm = distanceKm,
            visualMode = visualMode,
            reason = reason,
            updatedAtMillis = nowMillis(),
        )
        remember(next)
        if (current == null || current?.id == transaction.id) current = next
        return next
    }

    private fun remember(transaction: CoreLivePipelineTransaction) {
        val snapshotHash = transaction.snapshotHash ?: return
        transactionsBySnapshotHash[snapshotHash] = transaction
        while (transactionsBySnapshotHash.size > MAX_TRACKED_SNAPSHOTS) {
            val oldestKey = transactionsBySnapshotHash.entries.firstOrNull()?.key ?: break
            transactionsBySnapshotHash.remove(oldestKey)
        }
    }

    private companion object {
        const val MAX_TRACKED_SNAPSHOTS = 24
    }
}

data class CoreLivePipelineTransaction(
    val id: Long,
    val stage: CoreLivePipelineStage,
    val packageName: String?,
    val source: String,
    val rawLength: Int,
    val allowPopupCandidate: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val reason: String,
    val snapshotHash: Int? = null,
    val textLength: Int = 0,
    val visibleCardSignature: String? = null,
    val contractName: String? = null,
    val cardTemplateName: String? = null,
    val routeFromCache: Boolean? = null,
    val recommendation: Recommendation? = null,
    val distanceKm: Double? = null,
    val visualMode: CoreBubbleMode? = null,
    val abortedAtStage: CoreLivePipelineStage? = null,
) {
    fun traceSummary(): String =
        "id=$id stage=$stage package=${packageName ?: "null"} hash=${snapshotHash ?: -1} card=${cardTemplateName ?: "null"} contract=${contractName ?: "null"} visual=${visualMode ?: "null"} reason=$reason"
}

enum class CoreLivePipelineStage {
    Package,
    Read,
    VisibleCard,
    Card,
    Route,
    Decision,
    Visual,
    Aborted,
}
