package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PublicProjectionVehicleHashParity0725Test {
    private fun payload0725(
        physicalSeatCapacity: Int = 4,
        vehicleDayConfigured: Boolean = true,
        vehicleMakeModel: String = "Hyundai HB20",
        vehicleColor: String = "Cinza",
        vehiclePlate: String = "ABC1D23",
    ) = CanonicalPublicTripPayload0411(
        canonicalTripId = "trip-0725",
        canonicalRevision = 28L,
        blablaProfileUuid = "profile-0725",
        blablaProfileName = "Ezequiel",
        blablaTripId = "blabla-0725",
        title = "São Paulo → Três Corações",
        departureAtMillis = 1_800_000_000_000L,
        agendaVisibleUntilMillis0581 = 1_800_003_600_000L,
        timezoneId = "America/Sao_Paulo",
        status = "PUBLISHED",
        capacity = 4,
        physicalSeatCapacity = physicalSeatCapacity,
        vehicleDayConfigured = vehicleDayConfigured,
        vehicleMakeModel = vehicleMakeModel,
        vehicleColor = vehicleColor,
        vehiclePlate = vehiclePlate,
        stops = listOf(
            CanonicalPublicStop0411(
                id = "a",
                order = 0,
                name = "São Paulo",
                address = "Rua A",
                plannedDepartureMillis = 1_800_000_000_000L,
            ),
            CanonicalPublicStop0411(
                id = "b",
                order = 1,
                name = "Três Corações",
                address = "Rua B",
                plannedArrivalMillis = 1_800_010_800_000L,
            ),
        ),
        segmentLoads = listOf(1),
        segmentPassengerLoads = listOf(1),
        segmentBlockedLoads = listOf(0),
        availableSeatsMinimum = 3,
        availableSeatsMaximum = 3,
        operationalAvailableSeats = 3,
        publishedSeats = 3,
        rotaCertaSeatAllocation = 0,
        publicBookingEnabled = true,
        capacityReliable = true,
        itineraryAuthoritative = true,
        publicUrl = "https://rota-certa.example/trip-0725",
        blablaPublicUrl = "",
        publicationRevision = 32L,
        canonicalStateHash = "server-canonical-v1:" + "a".repeat(64),
    )

    @Test
    fun androidCanonicalJsonMatchesBackendVehicleFieldShapeAndOrder() {
        val json = canonicalPublicProjectionJson0411(payload0725().copy(publicationRevision = 0L))
        val orderedFields = listOf(
            "capacity",
            "physicalSeatCapacity",
            "vehicleDayConfigured",
            "vehicleMakeModel",
            "vehicleColor",
            "vehiclePlate",
            "stops",
        )
        val positions = orderedFields.map { field -> json.indexOf("\"$field\":") }
        assertTrue(positions.all { it >= 0 }, "Todos os campos do veículo precisam participar do JSON canônico.")
        assertTrue(
            positions.zipWithNext().all { (left, right) -> left < right },
            "A ordem serializada deve permanecer idêntica ao objeto canônico do backend.",
        )

        val backend = File("../trip-platform/functions/index.js").readText()
        val start = backend.indexOf("function canonicalPublicTripPayloadFromStored0434(raw)")
        val end = backend.indexOf("function canonicalPublicTripPayload0411(", start)
        assertTrue(start >= 0 && end > start)
        val backendShape = backend.substring(start, end)
        orderedFields.forEach { field ->
            assertTrue(
                backendShape.contains("$field:"),
                "Backend e Android precisam compartilhar o campo canônico $field.",
            )
        }
    }

    @Test
    fun everyVehicleFieldChangesTheCanonicalPublicHash() {
        val base = payload0725()
        val baseline = canonicalPublicProjectionHash0411(base)

        assertNotEquals(baseline, canonicalPublicProjectionHash0411(base.copy(physicalSeatCapacity = 7)))
        assertNotEquals(baseline, canonicalPublicProjectionHash0411(base.copy(vehicleDayConfigured = false)))
        assertNotEquals(baseline, canonicalPublicProjectionHash0411(base.copy(vehicleMakeModel = "Toyota Corolla")))
        assertNotEquals(baseline, canonicalPublicProjectionHash0411(base.copy(vehicleColor = "Preto")))
        assertNotEquals(baseline, canonicalPublicProjectionHash0411(base.copy(vehiclePlate = "XYZ9Z99")))
    }

    @Test
    fun publicationRevisionRemainsTransportOnlyWhileVehicleStateIsSemantic() {
        val base = payload0725()
        assertTrue(
            canonicalPublicProjectionHash0411(base) ==
                canonicalPublicProjectionHash0411(base.copy(publicationRevision = 999L)),
            "publicationRevision não pode alterar o hash semântico.",
        )
        assertNotEquals(
            canonicalPublicProjectionHash0411(base),
            canonicalPublicProjectionHash0411(base.copy(vehiclePlate = "DEF2G34")),
            "A identificação canônica do Carro do Dia deve alterar o hash público.",
        )
    }
}
