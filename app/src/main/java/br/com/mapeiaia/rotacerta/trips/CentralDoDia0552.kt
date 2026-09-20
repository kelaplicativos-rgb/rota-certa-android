package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.DiagnosticEventContext0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Central do Dia is deliberately a read model. It owns no canonical state and no persistence.
 * All business values come from TripStore-owned Trip/Booking state or an existing canonical
 * projection. Correction commands are delegated to AgendaBackgroundSync0392.
 */
internal enum class CentralIntegrityLevel0552 {
    OK,
    DIVERGENCE,
    ACTION_REQUIRED,
    UNKNOWN,
}

internal data class CentralIntegrityCheck0552(
    val key: String,
    val level: CentralIntegrityLevel0552,
    val title: String,
    val expected: String = "",
    val actual: String = "",
    val source: String = "",
    val detail: String = "",
)

internal data class CentralPassenger0552(
    val bookingId: String,
    val name: String,
    val seats: Int,
    val stateLabel: String,
    val source: BookingSource,
)

internal data class CentralTrip0552(
    val canonicalTripId: String,
    val departureAtMillis: Long,
    val expectedEndAtMillis: Long?,
    val profileUuid: String,
    val profileLabel: String,
    val origin: String,
    val destination: String,
    val passengerSeats: Int,
    val availableSeats: Int?,
    val operationalCapacity: Int?,
    val segmentLoads: List<SegmentLoad>,
    val passengers: List<CentralPassenger0552>,
    val checks: List<CentralIntegrityCheck0552>,
    val integrity: CentralIntegrityLevel0552,
    val nextAction: String,
    val canonicalRevision: Long,
    val canonicalStateHash: String,
    val expectedHash: String,
    val actualHash: String,
    val expectedBytes: Int,
    val actualBytes: Int,
    val firstDifferentByteOffset: Int,
    val differentByteRanges: List<String>,
    val readbackAtMillis: Long,
)

internal data class CentralDaySummary0552(
    val trips: Int,
    val passengerSeats: Int,
    val availableSeats: Int,
    val divergenceCount: Int,
    val actionRequiredCount: Int,
    val unknownCount: Int,
    val passedChecks: Int,
    val verifiableChecks: Int,
) {
    val integrityPercent: Int?
        get() = if (verifiableChecks <= 0) null else ((passedChecks * 100.0) / verifiableChecks).toInt().coerceIn(0, 100)
}

internal data class CentralDayReadModel0552(
    val date: LocalDate,
    val trips: List<CentralTrip0552>,
    val summary: CentralDaySummary0552,
    val nextAction: String,
)

internal object CentralDayReadModelBuilder0552 {
    fun build(
        trips: List<Trip>,
        bookings: List<Booking>,
        accounts: List<BlaBlaDynamicAccount>,
        date: LocalDate,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        localProfileLabel: String = "Agenda",
    ): CentralDayReadModel0552 {
        val canonicalTrips = trips
            .asSequence()
            .filterNot(Trip::deleted)
            .filter { trip -> Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId).toLocalDate() == date }
            .sortedBy(Trip::departureAtMillis)
            .toList()
        val byTrip = bookings.groupBy(Booking::tripId)
        val profileLabels = accounts
            .mapNotNull { account -> account.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let { it to account.displayLabel } }
            .toMap()
        val timelineProjection = localAgendaTimelineProjection0515(
            trips = trips,
            bookings = bookings,
            localProfileLabel = localProfileLabel,
            nowMillis = nowMillis,
        )
        val timelineLiveIds = timelineProjection.entries
            .filter { entry ->
                isPassengerTimelineCurrentOrUpcoming0548(
                    departureAtMillis = entry.departureAtMillis,
                    arrivalAtMillis = entry.arrivalAtMillis,
                    nowMillis = nowMillis,
                )
            }
            .flatMap { entry -> listOfNotNull(entry.tripId, entry.localTripId) }
            .filter(String::isNotBlank)
            .toSet()
        val operationalEntries = operationalConnectedSelection0564(
            entries = timelineProjection.entries,
            accounts = accounts,
        ).includedEntries
        val operationalActiveIds = operationalArchiveSelection0566(
            items = operationalEntries,
            nowMillis = nowMillis,
            departureAtMillis = TripTimelineEntry::departureAtMillis,
            arrivalAtMillis = TripTimelineEntry::arrivalAtMillis,
        ).active
            .flatMap { entry -> listOfNotNull(entry.tripId, entry.localTripId) }
            .filter(String::isNotBlank)
            .toSet()

