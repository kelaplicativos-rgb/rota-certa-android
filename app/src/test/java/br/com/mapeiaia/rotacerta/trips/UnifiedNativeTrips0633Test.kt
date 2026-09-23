package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedNativeTrips0633Test {
    private fun trip(
        origin: TripRecordOrigin = TripRecordOrigin.LOCAL,
        profileUuid: String? = null,
        blablaTripId: String? = null,
    ) = Trip(
        id = "native-trip-0633",
        title = "Santo André → São Tomé das Letras",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 3,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "Santo André"),
            TripStop(id = "b", order = 1, name = "São Tomé das Letras"),
        ),
        rotaCertaSeatAllocation = 3,
        recordOrigin = origin,
        blablaProfileUuid = profileUuid,
        blablaTripId = blablaTripId,
        publicBookingEnabled = true,
    )

    @Test
    fun nativeRotaCertaTripIsCanonicalWithoutBlaBlaIdentity() {
        val native = trip()
        assertTrue(native.isNativeRotaCertaTrip0633())
        assertFalse(native.usesGlobalExtraSeats0633())
    }

    @Test
    fun externalTripKeepsBlaBlaSeatChannelBehavior() {
        val external = trip(
            origin = TripRecordOrigin.EXTERNAL_BACKING,
            profileUuid = "profile-0633",
            blablaTripId = "bb-trip-0633",
        )
        assertFalse(external.isNativeRotaCertaTrip0633())
        assertTrue(external.usesGlobalExtraSeats0633())
    }

    @Test
    fun tripCenterIsUnifiedAndExposesVisibleCreateAction() {
        val header = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaHeaderNavigation0396.kt").readText()
        val browser = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()

        assertTrue(header.contains("ALL_TRIPS(\"Viagens\")"))
        assertTrue(browser.contains("val entries = remember(projectedEntries)"))
        assertTrue(browser.contains("Text(\"+ Nova viagem\")"))
        assertTrue(browser.contains("nativeRotaCerta0633"))
        assertTrue(browser.contains("onManageCanonicalTrip(canonicalId)"))
        assertFalse(browser.contains("Text(\"Nenhuma conta BlaBlaCar conectada.\")"))
        assertTrue(activity.contains("TripScreen.TIMELINE -> \"Viagens\""))
    }

    @Test
    fun nativeCreationPublishesFirstClassPublicTrip() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val editorStart = activity.indexOf("private fun TripEditor(")
        val editorEnd = activity.indexOf("private fun requestAgendaTripHtmlRefresh0607", editorStart)
        assertTrue(editorStart >= 0 && editorEnd > editorStart)
        val editor = activity.substring(editorStart, editorEnd)

        assertTrue(editor.contains("status = TripStatus.PUBLISHED"))
        assertTrue(editor.contains("publicBookingEnabled = true"))
        assertTrue(editor.contains("recordOrigin = TripRecordOrigin.LOCAL"))
        assertTrue(editor.contains("Text(if (initialTrip0633 == null) \"Publicar viagem\" else \"Salvar alterações\")"))
        assertTrue(editor.contains("Capacidade própria desta viagem. Não depende das vagas da BlaBlaCar."))
    }

    @Test
    fun nativeCreateEditCancelAndDeleteAreBackendFirst() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()

        assertTrue(activity.contains("TripRemoteApi(online0494).publish") || activity.contains("api0633.publish(trip)"))
        assertTrue(activity.contains("val published0494 = api0633.update(mutation0633)"))
        assertTrue(activity.contains("A viagem mudou em outro dispositivo. Atualize antes de editar novamente."))
        assertTrue(activity.contains("Viagem cancelada no estado canônico e removida da Agenda Pública."))
        assertTrue(activity.contains("publicationTombstone = true"))
        assertTrue(activity.contains("A viagem não foi excluída para evitar ficar publicada sem controle."))
    }

    @Test
    fun publicAgendaUsesSameCardAndNativeActionInsteadOfBrokenBlaBlaButton() {
        val shell = File("../trip-platform/public/public-agenda-shell-0569.js").readText()
        val rendererStart = shell.indexOf("function renderTripCard0569")
        val rendererEnd = shell.indexOf("function renderAgenda0569", rendererStart)
        assertTrue(rendererStart >= 0 && rendererEnd > rendererStart)
        val renderer = shell.substring(rendererStart, rendererEnd)

        assertTrue(renderer.contains("appendSegmentAvailability0580(card, item, stops)"))
        assertTrue(renderer.contains("shareTrip0623(item)"))
        assertTrue(renderer.contains("viewRide.textContent = \"Ver anúncio na BlaBlaCar\""))
        assertTrue(renderer.contains("viewNativeTrip.textContent = \"Ver viagem\""))
        assertTrue(renderer.contains("openFullTripBooking0623(item)"))
        assertFalse(renderer.contains("unavailable.textContent = \"Ver anúncio na BlaBlaCar\""))
    }

    @Test
    fun backendKeepsTripMutationAndCapacityValidationAtomic() {
        val backend = File("../trip-platform/functions/index.js").readText()
        val start = backend.indexOf("async function updateDriverTrip(")
        val end = backend.indexOf("async function", start + 30)
        assertTrue(start >= 0 && end > start)
        val fn = backend.substring(start, end)

        assertTrue(fn.contains("db.runTransaction"))
        assertTrue(fn.contains("reconciledSegmentCapacity"))
        assertTrue(fn.contains("assertNoOverbooking"))
        assertTrue(fn.contains("pending_reservations_require_decision"))
        assertTrue(fn.contains("canonicalServerProjectionPatch0468"))
    }
}
