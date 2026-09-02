package br.com.mapeiaia.rotacerta.trips

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
internal enum class RotaCertaAction0410 {
    CREATE_TRIPS, LIST_TRIPS, READ_TRIP, REVERIFY_TRIP, CHECK_SYNC, LIST_UNRESOLVED_TRIPS, LIST_FULL_TRIPS,
    OPEN_TRIP, SHARE_TRIP, GET_TRIP_PRICE, SET_TRIP_DATE, SET_TRIP_TIME, SET_TRIP_ORIGIN, SET_TRIP_DESTINATION,
    SET_TRIP_ROUTE, SET_TRIP_STOPOVERS, SET_MEETING_POINT, SET_TRIP_SEATS, SET_TRIP_PRICE, SET_TRIP_BOOST,
    SET_SMART_STOPOVERS, SET_INSTANT_BOOKING, SET_TWO_MAX_IN_BACK, SET_WOMEN_ONLY, SET_TRIP_VEHICLE,
    SET_TRIP_COMMENT, DUPLICATE_TRIP, CREATE_RETURN_TRIP, CANCEL_TRIP, READ_BOOKINGS, ACCEPT_BOOKING,
    DECLINE_BOOKING, CANCEL_BOOKING, READ_PASSENGERS, READ_PASSENGER, CONTACT_PASSENGER, READ_MESSAGES,
    SEND_MESSAGE, READ_PROFILE, READ_VEHICLE, PUBLIC_SEARCH,
}

internal enum class RotaCertaCoverage0410 { DISCOVERED, MAPPED, IMPLEMENTED, VERIFIED, BLOCKED, NOT_AUTOMATABLE, NOT_APPLICABLE }
internal enum class RotaCertaRisk0410 { READ_ONLY, LOW_RISK_MUTATION, HIGH_IMPACT, DESTRUCTIVE }
internal enum class RotaCertaExecutionPolicy0410 { CONFIRM_BEFORE_EXECUTION, AUTO_EXECUTE_VALIDATED }

internal data class RotaCertaCommandCapability0410(
    val action: RotaCertaAction0410,
    val aliases: Set<String>,
    val coverage: RotaCertaCoverage0410,
    val executor: String,
    val verifier: String,
    val risk: RotaCertaRisk0410,
    val supportsNetwork: Boolean,
    val supportsDom: Boolean,
    val supportsUiFallback: Boolean,
    val requiresAuthentication: Boolean,
    val requiresTripId: Boolean = false,
    val requiresPassengerId: Boolean = false,
    val requiresBookingId: Boolean = false,
    val requiresVerification: Boolean = false,
    val supportsAutoExecute: Boolean = false,
    val idempotent: Boolean = false,
    val blockedReason: String = "",
) {
    val mutable: Boolean get() = risk != RotaCertaRisk0410.READ_ONLY
    val interpreterEligible: Boolean
        get() = coverage in setOf(RotaCertaCoverage0410.IMPLEMENTED, RotaCertaCoverage0410.VERIFIED)
}

internal object RotaCertaCommandRegistry0410 {
    private fun read(
        action: RotaCertaAction0410,
        aliases: Set<String>,
        executor: String,
        verifier: String = "canonical-readback",
        coverage: RotaCertaCoverage0410 = RotaCertaCoverage0410.VERIFIED,
        tripId: Boolean = false,
    ) = RotaCertaCommandCapability0410(
        action = action, aliases = aliases, coverage = coverage, executor = executor, verifier = verifier,
        risk = RotaCertaRisk0410.READ_ONLY, supportsNetwork = true, supportsDom = false, supportsUiFallback = false,
        requiresAuthentication = true, requiresTripId = tripId, supportsAutoExecute = true, idempotent = true,
    )

    private fun blocked(
        action: RotaCertaAction0410,
        aliases: Set<String>,
        executor: String,
        network: Boolean = false,
        dom: Boolean = false,
        ui: Boolean = true,
        tripId: Boolean = true,
        passengerId: Boolean = false,
        bookingId: Boolean = false,
        risk: RotaCertaRisk0410 = RotaCertaRisk0410.HIGH_IMPACT,
        reason: String = "runtime_write_and_readback_not_verified_for_apk_6_26_0",
    ) = RotaCertaCommandCapability0410(
        action = action, aliases = aliases, coverage = RotaCertaCoverage0410.BLOCKED, executor = executor,
        verifier = "required-before-enable", risk = risk, supportsNetwork = network, supportsDom = dom,
        supportsUiFallback = ui, requiresAuthentication = true, requiresTripId = tripId,
        requiresPassengerId = passengerId, requiresBookingId = bookingId, requiresVerification = true,
        supportsAutoExecute = false, idempotent = false, blockedReason = reason,
    )

