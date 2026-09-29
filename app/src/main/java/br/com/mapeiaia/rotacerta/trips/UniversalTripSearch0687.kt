package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val UNIVERSAL_SEARCH_MARKER_0687 = "UNIVERSAL_SEARCH_ENGINE_0687"

internal enum class UniversalSearchKind0687(val label: String) {
    TRIP("Viagem"),
    BOOKING("Passageiro / reserva"),
    PASSENGER("Passageiro"),
}

internal data class UniversalSearchDocument0687(
    val key: String,
    val kind: UniversalSearchKind0687,
    val tripId: String = "",
    val bookingId: String = "",
    val passengerId: String = "",
    val title: String,
    val subtitle: String,
    val searchableRaw: String,
    val searchableNormalized: String,
)

internal data class UniversalSearchHit0687(
    val document: UniversalSearchDocument0687,
    val score: Int,
)

internal object UniversalSearchEngine0687 {
    private val localePtBr = Locale("pt", "BR")
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm EEEE MMMM", localePtBr)
    private val dateCompactFormatter = DateTimeFormatter.ofPattern("ddMMyyyy HHmm", localePtBr)

    internal fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun digits(value: String): String = value.filter(Char::isDigit)

    private fun timestampAliases(millis: Long?): String {
        val safe = millis?.takeIf { it > 0L } ?: return ""
        return runCatching {
            val local = Instant.ofEpochMilli(safe).atZone(zone)
            listOf(
                safe.toString(),
                local.format(dateTimeFormatter),
                local.format(dateCompactFormatter),
                local.dayOfMonth.toString(),
                "%02d".format(Locale.ROOT, local.dayOfMonth),
                local.monthValue.toString(),
                "%02d".format(Locale.ROOT, local.monthValue),
                local.year.toString(),
                "%02d:%02d".format(Locale.ROOT, local.hour, local.minute),
            ).joinToString(" ")
        }.getOrDefault(safe.toString())
    }

    private fun centsAliases(value: Long?): String {
        val cents = value ?: return ""
        val whole = cents / 100L
        val fraction = kotlin.math.abs(cents % 100L)
        return "$cents $whole,${fraction.toString().padStart(2, '0')} R$ $whole.${fraction.toString().padStart(2, '0')}"
    }

    private fun finalize(
        key: String,
        kind: UniversalSearchKind0687,
        tripId: String = "",
        bookingId: String = "",
        passengerId: String = "",
        title: String,
        subtitle: String,
        rawParts: Iterable<String>,
    ): UniversalSearchDocument0687 {
        val raw = rawParts.filter(String::isNotBlank).joinToString("\n")
        return UniversalSearchDocument0687(
            key = key,
            kind = kind,
            tripId = tripId,
            bookingId = bookingId,
            passengerId = passengerId,
            title = title.ifBlank { kind.label },
            subtitle = subtitle,
            searchableRaw = raw,
            searchableNormalized = normalize(raw),
        )
    }

