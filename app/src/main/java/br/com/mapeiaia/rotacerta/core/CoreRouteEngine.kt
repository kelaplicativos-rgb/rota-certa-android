package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.LiveRideRouteCache
import br.com.mapeiaia.rotacerta.RideFields
import java.text.Normalizer
import java.util.Locale

/**
 * Motor de rota/cache do Rota Certa Core.
 * A bolinha nao deve decidir cache nem aceitar resultado atrasado.
 * Toda rota calculada nasce vinculada a uma transacao do card visivel.
 */
class CoreRouteEngine(
    private val cache: LiveRideRouteCache = LiveRideRouteCache(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    fun beginTransaction(
        packageName: String?,
        fields: RideFields,
        cardTemplateId: String?,
        cardSignature: String?,
        visibleCardSignature: String?,
    ): CoreRouteTransaction = CoreRouteTransaction(
        packageName = normalizePart(packageName),
        destination = normalizePart(fields.destination),
        fare = normalizePart(fields.fare),
        cardTemplateId = normalizePart(cardTemplateId),
        cardSignature = normalizePart(cardSignature),
        visibleCardSignature = visibleCardSignature.orEmpty(),
        startedAtMillis = nowMillis(),
    )

    fun keyFor(
        fields: RideFields,
        settings: AppSettings,
        packageName: String?,
        cardSignature: String?,
    ): LiveRideRouteCache.Key? = LiveRideRouteCache.keyFor(
        fields = fields,
        settings = settings,
        packageName = packageName,
        cardSignature = cardSignature,
    )

    fun cachedRoute(key: LiveRideRouteCache.Key?): CoreRouteCacheResult {
        val cached = cache.get(key)
        return CoreRouteCacheResult(
            route = cached,
            fromCache = cached != null,
            reason = if (cached != null) "Rota encontrada no cache do Core." else "Rota ainda nao estava no cache do Core.",
        )
    }

    fun storeRoute(key: LiveRideRouteCache.Key?, route: LiveRideRouteCache.CachedRoute) {
        cache.put(key, route)
    }

    fun clear() {
        cache.clear()
    }

    fun isFresh(
        transaction: CoreRouteTransaction,
        currentPackageName: String?,
        currentVisibleCardSignature: String?,
    ): Boolean {
        val ageMillis = nowMillis() - transaction.startedAtMillis
        if (ageMillis > MAX_ROUTE_RESULT_AGE_MS) return false
        val normalizedCurrentPackage = normalizePart(currentPackageName)
        if (transaction.packageName.isNotBlank() && normalizedCurrentPackage != transaction.packageName) return false
        val visibleAtStart = transaction.visibleCardSignature
        val visibleNow = currentVisibleCardSignature.orEmpty()
        if (visibleAtStart.isNotBlank() && visibleNow.isNotBlank() && visibleAtStart != visibleNow) return false
        return true
    }

    fun freshnessReason(
        transaction: CoreRouteTransaction,
        currentPackageName: String?,
        currentVisibleCardSignature: String?,
    ): String {
        val ageMillis = nowMillis() - transaction.startedAtMillis
        val normalizedCurrentPackage = normalizePart(currentPackageName)
        val visibleNow = currentVisibleCardSignature.orEmpty()
        return when {
            ageMillis > MAX_ROUTE_RESULT_AGE_MS -> "Resultado de rota atrasado: ${ageMillis}ms."
            transaction.packageName.isNotBlank() && normalizedCurrentPackage != transaction.packageName ->
                "Pacote mudou durante a rota: inicio=${transaction.packageName}, atual=$normalizedCurrentPackage."
            transaction.visibleCardSignature.isNotBlank() && visibleNow.isNotBlank() && transaction.visibleCardSignature != visibleNow ->
                "Assinatura visivel mudou durante a rota."
            else -> "Resultado de rota ainda pertence ao card visivel."
        }
    }

    private fun normalizePart(value: String?): String =
        Normalizer.normalize(value.orEmpty().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        const val MAX_ROUTE_RESULT_AGE_MS: Long = 1_800L
    }
}

data class CoreRouteTransaction(
    val packageName: String,
    val destination: String,
    val fare: String,
    val cardTemplateId: String,
    val cardSignature: String,
    val visibleCardSignature: String,
    val startedAtMillis: Long,
)

data class CoreRouteCacheResult(
    val route: LiveRideRouteCache.CachedRoute?,
    val fromCache: Boolean,
    val reason: String,
)
