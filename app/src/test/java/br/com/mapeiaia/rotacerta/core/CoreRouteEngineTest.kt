package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRouteEngineTest {
    @Test
    fun freshRouteResultBelongsToCurrentVisibleAddress() {
        val engine = CoreRouteEngine(nowMillis = { 1_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "address-a",
            startedAtMillis = 100L,
        )

        assertTrue(engine.isFresh(transaction, " SINET.STARTUP.INDRIVER ", "address-a"))
        assertEquals(
            "Resultado de rota ainda pertence ao ultimo endereco visivel; troca de pacote nao invalida o calculo.",
            engine.freshnessReason(transaction, "sinet.startup.indriver", "address-a"),
        )
    }

    @Test
    fun oldRouteResultIsNotFresh() {
        val engine = CoreRouteEngine(nowMillis = { 10_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "address-a",
            startedAtMillis = 100L,
        )

        assertFalse(engine.isFresh(transaction, "sinet.startup.indriver", "address-a"))
        assertTrue(engine.freshnessReason(transaction, "sinet.startup.indriver", "address-a").contains("atrasado"))
    }

    @Test
    fun transientPackageChangeDoesNotDiscardUniversalRoute() {
        val engine = CoreRouteEngine(nowMillis = { 1_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "address-a",
            startedAtMillis = 100L,
        )

        assertTrue(engine.isFresh(transaction, "com.android.documentsui", "address-a"))
        assertTrue(engine.freshnessReason(transaction, "com.android.documentsui", "address-a").contains("troca de pacote nao invalida"))
    }

    @Test
    fun routeResultFromAnotherVisibleAddressIsNotFresh() {
        val engine = CoreRouteEngine(nowMillis = { 1_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "address-a",
            startedAtMillis = 100L,
        )

        assertFalse(engine.isFresh(transaction, "sinet.startup.indriver", "address-b"))
        assertTrue(engine.freshnessReason(transaction, "sinet.startup.indriver", "address-b").contains("Assinatura visivel mudou"))
    }

    @Test
    fun blankVisibleSignatureDoesNotRejectFreshRoute() {
        val engine = CoreRouteEngine(nowMillis = { 1_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "address-a",
            startedAtMillis = 100L,
        )

        assertTrue(engine.isFresh(transaction, "com.android.systemui", ""))
    }

    private fun routeTransaction(
        packageName: String,
        visibleCardSignature: String,
        startedAtMillis: Long,
    ): CoreRouteTransaction = CoreRouteTransaction(
        packageName = packageName,
        destination = "rua destino",
        fare = "r$ 44",
        cardTemplateId = "template-1",
        cardSignature = "template-signature",
        visibleCardSignature = visibleCardSignature,
        startedAtMillis = startedAtMillis,
    )
}
