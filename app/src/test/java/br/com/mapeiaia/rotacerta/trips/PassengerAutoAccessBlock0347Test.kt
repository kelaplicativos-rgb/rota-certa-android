package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerAutoAccessBlock0347Test {
    @Test
    fun existingPassengerUsesCapturedWhatsappWhenNoOverrideExists() {
        val profile = PassengerProfile(
            id = "passenger-permanent",
            displayName = "Pessoa",
            whatsapp = "11999999999",
        )
        assertEquals("11999999999", profile.agendaAccessContact())
    }

    @Test
    fun explicitAccessWhatsappNeverChangesPermanentPassengerId() {
        val original = PassengerProfile(
            id = "passenger-permanent",
            displayName = "Pessoa",
            whatsapp = "11911111111",
        )
        val edited = original.copy(agendaAccessWhatsapp = "11988888888")
        assertEquals("passenger-permanent", edited.id)
        assertEquals("11988888888", edited.agendaAccessContact())
        assertEquals("11911111111", edited.whatsapp)
    }

    @Test
    fun changedVisualDataCannotClearCanonicalBlock() {
        val blocked = PassengerProfile(
            id = "passenger-permanent",
            displayName = "Nome antigo",
            whatsapp = "11911111111",
            blocked = true,
            blockedReason = "Não aceito no meu carro",
        )
        val changed = blocked.copy(
            displayName = "Nome novo",
            whatsapp = "11922222222",
        )
        assertEquals("passenger-permanent", changed.id)
        assertTrue(changed.blocked)
        assertEquals("Não aceito no meu carro", changed.blockedReason)
    }

    @Test
    fun automaticDirectoryUsesUnifiedBaseAndCarriesBlockState() {
        val autoSync = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        val remote = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        assertTrue(autoSync.contains("PassengerIdentityStore(context).profiles()"))
        assertTrue(autoSync.contains("agendaAccessContact()"))
        assertTrue(remote.contains("passengerContact = it.agendaAccessContact()"))
        assertTrue(remote.contains("blocked = it.blocked"))
    }

    @Test
    fun timelineShowsBlockInlineAndSynchronizesByPassengerId() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertTrue(timeline.contains("⛔ NÃO ACEITO NO MEU CARRO"))
        assertTrue(timeline.contains("setPassengerAccessBlocked("))
        assertTrue(timeline.contains("passengerId = saved.id"))
        assertTrue(timeline.contains("PublicBookingRemoteSync0296.pullAndReconcile(context, store)"))
        assertTrue(timeline.contains("profileByExternalPassengerId"))
    }

    @Test
    fun driverNoLongerApprovesNormalPassengerManually() {
        val admin = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerAdminUi.kt").readText()
        assertTrue(admin.contains("Acesso automático pela base unificada"))
        assertTrue(admin.contains("Não aceito no meu carro"))
        assertFalse(admin.contains("Aprovar e autorizar"))
        assertFalse(admin.contains("Suspender acesso"))
    }

    @Test
    fun blockedFlagIsSerializedWithCanonicalProfileForRestartDurability() {
        val identity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerIdentityStore.kt").readText()
        assertTrue(identity.contains("val blocked: Boolean = false"))
        assertTrue(identity.contains("fun setBlocked(profileId: String, blocked: Boolean"))
        assertTrue(identity.contains("putString(profilesKey, json.encodeToString(listOf(normalized) + current)).apply()"))
        assertTrue(identity.contains("target.copy(blocked = blocked"))
    }
}
