package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRouteEngineTest {
    @Test
    fun freshRouteResultBelongsToCurrentPackageAndVisibleCard() {
        val engine = CoreRouteEngine(nowMillis = { 1_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "card-a",
            startedAtMillis = 100L,
        )

        assertTrue(engine.isFresh(transaction, " SINET.STARTUP.INDRIVER ", "card-a"))
        assertEquals(
            "Resultado de rota ainda pertence ao card visivel.",
            engine.freshnessReason(transaction, "sinet.startup.indriver", "card-a"),
        )
    }

    @Test
    fun oldRouteResultIsNotFresh() {
        val engine = CoreRouteEngine(nowMillis = { 2_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "card-a",
            startedAtMillis = 100L,
        )

        assertFalse(engine.isFresh(transaction, "sinet.startup.indriver", "card-a"))
        assertTrue(engine.freshnessReason(transaction, "sinet.startup.indriver", "card-a").contains("atrasado"))
    }

    @Test
    fun routeResultFromAnotherPackageIsNotFresh() {
        val engine = CoreRouteEngine(nowMillis = { 1_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "card-a",
            startedAtMillis = 100L,
        )

        assertFalse(engine.isFresh(transaction, "com.ubercab.driver", "card-a"))
        assertTrue(engine.freshnessReason(transaction, "com.ubercab.driver", "card-a").contains("Pacote mudou"))
    }

    @Test
    fun routeResultFromAnotherVisibleCardIsNotFresh() {
        val engine = CoreRouteEngine(nowMillis = { 1_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "card-a",
            startedAtMillis = 100L,
        )

        assertFalse(engine.isFresh(transaction, "sinet.startup.indriver", "card-b"))
        assertTrue(engine.freshnessReason(transaction, "sinet.startup.indriver", "card-b").contains("Assinatura visivel mudou"))
    }

    @Test
    fun blankVisibleSignatureDoesNotRejectFreshRoute() {
        val engine = CoreRouteEngine(nowMillis = { 1_000L })
        val transaction = routeTransaction(
            packageName = "sinet.startup.indriver",
            visibleCardSignature = "card-a",
            startedAtMillis = 100L,
        )

        assertTrue(engine.isFresh(transaction, "sinet.startup.indriver", ""))
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