        val mutable = canonicalTrips.map { trip ->
            val tripBookings = byTrip[trip.id].orEmpty()
            buildTrip(
                trip = trip,
                bookings = tripBookings,
                timelinePresent = trip.id in timelineLiveIds || trip.tripKey in timelineLiveIds,
                operationalBrowserPresent = trip.id in operationalActiveIds || trip.tripKey in operationalActiveIds,
                profileLabel = trip.blablaProfileUuid?.trim()?.lowercase()?.let(profileLabels::get)
                    ?: trip.externalSnapshot?.profile_name?.takeIf(String::isNotBlank)
                    ?: localProfileLabel,
                nowMillis = nowMillis,
            ).let { MutableCentralTrip0552(it) }
        }

        addContinuityChecks(mutable)
        val finalTrips = mutable.map(MutableCentralTrip0552::freeze)
        val allChecks = finalTrips.flatMap(CentralTrip0552::checks)
        val verifiable = allChecks.count { it.level != CentralIntegrityLevel0552.UNKNOWN }
        val passed = allChecks.count { it.level == CentralIntegrityLevel0552.OK }
        val summary = CentralDaySummary0552(
            trips = finalTrips.size,
            passengerSeats = finalTrips.sumOf(CentralTrip0552::passengerSeats),
            availableSeats = finalTrips.mapNotNull(CentralTrip0552::availableSeats).sum(),
            divergenceCount = finalTrips.count { it.integrity == CentralIntegrityLevel0552.DIVERGENCE },
            actionRequiredCount = finalTrips.count { it.integrity == CentralIntegrityLevel0552.ACTION_REQUIRED },
            unknownCount = allChecks.count { it.level == CentralIntegrityLevel0552.UNKNOWN },
            passedChecks = passed,
            verifiableChecks = verifiable,
        )
        return CentralDayReadModel0552(
            date = date,
            trips = finalTrips,
            summary = summary,
            nextAction = chooseGlobalNextAction(finalTrips, nowMillis),
        )
    }

    private fun buildTrip(
        trip: Trip,
        bookings: List<Booking>,
        timelinePresent: Boolean,
        operationalBrowserPresent: Boolean,
        profileLabel: String,
        nowMillis: Long,
    ): CentralTrip0552 {
        val stops = trip.stops.sortedBy(TripStop::order)
        val summary = operationalSeatSummary(trip, bookings, nowMillis)
        val segmentLoads = SeatAvailabilityEngine.segmentLoads(trip, bookings, nowMillis)
        val worstOverbooking = segmentLoads
            .filter { it.overbookingSeats > 0 }
            .maxByOrNull(SegmentLoad::overbookingSeats)
        val expectedOperationalVisibility =
            trip.status != TripStatus.CANCELLED && canonicalAgendaTripStillVisible0581(trip, nowMillis)
        val checks = mutableListOf<CentralIntegrityCheck0552>()
        val profileUuid = trip.blablaProfileUuid?.trim()?.lowercase().orEmpty()
        val externalTripId = trip.blablaTripId?.trim().orEmpty()
        val external = trip.externalSnapshot
        val hasAnyExternalIdentity = profileUuid.isNotBlank() || externalTripId.isNotBlank() || external != null

        checks += when {
            !hasAnyExternalIdentity -> CentralIntegrityCheck0552(
                key = "external_identity",
                level = CentralIntegrityLevel0552.UNKNOWN,
                title = "Identidade BlaBlaCar não aplicável/confirmada",
                source = "Agenda canônica",
                detail = "Viagem canônica sem vínculo externo forte; não é marcada como correta por ausência de evidência.",
            )
            profileUuid.isBlank() || externalTripId.isBlank() -> CentralIntegrityCheck0552(
                key = "external_identity",
                level = CentralIntegrityLevel0552.ACTION_REQUIRED,
                title = "Identidade externa incompleta",
                expected = "profileUuid + tripId",
                actual = "profileUuid=${profileUuid.isNotBlank()} tripId=${externalTripId.isNotBlank()}",
                source = "Agenda canônica",
            )
            external != null && (!external.profile_uuid.equals(profileUuid, true) || external.trip_id?.trim() != externalTripId) -> CentralIntegrityCheck0552(
                key = "external_identity",
                level = CentralIntegrityLevel0552.ACTION_REQUIRED,
                title = "Vínculo externo incompatível",
                expected = "mesma identidade forte da Agenda",
                actual = "snapshot não corresponde ao vínculo canônico",
                source = "BlaBlaCar → Agenda",
            )
            else -> CentralIntegrityCheck0552(
                key = "external_identity",
                level = CentralIntegrityLevel0552.OK,
                title = "Identidade externa coerente",
                source = "Agenda canônica",
            )
        }

        checks += when {
            !expectedOperationalVisibility -> CentralIntegrityCheck0552(
                key = "timeline_projection",
                level = CentralIntegrityLevel0552.OK,
                title = "Viagem fora da janela operacional ativa",
                source = "ciclo de vida canônico compartilhado",
            )
            timelinePresent -> CentralIntegrityCheck0552(
                key = "timeline_projection",
                level = CentralIntegrityLevel0552.OK,
                title = "Projeção operacional da Timeline contém a viagem",
                source = "localAgendaTimelineProjection0515 + ciclo de vida canônico",
            )
            else -> CentralIntegrityCheck0552(
                key = "timeline_projection",
                level = CentralIntegrityLevel0552.ACTION_REQUIRED,
                title = "Viagem operacional ausente da Timeline",
                expected = "viagem ativa projetada",
                actual = "ausente após filtro operacional",
                source = "Agenda → Timeline",
            )
        }

        checks += when {
            !expectedOperationalVisibility -> CentralIntegrityCheck0552(
                key = "all_trips_visibility",
                level = CentralIntegrityLevel0552.OK,
                title = "Todas as viagens: janela operacional encerrada",
                source = "ciclo de vida canônico compartilhado",
            )
            operationalBrowserPresent -> CentralIntegrityCheck0552(
                key = "all_trips_visibility",
                level = CentralIntegrityLevel0552.OK,
                title = "Todas as viagens mantém esta viagem operacional",
                source = "OperationalAllTripsBrowser + ciclo de vida canônico",
            )
            else -> CentralIntegrityCheck0552(
                key = "all_trips_visibility",
                level = CentralIntegrityLevel0552.ACTION_REQUIRED,
                title = "Viagem operacional ausente de Todas as viagens",
                expected = "card visível durante toda a viagem",
                actual = "card ausente da sequência ativa",
                source = "Agenda → Todas as viagens",
            )
        }

        checks += when {
            !trip.capacityReliable -> CentralIntegrityCheck0552(
                key = "capacity",
                level = CentralIntegrityLevel0552.DIVERGENCE,
                title = "Capacidade ainda não é confiável",
                source = "motor canônico de vagas por trecho",
                detail = "A Central não recalcula capacidade paralelamente.",
            )
            summary.overbookingSeats > 0 -> CentralIntegrityCheck0552(
                key = "capacity",
                level = CentralIntegrityLevel0552.ACTION_REQUIRED,
                title = "Ocupação excede a capacidade em um trecho",
                expected = "nenhum trecho acima da capacidade operacional",
                actual = worstOverbooking?.let { load ->
                    "${load.from.name} → ${load.to.name}: ${load.occupiedSeats} ocupada(s), ${load.overbookingSeats} acima"
                } ?: "overbooking=${summary.overbookingSeats}",
                source = "SeatAvailabilityEngine por segmento",
                detail = "O alerta é por simultaneidade no trecho, não pelo total de passageiros da viagem.",
            )
            else -> CentralIntegrityCheck0552(
                key = "capacity",
                level = CentralIntegrityLevel0552.OK,
                title = "Ocupação e vagas coerentes por segmento",
                actual = "ocupadas=${summary.confirmedPassengerSeats} disponíveis=${summary.availableSeats}",
                source = "operationalSeatSummary",
            )
        }

        if (external != null && external.passenger_roster_complete && trip.externalSnapshotComplete) {
            val externalSeats = external.passengers.sumOf { it.seats.coerceAtLeast(1) }
            val canonicalExternal = bookings.filter { it.source == BookingSource.BLABLACAR && it.status !in setOf(BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.EXPIRED) }
            val canonicalSeats = canonicalExternal.sumOf { it.seats.coerceAtLeast(1) }
            val externalNames = external.passengers.map { normalizeName(it.name) }.filter(String::isNotBlank).toSet()
            val canonicalNames = canonicalExternal.map { normalizeName(it.passengerName) }.filter(String::isNotBlank).toSet()
            val missing = externalNames - canonicalNames
            checks += if (externalSeats == canonicalSeats && missing.isEmpty()) {
                CentralIntegrityCheck0552(
                    key = "passengers",
                    level = CentralIntegrityLevel0552.OK,
                    title = "Passageiros coerentes com a última captura completa",
                    source = "BlaBlaCar → Agenda canônica",
                )
            } else {
                CentralIntegrityCheck0552(
                    key = "passengers",
                    level = CentralIntegrityLevel0552.DIVERGENCE,
                    title = "Passageiros divergentes",
                    expected = "BlaBlaCar=$externalSeats lugar(es)",
                    actual = "Agenda=$canonicalSeats lugar(es)",
                    source = "BlaBlaCar → Agenda canônica",
                    detail = if (missing.isEmpty()) "Contagem divergente." else "Faltando na Agenda: ${missing.joinToString(", ")}",
                )
            }
        } else {
            checks += CentralIntegrityCheck0552(
                key = "passengers",
                level = CentralIntegrityLevel0552.UNKNOWN,
                title = "Roster externo completo ainda não verificável",
                source = "último snapshot canônico",
            )
        }

        checks += when (trip.publicMirrorAttestationState0411) {
            PublicMirrorAttestationState0411.VALIDATED -> CentralIntegrityCheck0552(
                key = "readback",
                level = CentralIntegrityLevel0552.OK,
                title = "Readback público validado",
                source = "public-evidence-v2",
            )
            PublicMirrorAttestationState0411.DIVERGENT -> CentralIntegrityCheck0552(
                key = "readback",
                level = CentralIntegrityLevel0552.DIVERGENCE,
                title = "Agenda pública ainda diverge do estado canônico",
                source = "public-evidence-v2",
                detail = trip.publicMirrorMismatchFields0411
                    .joinToString(", ")
                    .ifBlank { "A última leitura pública não confirmou exatamente a revisão canônica atual." },
            )
            PublicMirrorAttestationState0411.PENDING -> CentralIntegrityCheck0552(
                key = "readback",
                level = CentralIntegrityLevel0552.UNKNOWN,
                title = "Readback pendente",
                source = "public-evidence-v2",
            )
            PublicMirrorAttestationState0411.UNPROVEN -> CentralIntegrityCheck0552(
                key = "readback",
                level = CentralIntegrityLevel0552.UNKNOWN,
                title = "Readback ainda não comprovado",
                source = "public-evidence-v2",
            )
        }

        val passengers = bookings
            .filter { it.capacityClaimType != CapacityClaimType.RESERVED_SEAT || it.passengerName.isNotBlank() }
            .sortedBy(Booking::createdAtMillis)
            .map { booking ->
                CentralPassenger0552(
                    bookingId = booking.id,
                    name = booking.passengerName.ifBlank { "Passageiro" },
                    seats = booking.seats.coerceAtLeast(1),
                    stateLabel = passengerStateLabel(booking),
                    source = booking.source,
                )
            }
        val expectedEnd = stops.lastOrNull()?.plannedArrivalMillis ?: stops.lastOrNull()?.plannedDepartureMillis
        val provisional = CentralTrip0552(
            canonicalTripId = trip.id,
            departureAtMillis = trip.departureAtMillis,
            expectedEndAtMillis = expectedEnd,
            profileUuid = profileUuid,
            profileLabel = profileLabel,
            origin = stops.firstOrNull()?.name.orEmpty(),
            destination = stops.lastOrNull()?.name.orEmpty(),
            passengerSeats = summary.confirmedPassengerSeats,
            availableSeats = summary.availableSeats.takeIf { trip.capacityReliable && summary.operationalLimitConfigured },
            operationalCapacity = trip.capacity.takeIf { trip.capacityReliable && summary.operationalLimitConfigured && it > 0 },
            segmentLoads = segmentLoads.takeIf { trip.capacityReliable && summary.operationalLimitConfigured }.orEmpty(),
            passengers = passengers,
            checks = checks,
            integrity = aggregate(checks),
            nextAction = nextActionForTrip(trip, bookings, checks, nowMillis),
            canonicalRevision = trip.canonicalRevision,
            canonicalStateHash = trip.canonicalStateHash,
            expectedHash = trip.publicMirrorExpectedHash0411,
            actualHash = trip.publicMirrorReadbackHash0411,
            expectedBytes = trip.publicMirrorExpectedBytes0421,
            actualBytes = trip.publicMirrorActualBytes0421,
            firstDifferentByteOffset = trip.publicMirrorFirstDifferentByteOffset0421,
            differentByteRanges = trip.publicMirrorDifferentByteRanges0421,
            readbackAtMillis = trip.publicMirrorLastReadbackAtMillis0421,
        )
        return provisional
    }

    private class MutableCentralTrip0552(private val source: CentralTrip0552) {
        private val addedChecks = mutableListOf<CentralIntegrityCheck0552>()
        val profileUuid: String get() = source.profileUuid
        val departureAtMillis: Long get() = source.departureAtMillis
        val expectedEndAtMillis: Long? get() = source.expectedEndAtMillis
        val origin: String get() = source.origin
        val destination: String get() = source.destination
        fun add(check: CentralIntegrityCheck0552) { addedChecks += check }
        fun freeze(): CentralTrip0552 {
            val checks = source.checks + addedChecks
            return source.copy(checks = checks, integrity = aggregate(checks))
        }
    }

    private fun addContinuityChecks(items: List<MutableCentralTrip0552>) {
        items.filter { it.profileUuid.isNotBlank() }
            .groupBy { it.profileUuid }
            .values
            .forEach { group ->
                val ordered = group.sortedBy { it.departureAtMillis }
                ordered.zipWithNext().forEach { (previous, next) ->
                    val previousEnd = previous.expectedEndAtMillis
                    when {
                        previousEnd != null && next.departureAtMillis < previousEnd -> next.add(
                            CentralIntegrityCheck0552(
                                key = "physical_continuity",
                                level = CentralIntegrityLevel0552.ACTION_REQUIRED,
                                title = "Viagens sobrepostas para o mesmo perfil",
                                expected = "próxima saída após ${previousEnd}",
                                actual = "saída=${next.departureAtMillis}",
                                source = "sequência cronológica canônica",
                                detail = "A sequência é temporalmente impossível sem alterar uma das viagens.",
                            ),
                        )
                        normalizePlace(previous.destination).isNotBlank() &&
                            normalizePlace(next.origin).isNotBlank() &&
                            normalizePlace(previous.destination) != normalizePlace(next.origin) -> next.add(
                                CentralIntegrityCheck0552(
                                    key = "physical_continuity",
                                    level = CentralIntegrityLevel0552.DIVERGENCE,
                                    title = "Deslocamento entre viagens precisa ser comprovado",
                                    expected = previous.destination,
                                    actual = next.origin,
                                    source = "sequência cronológica canônica",
                                    detail = "Sem tempo de rota autoritativo disponível, a Central sinaliza risco e não inventa velocidade/tempo de transferência.",
                                ),
                            )
                        else -> next.add(
                            CentralIntegrityCheck0552(
                                key = "physical_continuity",
                                level = CentralIntegrityLevel0552.OK,
                                title = "Continuidade física compatível com a evidência disponível",
                                source = "sequência cronológica canônica",
                            ),
                        )
                    }
                }
            }
    }

    private fun aggregate(checks: List<CentralIntegrityCheck0552>): CentralIntegrityLevel0552 = when {
        checks.any { it.level == CentralIntegrityLevel0552.ACTION_REQUIRED } -> CentralIntegrityLevel0552.ACTION_REQUIRED
        checks.any { it.level == CentralIntegrityLevel0552.DIVERGENCE } -> CentralIntegrityLevel0552.DIVERGENCE
        checks.any { it.level == CentralIntegrityLevel0552.OK } -> CentralIntegrityLevel0552.OK
        else -> CentralIntegrityLevel0552.UNKNOWN
    }

    private fun nextActionForTrip(
        trip: Trip,
        bookings: List<Booking>,
        checks: List<CentralIntegrityCheck0552>,
        nowMillis: Long,
    ): String = when {
        checks.any { it.level == CentralIntegrityLevel0552.ACTION_REQUIRED } -> "Corrigir divergência desta viagem"
        bookings.any { it.operationalStatus == PassengerOperationalStatus.AT_LOCATION } -> "Passageiro no local — preparar embarque"
        bookings.any { it.operationalStatus == PassengerOperationalStatus.PENDING || it.status == BookingStatus.REQUESTED } -> "Revisar passageiro aguardando"
        bookings.any { it.operationalStatus == PassengerOperationalStatus.IN_CAR && it.paymentStatus == PassengerPaymentStatus.UNPAID } -> "Pagamento pendente"
        trip.departureAtMillis > nowMillis -> "Saída em ${((trip.departureAtMillis - nowMillis) / 60_000L).coerceAtLeast(0L)} min"
        trip.status in setOf(TripStatus.ACTIVE, TripStatus.STARTING) -> "Viagem em andamento"
        else -> "Sem ação imediata"
    }

    private fun chooseGlobalNextAction(trips: List<CentralTrip0552>, nowMillis: Long): String {
        trips.firstOrNull { it.integrity == CentralIntegrityLevel0552.ACTION_REQUIRED }?.let {
            return "Ação necessária: ${it.origin} → ${it.destination}"
        }
        trips.firstOrNull { item -> item.passengers.any { it.stateLabel.contains("No local") } }?.let {
            return "Passageiro no local: ${it.origin} → ${it.destination}"
        }
        trips.firstOrNull { it.departureAtMillis >= nowMillis }?.let {
            val minutes = ((it.departureAtMillis - nowMillis) / 60_000L).coerceAtLeast(0L)
            return "Próxima viagem em $minutes min • ${it.origin} → ${it.destination}"
        }
        return if (trips.isEmpty()) "Nenhuma viagem canônica para hoje" else "Operação do dia sem próxima saída futura"
    }

    private fun passengerStateLabel(booking: Booking): String = when {
        booking.status == BookingStatus.REJECTED -> "Recusado"
        booking.status == BookingStatus.CANCELLED || booking.operationalStatus == PassengerOperationalStatus.CANCELLED -> "Cancelado"
        booking.paymentStatus == PassengerPaymentStatus.PAID -> "Pago"
        booking.operationalStatus == PassengerOperationalStatus.AT_LOCATION -> "No local"
        booking.operationalStatus == PassengerOperationalStatus.IN_CAR -> "No carro"
        booking.operationalStatus == PassengerOperationalStatus.COMPLETED -> "Concluído"
        booking.operationalStatus == PassengerOperationalStatus.PENDING || booking.status == BookingStatus.REQUESTED -> "Aguardando aprovação"
        else -> "Confirmado"
    }

    private fun normalizeName(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun normalizePlace(value: String): String = normalizeName(value.substringBefore(',').trim())
}

