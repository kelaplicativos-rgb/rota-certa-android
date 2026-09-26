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
    fun canonicalBlockedStateControlsAgendaAccessPresentation() {
        assertEquals("🟢 Acesso automático", passengerAccessLabel(DriverPassengerAccess(status = "AUTHORIZED")))
        assertEquals("🟡 Sincronização pendente", passengerAccessLabel(DriverPassengerAccess(status = "SUSPENDED")))
        assertEquals("⛔ Não aceito no meu carro", passengerAccessLabel(DriverPassengerAccess(status = "BLOCKED")))
        val profile = PassengerProfile(id = "p1", displayName = "Pessoa", whatsapp = "11999999999", blocked = true)
        assertTrue(profile.blocked)
        assertEquals("11999999999", profile.agendaAccessContact())
    }

    @Test
    fun automaticAgendaSyncPublishesOnlyUniqueCanonicalPhoneIdentities() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        assertTrue(source.contains("PassengerIdentityStore(context).profiles()"))
        assertTrue(source.contains("groupBy { passengerContactKey(it.agendaAccessContact()) }"))
        assertTrue(source.contains("profiles.size == 1"))
        assertTrue(source.contains("api.syncPassengerDirectory(canonicalPassengerProfiles)"))
    }

    @Test
    fun remoteContractCarriesCanonicalPassengerIdWithoutFirstAccessPassword() {
        val remote = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        val admin = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
        assertTrue(remote.contains("val passengerId: String = \"\""))
        assertTrue(remote.contains("syncPassengerDirectory"))
        assertTrue(remote.contains("blocked = it.blocked"))
        assertTrue(remote.contains("updatePassengerAccessWhatsapp"))
        assertTrue(admin.contains("WhatsApp de acesso"))
        assertTrue(admin.contains("agendaAccessWhatsapp"))
        assertTrue(admin.contains("Acesso automático pela base unificada"))
        assertFalse(admin.contains("Aprovar e autorizar"))
        assertFalse(admin.contains("Suspender acesso"))
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
