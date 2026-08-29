package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PassengerAdmin0314Test {
    @Test
    fun collectedAndRemotePassengersMergeByExactPhone() {
        val local = PassengerProfile(displayName = "Maria", whatsapp = "(11) 99999-0000")
        val collected = BlaBlaCollectorPassenger(name = "Maria BlaBla", phone = "+55 11 99999-0000")
        val remote = DriverPassengerAccess(
            passengerContact = "+5511999990000",
            displayName = "Maria",
            status = "ACTIVE",
            creditBalanceCents = 500,
        )
        val merged = mergePassengerAdminCandidates(listOf(local), listOf(collected), listOf(remote))
        assertEquals(1, merged.size)
        assertNotNull(merged.single().localProfile)
        assertEquals("ACTIVE", merged.single().remoteAccess?.status)
        assertEquals(500L, merged.single().remoteAccess?.creditBalanceCents)
    }

    @Test
    fun creditParsingUsesCentsWithoutFloatingPoint() {
        assertEquals(500L, parseCreditInput("5,00"))
        assertEquals(125L, parseCreditInput("1,25"))
        assertEquals("5,00", formatCreditInput(500L))
    }

    @Test
    fun accessLabelsKeepPendingSeparateFromBlocked() {
        assertEquals("⏳ Aguardando sua aprovação", passengerAccessLabel(DriverPassengerAccess(status = "PENDING")))
        assertEquals("🔴 Bloqueado", passengerAccessLabel(DriverPassengerAccess(status = "BLOCKED")))
    }
}