    val entries: Map<RotaCertaAction0410, RotaCertaCommandCapability0410> = listOf(
        RotaCertaCommandCapability0410(
            action = RotaCertaAction0410.CREATE_TRIPS,
            aliases = setOf("criar viagens", "publicar caronas", "criar ida e volta"),
            coverage = RotaCertaCoverage0410.IMPLEMENTED,
            executor = "AgendaBatchPublisherPlanner -> AgendaBatchPublisherStore -> AgendaBatchPublisherActivity",
            verifier = "Sincronizador Central -> TripStore strong identity readback",
            risk = RotaCertaRisk0410.HIGH_IMPACT,
            supportsNetwork = false, supportsDom = true, supportsUiFallback = true, requiresAuthentication = true,
            requiresVerification = true, supportsAutoExecute = false, idempotent = true,
        ),
        read(RotaCertaAction0410.LIST_TRIPS, setOf("listar viagens", "minhas viagens"), "TripStore"),
        read(RotaCertaAction0410.READ_TRIP, setOf("ver viagem", "detalhes da viagem"), "TripStore", tripId = true),
        read(
            RotaCertaAction0410.REVERIFY_TRIP, setOf("verificar viagem", "sincronizar card"),
            "AgendaBackgroundSync0392.enqueueTripReverify0407", "BlaBlaTripCommandStatusStore0407", tripId = true,
        ).copy(risk = RotaCertaRisk0410.LOW_RISK_MUTATION, requiresVerification = true, idempotent = true),
        read(RotaCertaAction0410.CHECK_SYNC, setOf("ver sincronização", "checar sincronização"), "TripStore + projection integrity"),
        read(RotaCertaAction0410.LIST_UNRESOLVED_TRIPS, setOf("viagens sem tripId", "viagens não resolvidas"), "TripStore"),
        read(RotaCertaAction0410.LIST_FULL_TRIPS, setOf("viagens lotadas", "quais estão lotadas"), "TripStore + SeatAvailabilityEngine"),
        read(RotaCertaAction0410.OPEN_TRIP, setOf("abrir viagem", "abrir na blablacar"), "ACTION_VIEW canonical BlaBlaCar URL", tripId = true),
        read(RotaCertaAction0410.SHARE_TRIP, setOf("compartilhar viagem"), "canonical public URL", tripId = true),
        read(RotaCertaAction0410.GET_TRIP_PRICE, setOf("preço da viagem", "valor da viagem"), "TripStore external snapshot", tripId = true),
        RotaCertaCommandCapability0410(
            action = RotaCertaAction0410.SET_TRIP_SEATS,
            aliases = setOf("alterar vagas", "colocar vagas", "mudar assentos"),
            coverage = RotaCertaCoverage0410.VERIFIED,
            executor = "BlaBlaSeatBrowserController via BlaBlaBrowserOrchestrator",
            verifier = "SEAT_OPTIONS post-write readback + BlaBlaPublicationSeatSyncStateStore",
            risk = RotaCertaRisk0410.HIGH_IMPACT,
            supportsNetwork = false, supportsDom = true, supportsUiFallback = true, requiresAuthentication = true,
            requiresTripId = true, requiresVerification = true, supportsAutoExecute = false, idempotent = true,
            blockedReason = "assistant_adapter_enabled_only_for_exact_target_with_WRITE_VERIFIED",
        ),
        read(RotaCertaAction0410.READ_BOOKINGS, setOf("reservas", "pedidos de reserva"), "TripStore/TripRemoteApi", coverage = RotaCertaCoverage0410.IMPLEMENTED, tripId = true),
        read(RotaCertaAction0410.READ_PASSENGERS, setOf("passageiros", "quem vai"), "TripStore canonical bookings", coverage = RotaCertaCoverage0410.IMPLEMENTED, tripId = true),
        read(RotaCertaAction0410.READ_PASSENGER, setOf("ver passageiro"), "TripStore canonical bookings", coverage = RotaCertaCoverage0410.IMPLEMENTED, tripId = true),
        read(RotaCertaAction0410.READ_PROFILE, setOf("ver perfil"), "BlaBla collector profile snapshot", coverage = RotaCertaCoverage0410.IMPLEMENTED),
        read(RotaCertaAction0410.READ_VEHICLE, setOf("ver veículo"), "BlaBla collector profile snapshot", coverage = RotaCertaCoverage0410.IMPLEMENTED),
        read(
            RotaCertaAction0410.PUBLIC_SEARCH,
            setOf("busca pública", "buscar publicamente", "procurar motorista", "buscar no blablacar"),
            "BlaBlaPublicSearchActivity",
            "BlaBlaPublicSearchStore validated/partial coverage",
            coverage = RotaCertaCoverage0410.VERIFIED,
        ),
        blocked(RotaCertaAction0410.SET_TRIP_DATE, setOf("alterar data"), "APK publication edit flow"),
        blocked(RotaCertaAction0410.SET_TRIP_TIME, setOf("alterar horário"), "APK /rides/offer/edit/itinerary/time"),
        blocked(RotaCertaAction0410.SET_TRIP_ORIGIN, setOf("alterar origem"), "APK trip edition departure autocomplete"),
        blocked(RotaCertaAction0410.SET_TRIP_DESTINATION, setOf("alterar destino"), "APK trip edition arrival autocomplete"),
        blocked(RotaCertaAction0410.SET_TRIP_ROUTE, setOf("alterar rota"), "APK publication edit itinerary"),
        blocked(RotaCertaAction0410.SET_TRIP_STOPOVERS, setOf("alterar paradas"), "APK publication edit stopovers"),
        blocked(RotaCertaAction0410.SET_MEETING_POINT, setOf("alterar ponto de encontro"), "APK PublicationEditMeetingPoints"),
        blocked(RotaCertaAction0410.SET_TRIP_PRICE, setOf("alterar preço"), "APK /rides/offer/edit/prices"),
        blocked(RotaCertaAction0410.SET_TRIP_BOOST, setOf("ativar boost", "desativar boost"), "APK ride booking request Boost"),
        blocked(RotaCertaAction0410.SET_SMART_STOPOVERS, setOf("smart stopovers"), "APK /rides/offer/edit/smartstopovers"),
        blocked(RotaCertaAction0410.SET_INSTANT_BOOKING, setOf("reserva instantânea"), "APK publication instant booking"),
        blocked(RotaCertaAction0410.SET_TWO_MAX_IN_BACK, setOf("dois atrás"), "APK publication comfort preference"),
        blocked(RotaCertaAction0410.SET_WOMEN_ONLY, setOf("só mulheres"), "APK publication preference"),
        blocked(RotaCertaAction0410.SET_TRIP_VEHICLE, setOf("trocar veículo"), "APK publication vehicle"),
        blocked(RotaCertaAction0410.SET_TRIP_COMMENT, setOf("alterar comentário"), "APK publication comment"),
        blocked(RotaCertaAction0410.DUPLICATE_TRIP, setOf("duplicar viagem"), "APK ride plan duplicate"),
        blocked(RotaCertaAction0410.CREATE_RETURN_TRIP, setOf("criar volta"), "APK smart publication return trip"),
        blocked(RotaCertaAction0410.CANCEL_TRIP, setOf("cancelar viagem"), "APK ride cancellation flow", risk = RotaCertaRisk0410.DESTRUCTIVE),
        blocked(RotaCertaAction0410.ACCEPT_BOOKING, setOf("aceitar passageiro", "aceitar reserva"), "APK driver booking request", passengerId = true, bookingId = true),
        blocked(RotaCertaAction0410.DECLINE_BOOKING, setOf("recusar passageiro", "recusar reserva"), "APK d2d driver ride request refuse", passengerId = true, bookingId = true, risk = RotaCertaRisk0410.DESTRUCTIVE),
        blocked(RotaCertaAction0410.CANCEL_BOOKING, setOf("cancelar reserva"), "APK /booking/cancel/v4", passengerId = true, bookingId = true, risk = RotaCertaRisk0410.DESTRUCTIVE),
        blocked(RotaCertaAction0410.CONTACT_PASSENGER, setOf("contatar passageiro"), "APK activity_contact_passenger", passengerId = true, risk = RotaCertaRisk0410.LOW_RISK_MUTATION),
        blocked(RotaCertaAction0410.READ_MESSAGES, setOf("ler mensagens"), "BlaBlaBrowserScriptRegistry message_thread", network = true, dom = true, passengerId = true, risk = RotaCertaRisk0410.READ_ONLY),
        blocked(RotaCertaAction0410.SEND_MESSAGE, setOf("enviar mensagem"), "APK messaging conversation input", network = true, dom = true, passengerId = true),
    ).associateBy { it.action }