    internal fun buildDocuments(
        trips: List<Trip>,
        bookings: List<Booking>,
        profiles: List<PassengerProfile> = emptyList(),
        externalMetadata: List<ExternalPassengerMetadata> = emptyList(),
        observationsByPassenger: Map<String, List<PassengerIdentityObservation>> = emptyMap(),
        ridesByPassenger: Map<String, List<PassengerRideRecord>> = emptyMap(),
    ): List<UniversalSearchDocument0687> {
        val tripById = trips.associateBy(Trip::id)
        val bookingsByPassenger = bookings
            .filter { it.passengerId.isNotBlank() }
            .groupBy(Booking::passengerId)
        val metadataByPassenger = externalMetadata
            .filter { it.passengerId.isNotBlank() }
            .groupBy(ExternalPassengerMetadata::passengerId)

        val docs = ArrayList<UniversalSearchDocument0687>(
            trips.size + bookings.size + profiles.size + externalMetadata.size,
        )

        trips.forEach { trip ->
            val orderedStops = trip.stops.sortedBy(TripStop::order)
            val origin = orderedStops.firstOrNull()?.name.orEmpty()
            val destination = orderedStops.lastOrNull()?.name.orEmpty()
            val route = listOf(origin, destination).filter(String::isNotBlank).joinToString(" → ")
            val subtitle = buildString {
                append(timestampAliases(trip.departureAtMillis))
                if (route.isNotBlank()) append(" • ").append(route)
                trip.blablaProfileName?.takeIf(String::isNotBlank)?.let { append(" • ").append(it) }
            }.trim(' ', '•')
            val stopAliases = orderedStops.joinToString("\n") { stop ->
                listOf(
                    stop.toString(),
                    stop.name,
                    stop.address,
                    digits(stop.address),
                    timestampAliases(stop.plannedArrivalMillis),
                    timestampAliases(stop.plannedDepartureMillis),
                    centsAliases(stop.priceToNextCents),
                ).joinToString(" ")
            }
            docs += finalize(
                key = "trip:" + trip.id,
                kind = UniversalSearchKind0687.TRIP,
                tripId = trip.id,
                title = trip.title.ifBlank { route.ifBlank { "Viagem" } },
                subtitle = subtitle,
                rawParts = listOf(
                    trip.toString(),
                    trip.id,
                    trip.remoteId.orEmpty(),
                    trip.tripKey,
                    trip.blablaTripId.orEmpty(),
                    trip.blablaProfileUuid.orEmpty(),
                    trip.blablaProfileName.orEmpty(),
                    trip.title,
                    route,
                    trip.notes,
                    timestampAliases(trip.departureAtMillis),
                    timestampAliases(trip.createdAtMillis),
                    timestampAliases(trip.updatedAtMillis),
                    stopAliases,
                ),
            )
        }

        bookings.forEach { booking ->
            val trip = tripById[booking.tripId]
            val orderedStops = trip?.stops?.sortedBy(TripStop::order).orEmpty()
            val boarding = orderedStops.firstOrNull { it.id == booking.boardingStopId }?.name.orEmpty()
            val dropoff = orderedStops.firstOrNull { it.id == booking.dropoffStopId }?.name.orEmpty()
            val route = listOf(boarding, dropoff).filter(String::isNotBlank).joinToString(" → ")
            val contactDigits = digits(booking.passengerContact)
            val subtitle = buildString {
                if (route.isNotBlank()) append(route)
                if (booking.passengerContact.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(booking.passengerContact)
                }
                if (trip != null) {
                    if (isNotEmpty()) append(" • ")
                    append(timestampAliases(trip.departureAtMillis))
                }
            }
            docs += finalize(
                key = "booking:" + booking.id,
                kind = UniversalSearchKind0687.BOOKING,
                tripId = booking.tripId,
                bookingId = booking.id,
                passengerId = booking.passengerId,
                title = booking.passengerName.ifBlank { "Reserva" },
                subtitle = subtitle,
                rawParts = listOf(
                    booking.toString(),
                    booking.id,
                    booking.tripId,
                    booking.passengerId,
                    booking.passengerName,
                    booking.passengerContact,
                    contactDigits,
                    booking.sourceReference,
                    booking.occupancyGroupId.orEmpty(),
                    booking.boardingAddress,
                    booking.dropoffAddress,
                    boarding,
                    dropoff,
                    route,
                    trip?.title.orEmpty(),
                    trip?.toString().orEmpty(),
                    timestampAliases(booking.createdAtMillis),
                    timestampAliases(booking.updatedAtMillis),
                    centsAliases(booking.fareMinorUnits),
                ),
            )
        }

        profiles.forEach { profile ->
            val profileBookings = bookingsByPassenger[profile.id].orEmpty()
            val metadata = metadataByPassenger[profile.id].orEmpty()
            val observations = observationsByPassenger[profile.id].orEmpty()
            val rides = ridesByPassenger[profile.id].orEmpty()
            val latestBooking = profileBookings.maxByOrNull(Booking::updatedAtMillis)
            val contact = profile.agendaAccessContact()
            docs += finalize(
                key = "passenger:" + profile.id,
                kind = UniversalSearchKind0687.PASSENGER,
                tripId = latestBooking?.tripId.orEmpty(),
                bookingId = latestBooking?.id.orEmpty(),
                passengerId = profile.id,
                title = profile.displayName.ifBlank { "Passageiro" },
                subtitle = contact,
                rawParts = listOf(
                    profile.toString(),
                    profile.id,
                    profile.displayName,
                    profile.whatsapp,
                    profile.agendaAccessWhatsapp,
                    contact,
                    digits(profile.whatsapp),
                    digits(profile.agendaAccessWhatsapp),
                    profileBookings.joinToString("\n"),
                    metadata.joinToString("\n"),
                    observations.joinToString("\n"),
                    rides.joinToString("\n"),
                    timestampAliases(profile.createdAtMillis),
                    timestampAliases(profile.updatedAtMillis),
                ),
            )
        }

        val knownReservationKeys = docs.asSequence()
            .filter { it.kind == UniversalSearchKind0687.PASSENGER }
            .map(UniversalSearchDocument0687::searchableRaw)
            .joinToString("\n")
        externalMetadata
            .filter { metadata -> metadata.reservationKey.isNotBlank() && !knownReservationKeys.contains(metadata.reservationKey) }
            .forEach { metadata ->
                docs += finalize(
                    key = "external:" + metadata.reservationKey,
                    kind = UniversalSearchKind0687.PASSENGER,
                    tripId = metadata.externalTripId,
                    passengerId = metadata.passengerId,
                    title = metadata.passengerId.ifBlank { "Reserva externa" },
                    subtitle = listOf(metadata.boardingAddress, metadata.dropoffAddress)
                        .filter(String::isNotBlank)
                        .joinToString(" → "),
                    rawParts = listOf(
                        metadata.toString(),
                        metadata.reservationKey,
                        metadata.externalTripId,
                        metadata.externalProfileUuid,
                        metadata.externalPassengerId,
                        metadata.passengerContact,
                        digits(metadata.passengerContact),
                        centsAliases(metadata.fareMinorUnits),
                        timestampAliases(metadata.updatedAtMillis),
                    ),
                )
            }

        return docs.distinctBy(UniversalSearchDocument0687::key)
    }

