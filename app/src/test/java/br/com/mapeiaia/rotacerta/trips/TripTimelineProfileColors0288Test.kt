package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class TripTimelineProfileColors0288Test {
    @Test
    fun registeredProfilesKeepSequentialStableSlots() {
        val slots = timelineProfileColorSlots(
            registeredProfileUuids = listOf(" EZEQUIEL-UUID ", "BARBOSA-UUID"),
            observedProfileIdentities = listOf(
                "barbosa-uuid",
                "ezequiel-uuid",
                "terceiro-perfil",
                "ezequiel-uuid",
                "quarto-perfil",
            ),
        )

        assertEquals(0, slots["ezequiel-uuid"])
        assertEquals(1, slots["barbosa-uuid"])
        assertEquals(2, slots["terceiro-perfil"])
        assertEquals(3, slots["quarto-perfil"])
        assertEquals(4, slots.size)
    }

    @Test
    fun duplicateAndBlankIdentitiesDoNotConsumeExtraColors() {
        val slots = timelineProfileColorSlots(
            registeredProfileUuids = listOf("perfil-a", "", "PERFIL-A"),
            observedProfileIdentities = listOf("perfil-a", " perfil-b ", "", "PERFIL-B"),
        )

        assertEquals(mapOf("perfil-a" to 0, "perfil-b" to 1), slots)
    }
}