    init { check(entries.size == RotaCertaAction0410.values().size) { "Every action must have explicit coverage metadata." } }

    fun capability(action: RotaCertaAction0410): RotaCertaCommandCapability0410 = entries.getValue(action)

    fun interpreterActions(hasPublisherAccount: Boolean, hasVerifiedSeatTarget: Boolean): Set<RotaCertaAction0410> =
        entries.values.filter { capability ->
            capability.interpreterEligible &&
                (capability.action != RotaCertaAction0410.CREATE_TRIPS || hasPublisherAccount) &&
                (capability.action != RotaCertaAction0410.SET_TRIP_SEATS || hasVerifiedSeatTarget)
        }.mapTo(linkedSetOf()) { it.action }

    fun coverageCounts(): Map<RotaCertaCoverage0410, Int> = entries.values.groupingBy { it.coverage }.eachCount()
}

@Serializable
internal data class RotaCertaTemporalReference0410(
    val raw: String = "",
    val explicitDate: String? = null,
    val relative: String? = null,
    val weekday: String? = null,
    val dayOfMonth: Int? = null,
    val month: Int? = null,
    val year: Int? = null,
    val time: String? = null,
)

@Serializable
internal data class RotaCertaStructuredCommand0410(
    val schemaVersion: String = "1.0",
    val commandId: String = UUID.randomUUID().toString(),
    val action: RotaCertaAction0410,
    val tripReference: String = "",
    val passengerReference: String = "",
    val bookingReference: String = "",
    val temporal: RotaCertaTemporalReference0410 = RotaCertaTemporalReference0410(),
    val dateTokens: List<String> = emptyList(),
    val roundTrip: Boolean = false,
    val origin: String = "",
    val destination: String = "",
    val publicTargetNames: List<String> = emptyList(),
    val seats: Int? = null,
    val priceText: String = "",
    val freeTextValue: String = "",
    val requestedPolicy: String = "",
    val interpretationConfidence: Double = 0.0,
    val interpretationNotes: String = "",
    val multipleActions: Boolean = false,
)

