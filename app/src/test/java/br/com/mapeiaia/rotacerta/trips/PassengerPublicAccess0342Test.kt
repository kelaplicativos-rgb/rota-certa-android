package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerPublicAccess0342Test {
    @Test
    fun brazilianWhatsappFormatsResolveToSameCanonicalContactKey() {
        val expected = passengerContactKey("(11) 99999-9999")
        assertEquals(expected, passengerContactKey("11 99999-9999"))
        assertEquals(expected, passengerContactKey("+55 11 99999-9999"))
        assertEquals(expected, passengerContactKey("5511999999999"))
    }

    @Test
    fun remotePassengerIdWinsOverConflictingPhoneWhenLinkingAdminCandidate() {
        val canonical = PassengerProfile(id = "canonical-a", displayName = "Carlos", whatsapp = "11911112222")
        val other = PassengerProfile(id = "canonical-b", displayName = "Outra pessoa", whatsapp = "11999998888")
        val remote = DriverPassengerAccess(
            id = "remote-access",
            passengerId = canonical.id,
            passengerContact = other.whatsapp,
            displayName = "Carlos remoto",
            status = "AUTHORIZED",
        )
        val merged = mergePassengerAdminCandidates(listOf(canonical, other), emptyList(), listOf(remote))
        val linked = merged.first { it.localProfile?.id == canonical.id }
        assertSame(remote, linked.remoteAccess)
    }

    @Test
    fun agendaAccessStatusesAreExplicitAndIndependentFromPersonaNonGrata() {
        assertEquals("🟢 Autorizado", passengerAccessLabel(DriverPassengerAccess(status = "AUTHORIZED")))
        assertEquals("🟡 Suspenso", passengerAccessLabel(DriverPassengerAccess(status = "SUSPENDED")))
        assertEquals("🔴 Bloqueado", passengerAccessLabel(DriverPassengerAccess(status = "BLOCKED")))
        val profile = PassengerProfile(id = "p1", displayName = "Pessoa", blocked = true)
        assertTrue(profile.blocked)
        assertEquals("", profile.publicAccessStatus)
    }

    @Test
    fun automaticAgendaSyncPublishesOnlyUniqueCanonicalPhoneIdentities() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        assertTrue(source.contains("PassengerIdentityStore(context).profiles()"))
        assertTrue(source.contains("groupBy { passengerContactKey(it.agendaAccessWhatsapp) }"))
        assertTrue(source.contains("profiles.size == 1"))
        assertTrue(source.contains("api.syncPassengerDirectory(canonicalPassengerProfiles)"))
    }

    @Test
    fun remoteContractCarriesCanonicalPassengerIdWithoutFirstAccessPassword() {
        val remote = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        val admin = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
        assertTrue(remote.contains("val passengerId: String = \"\""))
        assertTrue(remote.contains("syncPassengerDirectory"))
        assertTrue(remote.contains("setPassengerAccessStatus"))
        assertTrue(remote.contains("updatePassengerAccessWhatsapp"))
        assertTrue(admin.contains("WhatsApp de acesso"))
        assertTrue(admin.contains("agendaAccessWhatsapp"))
        assertTrue(admin.contains("Acesso autorizado no mesmo passengerId. O passageiro criará a própria senha ao usar uma ação privada."))
        assertFalse(admin.contains("Acesso liberado. Envie a senha temporária ao passageiro."))
    }

    @Test
    fun accessWhatsappCanChangeWithoutChangingCanonicalPassengerOrCapturedContact() {
        val original = PassengerProfile(
            id = "passenger-abc123",
            displayName = "João",
            whatsapp = "11911111111",
            agendaAccessWhatsapp = "11911111111",
        )
        val edited = original.copy(agendaAccessWhatsapp = "11999999999")
        assertEquals("passenger-abc123", edited.id)
        assertEquals("11911111111", edited.whatsapp)
        assertEquals("11999999999", edited.agendaAccessWhatsapp)
    }

    @Test
    fun remoteAccessContactDoesNotOverwriteCapturedContactWhenPassengerIdMatches() {
        val canonical = PassengerProfile(
            id = "passenger-abc123",
            displayName = "João",
            whatsapp = "11911111111",
            agendaAccessWhatsapp = "11999999999",
        )
        val remote = DriverPassengerAccess(
            id = "remote-access-id",
            passengerId = canonical.id,
            passengerContact = "11999999999",
            displayName = "João",
            status = "AUTHORIZED",
        )
        val candidate = mergePassengerAdminCandidates(
            localProfiles = listOf(canonical),
            collectedPassengers = emptyList(),
            remotePassengers = listOf(remote),
        ).single()

        assertEquals(canonical.id, candidate.localProfile?.id)
        assertEquals("11911111111", candidate.whatsapp)
        assertEquals("11999999999", candidate.agendaAccessWhatsapp)
    }

}
