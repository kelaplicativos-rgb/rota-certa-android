package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaAuthenticatedSessionStage47Test {
    @Test
    fun dynamicAccountHasNoBlaBlaIdentityUntilDiscovered() {
        val account = BlaBlaDynamicAccount(
            id = "local-account-id",
            label = "Conta 1",
            webProfileName = "rota_certa_blablacar_localaccountid",
        )

        assertNull(account.profileUuid)
        assertNull(account.profileName)
        assertNull(account.verifiedDefinition())
    }

    @Test
    fun discoveredUuidCreatesVerifiedExternalDefinition() {
        val account = BlaBlaDynamicAccount(
            id = "local-account-id",
            label = "Minha conta",
            webProfileName = "rota_certa_blablacar_localaccountid",
            profileUuid = "7371f028-9c55-4903-8444-308015823efd",
            profileName = "Nome público",
        )

        val definition = assertNotNull(account.verifiedDefinition())
        assertEquals("7371f028-9c55-4903-8444-308015823efd", definition.uuid)
        assertEquals("Nome público", definition.label)
        assertEquals("local-account-id", definition.slot)
    }

    @Test
    fun ridesOfferIsAcceptedOnlyWhenExpectedUuidAppearsInDriverProfileLink() {
        val candidate = BlaBlaDomRideCandidate(
            href = "https://www.blablacar.com.br/rides/offer?id=trip-123",
            text = "21 de agosto 11:00 17:10 Santo André Três Corações",
            departureTime = "11:00",
            arrivalTime = "17:10",
            origin = "Santo André",
            destination = "Três Corações",
            dateText = "21 de agosto",
        )
        val account = BlaBlaAccountDefinition(
            slot = "dynamic-id",
            label = "Conta validada",
            uuid = "7371f028-9c55-4903-8444-308015823efd",
            dataDirectorySuffix = "dynamic-profile",
        )
        val verifiedDetail = BlaBlaDomTripDetail(
            url = candidate.href,
            dateText = "21 de agosto de 2026",
            departureTime = "11:00",
            arrivalTime = "17:10",
            origin = "Santo André",
            destination = "Três Corações",
            driverName = "Nome público",
            profileLinks = listOf("https://www.blablacar.com.br/users/show/7371f028-9c55-4903-8444-308015823efd"),
        )
        val wrongDetail = verifiedDetail.copy(
            profileLinks = listOf("https://www.blablacar.com.br/users/show/175a7068-50d8-40c3-a27a-214b9c6e0461"),
        )

        val verified = BlaBlaDomNormalizer.toTrip(account, candidate, verifiedDetail, LocalDate.of(2026, 8, 20))
        assertNotNull(verified)
        assertEquals("trip-123", verified.trip_id)
        assertEquals("verified_from_trip_detail_profile_link", verified.uuid_validation)
        assertEquals("2026-08-21", verified.date)
        assertEquals(account.uuid, verified.profile_uuid)

        assertNull(BlaBlaDomNormalizer.toTrip(account, candidate, wrongDetail, LocalDate.of(2026, 8, 20)))
    }

    @Test
    fun portugueseDatesNormalizeWithoutUsingDisplayNameAsIdentity() {
        assertEquals(LocalDate.of(2026, 8, 21), BlaBlaDomNormalizer.parseDate("sexta, 21 de agosto de 2026", LocalDate.of(2026, 8, 20)))
        assertEquals(LocalDate.of(2026, 8, 20), BlaBlaDomNormalizer.parseDate("Hoje", LocalDate.of(2026, 8, 20)))
        assertEquals(LocalDate.of(2026, 8, 21), BlaBlaDomNormalizer.parseDate("Amanhã", LocalDate.of(2026, 8, 20)))
        assertTrue(BlaBlaDomNormalizer.parseDate("21/08/2026", LocalDate.of(2026, 8, 20)) != null)
    }
}
