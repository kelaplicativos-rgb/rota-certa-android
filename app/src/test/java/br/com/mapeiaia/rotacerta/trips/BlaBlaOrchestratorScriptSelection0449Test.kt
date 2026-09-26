package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaOrchestratorScriptSelection0449Test {
    private val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt").readText()
    private val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
    private val session = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorSessionModule.kt").readText()
    private val workspace = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaScriptWorkspace0486.kt").readText()
    private val registry = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaBrowserScriptRegistry.kt").readText()

    private fun trip(
        publicUrl: String? = "https://www.blablacar.com.br/trip?id=old-public",
        seats: Int? = 2,
        passenger: String = "Anterior",
        time: String = "10:00",
    ) = BlaBlaCollectorTrip(
        profile_uuid = "profile-0449",
        profile_name = "Motorista",
        date = "2026-09-04",
        departure_time = time,
        actual_departure = "Origem",
        actual_arrival = "Destino",
        trip_href = "https://www.blablacar.com.br/rides/offer?id=trip-0449",
        public_trip_href = publicUrl,
        public_trip_href_source = "previous",
        public_trip_href_binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        trip_id = "trip-0449",
        passengers = listOf(BlaBlaCollectorPassenger(name = passenger, seats = 1)),
        booked_seats = 1,
        published_seats = seats,
        passenger_roster_complete = true,
    )

    @Test
    fun datePeriodUiSelectsProfilesAndOnlyQueuesEnabledProfiles() {
        assertTrue(ui.contains("Text(\"Perfis\""))
        assertTrue(ui.contains("dateScopeSelectedAccountIds0449"))
        assertTrue(ui.contains("Switch("))
        assertTrue(ui.contains("selectedAccounts0449 = accounts.filter"))
        assertTrue(ui.contains("syncQueue = selectedAccounts0449.map { it.id }"))
    }

    @Test
    fun centralScriptWorkspaceExposesAll32RegisteredScriptsWithoutDuplicatingDatePeriodUi() {
        assertEquals(32, BlaBlaDateScopeScriptCatalog0449.selectableRequests.size)
        assertEquals(32, BlaBlaDateScopeScriptCatalog0449.selectableRequests.distinct().size)
        assertEquals(
            BlaBlaBrowserRequest.values().toSet(),
            BlaBlaDateScopeScriptCatalog0449.all,
        )
        assertEquals(
            listOf(
                "Conta e perfil",
                "Viagem",
                "Passageiros",
                "Vagas",
                "Pesquisa pública BlaBlaCar",
                "Mensagens",
                "Viagens arquivadas",
                "Diagnóstico",
            ),
            BlaBlaDateScopeScriptCatalog0449.groups.map { it.title },
        )
        assertEquals(22, BlaBlaDateScopeScriptCatalog0449.all.count { it.operation == BlaBlaBrowserOperation.CAPTURE })
        assertEquals(8, BlaBlaDateScopeScriptCatalog0449.all.count { it.operation == BlaBlaBrowserOperation.NAVIGATION })
        assertEquals(2, BlaBlaDateScopeScriptCatalog0449.all.count { it.operation == BlaBlaBrowserOperation.REMOTE_WRITE })
        assertEquals(
            setOf(BlaBlaBrowserRequest.SEAT_CHANGE, BlaBlaBrowserRequest.SEAT_SAVE),
            BlaBlaDateScopeScriptCatalog0449.remoteWriteRequests,
        )

        assertTrue(workspace.contains("BlaBlaDateScopeScriptCatalog0449.groups.forEach"))
        assertTrue(workspace.contains("group.requests.forEachIndexed"))
        assertTrue(workspace.contains("Text(\"Editar\")"))
        assertTrue(workspace.contains("Text(\"Salvar\")"))
        assertTrue(workspace.contains("Text(\"Restaurar original\")"))
        assertTrue(workspace.contains("Usar em Sincronizar por data/período"))
        assertTrue(workspace.contains("REMOTE_WRITE protegido"))
        assertTrue(registry.contains("workspace.overrideScript(request) ?: originalTemplate(request)"))

        assertTrue(ui.contains("scriptWorkspace0486.dateScopeEnabledRequests()"))
        assertTrue(ui.contains("centralizados no menu Scripts"))
        assertFalse(ui.contains("BlaBlaDateScopeScriptCatalog0449.groups.forEach"))
        assertFalse(ui.contains("Text(\"Todos\")"))
        assertFalse(ui.contains("Text(\"Só vagas\")"))
    }

    @Test
    fun uiCatalogMatchesPhysicalAndRegisteredScriptInventoryExactly() {
        val physical = File("src/main/assets/blablacar/scripts")
            .listFiles()
            .orEmpty()
            .filter { it.extension == "js" }
            .map { it.name }
            .toSet()
        val registered = BlaBlaBrowserRequest.values().map { it.assetName }.toSet()
        val exposed = BlaBlaDateScopeScriptCatalog0449.selectableRequests.map { it.assetName }.toSet()

        assertEquals(32, physical.size)
        assertEquals(32, registered.size)
        assertEquals(physical, registered)
        assertEquals(registered, exposed)
    }

    @Test
    fun datePeriodDefaultsKeepCanonicalListingAndPassengersButDoNotRequireSeatFlow0478() {
        val defaults = BlaBlaDateScopeScriptCatalog0449.dateScopeDefaultRequests0478

        assertEquals(29, defaults.size)
        assertTrue(BlaBlaDateScopeScriptCatalog0449.coreTripRequests.all(defaults::contains))
        assertTrue(BlaBlaDateScopeScriptCatalog0449.passengerRequests.all(defaults::contains))
        assertTrue(BlaBlaDateScopeScriptCatalog0449.publicUrlRequests.all(defaults::contains))
        assertTrue(BlaBlaDateScopeScriptCatalog0449.seatFlowRequests0478.none(defaults::contains))
        assertFalse(BlaBlaBrowserRequest.SEAT_OPTIONS in defaults)
        assertTrue(workspace.contains("return BlaBlaDateScopeScriptCatalog0449.dateScopeDefaultRequests0478"))
        assertTrue(ui.contains("dateScopeSelectedScripts0449 = scriptWorkspace0486.dateScopeEnabledRequests()"))
        assertTrue(workspace.contains("restoreDateScopeDefaults"))
    }

    @Test
    fun datePeriodDefault0478MaterializesNewTripWithoutSeatOptionsAndPreservesKnownSeats() {
        val selection = BlaBlaDateScopeScriptSelection0449.explicit(
            BlaBlaDateScopeScriptCatalog0449.dateScopeDefaultRequests0478,
        )
        val fresh = trip(
            publicUrl = "https://www.blablacar.com.br/trip?id=fresh-0478",
            seats = null,
            passenger = "Passageiro atual",
            time = "11:00",
        )
        val newTrip = mergeSelectiveCollectorTrip0449(null, fresh, selection)
        assertEquals("11:00", newTrip?.departure_time)
        assertEquals("Passageiro atual", newTrip?.passengers?.singleOrNull()?.name)
        assertEquals("https://www.blablacar.com.br/trip?id=fresh-0478", newTrip?.public_trip_href)
        assertNull(newTrip?.published_seats)
        assertFalse(selection.wantsSeatData())

        val previous = trip(seats = 3, passenger = "Anterior", time = "10:00")
        val refreshed = mergeSelectiveCollectorTrip0449(previous, fresh, selection)
        assertEquals(3, refreshed?.published_seats)
        assertEquals("11:00", refreshed?.departure_time)
        assertEquals("Passageiro atual", refreshed?.passengers?.singleOrNull()?.name)
    }

    @Test
    fun onlySeatsUpdatesPublishedSeatsAndPreservesEveryUnselectedField() {
        val previous = trip(seats = 2, passenger = "Anterior")
        val fresh = trip(
            publicUrl = "https://www.blablacar.com.br/trip?id=new-public",
            seats = 4,
            passenger = "Novo",
            time = "11:30",
        )
        val selection = BlaBlaDateScopeScriptSelection0449.explicit(
            BlaBlaDateScopeScriptCatalog0449.seatRequests,
        )
        val merged = mergeSelectiveCollectorTrip0449(previous, fresh, selection)

        assertEquals(4, merged?.published_seats)
        assertEquals(previous.public_trip_href, merged?.public_trip_href)
        assertEquals(previous.passengers, merged?.passengers)
        assertEquals(previous.departure_time, merged?.departure_time)
        assertFalse(selection.wantsPublicUrl())
        assertFalse(selection.wantsPassengerData())
        assertTrue(selection.wantsSeatData())
    }

    @Test
    fun publicUrlOffPreservesExistingPermalinkEvenWhenFreshCaptureContainsAnotherOne() {
        val previous = trip(publicUrl = "https://www.blablacar.com.br/trip?id=keep-me")
        val fresh = trip(publicUrl = "https://www.blablacar.com.br/trip?id=do-not-commit")
        val selection = BlaBlaDateScopeScriptSelection0449.explicit(
            BlaBlaDateScopeScriptCatalog0449.all - BlaBlaDateScopeScriptCatalog0449.publicUrlRequests,
        )

        val merged = mergeSelectiveCollectorTrip0449(previous, fresh, selection)

        assertEquals(previous.public_trip_href, merged?.public_trip_href)
        assertEquals(previous.public_trip_href_source, merged?.public_trip_href_source)
        assertFalse(selection.wantsPublicUrl())
        assertTrue(dynamic.contains("group=public_url requested=false action=skip_capture_preserve_previous"))
        assertTrue(dynamic.contains("if (!scriptSelection0449.wantsPublicUrl())"))
    }

    @Test
    fun allScriptsKeepsLegacyFullFreshTripBehavior() {
        val previous = trip(seats = 1, passenger = "Anterior")
        val fresh = trip(
            publicUrl = "https://www.blablacar.com.br/trip?id=fresh",
            seats = 3,
            passenger = "Atual",
            time = "12:00",
        )
        val selection = BlaBlaDateScopeScriptSelection0449.explicit(BlaBlaDateScopeScriptCatalog0449.all)

        assertFalse(selection.selective)
        assertEquals(fresh, mergeSelectiveCollectorTrip0449(previous, fresh, selection))
    }

    @Test
    fun downstreamOnlySelectionDoesNotInventNewTripWithoutExistingCanonicalCard() {
        val fresh = trip(seats = 3)
        val selection = BlaBlaDateScopeScriptSelection0449.explicit(BlaBlaDateScopeScriptCatalog0449.seatRequests)

        assertNull(mergeSelectiveCollectorTrip0449(null, fresh, selection))
        assertTrue(dynamic.contains("SELECTIVE_SYNC_REQUIRES_EXISTING_TRIP_0449"))
    }

    @Test
    fun explicitScriptSelectionTravelsThroughIntentAndSelectiveSnapshotCannotDeleteSiblings() {
        assertTrue(dynamic.contains("EXTRA_ENABLED_SCRIPTS_0449"))
        assertTrue(dynamic.contains("internal fun syncDates("))
        assertTrue(dynamic.contains("enabledScripts: Collection<BlaBlaBrowserRequest>"))
        assertTrue(dynamic.contains("private fun syncDatesInternal0449("))
        assertTrue(dynamic.contains("BlaBlaDateScopeScriptSelection0449.fromNames"))
        assertTrue(dynamic.contains("selectiveScriptSync0449 = scriptSelection0449.selective"))
        assertTrue(session.contains("selectiveScriptSync0449: Boolean = false"))
        assertTrue(session.contains("&& !selectiveScriptSync0449"))
    }

    @Test
    fun passengerAndSeatStagesAreGatedByRequestedOutputs() {
        assertTrue(dynamic.contains("if (scriptSelection0449.wantsPassengerData())"))
        assertTrue(dynamic.contains("scriptSelection0449.wantsSeatData()"))
        assertTrue(dynamic.contains("group=passengers requested=false action=preserve_previous"))
        assertTrue(dynamic.contains("passengers = if (scriptSelection0449.wantsPassengerData())"))
        assertTrue(dynamic.contains("publishedSeats = if (scriptSelection0449.wantsSeatData())"))
    }
}