internal object CentralDayCommandBridge0552 {
    fun refreshTrip(context: Context, trip: Trip): Boolean {
        val target = target(context, trip) ?: return false
        val current = BlaBlaTripCommandStatusStore0407(context).get(target)
        if (current?.pending == true) return false
        val command = BlaBlaCommand0407.forTarget(
            target = target,
            operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
            origin = BlaBlaCommandOrigin0407.CARD,
        )
        val queued = AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(
            context = context,
            target = target,
            commandId = command.commandId,
            requestedAtMillis = command.requestedAtMillis,
        )
        UnifiedDebugEventStore.record(
            "CENTRAL_DAY_TRIP_REPAIR_0552",
            context.packageName,
            "tripKey=${seatSyncDiagnosticKey(trip.tripKey.ifBlank { trip.id })} strongIdentity=true queued=$queued path=BlaBlaCar_to_Agenda_to_Timeline directCentralWrite=false",
        )
        return queued
    }

    fun refreshAll(context: Context, trips: List<Trip>): Int = trips.count { refreshTrip(context, it) }

    internal fun target(context: Context, trip: Trip): BlaBlaTripTarget0407? {
        val tenantId = RotaCertaTenantRegistry(context.applicationContext).activeScope().tenantId.trim()
        val profileUuid = trip.blablaProfileUuid?.trim()?.lowercase().orEmpty()
        val tripId = trip.blablaTripId?.trim().orEmpty()
        if (tenantId.isBlank() || profileUuid.isBlank() || tripId.isBlank()) return null
        val accounts = BlaBlaDynamicAccountRegistry(context.applicationContext).list().filter {
            it.profileUuid?.trim()?.lowercase() == profileUuid
        }
        if (accounts.size != 1) return null
        val persisted = trip.blablaManageUrl?.trim().orEmpty()
        val manageHref = if (persisted.isNotBlank() && BlaBlaCollectorUrlModule.tripId(persisted) == tripId) {
            BlaBlaCollectorUrlModule.canonical(persisted)
        } else {
            BlaBlaCollectorUrlModule.canonical("${BlaBlaCollectorUrlModule.ORIGIN}/rides/offer/$tripId")
        }
        if (BlaBlaCollectorUrlModule.tripId(manageHref) != tripId) return null
        return BlaBlaTripTarget0407(
            tenantId = tenantId,
            accountId = accounts.single().id,
            profileUuid = profileUuid,
            tripId = tripId,
            tripHref = manageHref,
        )
    }
}

