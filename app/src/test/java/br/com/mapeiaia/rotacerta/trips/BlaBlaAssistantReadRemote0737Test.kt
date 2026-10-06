package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BlaBlaAssistantReadRemote0737Test {
    @Test
    fun canonicalUuid_acceptsRealBlaBlaUuidV7TripId() {
        val value = "01a0359e-de23-7a2b-ab27-43990c399a74"
        assertEquals(value, canonicalUuid0737(value))
    }

    @Test
    fun canonicalUuid_rejectsNamesAndMalformedValues() {
        assertNull(canonicalUuid0737("Ezequiel"))
        assertNull(canonicalUuid0737("01a0359e-de23"))
    }

    @Test
    fun remotePayloadPrivacyDefaultsAreFailClosed() {
        val privacy = BlaBlaHtmlRemotePrivacy0737()
        assertFalse(privacy.rawHtmlUploaded)
        assertFalse(privacy.cookiesUploaded)
        assertFalse(privacy.blablaCredentialsUploaded)
        assertFalse(privacy.passengerPhoneUploaded)
        assertFalse(privacy.passengerBookingHrefUploaded)
        assertFalse(privacy.writesAgenda)
        assertFalse(privacy.writesTimeline)
        assertFalse(privacy.writesCollectorCanonicalState)
    }
}