    internal fun search(
        documents: List<UniversalSearchDocument0687>,
        query: String,
    ): List<UniversalSearchHit0687> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()
        val terms = normalizedQuery.split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return emptyList()

        return documents.mapNotNull { document ->
            val body = document.searchableNormalized
            if (terms.any { !body.contains(it) }) return@mapNotNull null
            val title = normalize(document.title)
            val subtitle = normalize(document.subtitle)
            val fullQueryInTitle = title.contains(normalizedQuery)
            val fullQueryInSubtitle = subtitle.contains(normalizedQuery)
            val fullQueryInBody = body.contains(normalizedQuery)
            val firstBodyPosition = terms.minOfOrNull { body.indexOf(it).coerceAtLeast(0) } ?: 0
            val score = when {
                title == normalizedQuery -> 0
                fullQueryInTitle -> 10
                fullQueryInSubtitle -> 20
                fullQueryInBody -> 30
                else -> 40
            } + firstBodyPosition.coerceAtMost(10_000)
            UniversalSearchHit0687(document, score)
        }.sortedWith(
            compareBy<UniversalSearchHit0687> { it.score }
                .thenBy { it.document.kind.ordinal }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.document.title },
        )
    }
}

@Composable
internal fun UniversalSearchScreen0687(
    trips: List<Trip>,
    bookings: List<Booking>,
    onOpenResult: (UniversalSearchHit0687) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var documents by remember { mutableStateOf<List<UniversalSearchDocument0687>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(trips, bookings) {
        loading = true
        documents = withContext(Dispatchers.IO) {
            buildUniversalSearchSnapshot0687(context, trips, bookings)
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val results = remember(documents, query) {
        UniversalSearchEngine0687.search(documents, query)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            label = { Text("Buscar em tudo") },
            placeholder = { Text("Ex.: 17, Gabriela, Penha, R$ 93") },
            supportingText = {
                Text("Pesquisa literal por qualquer caractere, número, data, telefone, endereço, valor, viagem ou passageiro.")
            },
        )

        when {
            loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                }
            }
            query.isBlank() -> {
                Text(
                    "Digite até mesmo 1 caractere. Não existe tamanho mínimo de busca.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            results.isEmpty() -> {
                Text(
                    "Nenhum resultado para “$query”.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                Text(
                    "${results.size} resultado${if (results.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                )
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = results,
                        key = { it.document.key },
                    ) { hit ->
                        val doc = hit.document
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenResult(hit) },
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    doc.kind.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    doc.title,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (doc.subtitle.isNotBlank()) {
                                    Text(
                                        doc.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildUniversalSearchSnapshot0687(
    context: Context,
    trips: List<Trip>,
    bookings: List<Booking>,
): List<UniversalSearchDocument0687> {
    val identityStore = PassengerIdentityStore(context)
    val profiles = identityStore.profiles()
    val observations = profiles.associate { profile ->
        profile.id to identityStore.observations(profile.id)
    }
    val rides = profiles.associate { profile ->
        profile.id to identityStore.rideRecords(profile.id)
    }
    val metadata = identityStore.externalMetadataSnapshot0394().values.toList()

    return UniversalSearchEngine0687.buildDocuments(
        trips = trips,
        bookings = bookings,
        profiles = profiles,
        externalMetadata = metadata,
        observationsByPassenger = observations,
        ridesByPassenger = rides,
    )
}
