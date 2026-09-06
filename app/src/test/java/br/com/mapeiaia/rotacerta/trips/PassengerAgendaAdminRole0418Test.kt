package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerAgendaAdminRole0418Test {
    @Test
    fun passengerAccessDoesNotBecomeAdminByDefault() {
        assertFalse(DriverPassengerAccess().agendaAdmin)
    }

    @Test
    fun adminRoleIsExplicitOnExistingPassengerIdentity() {
        val access = DriverPassengerAccess(
            passengerId = "passenger-canonical",
            passengerContact = "+5511999999999",
            status = "AUTHORIZED",
            accountActivated = true,
            agendaAdmin = true,
        )
        assertTrue(access.agendaAdmin)
        assertTrue(access.accountActivated)
    }
}