@Composable
internal fun CentralDoDiaScreen0552(
    trips: List<Trip>,
    bookings: List<Booking>,
    localProfileLabel: String,
    onRefreshLocal: () -> Unit,
    onOpenTimeline: (tripId: String, bookingId: String?) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val store0593 = remember(context) { TripStore(context.applicationContext) }
    val commandRevision by BlaBlaTripControlEvents0407.revision.collectAsState()
    val accounts = remember(commandRevision, trips) { BlaBlaDynamicAccountRegistry(context).list() }
    val today = LocalDate.now()
    val model = remember(trips, bookings, accounts, commandRevision, today, localProfileLabel) {
        CentralDayReadModelBuilder0552.build(
            trips = trips,
            bookings = bookings,
            accounts = accounts,
            date = today,
            localProfileLabel = localProfileLabel,
        )
    }
    val operationalProjection0593 = remember(trips, bookings, commandRevision, localProfileLabel) {
        localAgendaTimelineProjection0515(
            trips = trips,
            bookings = bookings,
            localProfileLabel = localProfileLabel,
        )
    }
    val entryByTripId0593 = remember(operationalProjection0593.entries) {
        operationalProjection0593.entries.associateBy(TripTimelineEntry::tripId)
    }
    var diagnosticTripId by remember { mutableStateOf<String?>(null) }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault()) }

    LaunchedEffect(commandRevision) {
        if (commandRevision > 0L) onRefreshLocal()
    }

    LaunchedEffect(model.date, model.trips.size, bookings.size) {
        UnifiedDebugEventStore.recordAlways(
            "CENTRAL_DAY_RENDER_READY_0594",
            context.packageName,
            "trips=${model.trips.size} bookings=${bookings.size} passengers=${model.summary.passengerSeats}",
            diagnosticContext = DiagnosticEventContext0507(
                parentModule = DiagnosticModule0507.CENTRAL_DAY,
                originModule = DiagnosticModule0507.CENTRAL_DAY,
                executorModule = DiagnosticModule0507.CENTRAL_DAY,
                submodule = "PASSENGER_CONTROLS",
                component = "CentralDoDiaScreen0552",
                operation = "CENTRAL_DAY_RENDER",
                result = "READY",
            ),
        )
    }

    var expandedPassengerTripIds0591 by remember { mutableStateOf(emptySet<String>()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                (model.summary.integrityPercent?.let { "${it}% íntegra" } ?: "Integridade pendente") +
                    " • ${model.summary.trips} viagens • ${model.summary.passengerSeats} lugares • ${model.summary.availableSeats} vagas",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "⚠ ${model.summary.divergenceCount} • 🔴 ${model.summary.actionRequiredCount} • ? ${model.summary.unknownCount}  |  ${model.nextAction}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val todayIds = model.trips.map(CentralTrip0552::canonicalTripId).toSet()
                    val queued = CentralDayCommandBridge0552.refreshAll(context, trips.filter { it.id in todayIds })
                    onMessage(
                        if (queued > 0) {
                            "📡 ${queued} viagem(ns) enviada(s) ao sincronizador canônico existente."
                        } else {
                            "Nenhuma viagem elegível foi enfileirada; verifique identidade/sessão."
                        },
                    )
                },
            ) { Text("↻ Atualizar operação de hoje") }
        }
    }

    if (model.trips.isEmpty()) {
        Text("Nenhuma viagem canônica encontrada para hoje.", style = MaterialTheme.typography.bodyMedium)
    }

    model.trips.forEach { item ->
        val passengersExpanded0591 = item.canonicalTripId in expandedPassengerTripIds0591
        val canonicalTrip0593 = trips.firstOrNull { trip -> trip.id == item.canonicalTripId }
        val timelineEntry0593 = entryByTripId0593[item.canonicalTripId]
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val icon = when (item.integrity) {
                    CentralIntegrityLevel0552.OK -> "🟢"
                    CentralIntegrityLevel0552.DIVERGENCE -> "🟡"
                    CentralIntegrityLevel0552.ACTION_REQUIRED -> "🔴"
                    CentralIntegrityLevel0552.UNKNOWN -> "⚪"
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${icon} ${formatter.format(Instant.ofEpochMilli(item.departureAtMillis))} • ${item.profileLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    TextButton(
                        onClick = {
                            val trip = trips.firstOrNull { it.id == item.canonicalTripId }
                            val queued = trip?.let { CentralDayCommandBridge0552.refreshTrip(context, it) } == true
                            onMessage(
                                if (queued) {
                                    "🔄 Atualizando esta viagem pela cadeia BlaBlaCar → Agenda → Timeline…"
                                } else {
                                    "Atualização não iniciada: identidade forte indisponível ou atualização já em andamento."
                                },
                            )
                        },
                    ) {
                        Text(
                            if (item.integrity in setOf(
                                    CentralIntegrityLevel0552.DIVERGENCE,
                                    CentralIntegrityLevel0552.ACTION_REQUIRED,
                                )
                            ) {
                                "↻ Corrigir"
                            } else {
                                "↻ Atualizar"
                            },
                        )
                    }
                }

                Text("${item.origin} → ${item.destination}", style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    "${item.passengerSeats} lugares • ${item.availableSeats?.let { "${it} vagas" } ?: "vagas não verificáveis"} • ${item.nextAction}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )

                Text(
                    "Vagas por trecho",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (item.segmentLoads.isEmpty()) {
                    Text(
                        "Disponibilidade por trecho aguardando estado canônico.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    item.segmentLoads.forEach { load0595 ->
                        val capacity0595 = item.operationalCapacity
                        val availability0595 = when (load0595.availableSeats.coerceAtLeast(0)) {
                            0 -> "LOTADO"
                            1 -> "1 vaga"
                            else -> "${load0595.availableSeats.coerceAtLeast(0)} vagas"
                        }
                        val occupancy0595 = capacity0595?.let { cap ->
                            val passengers = load0595.passengerSeats.coerceAtLeast(0)
                            "$passengers/$cap"
                        } ?: load0595.passengerSeats.coerceAtLeast(0).toString()
                        val dots0595 = capacity0595?.takeIf { it in 1..12 }?.let { cap ->
                            val occupiedDots = load0595.occupiedSeats.coerceIn(0, cap)
                            "●".repeat(occupiedDots) + "○".repeat((cap - occupiedDots).coerceAtLeast(0))
                        }.orEmpty()
                        val blocked0595 = load0595.blockedSeats.coerceAtLeast(0)
                        val overbooking0595 = load0595.overbookingSeats.coerceAtLeast(0)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "${load0595.from.name} → ${load0595.to.name}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                            if (dots0595.isNotBlank()) {
                                Text(dots0595, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            Text("👥 $occupancy0595", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            if (blocked0595 > 0) {
                                Text("🚫$blocked0595", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            Text(
                                if (overbooking0595 > 0) "$availability0595 +$overbooking0595" else availability0595,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val opening0594 = !passengersExpanded0591
                            val controlsAvailable0594 = canonicalTrip0593 != null && timelineEntry0593 != null
                            UnifiedDebugEventStore.recordAlways(
                                "CENTRAL_DAY_PASSENGER_PANEL_TOGGLE_0594",
                                context.packageName,
                                "opening=$opening0594 controlsAvailable=$controlsAvailable0594 passengers=${item.passengers.size}",
                                diagnosticContext = DiagnosticEventContext0507(
                                    parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                    originModule = DiagnosticModule0507.CENTRAL_DAY,
                                    executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                    submodule = "PASSENGER_CONTROLS",
                                    component = "CentralDoDiaScreen0552",
                                    operation = "CENTRAL_DAY_PASSENGER_PANEL",
                                    entityType = "trip",
                                    entityId = seatSyncDiagnosticKey(item.canonicalTripId),
                                    result = if (controlsAvailable0594) "OPERATIONAL_CONTROLS" else "READ_ONLY_FALLBACK",
                                ),
                            )
                            expandedPassengerTripIds0591 =
                                if (passengersExpanded0591) {
                                    expandedPassengerTripIds0591 - item.canonicalTripId
                                } else {
                                    expandedPassengerTripIds0591 + item.canonicalTripId
                                }
                        },
                    ) {
                        Text(
                            if (passengersExpanded0591) {
                                "Passageiros ${item.passengers.size} ▲"
                            } else {
                                "Passageiros ${item.passengers.size} ▼"
                            },
                            maxLines = 1,
                        )
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenTimeline(item.canonicalTripId, null) },
                    ) { Text("Atalhos", maxLines = 1) }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { diagnosticTripId = item.canonicalTripId },
                    ) { Text("Integridade", maxLines = 1) }
                }

                if (passengersExpanded0591) {
                    if (canonicalTrip0593 != null && timelineEntry0593 != null) {
                        EnhancedPassengerTimelineSection(
                            entry = timelineEntry0593,
                            trip = canonicalTrip0593,
                            store = store0593,
                            currentCoordinate = null,
                            onChanged = { message ->
                                onMessage(message)
                                onRefreshLocal()
                            },
                            canonicalBookings0494 = bookings.filter { booking ->
                                booking.tripId == item.canonicalTripId
                            },
                            showTripActions0549 = false,
                            compactEmbeddedControls0593 = true,
                        )
                    } else {
                        item.passengers.forEach { passenger ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    passenger.name + " • " + passenger.stateLabel,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                TextButton(
                                    onClick = { onOpenTimeline(item.canonicalTripId, passenger.bookingId) },
                                ) { Text("Operar") }
                            }
                        }
                    }
                }
            }
        }
    }

    diagnosticTripId?.let { id ->
        model.trips.firstOrNull { it.canonicalTripId == id }?.let { item ->
            AlertDialog(
                onDismissRequest = { diagnosticTripId = null },
                title = { Text("Diagnóstico de integridade") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.checks.forEach { check ->
                            val icon = when (check.level) {
                                CentralIntegrityLevel0552.OK -> "✅"
                                CentralIntegrityLevel0552.DIVERGENCE -> "⚠️"
                                CentralIntegrityLevel0552.ACTION_REQUIRED -> "🔴"
                                CentralIntegrityLevel0552.UNKNOWN -> "❔"
                            }
                            Text("$icon ${check.title}")
                            if (check.expected.isNotBlank()) Text("Esperado: ${check.expected}", style = MaterialTheme.typography.bodySmall)
                            if (check.actual.isNotBlank()) Text("Encontrado: ${check.actual}", style = MaterialTheme.typography.bodySmall)
                            if (check.source.isNotBlank()) Text("Fonte: ${check.source}", style = MaterialTheme.typography.bodySmall)
                            if (check.detail.isNotBlank()) Text(check.detail, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Revisão canônica: ${item.canonicalRevision}", style = MaterialTheme.typography.bodySmall)
                        if (item.canonicalStateHash.isNotBlank()) Text("Hash canônico: ${item.canonicalStateHash}", style = MaterialTheme.typography.bodySmall)
                        if (item.expectedHash.isNotBlank() || item.actualHash.isNotBlank()) {
                            Text(
                                "Evidência técnica de readback disponível no relatório de depuração.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { diagnosticTripId = null }) { Text("Fechar") } },
            )
        }
    }
}