@Serializable
internal data class RotaCertaAssistantInterpretRequest0410(
    val text: String,
    val timezone: String,
    val locale: String = "pt-BR",
    val allowedActions: List<String>,
    val conversationTripId: String = "",
)

@Serializable
internal data class RotaCertaAssistantInterpretResponse0410(
    val command: RotaCertaStructuredCommand0410,
    val interpreter: String = "",
    val model: String = "",
)

internal enum class RotaCertaValidationCode0410 {
    OK, INVALID_DATE, DATE_WEEKDAY_CONFLICT, PAST_DATE, INVALID_TIME, AMBIGUOUS_DATE, TRIP_NOT_FOUND,
    AMBIGUOUS_TRIP, PASSENGER_NOT_FOUND, AMBIGUOUS_PASSENGER, BOOKING_NOT_FOUND, AMBIGUOUS_BOOKING,
    MISSING_TRIP_ID, CAPABILITY_BLOCKED, STALE_PLAN, INVALID_ARGUMENT,
}

internal data class RotaCertaTemporalResolution0410(
    val code: RotaCertaValidationCode0410,
    val dates: List<LocalDate> = emptyList(),
    val time: LocalTime? = null,
    val message: String = "",
)

internal object RotaCertaTemporalResolver0410 {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val slashDate = DateTimeFormatter.ofPattern("d/M/uuuu")
    private val weekdayPt = mapOf(
        "segunda" to DayOfWeek.MONDAY, "segunda-feira" to DayOfWeek.MONDAY,
        "terça" to DayOfWeek.TUESDAY, "terca" to DayOfWeek.TUESDAY,
        "terça-feira" to DayOfWeek.TUESDAY, "terca-feira" to DayOfWeek.TUESDAY,
        "quarta" to DayOfWeek.WEDNESDAY, "quarta-feira" to DayOfWeek.WEDNESDAY,
        "quinta" to DayOfWeek.THURSDAY, "quinta-feira" to DayOfWeek.THURSDAY,
        "sexta" to DayOfWeek.FRIDAY, "sexta-feira" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY, "domingo" to DayOfWeek.SUNDAY,
    )

    fun resolve(command: RotaCertaStructuredCommand0410, now: Instant, zoneId: ZoneId): RotaCertaTemporalResolution0410 {
        val today = now.atZone(zoneId).toLocalDate()
        val parsedTime = parseTime(command.temporal.time)
            ?: if (!command.temporal.time.isNullOrBlank()) {
                return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.INVALID_TIME, message = "Horário inválido.")
            } else null

