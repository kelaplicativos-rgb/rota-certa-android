package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PassengerMessageVehicle0719Test {
    private val entry = TripTimelineEntry(
        tripId = "trip-0719",
        profileId = "profile",
        profileLabel = "Motorista",
        departureAtMillis = 1_800_000_000_000L,
        arrivalAtMillis = null,
        origin = "Três Corações",
        destination = "São Paulo",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = 1,
        maximumOccupiedSeats = 1,
        sourcePassengerSeats = emptyMap(),
    )

    private val row = EnhancedPassengerCardRow(
        name = "Gabriela Souza",
        phone = null,
        seats = 1,
        boarding = "Três Corações",
        dropoff = "São Paulo",
        sources = setOf(BookingSource.ROTA_CERTA),
        fareMinorUnits = 9_300L,
        fareCurrencyCode = "BRL",
    )

    private fun vehicle() = PassengerMessageVehicle0714(
        makeModel = "Hyundai HB20",
        color = "Cinza-escuro",
        plate = "TBJ4F74",
    )

    @Test
    fun carOfDayAlwaysWinsWhenConfigured() {
        val selected = choosePassengerMessageVehicle0719(
            tripConfigured = true,
            tripVehicle = vehicle(),
            settingsVehicle = PassengerMessageVehicle0714("Outro carro", "Branco", ""),
            profileVehicle = PassengerMessageVehicle0714("Perfil", "Preto", ""),
        )
        assertEquals(vehicle(), selected)
    }

    @Test
    fun rotaCertaSettingsWinBeforeBlaBlaProfileWhenNoCarOfDayExists() {
        val settings = PassengerMessageVehicle0714("Hyundai HB20", "Cinza", "")
        val selected = choosePassengerMessageVehicle0719(
            tripConfigured = false,
            tripVehicle = PassengerMessageVehicle0714(),
            settingsVehicle = settings,
            profileVehicle = PassengerMessageVehicle0714("Perfil antigo", "Azul", ""),
        )
        assertEquals(settings, selected)
    }

    @Test
    fun profileIsOnlyFallbackAndMissingProfileNeverStopsFallbackChain() {
        val profile = PassengerMessageVehicle0714("Veículo do perfil", "Prata", "")
        assertEquals(
            profile,
            choosePassengerMessageVehicle0719(
                tripConfigured = false,
                tripVehicle = PassengerMessageVehicle0714(),
                settingsVehicle = PassengerMessageVehicle0714(),
                profileVehicle = profile,
            ),
        )
        assertEquals(
            PassengerMessageVehicle0714(),
            choosePassengerMessageVehicle0719(
                tripConfigured = false,
                tripVehicle = PassengerMessageVehicle0714(),
                settingsVehicle = PassengerMessageVehicle0714(),
                profileVehicle = null,
            ),
        )
    }

    @Test
    fun everyPredefinedPassengerMessageCarriesTheSameVehicleBlock() {
        PassengerQuickMessageType0656.entries.forEach { type ->
            val message = passengerQuickMessageText0656(
                entry = entry,
                row = row,
                type = type,
                localeTag = "pt-BR",
                vehicleMakeModel = "Hyundai HB20",
                vehicleColor = "Cinza-escuro",
                vehiclePlate = "TBJ4F74",
            )
            assertContains(message, "🚗 Carro: Hyundai HB20 • cinza-escuro", "template=$type")
            assertContains(message, "Placa: TBJ4F74", "template=$type")
        }
    }
}
