package br.com.mapeiaia.rotacerta.core

/**
 * Guardiao de frescor da bolinha.
 * Um resultado so pode aplicar rota/decisao/visual se ainda pertencer ao mesmo
 * pacote, snapshot/hash e assinatura visivel do card atual.
 */
object CoreFreshnessGuard {
    fun evaluate(
        transaction: CoreLivePipelineTransaction?,
        currentPackageName: String?,
        currentSnapshotHash: Int?,
        currentVisibleCardSignature: String?,
    ): CoreFreshnessDecision {
        if (transaction == null) {
            return CoreFreshnessDecision.stale("Sem transacao ativa do pipeline; resultado bloqueado.")
        }
        val transactionPackage = CorePackageMonitor.normalize(transaction.packageName)
        val currentPackage = CorePackageMonitor.normalize(currentPackageName)
        if (transactionPackage != null && currentPackage != null && transactionPackage != currentPackage) {
            return CoreFreshnessDecision.stale(
                "Pacote mudou antes do resultado: esperado=$transactionPackage atual=$currentPackage.",
            )
        }
        val transactionHash = transaction.snapshotHash
        if (transactionHash != null && currentSnapshotHash != null && transactionHash != currentSnapshotHash) {
            return CoreFreshnessDecision.stale(
                "Snapshot mudou antes do resultado: esperado=$transactionHash atual=$currentSnapshotHash.",
            )
        }
        val transactionSignature = transaction.visibleCardSignature
        if (!transactionSignature.isNullOrBlank() &&
            !currentVisibleCardSignature.isNullOrBlank() &&
            transactionSignature != currentVisibleCardSignature
        ) {
            return CoreFreshnessDecision.stale(
                "Assinatura do card mudou antes do resultado.",
            )
        }
        return CoreFreshnessDecision.fresh("Resultado ainda pertence ao pacote/hash/card atual.")
    }
}

data class CoreFreshnessDecision(
    val fresh: Boolean,
    val reason: String,
) {
    companion object {
        fun fresh(reason: String): CoreFreshnessDecision = CoreFreshnessDecision(fresh = true, reason = reason)
        fun stale(reason: String): CoreFreshnessDecision = CoreFreshnessDecision(fresh = false, reason = reason)
    }
}