        if (command.dateTokens.isNotEmpty()) {
            val dates = mutableListOf<LocalDate>()
            for (token in command.dateTokens.distinct()) {
                val result = parseToken(token, today)
                if (result.first != RotaCertaValidationCode0410.OK || result.second == null) {
                    return RotaCertaTemporalResolution0410(result.first, message = "Data inválida: $token")
                }
                dates += result.second!!
            }
            val unique = dates.distinct().sorted()
            if (command.action == RotaCertaAction0410.CREATE_TRIPS && unique.any { it.isBefore(today) }) {
                return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.PAST_DATE, message = "Não é permitido criar viagem em data passada.")
            }
            return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.OK, dates = unique, time = parsedTime)
        }

        val t = command.temporal
        val explicit = t.explicitDate?.takeIf(String::isNotBlank)?.let { raw ->
            try { LocalDate.parse(raw, iso) } catch (_: DateTimeParseException) {
                return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.INVALID_DATE, message = "Data explícita inválida.")
            }
        }
        val components = if (t.dayOfMonth != null || t.month != null || t.year != null) {
            val day = t.dayOfMonth ?: return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.AMBIGUOUS_DATE, message = "Dia não informado.")
            val month = t.month ?: today.monthValue
            val year = t.year ?: today.year
            if (day !in 1..31 || month !in 1..12) return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.INVALID_DATE, message = "Dia ou mês inválido.")
            try { LocalDate.of(year, month, day) } catch (_: Throwable) {
                return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.INVALID_DATE, message = "A data informada não existe.")
            }
        } else null
        val relative = resolveRelative(t.relative, t.weekday, today)
        if (relative.first != RotaCertaValidationCode0410.OK) return RotaCertaTemporalResolution0410(relative.first, message = relative.third)
        val candidates = listOfNotNull(explicit, components, relative.second).distinct()
        if (candidates.size > 1) return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.DATE_WEEKDAY_CONFLICT, message = "As referências de data apontam para dias diferentes.")
        val resolved = candidates.singleOrNull()
        val expectedWeekday = t.weekday?.trim()?.lowercase(Locale.ROOT)?.let(weekdayPt::get)
        if (resolved != null && expectedWeekday != null && resolved.dayOfWeek != expectedWeekday) {
            return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.DATE_WEEKDAY_CONFLICT, message = "O dia da semana não corresponde à data informada.")
        }
        if (command.action == RotaCertaAction0410.CREATE_TRIPS && resolved?.isBefore(today) == true) {
            return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.PAST_DATE, message = "Não é permitido criar viagem em data passada.")
        }
        return RotaCertaTemporalResolution0410(RotaCertaValidationCode0410.OK, dates = listOfNotNull(resolved), time = parsedTime)
    }

    private fun parseTime(raw: String?): LocalTime? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(value) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun parseToken(token: String, today: LocalDate): Pair<RotaCertaValidationCode0410, LocalDate?> {
        val raw = token.trim().lowercase(Locale.ROOT)
        if (raw.isBlank()) return RotaCertaValidationCode0410.INVALID_DATE to null
        when (raw) {
            "hoje" -> return RotaCertaValidationCode0410.OK to today
            "amanhã", "amanha" -> return RotaCertaValidationCode0410.OK to today.plusDays(1)
            "depois de amanhã", "depois de amanha" -> return RotaCertaValidationCode0410.OK to today.plusDays(2)
        }
        runCatching { LocalDate.parse(raw, iso) }.getOrNull()?.let { return RotaCertaValidationCode0410.OK to it }
        Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").matchEntire(raw)?.let {
            return try { RotaCertaValidationCode0410.OK to LocalDate.parse(raw, slashDate) }
            catch (_: Throwable) { RotaCertaValidationCode0410.INVALID_DATE to null }
        }
        val day = raw.removePrefix("dia ").trim().toIntOrNull()
        if (day != null) {
            if (day !in 1..31) return RotaCertaValidationCode0410.INVALID_DATE to null
            return try { RotaCertaValidationCode0410.OK to YearMonth.from(today).atDay(day) }
            catch (_: Throwable) { RotaCertaValidationCode0410.INVALID_DATE to null }
        }
        weekdayPt[raw]?.let { weekday ->
            var cursor = today
            repeat(7) {
                if (cursor.dayOfWeek == weekday) return RotaCertaValidationCode0410.OK to cursor
                cursor = cursor.plusDays(1)
            }
        }
        return RotaCertaValidationCode0410.INVALID_DATE to null
    }

    private fun resolveRelative(relativeRaw: String?, weekdayRaw: String?, today: LocalDate): Triple<RotaCertaValidationCode0410, LocalDate?, String> {
        val relative = relativeRaw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val weekday = weekdayRaw?.trim()?.lowercase(Locale.ROOT)?.let(weekdayPt::get)
        val resolved = when (relative) {
            "", "NONE" -> null
            "TODAY" -> today
            "TOMORROW" -> today.plusDays(1)
            "DAY_AFTER_TOMORROW" -> today.plusDays(2)
            "NEXT_WEEK" -> today.plusWeeks(1)
            "WEEKEND" -> { var d = today; while (d.dayOfWeek != DayOfWeek.SATURDAY) d = d.plusDays(1); d }
            "WEEKDAY", "NEXT_WEEKDAY" -> {
                if (weekday == null) return Triple(RotaCertaValidationCode0410.AMBIGUOUS_DATE, null, "Dia da semana não informado.")
                var d = if (relative == "NEXT_WEEKDAY") today.plusDays(1) else today
                while (d.dayOfWeek != weekday) d = d.plusDays(1)
                d
            }
            else -> return Triple(RotaCertaValidationCode0410.AMBIGUOUS_DATE, null, "Referência temporal desconhecida.")
        }
        return Triple(RotaCertaValidationCode0410.OK, resolved, "")
    }
}

