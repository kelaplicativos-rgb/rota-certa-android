package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBubbleStateControllerTest {
    @Test
    fun initialStateIsHiddenWithoutDistance() {
        val controller = CoreBubbleStateController(nowMillis = { 100L })

        val state = controller.current()

        assertEquals(CoreBubbleMode.Hidden, state.mode)
        assertNull(state.distanceKm)
        assertEquals("Estado inicial da bolinha.", state.reason)
        assertEquals(100L, state.updatedAtMillis)
    }

    @Test
    fun goodRideKeepsDistanceWhenReasonIsValid() {
        val controller = CoreBubbleStateController(nowMillis = { 200L })

        val state = controller.render(
            mode = CoreBubbleMode.Good,
            distanceKm = 6.8,
            reason = "Card cadastrado confirmado; destino dentro do raio.",
        )

        assertEquals(CoreBubbleMode.Good, state.mode)
        assertEquals(6.8, state.distanceKm!!, 0.0)
        assertTrue(state.changed)
    }

    @Test
    fun badRideKeepsDistanceWhenReasonIsValid() {
        val controller = CoreBubbleStateController(nowMillis = { 300L })

        val state = controller.render(
            mode = CoreBubbleMode.Bad,
            distanceKm = 10.3,
            reason = "Card cadastrado confirmado; destino fora do raio.",
        )

        assertEquals(CoreBubbleMode.Bad, state.mode)
        assertEquals(10.3, state.distanceKm!!, 0.0)
        assertTrue(state.changed)
    }

    @Test
    fun missingRegisteredCardReasonForcesWaitingAndClearsDistance() {
        val controller = CoreBubbleStateController(nowMillis = { 400L })

        val state = controller.render(
            mode = CoreBubbleMode.Good,
            distanceKm = 6.8,
            reason = "Tela parece card de corrida, mas ainda nao bate com nenhum card cadastrado.",
        )

        assertEquals(CoreBubbleMode.Waiting, state.mode)
        assertNull(state.distanceKm)
        assertTrue(state.changed)
    }

    @Test
    fun registerModelReasonForcesWaitingAndClearsDistance() {
        val controller = CoreBubbleStateController(nowMillis = { 500L })

        val state = controller.render(
            mode = CoreBubbleMode.Bad,
            distanceKm = 12.0,
            reason = "Cadastre o modelo para liberar o farol.",
        )

        assertEquals(CoreBubbleMode.Waiting, state.mode)
        assertNull(state.distanceKm)
    }

    @Test
    fun unconfirmedScreenReasonForcesWaitingAndClearsDistance() {
        val controller = CoreBubbleStateController(nowMillis = { 600L })

        val state = controller.render(
            mode = CoreBubbleMode.Bad,
            distanceKm = 9.2,
            reason = "Tela nao confirmada por card cadastrado.",
        )

        assertEquals(CoreBubbleMode.Waiting, state.mode)
        assertNull(state.distanceKm)
    }

    @Test
    fun individualRegisteredCardOnlyReasonForcesWaitingAndClearsDistance() {
        val controller = CoreBubbleStateController(nowMillis = { 700L })

        val state = controller.render(
            mode = CoreBubbleMode.Good,
            distanceKm = 5.4,
            reason = "Somente card individual cadastrado libera verde ou vermelho.",
        )

        assertEquals(CoreBubbleMode.Waiting, state.mode)
        assertNull(state.distanceKm)
    }

    @Test
    fun waitingAndHiddenAlwaysClearDistance() {
        val controller = CoreBubbleStateController(nowMillis = { 800L })

        val waiting = controller.render(
            mode = CoreBubbleMode.Waiting,
            distanceKm = 8.1,
            reason = "Aguardando card cadastrado.",
        )
        val hidden = controller.render(
            mode = CoreBubbleMode.Hidden,
            distanceKm = 8.1,
            reason = "Fora de app monitorado.",
        )

        assertEquals(CoreBubbleMode.Waiting, waiting.mode)
        assertNull(waiting.distanceKm)
        assertEquals(CoreBubbleMode.Hidden, hidden.mode)
        assertNull(hidden.distanceKm)
    }

    @Test
    fun repeatedSameStateIsNotChanged() {
        val controller = CoreBubbleStateController(nowMillis = { 900L })

        controller.render(CoreBubbleMode.Waiting, null, "Aguardando card cadastrado.")
        val repeated = controller.render(CoreBubbleMode.Waiting, null, "Mesmo estado.")

        assertFalse(repeated.changed)
    }
}
