package br.com.mapeiaia.rotacerta.trips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.Coordinate
import br.com.mapeiaia.rotacerta.R
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.text.Normalizer

internal data class EnhancedPassengerCardRow(
    val name: String,
    val phone: String?,
    val seats: Int,
    val boarding: String?,
    val dropoff: String?,
    val sources: Set<BookingSource>,
    val passengerId: String? = null,
    val localBookingId: String? = null,
    val externalReservationKey: String? = null,
    val externalBookingHref: String? = null,
    val externalProfileUuid: String? = null,
    val bookingStatus: BookingStatus? = null,
    val fareMinorUnits: Long? = null,
    val fareCurrencyCode: String = "",
    val boardingAddress: String = "",
    val dropoffAddress: String = "",
    val boardingStopIndex: Int? = null,
    val matchedByPhone: Boolean = false,
    val probableMatch: Boolean = false,
    val externalPassengerId: String? = null,
)

@Composable
internal fun EnhancedPassengerTimelineSection(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    currentCoordinate: Coordinate?,
    onChanged: (String) -> Unit,
    onSyncExactCard: (() -> Unit)? = null,
    onSyncSeatsOnly: (() -> Unit)? = null,
    onAddManualPassenger: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val passengerStore = remember(context) { PassengerIdentityStore(context) }
    val rawRows = enhancedPassengerRows(entry, trip, store, passengerStore)
    if (hasExternalTripActionEvidence(entry)) {
        TripBlaBlaTripActionRow(entry, onSyncExactCard, onSyncSeatsOnly, onAddManualPassenger)
    }
    if (rawRows.isEmpty()) return

    val progress = trip?.let { TripPassengerRouteOrder.progress(it, currentCoordinate) }
    // Keep trusted route/GPS ordering internally, but do not expose a
    // "next action" status in the card. The pickup/dropoff emojis are the
    // explicit GPS actions while the place labels keep their existing editor action.
    val rows = passengerTimelineOperationalOrder(rawRows, progress)

    var profileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var editManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var cancelManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var createProfileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var fareEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var boardingAddressEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var dropoffAddressEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }

    rows.forEachIndexed { index, passenger ->
        if (index > 0) HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val phone = passenger.phone
                IconButton(
                    onClick = {
                        if (!phone.isNullOrBlank()) {
                            UnifiedDebugEventStore.record(
                                "PASSENGER_WHATSAPP_OPEN",
                                context.packageName,
                                "timeline=true phone_present=true",
                            )
                            openPassengerWhatsApp(context, phone)
                        }
                    },
                    enabled = !phone.isNullOrBlank(),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_whatsapp_action),
                        contentDescription = "WhatsApp",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // 0.1.285: restored compact per-passenger shortcuts from the proven pre-regression row.
                externalTripTarget(entry.blablaProfileUuid, entry.blablaTripHref)?.let {
                    IconButton(
                        onClick = {
                            if (!openExternalTripBlaBla(context, entry.blablaProfileUuid, entry.blablaTripHref)) {
                                Toast.makeText(
                                    context,
                                    "Conta BlaBlaCar desta viagem não está conectada.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_blablacar_action),
                            contentDescription = "Abrir viagem no BlaBlaCar",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                TextButton(
                    onClick = {
                        val externalTarget = externalPassengerTarget(passenger)
                        if (externalTarget != null) {
                            if (!openExternalPassengerBlaBla(context, passenger)) {
                                Toast.makeText(
                                    context,
                                    "Conta BlaBlaCar desta reserva não está conectada.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        } else if (!passenger.passengerId.isNullOrBlank() && passengerStore.profile(passenger.passengerId) != null) {
                            profileRow = passenger
                        } else {
                            createProfileRow = passenger
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = COMPACT_NAME_PADDING,
                ) {
                    Text(
                        passenger.name.ifBlank { "Passageiro" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (passenger.fareMinorUnits != null) {
                            copyPassengerFareValue(context, passenger)
                        } else {
                            fareEditRow = passenger
                        }
                    },
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("💰", maxLines = 1)
                }

                OutlinedButton(
                    onClick = { copyPassengerConfirmationMessage(context, entry, passenger) },
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("💬", maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val pickupTarget = passengerPickupMapTarget(passenger)
                TextButton(
                    onClick = {
                        if (pickupTarget != null) openPassengerPickupMap(context, pickupTarget)
                    },
                    enabled = pickupTarget != null,
                    modifier = Modifier.size(36.dp),
                    contentPadding = ADDRESS_ICON_PADDING,
                ) {
                    Text("📍", maxLines = 1)
                }
                TextButton(
                    onClick = { boardingAddressEditRow = passenger },
                    modifier = Modifier.weight(1f),
                    contentPadding = ADDRESS_PLACE_PADDING,
                ) {
                    Text(
                        passengerTimelineCompactPlace(passenger.boarding),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("→")
                val dropoffTarget = passengerDropoffMapTarget(passenger)
                TextButton(
                    onClick = {
                        if (dropoffTarget != null) openPassengerDropoffMap(context, dropoffTarget)
                    },
                    enabled = dropoffTarget != null,
                    modifier = Modifier.size(36.dp),
                    contentPadding = ADDRESS_ICON_PADDING,
                ) {
                    Text("🏁", maxLines = 1)
                }
                TextButton(
                    onClick = { dropoffAddressEditRow = passenger },
                    modifier = Modifier.weight(1f),
                    contentPadding = ADDRESS_PLACE_PADDING,
                ) {
                    Text(
                        passengerTimelineCompactPlace(passenger.dropoff),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val source = passenger.sources.joinToString(" + ") { enhancedSourceShort(it) }
            val seats = if (passenger.seats == 1) "1 lugar" else "${passenger.seats} lugares"
            val identity = when {
                passenger.matchedByPhone -> " • ✓"
                passenger.probableMatch -> " • ⚠"
                passenger.phone.isNullOrBlank() -> " • tel.?"
                else -> ""
            }

            Text(
                "$source • $seats$identity",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val canonicalProfile = passenger.passengerId?.let(passengerStore::profile)
                ?: passengerStore.profileByExternalPassengerId(passenger.externalPassengerId)
            val rideHistory = canonicalProfile?.let { passengerStore.rideHistory(it.id) }
            val identityLabel = when {
                canonicalProfile?.blocked == true -> "🚫 PASSAGEIRO BLOQUEADO"
                rideHistory != null && rideHistory.totalRides > 0 -> "${rideHistory.totalRides} carona(s) registrada(s)"
                passenger.externalPassengerId != null -> "Identidade BlaBlaCar disponível"
                else -> null
            }
            identityLabel?.let { label ->
                TextButton(
                    onClick = {
                        if (canonicalProfile != null) profileRow = passenger.copy(passengerId = canonicalProfile.id)
                        else createProfileRow = passenger
                    },
                    contentPadding = COMPACT_NAME_PADDING,
                ) { Text(label, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    profileRow?.let { row ->
        val profile = passengerStore.profile(row.passengerId)
            ?: passengerStore.profileByExternalPassengerId(row.externalPassengerId)
        val history = profile?.let { passengerStore.rideHistory(it.id) }
        val manualBooking = trip?.let { currentTrip ->
            row.localBookingId?.let { bookingId ->
                store.bookingsFor(currentTrip.id).firstOrNull { booking ->
                    booking.id == bookingId &&
                        booking.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER) &&
                        booking.capacityClaimType == CapacityClaimType.PASSENGER &&
                        booking.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)
                }
            }
        }
        AlertDialog(
            onDismissRequest = { profileRow = null },
            title = { Text("Passageiro Rota Certa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile?.displayName ?: row.name)
                    val phone = profile?.whatsapp?.takeIf(String::isNotBlank) ?: row.phone
                    Text(phone?.takeIf(String::isNotBlank) ?: "Telefone não informado")
                    Text("Identidade canônica vinculada", style = MaterialTheme.typography.bodySmall)
                    history?.let { Text("${it.totalRides} carona(s) registrada(s)", style = MaterialTheme.typography.bodySmall) }
                    if (profile?.blocked == true) Text("🚫 PASSAGEIRO BLOQUEADO", color = MaterialTheme.colorScheme.error)
                    if (manualBooking != null) {
                        TextButton(onClick = {
                            editManualRow = row
                            profileRow = null
                        }) { Text("Editar lugares / trecho") }
                        TextButton(onClick = {
                            cancelManualRow = row
                            profileRow = null
                        }) { Text("Cancelar / excluir desta viagem") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    profile?.let { passengerStore.setBlocked(it.id, !it.blocked, if (!it.blocked) "Bloqueado pelo motorista" else "") }
                    profileRow = null
                    onChanged(if (profile?.blocked == true) "Passageiro desbloqueado." else "Passageiro bloqueado; futuras reservas com a mesma identidade serão sinalizadas e retiradas quando possível.")
                }) { Text(if (profile?.blocked == true) "Desbloquear" else "Bloquear") }
            },
            dismissButton = { TextButton(onClick = { profileRow = null }) { Text("Fechar") } },
        )
    }

    editManualRow?.let { row ->
        val currentTrip = trip
        val booking = currentTrip?.let { selectedTrip ->
            row.localBookingId?.let { bookingId ->
                store.bookingsFor(selectedTrip.id).firstOrNull { candidate ->
                    candidate.id == bookingId &&
                        candidate.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER) &&
                        candidate.capacityClaimType == CapacityClaimType.PASSENGER &&
                        candidate.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)
                }
            }
        }
        if (currentTrip != null && booking != null) {
            ManualPassengerOccupancyEditorDialog(
                trip = currentTrip,
                booking = booking,
                existingBookings = store.bookingsFor(currentTrip.id),
                onDismiss = { editManualRow = null },
                onSave = { updated ->
                    store.saveBooking(updated)
                    editManualRow = null
                    onChanged("Passageiro atualizado. Ocupação física por trecho recalculada.")
                    onSyncSeatsOnly?.invoke()
                },
                onError = onChanged,
            )
        } else {
            editManualRow = null
        }
    }

    cancelManualRow?.let { row ->
        val currentTrip = trip
        val booking = currentTrip?.let { selectedTrip ->
            row.localBookingId?.let { bookingId ->
                store.bookingsFor(selectedTrip.id).firstOrNull { candidate ->
                    candidate.id == bookingId &&
                        candidate.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER) &&
                        candidate.capacityClaimType == CapacityClaimType.PASSENGER &&
                        candidate.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)
                }
            }
        }
        AlertDialog(
            onDismissRequest = { cancelManualRow = null },
            title = { Text("Cancelar passageiro desta viagem?") },
            text = {
                Text(
                    "A reserva particular será cancelada na Agenda. Se a redução externa já estiver comprovada, o Rota Certa devolverá ${booking?.seats ?: row.seats} vaga(s) à mesma publicação BlaBlaCar e confirmará o número final antes de concluir.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = currentTrip != null && booking != null,
                    onClick = {
                        val selectedTrip = currentTrip ?: return@TextButton
                        val selectedBooking = booking ?: return@TextButton
                        store.saveBooking(selectedBooking.copy(status = BookingStatus.CANCELLED))
                        UnifiedDebugEventStore.record(
                            "AGENDA_MANUAL_PASSENGER_CANCELLED",
                            context.packageName,
                            "timeline=true seats=${selectedBooking.seats} desiredStateRecalculation=true",
                        )
                        cancelManualRow = null
                        onChanged("Passageiro manual cancelado. Ocupação física por trecho recalculada.")
                        onSyncSeatsOnly?.invoke()
                    },
                ) { Text("Cancelar e devolver vaga(s)") }
            },
            dismissButton = { TextButton(onClick = { cancelManualRow = null }) { Text("Manter passageiro") } },
        )
    }

    createProfileRow?.let { row ->
        val exact = passengerStore.exactContactMatches(row.phone.orEmpty()).singleOrNull()
        AlertDialog(
            onDismissRequest = { createProfileRow = null },
            title = { Text("Vincular passageiro") },
            text = {
                Text(
                    if (exact != null) {
                        "Existe um cadastro com o mesmo contato (${exact.displayName}). O vínculo só será feito se você confirmar."
                    } else {
                        "Criar um cadastro Rota Certa separado para ${row.name}? Nome ou telefone nunca serão fundidos automaticamente."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    var profile = exact ?: passengerStore.createProfile(row.name, row.phone.orEmpty())
                    row.externalPassengerId?.let { externalId ->
                        passengerStore.linkExternalPassengerId(profile.id, externalId)?.let { linked -> profile = linked }
                    }
                    if (linkPassengerProfile(row, profile.id, store, passengerStore)) {
                        onChanged("Cadastro do passageiro vinculado explicitamente.")
                    } else {
                        Toast.makeText(
                            context,
                            "Reserva sem referência estável; não foi possível persistir o vínculo.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    createProfileRow = null
                }) { Text(if (exact != null) "Vincular cadastro" else "Criar cadastro") }
            },
            dismissButton = { TextButton(onClick = { createProfileRow = null }) { Text("Cancelar") } },
        )
    }

    fareEditRow?.let { row ->
        PassengerFareEditorDialog(
            row = row,
            onDismiss = { fareEditRow = null },
            onSave = { amount, currency ->
                if (savePassengerFare(row, amount, currency, store, passengerStore)) {
                    copyPassengerFareValue(
                        context,
                        row.copy(
                            fareMinorUnits = amount,
                            fareCurrencyCode = currency,
                        ),
                    )
                    onChanged("Valor salvo e copiado.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; valor não foi salvo.", Toast.LENGTH_LONG).show()
                }
                fareEditRow = null
            },
        )
    }

    boardingAddressEditRow?.let { row ->
        PassengerAddressEditorDialog(
            title = "Endereço completo de embarque",
            initialValue = passengerAddressEditorInitialValue(row.boardingAddress, row.boarding),
            onDismiss = { boardingAddressEditRow = null },
            onSave = { address ->
                if (savePassengerAddress(row, address, true, store, passengerStore)) {
                    onChanged("Endereço de embarque salvo.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; endereço não foi salvo.", Toast.LENGTH_LONG).show()
                }
                boardingAddressEditRow = null
            },
        )
    }

    dropoffAddressEditRow?.let { row ->
        PassengerAddressEditorDialog(
            title = "Endereço completo de destino",
            initialValue = passengerAddressEditorInitialValue(row.dropoffAddress, row.dropoff),
            onDismiss = { dropoffAddressEditRow = null },
            onSave = { address ->
                if (savePassengerAddress(row, address, false, store, passengerStore)) {
                    onChanged("Endereço de destino salvo.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; endereço não foi salvo.", Toast.LENGTH_LONG).show()
                }
                dropoffAddressEditRow = null
            },
        )
    }
}

internal fun enhancedPassengerRows(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
): List<EnhancedPassengerCardRow> {
    val rows = entry.blablaPassengers.map { passenger ->
        val metadataKey = externalPassengerReservationKey(entry.blablaProfileUuid, passenger.booking_href)
        val metadata = passengerStore.externalMetadata(metadataKey)
        val boarding = passengerTimelinePlaceLabel(passenger.name, passenger.boarding)
        val dropoff = passengerTimelinePlaceLabel(passenger.name, passenger.dropoff)
        EnhancedPassengerCardRow(
            name = passenger.name.trim(),
            phone = passenger.phone?.trim()?.takeIf(String::isNotEmpty),
            seats = passenger.seats.coerceAtLeast(1),
            boarding = boarding,
            dropoff = dropoff,
            sources = setOf(BookingSource.BLABLACAR),
            passengerId = metadata?.passengerId?.takeIf(String::isNotBlank),
            externalReservationKey = metadataKey,
            externalBookingHref = passenger.booking_href?.trim()?.takeIf(String::isNotEmpty),
            externalProfileUuid = entry.blablaProfileUuid?.trim()?.takeIf(String::isNotEmpty),
            fareMinorUnits = metadata?.fareMinorUnits,
            fareCurrencyCode = metadata?.fareCurrencyCode.orEmpty(),
            boardingAddress = metadata?.boardingAddress.orEmpty(),
            externalPassengerId = metadata?.externalPassengerId?.takeIf(String::isNotBlank),
            dropoffAddress = metadata?.dropoffAddress.orEmpty(),
            boardingStopIndex = trip?.let { TripPassengerRouteOrder.stopIndexForLabel(it, boarding) },
        )
    }.toMutableList()

    if (trip != null) {
        val stops = trip.stops.associateBy(TripStop::id)
        val local = store.bookingsFor(trip.id)
            .filter { it.capacityClaimType == CapacityClaimType.PASSENGER }
            .filter { it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD }
            .filter { it.seats > 0 }

        local.forEach { booking ->
            val phone = booking.passengerContact.trim().takeIf(String::isNotEmpty)
            val boarding = stops[booking.boardingStopId]?.name
            val dropoff = stops[booking.dropoffStopId]?.name
            val phoneKey = passengerContactKey(phone)
            val candidateIndex = rows.indexOfFirst { current ->
                val currentPhone = passengerContactKey(current.phone)
                phoneKey.isNotBlank() && currentPhone.isNotBlank() && phoneKey == currentPhone
            }
            val secondaryIndex = if (candidateIndex >= 0) -1 else rows.indexOfFirst { current ->
                enhancedPassengerNameKey(current.name) == enhancedPassengerNameKey(booking.passengerName) &&
                    current.seats == booking.seats &&
                    enhancedRouteEvidenceMatches(current.boarding, current.dropoff, boarding, dropoff)
            }
            val index = if (candidateIndex >= 0) candidateIndex else secondaryIndex
            val stopIndex = TripPassengerRouteOrder.stopIndexForId(trip, booking.boardingStopId)
            if (index >= 0) {
                val current = rows[index]
                rows[index] = current.copy(
                    name = current.name.ifBlank { booking.passengerName.trim() },
                    phone = current.phone?.takeIf(String::isNotBlank) ?: phone,
                    seats = maxOf(current.seats, booking.seats),
                    boarding = current.boarding ?: boarding,
                    dropoff = current.dropoff ?: dropoff,
                    sources = current.sources + booking.source,
                    passengerId = booking.passengerId.takeIf(String::isNotBlank) ?: current.passengerId,
                    localBookingId = booking.id,
                    bookingStatus = booking.status,
                    fareMinorUnits = booking.fareMinorUnits ?: current.fareMinorUnits,
                    fareCurrencyCode = booking.fareCurrencyCode.takeIf(String::isNotBlank) ?: current.fareCurrencyCode,
                    boardingAddress = booking.boardingAddress.takeIf(String::isNotBlank) ?: current.boardingAddress,
                    dropoffAddress = booking.dropoffAddress.takeIf(String::isNotBlank) ?: current.dropoffAddress,
                    boardingStopIndex = stopIndex ?: current.boardingStopIndex,
                    matchedByPhone = candidateIndex >= 0,
                    probableMatch = candidateIndex < 0,
                )
            } else {
                rows += EnhancedPassengerCardRow(
                    name = booking.passengerName.trim(),
                    phone = phone,
                    seats = booking.seats,
                    boarding = boarding,
                    dropoff = dropoff,
                    sources = setOf(booking.source),
                    passengerId = booking.passengerId.takeIf(String::isNotBlank),
                    localBookingId = booking.id,
                    bookingStatus = booking.status,
                    fareMinorUnits = booking.fareMinorUnits,
                    fareCurrencyCode = booking.fareCurrencyCode,
                    boardingAddress = booking.boardingAddress,
                    dropoffAddress = booking.dropoffAddress,
                    boardingStopIndex = stopIndex,
                )
            }
        }
    }

    return rows
        .filter { it.name.isNotBlank() }
        .sortedWith(
            compareBy<EnhancedPassengerCardRow> { it.boardingStopIndex == null }
                .thenBy { it.boardingStopIndex ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
}

internal fun passengerTimelinePlaceLabel(passengerName: String, raw: String?): String? {
    val value = raw
        ?.replace('\u00A0', ' ')
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val expectedName = enhancedPassengerNameKey(passengerName)
    if (expectedName.isBlank()) return value

    val separators = charArrayOf(',', '•', '·', ':', ';')
    separators.forEach { separator ->
        val index = value.indexOf(separator)
        if (index <= 0 || index >= value.lastIndex) return@forEach
        val left = value.substring(0, index)
            .trim()
            .replace(Regex("""\s*\(\s*\d+\s*\)\s*$"""), "")
            .trim()
        if (enhancedPassengerNameKey(left) == expectedName) {
            return value.substring(index + 1).trim().takeIf(String::isNotEmpty)
        }
    }
    return value
}

internal fun passengerTimelineCompactPlace(raw: String?, maxLength: Int = 18): String {
    val place = raw
        ?.replace('\u00A0', ' ')
        ?.substringBefore(',')
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        .orEmpty()
    if (place.isBlank()) return "Pendente"
    if (place.length <= maxLength) return place

    val words = place.split(' ').filter(String::isNotBlank)
    if (words.size > 2) {
        val candidate = buildString {
            append(words.take(2).joinToString(" "))
            words.drop(2).forEach { word ->
                append(' ')
                append(if (word.length <= 2) word else "${word.first()}.")
            }
        }
        if (candidate.length <= maxLength) return candidate
    }

    val safeLength = maxLength.coerceAtLeast(4)
    return place.take(safeLength - 1).trimEnd() + "…"
}

internal fun passengerTimelineFareClipboardText(
    amountMinorUnits: Long,
    currencyCode: String,
    localeTag: String,
): String = PassengerMoney.formatMinorUnits(amountMinorUnits, currencyCode, localeTag)

internal fun passengerAddressEditorInitialValue(savedAddress: String?, collectedPlace: String?): String =
    savedAddress?.trim()?.takeIf(String::isNotEmpty)
        ?: collectedPlace?.trim()?.takeIf(String::isNotEmpty)
        ?: ""

@Composable
private fun PassengerAddressEditorDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    val normalized = value.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(240) },
                    label = { Text("Rua, número, bairro, cidade") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Este endereço pertence a esta reserva/viagem. Os atalhos 📍/🏁 abrem o GPS usando o endereço completo salvo quando ele estiver disponível.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized.isNotBlank(),
                onClick = { onSave(normalized) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ManualPassengerOccupancyEditorDialog(
    trip: Trip,
    booking: Booking,
    existingBookings: List<Booking>,
    onDismiss: () -> Unit,
    onSave: (Booking) -> Unit,
    onError: (String) -> Unit,
) {
    val stops = trip.stops.sortedBy(TripStop::order)
    var seats by remember(booking.id) { mutableStateOf(booking.seats) }
    var fromId by remember(booking.id) { mutableStateOf(booking.boardingStopId) }
    var toId by remember(booking.id) { mutableStateOf(booking.dropoffStopId) }
    var fromOpen by remember(booking.id) { mutableStateOf(false) }
    var toOpen by remember(booking.id) { mutableStateOf(false) }
    val fromIndex = stops.indexOfFirst { it.id == fromId }
    val toIndex = stops.indexOfFirst { it.id == toId }
    val valid = fromIndex >= 0 && toIndex > fromIndex
    val availability = if (valid) runCatching {
        SeatAvailabilityEngine.availability(
            trip,
            existingBookings.filterNot { it.id == booking.id },
            fromId,
            toId,
            seats,
        )
    }.getOrNull() else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar lugares / trecho") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(booking.passengerName)
                Column {
                    OutlinedButton(onClick = { fromOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Embarque: ${stops.firstOrNull { it.id == fromId }?.name ?: "Selecionar"}")
                    }
                    DropdownMenu(expanded = fromOpen, onDismissRequest = { fromOpen = false }) {
                        stops.dropLast(1).forEach { stop ->
                            DropdownMenuItem(
                                text = { Text(stop.name) },
                                onClick = {
                                    fromId = stop.id
                                    if (stops.indexOfFirst { it.id == toId } <= stop.order) toId = ""
                                    fromOpen = false
                                },
                            )
                        }
                    }
                }
                Column {
                    OutlinedButton(enabled = fromIndex >= 0, onClick = { toOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Destino: ${stops.firstOrNull { it.id == toId }?.name ?: "Selecionar"}")
                    }
                    DropdownMenu(expanded = toOpen, onDismissRequest = { toOpen = false }) {
                        stops.filterIndexed { index, _ -> fromIndex >= 0 && index > fromIndex }.forEach { stop ->
                            DropdownMenuItem(text = { Text(stop.name) }, onClick = { toId = stop.id; toOpen = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { if (seats > 1) seats-- }) { Text("−") }
                    Text(if (seats == 1) "1 lugar" else "$seats lugares")
                    OutlinedButton(onClick = { if (seats < trip.capacity) seats++ }) { Text("+") }
                }
                Text(
                    when {
                        !valid -> "Selecione embarque e destino."
                        availability?.canBook == true -> "${availability.availableSeats} vaga(s) disponíveis neste trecho antes da alteração."
                        else -> "Sem capacidade física para essa alteração."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && availability?.canBook == true,
                onClick = {
                    runCatching {
                        QuickPassengerEngine.updateManualBooking(
                            trip = trip,
                            existingBookings = existingBookings,
                            booking = booking,
                            boardingStopId = fromId,
                            dropoffStopId = toId,
                            seats = seats,
                        )
                    }.onSuccess(onSave).onFailure { onError(it.message ?: "Não foi possível atualizar o passageiro.") }
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PassengerFareEditorDialog(
    row: EnhancedPassengerCardRow,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit,
) {
    val context = LocalContext.current
    val spec = remember(context) { PassengerMoney.spec(context) }
    var value by remember(row.localBookingId, row.externalReservationKey) { mutableStateOf("") }
    val parsed = PassengerMoney.parseMinorUnits(value, spec)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${row.name} • ${passengerTimelineCompactPlace(row.boarding)} → ${passengerTimelineCompactPlace(row.dropoff)}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(32) },
                    label = { Text(if (spec.currencyCode.isBlank()) "Valor" else "Valor (${spec.currencyCode})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isNotBlank() && parsed == null) {
                    Text("Valor inválido.", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Ao salvar, o valor é copiado. O preço geral da viagem não é usado como valor individual.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { parsed?.let { onSave(it, spec.currencyCode) } },
            ) { Text("Salvar e copiar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun savePassengerAddress(
    row: EnhancedPassengerCardRow,
    addressRaw: String,
    boarding: Boolean,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
): Boolean {
    val address = addressRaw.trim().takeIf(String::isNotEmpty) ?: return false
    row.localBookingId?.let { bookingId ->
        val booking = store.bookings().firstOrNull { it.id == bookingId } ?: return@let
        store.saveBooking(
            if (boarding) {
                booking.copy(boardingAddress = address, localMetadataTouched = true)
            } else {
                booking.copy(dropoffAddress = address, localMetadataTouched = true)
            },
        )
        return true
    }
    val key = row.externalReservationKey ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(
        if (boarding) current.copy(boardingAddress = address) else current.copy(dropoffAddress = address),
    )
    return true
}

private fun savePassengerFare(
    row: EnhancedPassengerCardRow,
    amount: Long,
    currency: String,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
): Boolean {
    row.localBookingId?.let { bookingId ->
        val booking = store.bookings().firstOrNull { it.id == bookingId } ?: return@let
        store.saveBooking(
            booking.copy(
                fareMinorUnits = amount,
                fareCurrencyCode = currency,
                localMetadataTouched = true,
            ),
        )
        return true
    }
    val key = row.externalReservationKey ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(current.copy(fareMinorUnits = amount, fareCurrencyCode = currency))
    return true
}

private fun linkPassengerProfile(
    row: EnhancedPassengerCardRow,
    passengerId: String,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
): Boolean {
    row.localBookingId?.let { bookingId ->
        val booking = store.bookings().firstOrNull { it.id == bookingId } ?: return@let
        store.saveBooking(booking.copy(passengerId = passengerId, localMetadataTouched = true))
        return true
    }
    val key = row.externalReservationKey ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(current.copy(passengerId = passengerId))
    return true
}

internal fun passengerConfirmationMessage(
    entry: TripTimelineEntry,
    row: EnhancedPassengerCardRow,
    context: Context? = null,
): String {
    val zone = java.time.ZoneId.systemDefault()
    val departure = java.time.Instant.ofEpochMilli(entry.departureAtMillis).atZone(zone)
    val date = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(departure)
    val origin = row.boarding?.trim()?.takeIf(String::isNotEmpty) ?: entry.origin.trim()
    val destination = row.dropoff?.trim()?.takeIf(String::isNotEmpty) ?: entry.destination.trim()
    val lines = mutableListOf<String>()
    lines += "Olá, ${row.name.ifBlank { "passageiro" }}! Sua viagem está confirmada ✅"
    lines += ""
    if (origin.isNotBlank() && destination.isNotBlank()) lines += "🚗 $origin → $destination"
    lines += "📅 $date"
    lines += if (row.seats == 1) "💺 1 vaga" else "💺 ${row.seats} vagas"
    if (row.fareMinorUnits != null && context != null) {
        val formatted = passengerTimelineFareClipboardText(row.fareMinorUnits, row.fareCurrencyCode, PassengerMoney.spec(context).localeTag)
        lines += "💰 $formatted"
    }
    lines += ""
    lines += "Quando eu estiver a caminho, envio a localização. 👍"
    return lines.joinToString("\n")
}

private fun copyPassengerConfirmationMessage(context: Context, entry: TripTimelineEntry, row: EnhancedPassengerCardRow) {
    val message = passengerConfirmationMessage(entry, row, context)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Confirmação da viagem", message))
    UnifiedDebugEventStore.record(
        "PASSENGER_CONFIRMATION_MESSAGE_COPIED",
        context.packageName,
        "timeline=true seats=${row.seats} farePresent=${row.fareMinorUnits != null} boardingPresent=${row.boarding?.isNotBlank() == true} dropoffPresent=${row.dropoff?.isNotBlank() == true}",
    )
    Toast.makeText(context, "Mensagem de confirmação copiada.", Toast.LENGTH_SHORT).show()
}

private fun copyPassengerFareValue(context: Context, row: EnhancedPassengerCardRow) {
    val amount = row.fareMinorUnits ?: return
    val localeTag = PassengerMoney.spec(context).localeTag
    val formatted = passengerTimelineFareClipboardText(amount, row.fareCurrencyCode, localeTag)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Valor da reserva", formatted))
    Toast.makeText(context, "Valor copiado: $formatted", Toast.LENGTH_SHORT).show()
}

internal data class ExternalTripTarget(val profileUuid: String, val href: String)

internal fun externalTripTarget(profileUuid: String?, href: String?): ExternalTripTarget? {
    val profile = profileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    if (!CANONICAL_PROFILE_UUID.matches(profile)) return null
    val rawHref = href?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { java.net.URI(rawHref) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host != "www.blablacar.com.br") return null
    val path = uri.path.orEmpty().trimEnd('/')
    val pathIdentity = Regex(
        "^/rides/offer/(?!edit(?:/|$)|passenger(?:/|$))[^/?#]+$",
        RegexOption.IGNORE_CASE,
    ).matches(path) || Regex("^/trip/[^/?#]+$", RegexOption.IGNORE_CASE).matches(path)
    val rawQuery = uri.rawQuery.orEmpty()
    val queryId = rawQuery.split('&')
        .firstOrNull { it.substringBefore('=') == "id" }
        ?.substringAfter('=', "")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val queryIdentity = path in setOf("/rides/offer", "/trip") && queryId != null
    if (!pathIdentity && !queryIdentity) return null
    val keptQuery = rawQuery.split('&')
        .filter(String::isNotBlank)
        .filterNot { it.substringBefore('=') == "search_uuid" }
        .joinToString("&")
        .takeIf(String::isNotBlank)
    val canonicalHref = runCatching {
        java.net.URI(uri.scheme, uri.authority, uri.path, keptQuery, null).toString()
    }.getOrNull() ?: return null
    return ExternalTripTarget(profileUuid = profile, href = canonicalHref)
}

internal fun hasExternalTripActionEvidence(entry: TripTimelineEntry): Boolean =
    entry.sourcePassengerSeats[BookingSource.BLABLACAR]?.let { it > 0 } == true ||
        !entry.blablaTripId.isNullOrBlank() ||
        !entry.blablaTripHref.isNullOrBlank() ||
        !entry.blablaProfileUuid.isNullOrBlank()

@Composable
private fun TripBlaBlaTripActionRow(
    entry: TripTimelineEntry,
    onSyncExactCard: (() -> Unit)?,
    onSyncSeatsOnly: (() -> Unit)?,
    onAddManualPassenger: (() -> Unit)?,
) {
    val context = LocalContext.current
    val target = BlaBlaReliableSeatSyncBridge.targetForTimeline(entry)
    val seatStateStore = remember(context) { BlaBlaPublicationSeatSyncStateStore(context) }
    val seatState = target?.let { seatStateStore.get(it.profileUuid, it.tripId) }
    val seatLabel = when (seatState?.state) {
        BlaBlaPublicationSeatSyncVisualState.SYNCING -> "💺⏳"
        BlaBlaPublicationSeatSyncVisualState.SYNCED -> "💺✅"
        BlaBlaPublicationSeatSyncVisualState.PENDING -> "💺⚠️"
        BlaBlaPublicationSeatSyncVisualState.ERROR -> "💺❌"
        BlaBlaPublicationSeatSyncVisualState.AVAILABLE, null -> "💺🔄"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (onAddManualPassenger != null) {
            TextButton(
                onClick = {
                    UnifiedDebugEventStore.record(
                        "AGENDA_CARD_MANUAL_PASSENGER_OPEN",
                        context.packageName,
                        "timeline=true externalPublication=true",
                    )
                    onAddManualPassenger()
                },
                contentPadding = COMPACT_ACTION_PADDING,
            ) { Text("👤➕") }
        }
        if (onSyncSeatsOnly != null && target != null) {
            TextButton(
                enabled = seatState?.state != BlaBlaPublicationSeatSyncVisualState.SYNCING,
                onClick = {
                    UnifiedDebugEventStore.record(
                        "AGENDA_SEAT_ONLY_SYNC_REQUESTED",
                        context.packageName,
                        "profileUuidPresent=true tripIdPresent=true",
                    )
                    onSyncSeatsOnly()
                },
                contentPadding = COMPACT_ACTION_PADDING,
            ) { Text(seatLabel) }
        }
        if (onSyncExactCard != null && !entry.blablaProfileUuid.isNullOrBlank() && !entry.blablaTripId.isNullOrBlank()) {
            TextButton(
                onClick = {
                    UnifiedDebugEventStore.record(
                        "AGENDA_EXACT_CARD_SYNC_REQUESTED",
                        context.packageName,
                        "profileUuidPresent=true tripIdPresent=true",
                    )
                    onSyncExactCard()
                },
                contentPadding = COMPACT_ACTION_PADDING,
            ) { Text("🔄") }
        }
        IconButton(
            onClick = {
                if (!openExternalTripBlaBla(context, entry.blablaProfileUuid, entry.blablaTripHref)) {
                    Toast.makeText(
                        context,
                        "Link direto da viagem indisponível. Sincronize o BlaBlaCar para recuperar a referência desta publicação.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_blablacar_action),
                contentDescription = "Abrir viagem no BlaBlaCar",
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp),
            )
        }
        }
        Text(
            seatState?.message ?: "💺🔄 Sincronizar somente as vagas deste card",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal data class PassengerPickupMapTarget(val query: String)

internal fun passengerPickupMapTarget(row: EnhancedPassengerCardRow): PassengerPickupMapTarget? {
    val exact = row.boardingAddress.trim().takeIf(String::isNotEmpty)
    val collected = row.boarding?.trim()?.takeIf(String::isNotEmpty)
    val query = exact ?: collected ?: return null
    return PassengerPickupMapTarget(query)
}

internal fun passengerDropoffMapTarget(row: EnhancedPassengerCardRow): PassengerPickupMapTarget? {
    val exact = row.dropoffAddress.trim().takeIf(String::isNotEmpty)
    val collected = row.dropoff?.trim()?.takeIf(String::isNotEmpty)
    val query = exact ?: collected ?: return null
    return PassengerPickupMapTarget(query)
}

internal data class ExternalPassengerTarget(val profileUuid: String, val href: String)

internal fun externalPassengerTarget(row: EnhancedPassengerCardRow): ExternalPassengerTarget? {
    val profileUuid = row.externalProfileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    val href = row.externalBookingHref?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!href.startsWith("https://www.blablacar.com.br/") || !href.contains("/rides/offer/passenger/")) return null
    return ExternalPassengerTarget(profileUuid = profileUuid, href = href)
}

private fun openExternalTripBlaBla(context: Context, profileUuid: String?, href: String?): Boolean {
    val target = externalTripTarget(profileUuid, href) ?: return false
    val account = BlaBlaDynamicAccountRegistry(context).list()
        .firstOrNull { it.profileUuid?.trim()?.lowercase() == target.profileUuid }
        ?: return false
    UnifiedDebugEventStore.record(
        "BLABLACAR_TRIP_OPEN_EXPLICIT",
        context.packageName,
        "timeline=true profile_uuid=${target.profileUuid} href_present=true",
    )
    context.startActivity(
        BlaBlaDynamicSessionIntents.manage(context, account, target.href)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return true
}

private fun openPassengerPickupMap(context: Context, target: PassengerPickupMapTarget) {
    val uri = Uri.parse("geo:0,0?q=${Uri.encode(target.query)}")
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val mapsIntent = Intent(Intent.ACTION_VIEW, uri)
        .setPackage("com.google.android.apps.maps")
        .addFlags(flags)
    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(flags)
    UnifiedDebugEventStore.record(
        "PASSENGER_PICKUP_MAP_OPEN",
        context.packageName,
        "timeline=true exact_or_collected_pickup=true",
    )
    runCatching { context.startActivity(mapsIntent) }
        .recoverCatching { context.startActivity(fallbackIntent) }
        .onFailure {
            Toast.makeText(context, "Não foi possível abrir o local de embarque.", Toast.LENGTH_LONG).show()
        }
}

private fun openPassengerDropoffMap(context: Context, target: PassengerPickupMapTarget) {
    val uri = Uri.parse("geo:0,0?q=${Uri.encode(target.query)}")
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val mapsIntent = Intent(Intent.ACTION_VIEW, uri)
        .setPackage("com.google.android.apps.maps")
        .addFlags(flags)
    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(flags)
    UnifiedDebugEventStore.record(
        "PASSENGER_DROPOFF_MAP_OPEN",
        context.packageName,
        "timeline=true exact_or_collected_dropoff=true",
    )
    runCatching { context.startActivity(mapsIntent) }
        .recoverCatching { context.startActivity(fallbackIntent) }
        .onFailure {
            Toast.makeText(context, "Não foi possível abrir o local de destino.", Toast.LENGTH_LONG).show()
        }
}

private fun openExternalPassengerBlaBla(context: Context, row: EnhancedPassengerCardRow): Boolean {
    val target = externalPassengerTarget(row) ?: return false
    val account = BlaBlaDynamicAccountRegistry(context).list()
        .firstOrNull { it.profileUuid?.trim()?.lowercase() == target.profileUuid }
        ?: return false
    context.startActivity(
        BlaBlaDynamicSessionIntents.manage(context, account, target.href)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return true
}

private fun openPassengerWhatsApp(context: Context, raw: String) {
    val digits = raw.filter(Char::isDigit)
    if (digits.length !in 8..15) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "Não foi possível abrir o WhatsApp.", Toast.LENGTH_LONG).show()
    }
}

private fun enhancedPassengerNameKey(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun enhancedRouteEvidenceMatches(
    aBoard: String?,
    aDrop: String?,
    bBoard: String?,
    bDrop: String?,
): Boolean {
    if (aBoard.isNullOrBlank() || aDrop.isNullOrBlank() || bBoard.isNullOrBlank() || bDrop.isNullOrBlank()) return false
    return enhancedPlaceKey(aBoard) == enhancedPlaceKey(bBoard) &&
        enhancedPlaceKey(aDrop) == enhancedPlaceKey(bDrop)
}

private fun enhancedPlaceKey(raw: String): String = Normalizer.normalize(raw.substringBefore(',').trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun enhancedSourceShort(source: BookingSource): String = when (source) {
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.OTHER -> "Outro"
}

private val CANONICAL_PROFILE_UUID = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
)

private val COMPACT_ACTION_PADDING = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
private val COMPACT_NAME_PADDING = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
private val ADDRESS_PLACE_PADDING = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
private val ADDRESS_ICON_PADDING = PaddingValues(0.dp)