internal data class RotaCertaResolvedEntities0410(
    val code: RotaCertaValidationCode0410,
    val trip: Trip? = null,
    val booking: Booking? = null,
    val message: String = "",
)

internal object RotaCertaEntityResolver0410 {
    private val tripTargetActions = setOf(
        RotaCertaAction0410.READ_TRIP, RotaCertaAction0410.REVERIFY_TRIP, RotaCertaAction0410.OPEN_TRIP,
        RotaCertaAction0410.SHARE_TRIP, RotaCertaAction0410.GET_TRIP_PRICE, RotaCertaAction0410.SET_TRIP_DATE,
        RotaCertaAction0410.SET_TRIP_TIME, RotaCertaAction0410.SET_TRIP_ORIGIN, RotaCertaAction0410.SET_TRIP_DESTINATION,
        RotaCertaAction0410.SET_TRIP_ROUTE, RotaCertaAction0410.SET_TRIP_STOPOVERS, RotaCertaAction0410.SET_MEETING_POINT,
        RotaCertaAction0410.SET_TRIP_SEATS, RotaCertaAction0410.SET_TRIP_PRICE, RotaCertaAction0410.SET_TRIP_BOOST,
        RotaCertaAction0410.SET_SMART_STOPOVERS, RotaCertaAction0410.SET_INSTANT_BOOKING, RotaCertaAction0410.SET_TWO_MAX_IN_BACK,
        RotaCertaAction0410.SET_WOMEN_ONLY, RotaCertaAction0410.SET_TRIP_VEHICLE, RotaCertaAction0410.SET_TRIP_COMMENT,
        RotaCertaAction0410.DUPLICATE_TRIP, RotaCertaAction0410.CREATE_RETURN_TRIP, RotaCertaAction0410.CANCEL_TRIP,
        RotaCertaAction0410.READ_BOOKINGS, RotaCertaAction0410.READ_PASSENGERS, RotaCertaAction0410.READ_PASSENGER,
        RotaCertaAction0410.CONTACT_PASSENGER, RotaCertaAction0410.READ_MESSAGES, RotaCertaAction0410.SEND_MESSAGE,
        RotaCertaAction0410.ACCEPT_BOOKING, RotaCertaAction0410.DECLINE_BOOKING, RotaCertaAction0410.CANCEL_BOOKING,
    )
    private val passengerActions = setOf(
        RotaCertaAction0410.READ_PASSENGER, RotaCertaAction0410.CONTACT_PASSENGER,
        RotaCertaAction0410.READ_MESSAGES, RotaCertaAction0410.SEND_MESSAGE,
        RotaCertaAction0410.ACCEPT_BOOKING, RotaCertaAction0410.DECLINE_BOOKING, RotaCertaAction0410.CANCEL_BOOKING,
    )

