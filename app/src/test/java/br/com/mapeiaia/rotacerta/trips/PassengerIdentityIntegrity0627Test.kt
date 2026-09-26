package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerIdentityIntegrity0627Test {
    @Test
    fun kelSentinelConsolidatesOnePhysicalPassengerEvenWhenLegacyBookingIdsDiffer() {
        val profiles = listOf(
            PassengerProfile(
                id = "kel-1",
                displayName = "Kel",
                whatsapp = "+5511947434112",
                externalPassengerIds = setOf("legacy-booking-a"),
                createdAtMillis = 10L,
                updatedAtMillis = 20L,
            ),
            PassengerProfile(
                id = "kel-2",
                displayName = "kel",
                whatsapp = "(11) 94743-4112",
                externalPassengerIds = setOf("legacy-booking-b"),
                createdAtMillis = 30L,
                updatedAtMillis = 40L,
            ),
            PassengerProfile(
                id = "kel-3",
                displayName = "KEL",
                whatsapp = "11947434112",
                externalPassengerIds = setOf("legacy-booking-c"),
                createdAtMillis = 50L,
                updatedAtMillis = 60L,
            ),
            PassengerProfile(
                id = "kel-4",
                displayName = "Kel",
                whatsapp = "55 11 94743-4112",
                externalPassengerIds = setOf("legacy-booking-d"),
                createdAtMillis = 70L,
                updatedAtMillis = 80L,
            ),
        )

        val aliases = buildPassengerMergeAliases0627(profiles)

        assertEquals(3, aliases.size)
        assertEquals(setOf("kel-2", "kel-3", "kel-4"), aliases.keys)
        assertTrue(aliases.values.all { it == "kel-1" })

        val merged = mergePassengerProfiles0627("kel-1", profiles)
        assertEquals("kel-1", merged.id)
        assertEquals("kel", normalizePassengerSearch(merged.displayName))
        assertEquals("11947434112", passengerContactKey(merged.whatsapp))
        assertEquals(
            setOf("legacy-booking-a", "legacy-booking-b", "legacy-booking-c", "legacy-booking-d"),
            merged.externalPassengerIds,
        )
    }

    @Test
    fun samePhoneWithDifferentNamesDoesNotAutoMerge() {
        val aliases = buildPassengerMergeAliases0627(
            listOf(
                PassengerProfile(id = "a", displayName = "Ana", whatsapp = "11947434112"),
                PassengerProfile(id = "b", displayName = "Beatriz", whatsapp = "+5511947434112"),
            ),
        )

        assertTrue(aliases.isEmpty())
    }

    @Test
    fun conflictingOnlineIdentitiesDoNotAutoMergeEvenWithSameNameAndPhone() {
        val aliases = buildPassengerMergeAliases0627(
            listOf(
                PassengerProfile(
                    id = "a",
                    displayName = "Kel",
                    whatsapp = "11947434112",
                    onlineIdentityIds = setOf("online-kel-a"),
                ),
                PassengerProfile(
                    id = "b",
                    displayName = "kel",
                    whatsapp = "+5511947434112",
                    onlineIdentityIds = setOf("online-kel-b"),
                ),
            ),
        )

        assertTrue(aliases.isEmpty())
    }

    @Test
    fun bookingUrlIsNeverAcceptedAsPassengerIdentity() {
        val booking = "https://www.blablacar.com.br/rides/offer/booking/booking-12345678?id=trip-12345678"
        val passenger = "https://www.blablacar.com.br/rides/offer/passenger/passenger-12345678/0?id=trip-12345678"

        assertEquals("", BlaBlaCollectorUrlModule.passengerIdentityKey(booking))
        assertEquals("passenger-12345678", BlaBlaCollectorUrlModule.passengerIdentityKey(passenger))
        assertFalse(BlaBlaCollectorUrlModule.passengerIdentityKey(booking).contains("booking"))
    }
}
