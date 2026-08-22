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
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.Coordinate
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
    val fareMinorUnits: Long? = null,
    val fareCurrencyCode: String = "",
    val boardingAddress: String = "",
    val dropoffAddress: String = "",
    val boardingStopIndex: Int? = null,
    val matchedByPhone: Boolean = false,
    val probableMatch: Boolean = false,
)

@Composable
internal fun EnhancedPassengerTimelineSection(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    currentCoordinate: Coordinate?,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val passengerStore = remember(context) { PassengerIdentityStore(context) }
    val rows = enhancedPassengerRows(entry, trip, store, passengerStore)
    if (rows.isEmpty()) return

    val progress = trip?.let { TripPassengerRouteOrder.progress(it, currentCoordinate) }
    val nextRowIndex = progress?.let { trustedProgress ->
        rows.indexOfFirst { row ->
            val order = row.boardingStopIndex ?: return@indexOfFirst false
            TripPassengerRouteOrder.isNextBoarding(order, trustedProgress)
        }.takeIf { it >= 0 }
    }

    var profileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var createProfileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var addressEdit by remember { mutableStateOf<Pair<EnhancedPassengerCardRow, Boolean>?>(null) }
    var fareEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }

    rows.forEachIndexed { index, passenger ->
        if (index > 0) HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (nextRowIndex == index && passenger.boardingStopIndex != null) {
                Text("📍 PRÓXIMO EMBARQUE", style = MaterialTheme.typography.labelMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val phone = passenger.phone
                OutlinedButton(
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
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("WA", maxLines = 1)
                }

                TextButton(
                    onClick = {
                        if (!passenger.passengerId.isNullOrBlank() && passengerStore.profile(passenger.passengerId) != null) {
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(
                    onClick = { addressEdit = passenger to true },
                    modifier = Modifier.weight(1f),
                    contentPadding = COMPACT_ROUTE_PADDING,
                ) {
                    Text(
                        "📍 ${passengerTimelineCompactPlace(passenger.boarding)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("→", maxLines = 1)
                TextButton(
                    onClick = { addressEdit = passenger to false },
                    modifier = Modifier.weight(1f),
                    contentPadding = COMPACT_ROUTE_PADDING,
                ) {
                    Text(
                        "🏁 ${passengerTimelineCompactPlace(passenger.dropoff)}",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "$source • $seats$identity",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    enabled = passenger.boardingAddress.isNotBlank(),
                    onClick = { openNavigation(context, passenger.boardingAddress) },
                    contentPadding = COMPACT_NAV_PADDING,
                ) { Text("🧭📍", maxLines = 1) }
                TextButton(
                    enabled = passenger.dropoffAddress.isNotBlank(),
                    onClick = { openNavigation(context, passenger.dropoffAddress) },
                    contentPadding = COMPACT_NAV_PADDING,
                ) { Text("🧭🏁", maxLines = 1) }
            }

            if (passenger.boardingStopIndex == null && trip != null) {
                Text(
                    "⚠ ordem de embarque pendente",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    profileRow?.let { row ->
        val profile = passengerStore.profile(row.passengerId)
        AlertDialog(
            onDismissRequest = { profileRow = null },
            title = { Text("Passageiro Rota Certa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile?.displayName ?: row.name)
                    val phone = profile?.whatsapp?.takeIf(String::isNotBlank) ?: row.phone
                    Text(phone?.takeIf(String::isNotBlank) ?: "Telefone não informado")
                    Text("Identidade canônica vinculada", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { profileRow = null }) { Text("Fechar") } },
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
                    val profile = exact ?: passengerStore.createProfile(row.name, row.phone.orEmpty())
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

    addressEdit?.let { (row, boarding) ->
        PassengerAddressEditorDialog(
            row = row,
            boarding = boarding,
            onDismiss = { addressEdit = null },
            onSave = { value ->
                if (savePassengerAddress(row, boarding, value, store, passengerStore)) {
                    onChanged(if (boarding) "Endereço exato de embarque salvo." else "Endereço exato de destino salvo.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; endereço não foi salvo.", Toast.LENGTH_LONG).show()
                }
                addressEdit = null
            },
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
            fareMinorUnits = metadata?.fareMinorUnits,
            fareCurrencyCode = metadata?.fareCurrencyCode.orEmpty(),
            boardingAddress = metadata?.boardingAddress.orEmpty(),
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

@Composable
private fun PassengerAddressEditorDialog(
    row: EnhancedPassengerCardRow,
    boarding: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val place = if (boarding) row.boarding else row.dropoff
    val current = if (boarding) row.boardingAddress else row.dropoffAddress
    var value by remember(row.localBookingId, row.externalReservationKey, boarding, current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (boarding) "Endereço de embarque" else "Endereço de destino") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(place?.takeIf(String::isNotBlank) ?: "Local não identificado")
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(500) },
                    label = { Text("Endereço exato") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text(
                    "A cidade/parada continua definindo a ordem da rota; este endereço é exclusivo desta reserva.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text("Salvar") } },
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
    boarding: Boolean,
    value: String,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
): Boolean {
    row.localBookingId?.let { bookingId ->
        val booking = store.bookings().firstOrNull { it.id == bookingId } ?: return@let
        store.saveBooking(
            if (boarding) booking.copy(boardingAddress = value.trim(), localMetadataTouched = true)
            else booking.copy(dropoffAddress = value.trim(), localMetadataTouched = true),
        )
        return true
    }
    val key = row.externalReservationKey ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(
        if (boarding) current.copy(boardingAddress = value.trim())
        else current.copy(dropoffAddress = value.trim()),
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

private fun copyPassengerFareValue(context: Context, row: EnhancedPassengerCardRow) {
    val amount = row.fareMinorUnits ?: return
    val localeTag = PassengerMoney.spec(context).localeTag
    val formatted = passengerTimelineFareClipboardText(amount, row.fareCurrencyCode, localeTag)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Valor da reserva", formatted))
    Toast.makeText(context, "Valor copiado: $formatted", Toast.LENGTH_SHORT).show()
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

private fun openNavigation(context: Context, address: String) {
    val destination = address.trim().takeIf(String::isNotEmpty) ?: return
    val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "Nenhum aplicativo de navegação conseguiu abrir este endereço.", Toast.LENGTH_LONG).show()
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

private val COMPACT_ACTION_PADDING = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
private val COMPACT_NAME_PADDING = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
private val COMPACT_ROUTE_PADDING = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
private val COMPACT_NAV_PADDING = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