    fun resolve(
        command: RotaCertaStructuredCommand0410,
        trips: List<Trip>,
        bookings: List<Booking>,
        temporal: RotaCertaTemporalResolution0410,
        zoneId: ZoneId,
    ): RotaCertaResolvedEntities0410 {
        if (command.action !in tripTargetActions) return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.OK)
        val date = temporal.dates.singleOrNull()
        val time = temporal.time
        val ref = command.tripReference.trim()
        val origin = command.origin.trim()
        val destination = command.destination.trim()
        val candidates = trips.asSequence()
            .filter { !it.deleted && it.status != TripStatus.CANCELLED }
            .filter { trip -> date == null || Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId).toLocalDate() == date }
            .filter { trip ->
                if (time == null) true
                else Instant.ofEpochMilli(trip.departureAtMillis).atZone(zoneId).toLocalTime().let {
                    it.hour == time.hour && it.minute == time.minute
                }
            }
            .filter { trip -> tripRouteMatches0411(trip, origin, destination) }
            .filter { trip -> ref.isBlank() || tripMatches(trip, ref) }
            .toList()
        if (candidates.isEmpty()) return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.TRIP_NOT_FOUND, message = "Nenhuma viagem corresponde à referência informada.")
        if (candidates.size != 1) return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.AMBIGUOUS_TRIP, message = "Encontrei mais de uma viagem possível. Informe rota, horário ou abra uma delas primeiro.")
        val trip = candidates.single()
        if (RotaCertaCommandRegistry0410.capability(command.action).requiresTripId && trip.blablaTripId.isNullOrBlank()) {
            return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.MISSING_TRIP_ID, trip = trip, message = "A viagem foi encontrada, mas o tripId BlaBlaCar ainda não está confirmado.")
        }
        if (command.action !in passengerActions) return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.OK, trip = trip)

        val passengerRef = command.passengerReference.trim()
        if (passengerRef.isBlank()) return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.PASSENGER_NOT_FOUND, trip = trip, message = "Informe qual passageiro.")
        val passengerCandidates = bookings.filter { booking ->
            booking.tripId == trip.id &&
                booking.status !in setOf(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.EXPIRED) &&
                (booking.passengerId.equals(passengerRef, ignoreCase = true) ||
                    booking.id.equals(command.bookingReference, ignoreCase = true) ||
                    booking.passengerName.contains(passengerRef, ignoreCase = true))
        }
        if (passengerCandidates.isEmpty()) return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.PASSENGER_NOT_FOUND, trip = trip, message = "Passageiro não encontrado nessa viagem.")
        if (passengerCandidates.size != 1) return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.AMBIGUOUS_PASSENGER, trip = trip, message = "Encontrei mais de um passageiro com essa referência nessa viagem.")
        return RotaCertaResolvedEntities0410(RotaCertaValidationCode0410.OK, trip = trip, booking = passengerCandidates.single())
    }

    private fun tripRouteMatches0411(
        trip: Trip,
        origin: String,
        destination: String,
    ): Boolean {
        if (origin.isBlank() && destination.isBlank()) return true
        val stops = trip.stops.sortedBy(TripStop::order)
        if (stops.size < 2) return false
        fun stopMatches(stop: TripStop, reference: String): Boolean {
            if (reference.isBlank()) return true
            val needle = normalize(reference)
            if (needle.isBlank()) return true
            return listOf(stop.name, stop.address)
                .map(::normalize)
                .filter(String::isNotBlank)
                .any { value ->
                    value == needle || value.contains(needle) ||
                        (value.length >= 4 && needle.contains(value))
                }
        }
        return stopMatches(stops.first(), origin) && stopMatches(stops.last(), destination)
    }

    private fun tripMatches(trip: Trip, reference: String): Boolean {
        val needle = normalize(reference)
        if (needle.isBlank()) return true
        val haystacks = buildList {
            add(trip.id); add(trip.title); add(trip.blablaTripId.orEmpty()); add(trip.blablaProfileUuid.orEmpty())
            trip.stops.forEach { add(it.name); add(it.address) }
        }.map(::normalize)
        return haystacks.any { candidate -> candidate == needle || candidate.contains(needle) || (candidate.length >= 4 && needle.contains(candidate)) }
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "").lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]+"""), " ").trim()
}

internal data class RotaCertaResolvedExecutionPlan0410(
    val planId: String,
    val commandId: String,
    val action: RotaCertaAction0410,
    val policy: RotaCertaExecutionPolicy0410,
    val risk: RotaCertaRisk0410,
    val trip: Trip? = null,
    val booking: Booking? = null,
    val dates: List<LocalDate> = emptyList(),
    val time: LocalTime? = null,
    val idempotencyKey: String,
    val observedRevision: Long = 0L,
    val expectedEffects: List<String> = emptyList(),
)

internal data class RotaCertaPlanResult0410(
    val code: RotaCertaValidationCode0410,
    val plan: RotaCertaResolvedExecutionPlan0410? = null,
    val message: String = "",
)

internal object RotaCertaCommandPlanner0410 {
    fun plan(
        command: RotaCertaStructuredCommand0410,
        trips: List<Trip>,
        bookings: List<Booking>,
        now: Instant,
        zoneId: ZoneId,
    ): RotaCertaPlanResult0410 {
        if (command.multipleActions) return RotaCertaPlanResult0410(RotaCertaValidationCode0410.INVALID_ARGUMENT, message = "Envie uma operação por mensagem para que cada plano possa ser validado e verificado separadamente.")
        val capability = RotaCertaCommandRegistry0410.capability(command.action)
        if (capability.coverage !in setOf(RotaCertaCoverage0410.IMPLEMENTED, RotaCertaCoverage0410.VERIFIED)) {
            return RotaCertaPlanResult0410(RotaCertaValidationCode0410.CAPABILITY_BLOCKED, message = "A operação foi descoberta, mas ainda não possui execução + verificação comprovadas nesta versão.")
        }
        val temporal = RotaCertaTemporalResolver0410.resolve(command, now, zoneId)
        if (temporal.code != RotaCertaValidationCode0410.OK) return RotaCertaPlanResult0410(temporal.code, message = temporal.message)
        val entities = RotaCertaEntityResolver0410.resolve(command, trips, bookings, temporal, zoneId)
        if (entities.code != RotaCertaValidationCode0410.OK) return RotaCertaPlanResult0410(entities.code, message = entities.message)
        if (command.action == RotaCertaAction0410.SET_TRIP_SEATS && (command.seats == null || command.seats !in 0..4)) {
            return RotaCertaPlanResult0410(RotaCertaValidationCode0410.INVALID_ARGUMENT, message = "Informe a quantidade de vagas entre 0 e 4.")
        }
        if (command.action == RotaCertaAction0410.CREATE_TRIPS && temporal.dates.isEmpty()) {
            return RotaCertaPlanResult0410(RotaCertaValidationCode0410.AMBIGUOUS_DATE, message = "Informe ao menos uma data para criar as viagens.")
        }
        if (command.action == RotaCertaAction0410.PUBLIC_SEARCH &&
            (command.origin.isBlank() || command.destination.isBlank())
        ) {
            return RotaCertaPlanResult0410(
                RotaCertaValidationCode0410.INVALID_ARGUMENT,
                message = "Informe origem e destino para a busca pública.",
            )
        }
        val policy = if (capability.risk in setOf(RotaCertaRisk0410.HIGH_IMPACT, RotaCertaRisk0410.DESTRUCTIVE)) RotaCertaExecutionPolicy0410.CONFIRM_BEFORE_EXECUTION else RotaCertaExecutionPolicy0410.AUTO_EXECUTE_VALIDATED
        val targetIdentity = entities.trip?.let { listOf(it.id, it.blablaProfileUuid.orEmpty(), it.blablaTripId.orEmpty(), it.canonicalRevision.toString()).joinToString("|") }.orEmpty()
        val keyMaterial = listOf(
            command.schemaVersion, command.action.name, targetIdentity, entities.booking?.id.orEmpty(),
            temporal.dates.joinToString(","), command.origin, command.destination,
            command.publicTargetNames.joinToString(","), command.seats?.toString().orEmpty(),
            command.priceText, command.freeTextValue,
        ).joinToString("|")
        val idempotencyKey = sha256TripPublication0387(keyMaterial)
        return RotaCertaPlanResult0410(
            code = RotaCertaValidationCode0410.OK,
            plan = RotaCertaResolvedExecutionPlan0410(
                planId = UUID.randomUUID().toString(), commandId = command.commandId, action = command.action,
                policy = policy, risk = capability.risk, trip = entities.trip, booking = entities.booking,
                dates = temporal.dates, time = temporal.time, idempotencyKey = idempotencyKey,
                observedRevision = entities.trip?.canonicalRevision ?: 0L,
                expectedEffects = listOf(capability.executor, capability.verifier),
            ),
        )
    }

    fun isStale(plan: RotaCertaResolvedExecutionPlan0410, currentTrips: List<Trip>): Boolean {
        val target = plan.trip ?: return false
        val current = currentTrips.firstOrNull { it.id == target.id } ?: return true
        return current.canonicalRevision != plan.observedRevision ||
            current.status == TripStatus.CANCELLED ||
            current.blablaTripId != target.blablaTripId ||
            current.blablaProfileUuid != target.blablaProfileUuid
    }
}
