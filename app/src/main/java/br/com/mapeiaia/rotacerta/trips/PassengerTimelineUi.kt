package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.mapeiaia.rotacerta.Coordinate
import br.com.mapeiaia.rotacerta.LiveTrackingShareManager0668
import br.com.mapeiaia.rotacerta.PassengerTrackingLinkRequest0668
import br.com.mapeiaia.rotacerta.WorkTrackingService
import br.com.mapeiaia.rotacerta.passengerTrackingExpiry0668
import br.com.mapeiaia.rotacerta.shareTrackingLink0668
import br.com.mapeiaia.rotacerta.DiagnosticEventContext0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.R
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val operationalStatus: PassengerOperationalStatus = PassengerOperationalStatus.PENDING,
    val paymentStatus: PassengerPaymentStatus = PassengerPaymentStatus.UNPAID,
    val lastDriverSelection: String = "",
    val fareMinorUnits: Long? = null,
    val fareCurrencyCode: String = "",
    val boardingAddress: String = "",
    val dropoffAddress: String = "",
    val boardingLatitude: Double? = null,
    val boardingLongitude: Double? = null,
    val dropoffLatitude: Double? = null,
    val dropoffLongitude: Double? = null,
    val boardingStopIndex: Int? = null,
    val dropoffStopIndex: Int? = null,
    val matchedByPhone: Boolean = false,
    val probableMatch: Boolean = false,
    val externalPassengerId: String? = null,
)

internal enum class PassengerQuickMessageType0656 {
    CONFIRM_NOW,
    CONFIRM_TOMORROW,
    CONFIRM_ONE_HOUR,
    AT_LOCATION,
    FARE,
}

internal data class PassengerQuickMessageChoice0656(
    val type: PassengerQuickMessageType0656,
    val label: String,
)

internal val passengerQuickMessageChoices0656: List<PassengerQuickMessageChoice0656> = listOf(
    PassengerQuickMessageChoice0656(PassengerQuickMessageType0656.CONFIRM_NOW, "Confirmar agora"),
    PassengerQuickMessageChoice0656(PassengerQuickMessageType0656.CONFIRM_TOMORROW, "Confirmar amanhã"),
    PassengerQuickMessageChoice0656(PassengerQuickMessageType0656.CONFIRM_ONE_HOUR, "Confirmar 1 hora antes"),
    PassengerQuickMessageChoice0656(PassengerQuickMessageType0656.AT_LOCATION, "Estou no local"),
    PassengerQuickMessageChoice0656(PassengerQuickMessageType0656.FARE, "Valor da reserva"),
)

internal fun passengerFirstName0714(raw: String): String =
    raw.trim().split(Regex("\\s+")).firstOrNull()?.takeIf(String::isNotBlank) ?: "Passageiro"

internal data class PassengerMessageVehicle0714(
    val makeModel: String = "",
    val color: String = "",
    val plate: String = "",
)

internal fun PassengerMessageVehicle0714.hasVehicleIdentity0719(): Boolean =
    makeModel.isNotBlank() || color.isNotBlank() || plate.isNotBlank()

internal fun choosePassengerMessageVehicle0719(
    tripConfigured: Boolean,
    tripVehicle: PassengerMessageVehicle0714,
    settingsVehicle: PassengerMessageVehicle0714,
    profileVehicle: PassengerMessageVehicle0714?,
): PassengerMessageVehicle0714 {
    if (tripConfigured) return tripVehicle
    if (settingsVehicle.hasVehicleIdentity0719()) return settingsVehicle
    if (profileVehicle?.hasVehicleIdentity0719() == true) return profileVehicle
    return PassengerMessageVehicle0714()
}

internal fun resolvePassengerMessageVehicle0714(
    context: Context,
    entry: TripTimelineEntry,
    store: TripStore,
    trip: Trip? = null,
): PassengerMessageVehicle0714 {
    val tripVehicle0719 = PassengerMessageVehicle0714(
        makeModel = trip?.vehicleMakeModel.orEmpty().trim(),
        color = trip?.vehicleColor.orEmpty().trim(),
        plate = trip?.vehiclePlate.orEmpty().trim().uppercase(),
    )
    val settings0719 = store.onlineSettings()
    val settingsVehicle0719 = PassengerMessageVehicle0714(
        makeModel = settings0719.vehicleMakeModel.trim(),
        color = settings0719.vehicleColor.trim(),
    )
    val profileUuid0719 = entry.blablaProfileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
    val profileVehicle0719 = profileUuid0719?.let { profileUuid ->
        val account0719 = BlaBlaDynamicAccountRegistry(context.applicationContext).list()
            .filter { it.profileUuid?.trim()?.lowercase() == profileUuid }
            .singleOrNull()
            ?: return@let null
        val snapshot0719 = BlaBlaPublicProfileStore(context.applicationContext).read(account0719.id)
            ?.takeIf {
                it.identityVerified &&
                    it.profileUuid.trim().equals(profileUuid, ignoreCase = true)
            }
            ?: return@let null
        PassengerMessageVehicle0714(
            makeModel = snapshot0719.vehicleMakeModel.trim(),
            color = snapshot0719.vehicleColor.trim(),
        )
    }
    return choosePassengerMessageVehicle0719(
        tripConfigured = trip?.vehicleDayConfigured == true,
        tripVehicle = tripVehicle0719,
        settingsVehicle = settingsVehicle0719,
        profileVehicle = profileVehicle0719,
    )
} // MESSAGE_VEHICLE_AUTHORITY_0719

internal fun passengerTrackingMessage0714(passengerName: String): String =
    buildString {
        append("🚗 ").append(passengerFirstName0714(passengerName)).append(", acompanhe a viagem em tempo real.\n\n")
        append("O acompanhamento termina automaticamente no desembarque.\n\n")
        append("👇 Toque somente no link abaixo:")
    }

internal fun passengerTrackingPayload0714(message: String, url: String): String = buildString {
    append(message.trim())
    if (url.isNotBlank()) append("\n\n").append(url.trim())
}

internal data class PassengerTimelineRenderSnapshot0394(
    val rows: List<EnhancedPassengerCardRow>,
    val profilesByRowKey: Map<String, PassengerProfile>,
    val bookingsById: Map<String, Booking>,
    val historiesByProfileId: Map<String, PassengerPersistentHistory>,
    val completedRowKeys: Set<String>,
)

internal fun passengerTimelineRowKey0394(row: EnhancedPassengerCardRow): String =
    row.localBookingId?.trim()?.takeIf(String::isNotEmpty)
        ?: row.externalReservationKey?.trim()?.takeIf(String::isNotEmpty)
        ?: row.externalPassengerId?.trim()?.takeIf(String::isNotEmpty)
        ?: listOf(row.name.trim().lowercase(), row.phone.orEmpty().filter(Char::isDigit), row.boarding.orEmpty(), row.dropoff.orEmpty())
            .joinToString("|")

internal fun buildPassengerTimelineRenderSnapshot0394(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
    completionService: PassengerCompletionService,
    canonicalBookings0494: List<Booking>? = null,
): PassengerTimelineRenderSnapshot0394 {
    val localBookings = trip?.let { selectedTrip ->
        canonicalBookings0494?.filter { it.tripId == selectedTrip.id }
            ?: store.bookingsFor(selectedTrip.id)
    }.orEmpty()
    val hasLegacyPassengers0705 =
        !entry.canonicalBackendAuthoritative0494 && entry.blablaPassengers.isNotEmpty()
    if (localBookings.isEmpty() && !hasLegacyPassengers0705) {
        return PassengerTimelineRenderSnapshot0394(
            rows = emptyList(),
            profilesByRowKey = emptyMap(),
            bookingsById = emptyMap(),
            historiesByProfileId = emptyMap(),
            completedRowKeys = emptySet(),
        )
    }
    val externalMetadata = passengerStore.externalMetadataSnapshot0394()
    val rows = enhancedPassengerRows(
        entry = entry,
        trip = trip,
        store = store,
        passengerStore = passengerStore,
        externalMetadataSnapshot0394 = externalMetadata,
        localBookingsSnapshot0394 = localBookings,
    )
    val profilesByRowKey = passengerStore.resolveCanonicalPassengersBatch0705(
        rows.map { row ->
            PassengerIdentityLookup0705(
                key = passengerTimelineRowKey0394(row),
                passengerId = row.passengerId,
                externalPassengerId = row.externalPassengerId,
                whatsapp = row.phone,
            )
        },
    )
    val historiesByProfileId = passengerStore.persistentHistorySnapshot(
        profilesByRowKey.values.map(PassengerProfile::id).toSet(),
    )
    val completedRowKeys = rows.mapNotNull { row ->
        val rowKey = passengerTimelineRowKey0394(row)
        val profile = profilesByRowKey[rowKey] ?: return@mapNotNull null
        val occurrenceKey = completionService.occurrenceKey(entry, row)
        val completed = historiesByProfileId[profile.id]
            ?.rides
            .orEmpty()
            .any { record -> record.rideKey == occurrenceKey && record.status == PassengerOccurrenceStatus.COMPLETED }
        rowKey.takeIf { completed }
    }.toSet()
    return PassengerTimelineRenderSnapshot0394(
        rows = rows,
        profilesByRowKey = profilesByRowKey,
        bookingsById = localBookings.associateBy(Booking::id),
        historiesByProfileId = historiesByProfileId,
        completedRowKeys = completedRowKeys,
    )
}

internal fun buildImmediateCanonicalPassengerTimelineRenderSnapshot0517(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
    canonicalBookings0494: List<Booking>?,
): PassengerTimelineRenderSnapshot0394? {
    if (trip == null || canonicalBookings0494 == null) return null
    val localBookings = canonicalBookings0494.filter { it.tripId == trip.id }
    val hasLegacyPassengers0705 =
        !entry.canonicalBackendAuthoritative0494 && entry.blablaPassengers.isNotEmpty()
    if (localBookings.isEmpty() && !hasLegacyPassengers0705) {
        return PassengerTimelineRenderSnapshot0394(
            rows = emptyList(),
            profilesByRowKey = emptyMap(),
            bookingsById = emptyMap(),
            historiesByProfileId = emptyMap(),
            completedRowKeys = emptySet(),
        )
    }
    val rows = enhancedPassengerRows(
        entry = entry,
        trip = trip,
        store = store,
        passengerStore = passengerStore,
        externalMetadataSnapshot0394 = emptyMap(),
        localBookingsSnapshot0394 = localBookings,
    )
    return PassengerTimelineRenderSnapshot0394(
        rows = rows,
        profilesByRowKey = emptyMap(),
        bookingsById = localBookings.associateBy(Booking::id),
        historiesByProfileId = emptyMap(),
        completedRowKeys = emptySet(),
    )
}

internal data class TripChronologicalStop0667(
    val stop: TripStop,
    val index: Int,
    val timeMillis: Long?,
)

internal fun tripChronologicalStops0667(
    trip: Trip,
    departureAtMillis: Long,
    arrivalAtMillis: Long?,
): List<TripChronologicalStop0667> {
    val ordered0667 = trip.stops.sortedBy(TripStop::order)
    val lastIndex0667 = ordered0667.lastIndex
    return ordered0667.mapIndexed { index0667, stop0667 ->
        val timeMillis0667 = when {
            index0667 == 0 ->
                stop0667.plannedDepartureMillis
                    ?: stop0667.plannedArrivalMillis
                    ?: departureAtMillis
            index0667 == lastIndex0667 ->
                stop0667.plannedArrivalMillis
                    ?: stop0667.plannedDepartureMillis
                    ?: arrivalAtMillis
            else ->
                stop0667.plannedArrivalMillis
                    ?: stop0667.plannedDepartureMillis
        }
        TripChronologicalStop0667(
            stop = stop0667,
            index = index0667,
            timeMillis = timeMillis0667,
        )
    }
}

internal fun tripChronologicalStopTimeLabel0667(
    timeMillis: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? = timeMillis?.let { millis0667 ->
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis0667).atZone(zoneId))
}

internal fun passengerActiveOnSegment0671(
    row: EnhancedPassengerCardRow,
    segmentIndex: Int,
): Boolean {
    val boarding0671 = row.boardingStopIndex ?: return false
    val dropoff0671 = row.dropoffStopIndex ?: return false
    if (row.bookingStatus in setOf(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.EXPIRED)) return false
    if (row.operationalStatus == PassengerOperationalStatus.CANCELLED) return false
    return boarding0671 <= segmentIndex && segmentIndex < dropoff0671
}

internal fun passengerRowsOnSegment0672(
    rows: List<EnhancedPassengerCardRow>,
    segmentIndex: Int,
): List<EnhancedPassengerCardRow> =
    rows.filter { passengerActiveOnSegment0671(it, segmentIndex) }

internal fun effectivePassengerOperationalStatus0673(
    status: PassengerOperationalStatus,
    lastDriverSelection: String,
): PassengerOperationalStatus {
    val explicit0673 = lastDriverSelection.trim().uppercase()
    return if (
        status == PassengerOperationalStatus.CONFIRMED &&
        explicit0673 !in setOf("CONFIRMED", "AT_LOCATION", "IN_CAR", "PAID", "COMPLETED", "CANCELLED")
    ) {
        PassengerOperationalStatus.PENDING
    } else {
        status
    }
}

internal fun passengerOperationalStatusLabel0673(
    status: PassengerOperationalStatus,
    lastDriverSelection: String = "",
): String = when (effectivePassengerOperationalStatus0673(status, lastDriverSelection)) {
    PassengerOperationalStatus.PENDING -> "Pendente"
    PassengerOperationalStatus.CONFIRMED -> "Confirmado"
    PassengerOperationalStatus.AT_LOCATION -> "No local"
    PassengerOperationalStatus.IN_CAR -> "No carro"
    PassengerOperationalStatus.COMPLETED -> "Concluído"
    PassengerOperationalStatus.CANCELLED -> "Cancelado"
}

internal fun passengerRowsBoardingAtSegment0673(
    rows: List<EnhancedPassengerCardRow>,
    segmentIndex: Int,
): List<EnhancedPassengerCardRow> = rows.filter { row0673 ->
    row0673.boardingStopIndex == segmentIndex &&
        row0673.bookingStatus !in setOf(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.EXPIRED) &&
        row0673.operationalStatus != PassengerOperationalStatus.CANCELLED
}

internal fun passengerRowsWithoutBoardingStop0673(
    rows: List<EnhancedPassengerCardRow>,
): List<EnhancedPassengerCardRow> = rows.filter { row0673 ->
    row0673.boardingStopIndex == null &&
        row0673.bookingStatus !in setOf(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.EXPIRED) &&
        row0673.operationalStatus != PassengerOperationalStatus.CANCELLED
}

internal fun passengerSegmentPaxLabel0671(
    rows: List<EnhancedPassengerCardRow>,
    segmentIndex: Int,
    @Suppress("UNUSED_PARAMETER") expectedPassengerSeats: Int,
): String = passengerRowsOnSegment0672(rows, segmentIndex)
    .joinToString(" • ") { row0672 -> row0672.name.ifBlank { "Passageiro" } }

internal fun passengerScheduleZone0672(trip: Trip?): ZoneId {
    val configured0672 = trip?.publicTimezoneId0411?.trim()?.takeIf(String::isNotBlank)
    return configured0672
        ?.let { zone0672 -> runCatching { ZoneId.of(zone0672) }.getOrNull() }
        ?: ZoneId.systemDefault()
}

internal fun segmentStartTimeMillis0671(
    trip: Trip,
    load: SegmentLoad,
    departureAtMillis: Long,
    arrivalAtMillis: Long?,
): Long? = tripChronologicalStops0667(
    trip = trip,
    departureAtMillis = departureAtMillis,
    arrivalAtMillis = arrivalAtMillis,
).firstOrNull { it.stop.id == load.from.id }?.timeMillis

@Composable
private fun PassengerQuickActionLine0673(
    passenger0673: EnhancedPassengerCardRow,
    onNameClick0673: (EnhancedPassengerCardRow) -> Unit,
    onWhatsApp0673: (EnhancedPassengerCardRow) -> Unit,
    onTracking0674: (EnhancedPassengerCardRow) -> Unit,
    trackingActive0676: Boolean,
    onTrackingStop0676: (EnhancedPassengerCardRow) -> Unit,
    onQuickMessage0673: (EnhancedPassengerCardRow) -> Unit,
    onPickup0673: (EnhancedPassengerCardRow) -> Unit,
    onDropoff0673: (EnhancedPassengerCardRow) -> Unit,
    onStatus0673: (EnhancedPassengerCardRow) -> Unit,
) {
    val name0673 = passenger0673.name.ifBlank { "Passageiro" }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        TextButton(
            onClick = { onNameClick0673(passenger0673) },
            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
            contentPadding = COMPACT_NAME_PADDING,
        ) {
            Text(
                text = name0673,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { onWhatsApp0673(passenger0673) },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_whatsapp_action),
                contentDescription = "WhatsApp de $name0673",
                tint = Color.Unspecified,
                modifier = Modifier.size(21.dp),
            )
        }
        PassengerTrackingShortcut0676(
            active0676 = trackingActive0676,
            onTap0676 = { onTracking0674(passenger0673) },
            onLongPress0676 = { onTrackingStop0676(passenger0673) },
        )
        TextButton(
            onClick = { onQuickMessage0673(passenger0673) },
            modifier = Modifier.size(36.dp),
            contentPadding = ADDRESS_ICON_PADDING,
        ) { Text("💬", maxLines = 1) }
        TextButton(
            onClick = { onPickup0673(passenger0673) },
            modifier = Modifier.size(36.dp),
            contentPadding = ADDRESS_ICON_PADDING,
        ) { Text("📍", maxLines = 1) }
        TextButton(
            onClick = { onDropoff0673(passenger0673) },
            modifier = Modifier.size(36.dp),
            contentPadding = ADDRESS_ICON_PADDING,
        ) { Text("🏁", maxLines = 1) }
        TextButton(
            onClick = { onStatus0673(passenger0673) },
            modifier = Modifier.size(36.dp),
            contentPadding = ADDRESS_ICON_PADDING,
        ) { Text("🚦", maxLines = 1) }
    }
}

@Composable
private fun PassengerTrackingShortcut0676(
    active0676: Boolean,
    onTap0676: () -> Unit,
    onLongPress0676: () -> Unit,
) {
    val container0676 = if (active0676) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container0676)
            // 0.1.712: native gesture arbitration owns this child surface. A long press
            // is consumed here and never falls through as a card click or a short tap.
            .combinedClickable(
                onClickLabel = "Compartilhar acompanhamento",
                onLongClickLabel = if (active0676) "Encerrar acompanhamento" else null,
                onClick = onTap0676,
                onLongClick = if (active0676) onLongPress0676 else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("🛰️", maxLines = 1)
    }
}

@Composable
private fun SegmentVacancyLine0671(
    entry: TripTimelineEntry,
    trip: Trip,
    load0671: SegmentLoad,
    segmentIndex0671: Int,
    rows0671: List<EnhancedPassengerCardRow>,
    onPassengerClick0672: (EnhancedPassengerCardRow) -> Unit,
    onWhatsApp0673: (EnhancedPassengerCardRow) -> Unit = onPassengerClick0672,
    onTracking0674: (EnhancedPassengerCardRow) -> Unit = onPassengerClick0672,
    isTrackingActive0676: (EnhancedPassengerCardRow) -> Boolean = { false },
    onTrackingStop0676: (EnhancedPassengerCardRow) -> Unit = {},
    onQuickMessage0673: (EnhancedPassengerCardRow) -> Unit = onPassengerClick0672,
    onPickup0673: (EnhancedPassengerCardRow) -> Unit = onPassengerClick0672,
    onDropoff0673: (EnhancedPassengerCardRow) -> Unit = onPassengerClick0672,
    onStatus0673: (EnhancedPassengerCardRow) -> Unit = onPassengerClick0672,
) {
    val available0671 = load0671.availableSeats.coerceAtLeast(0)
    val vacancy0671 = when (available0671) {
        0 -> "CHEIO"
        1 -> "1 vaga"
        else -> "${available0671} vagas"
    }
    val overbooking0671 = load0671.overbookingSeats.coerceAtLeast(0)
    val boardingPassengers0673 = passengerRowsBoardingAtSegment0673(rows0671, segmentIndex0671)
    val time0671 = tripChronologicalStopTimeLabel0667(
        timeMillis = segmentStartTimeMillis0671(
            trip,
            load0671,
            entry.departureAtMillis,
            entry.arrivalAtMillis,
        ),
        zoneId = passengerScheduleZone0672(trip),
    ) ?: "—"
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = time0671,
                modifier = Modifier.width(42.dp).padding(top = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = "${load0671.from.name} → ${load0671.to.name}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (overbooking0671 > 0) "$vacancy0671 • EXCESSO $overbooking0671" else vacancy0671,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
        }
        boardingPassengers0673.forEach { passenger0673 ->
            PassengerQuickActionLine0673(
                passenger0673 = passenger0673,
                onNameClick0673 = onPassengerClick0672,
                onWhatsApp0673 = onWhatsApp0673,
                onTracking0674 = onTracking0674,
                trackingActive0676 = isTrackingActive0676(passenger0673),
                onTrackingStop0676 = onTrackingStop0676,
                onQuickMessage0673 = onQuickMessage0673,
                onPickup0673 = onPickup0673,
                onDropoff0673 = onDropoff0673,
                onStatus0673 = onStatus0673,
            )
        }
        val blocked0672 = load0671.blockedSeats.coerceAtLeast(0)
        if (blocked0672 > 0) {
            Text(
                text = "🚫 $blocked0672 bloqueada" + if (blocked0672 == 1) "" else "s",
                modifier = Modifier.padding(start = 50.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChronologicalTripStopLine0667(
    item0667: TripChronologicalStop0667,
    isLast0667: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = tripChronologicalStopTimeLabel0667(item0667.timeMillis) ?: "—",
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        Column(
            modifier = Modifier.width(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "●",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!isLast0667) {
                Text(
                    text = "│",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        Text(
            text = item0667.stop.name.ifBlank { "Parada" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun EnhancedPassengerTimelineSection(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    currentCoordinate: Coordinate?,
    onChanged: (String) -> Unit,
    onAddManualPassenger: (() -> Unit)? = null,
    focusedBookingId: String? = null,
    canonicalBookings0494: List<Booking>? = null,
    showTripActions0549: Boolean = true,
    compactEmbeddedControls0593: Boolean = false,
    embedChronologicalStops0667: Boolean = false,
    segmentLoads0671: List<SegmentLoad> = emptyList(),
) {
    val context = LocalContext.current
    val passengerStore = remember(context) { PassengerIdentityStore(context) }
    val completionService = remember(context, passengerStore) {
        PassengerCompletionService(context, passengerStore)
    }
    val mutationCoordinator = remember(context, store) { TripMutationCoordinator0387(context, store) }
    val scope = rememberCoroutineScope()
    val liveTrackingManager0668 = remember(context) { LiveTrackingShareManager0668(context) }
    var pendingTrackingRequest0668 by remember { mutableStateOf<PassengerTrackingLinkRequest0668?>(null) }
    var trackingRevision0676 by remember { mutableIntStateOf(0) }
    var trackingStopInFlight0676 by remember { mutableStateOf<Set<String>>(emptySet()) }
    val activePassengerTrackingKeys0730 = remember(trackingRevision0676) {
        liveTrackingManager0668.activePassengerKeysSnapshot0730()
    }

    fun passengerTrackingActive0676(row0676: EnhancedPassengerCardRow): Boolean =
        passengerTimelineRowKey0394(row0676) in activePassengerTrackingKeys0730

    fun openPassengerPickupShortcut0731(row0731: EnhancedPassengerCardRow) {
        val target0731 = passengerPickupMapTarget(row0731)
        if (target0731 == null) {
            onChanged("Local de embarque indisponível para navegação.")
            return
        }
        openPassengerPickupMap(context, target0731)
    }

    fun openPassengerDropoffShortcut0731(row0731: EnhancedPassengerCardRow) {
        val target0731 = passengerDropoffMapTarget(row0731)
        if (target0731 == null) {
            onChanged("Local de desembarque indisponível para navegação.")
            return
        }
        openPassengerDropoffMap(context, target0731)
    }

    fun stopPassengerTracking0676(row0676: EnhancedPassengerCardRow) {
        val passengerKey0676 = passengerTimelineRowKey0394(row0676)
        if (passengerKey0676.isBlank() || passengerKey0676 in trackingStopInFlight0676) return
        if (!liveTrackingManager0668.isPassengerShareActive(passengerKey0676)) return
        trackingStopInFlight0676 = trackingStopInFlight0676 + passengerKey0676
        UnifiedDebugEventStore.recordAlways(
            "PASSENGER_TRACKING_STOP_REQUEST_0712",
            context.packageName,
            "active=true inFlight=true",
        )
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { liveTrackingManager0668.closePassengerShare(passengerKey0676) }
            }.onSuccess { closed0676 ->
                if (closed0676) {
                    trackingRevision0676++
                    runCatching {
                        context.startService(
                            Intent(context, WorkTrackingService::class.java)
                                .setAction(WorkTrackingService.ACTION_RECONCILE_LOCATION_CORE_0681),
                        )
                    }
                    onChanged("Acompanhamento de " + row0676.name.ifBlank { "Passageiro" } + " encerrado imediatamente.")
                }
            }.onFailure { error0676 ->
                onChanged("Não foi possível encerrar o acompanhamento: " + (error0676.message ?: "falha no servidor"))
            }
            trackingStopInFlight0676 = trackingStopInFlight0676 - passengerKey0676
        }
    }

    fun publishPassengerTracking0668(request0668: PassengerTrackingLinkRequest0668) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, WorkTrackingService::class.java)
                .setAction(WorkTrackingService.ACTION_ENSURE_LOCATION_CORE_0681),
        )
        onChanged("Criando link temporário de acompanhamento para " + request0668.passengerName + "…")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { liveTrackingManager0668.createPassengerLink(request0668) }
            }.onSuccess { outcome0668 ->
                pendingTrackingRequest0668 = null
                trackingRevision0676++
                val trackingMessage0676 = passengerTrackingMessage0714(request0668.passengerName)
                val openedDirectly0676 = openPassengerTrackingWhatsApp0676(
                    context = context,
                    rawPhone0676 = request0668.passengerPhone,
                    message0676 = trackingMessage0676,
                    url0676 = outcome0668.url,
                )
                if (!openedDirectly0676) {
                    shareTrackingLink0668(
                        context = context,
                        title = "Compartilhar acompanhamento",
                        message = trackingMessage0676,
                        url = outcome0668.url,
                    )
                }
                onChanged(
                    if (outcome0668.reused) {
                        "Link temporário de " + request0668.passengerName + " aberto para compartilhar novamente."
                    } else {
                        "Link temporário de " + request0668.passengerName + " criado: ao vivo, sem rastro e com expiração no desembarque."
                    },
                )
            }.onFailure { error0668 ->
                onChanged("O GPS foi iniciado, mas o link temporário não pôde ser criado: " + (error0668.message ?: "falha no servidor"))
            }
        }
    }

    val trackingPermissionLauncher0668 = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions0668 ->
        val granted0668 =
            permissions0668[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions0668[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val pending0668 = pendingTrackingRequest0668
        if (granted0668 && pending0668 != null) {
            publishPassengerTracking0668(pending0668)
        } else if (!granted0668) {
            pendingTrackingRequest0668 = null
            onChanged("Autorize a localização para criar o link de acompanhamento.")
        }
    }

    fun requestPassengerTracking0668(request0668: PassengerTrackingLinkRequest0668) {
        val granted0668 =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted0668) {
            publishPassengerTracking0668(request0668)
        } else {
            pendingTrackingRequest0668 = request0668
            val requested0668 = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requested0668 += Manifest.permission.POST_NOTIFICATIONS
            }
            trackingPermissionLauncher0668.launch(requested0668.toTypedArray())
        }
    }

    fun sharePassengerTrackingShortcut0674(row0674: EnhancedPassengerCardRow) {
        val passengerKey0674 = passengerTimelineRowKey0394(row0674)
        if (passengerKey0674 in trackingStopInFlight0676) {
            onChanged("Encerramento do acompanhamento em andamento. Aguarde a confirmação do servidor.")
            return
        }
        val selectedTrip0674 = trip
        val latitude0674 = row0674.dropoffLatitude
        val longitude0674 = row0674.dropoffLongitude
        when {
            selectedTrip0674 == null ->
                onChanged("Viagem canônica indisponível para criar o acompanhamento.")
            latitude0674 == null || longitude0674 == null ->
                onChanged("Atualize esta viagem pelo HTML para obter o ponto exato de desembarque antes de compartilhar o acompanhamento.")
            else -> {
                UnifiedDebugEventStore.recordAlways(
                    "PASSENGER_QUICK_TRACKING_0674",
                    context.packageName,
                    "passengerScoped=true source=QUICK_ROW preShareHistory=false",
                )
                requestPassengerTracking0668(
                    PassengerTrackingLinkRequest0668(
                        tripId = selectedTrip0674.id,
                        passengerKey = passengerTimelineRowKey0394(row0674),
                        passengerName = row0674.name.ifBlank { "Passageiro" },
                        passengerPhone = row0674.phone.orEmpty(),
                        destinationLatitude = latitude0674,
                        destinationLongitude = longitude0674,
                        destinationLabel = row0674.dropoffAddress.ifBlank { row0674.dropoff.orEmpty() },
                        expiresAtMillis = passengerTrackingExpiry0668(entry.arrivalAtMillis),
                    ),
                )
            }
        }
    }

    var identityRevision by remember { mutableIntStateOf(0) }
    var completionRevision by remember { mutableIntStateOf(0) }
    val immediateCanonicalSnapshot0517 = remember(entry, trip, canonicalBookings0494) {
        buildImmediateCanonicalPassengerTimelineRenderSnapshot0517(
            entry = entry,
            trip = trip,
            store = store,
            passengerStore = passengerStore,
            canonicalBookings0494 = canonicalBookings0494,
        )
    }
    var renderSnapshot0394 by remember(entry.tripId, trip?.id) {
        mutableStateOf<PassengerTimelineRenderSnapshot0394?>(immediateCanonicalSnapshot0517)
    }
    LaunchedEffect(entry, trip, canonicalBookings0494, identityRevision, completionRevision) {
        renderSnapshot0394 = preservePassengerTimelineSnapshotDuringRefresh0532(
            current = renderSnapshot0394,
            immediateCanonical = immediateCanonicalSnapshot0517,
        )
        try {
            val resolved0512 = withContext(Dispatchers.IO) {
                buildPassengerTimelineRenderSnapshot0394(
                    entry = entry,
                    trip = trip,
                    store = store,
                    passengerStore = passengerStore,
                    completionService = completionService,
                    canonicalBookings0494 = canonicalBookings0494,
                )
            }
            renderSnapshot0394 = resolved0512
            UnifiedDebugEventStore.record(
                "PASSENGER_PROJECTION_RESOLVED",
                context.packageName,
                "canonicalTripId=" + seatSyncDiagnosticKey(entry.tripId) +
                    " canonicalRevision=" + entry.canonicalRevision0494 +
                    " rows=" + resolved0512.rows.size +
                    " source=" + if (entry.canonicalBackendAuthoritative0494) "CANONICAL_BACKEND" else "LOCAL_CANONICAL_AGENDA",
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            UnifiedDebugEventStore.record(
                "PASSENGER_PROJECTION_FAILED",
                context.packageName,
                "canonicalTripId=" + seatSyncDiagnosticKey(entry.tripId) +
                    " canonicalRevision=" + entry.canonicalRevision0494 +
                    " reasonType=" + error.javaClass.simpleName.take(80) +
                    " canonicalBackend=" + entry.canonicalBackendAuthoritative0494,
            )
        }
    }
    val renderSnapshot = renderSnapshot0394 ?: immediateCanonicalSnapshot0517
    val rawRows = renderSnapshot?.rows.orEmpty()
    val externalObservationKey = rawRows
        .filter { BookingSource.BLABLACAR in it.sources }
        .joinToString("|") { row ->
            listOf(row.externalPassengerId.orEmpty(), row.name, row.phone.orEmpty(), row.externalReservationKey.orEmpty()).joinToString("~")
        }
    LaunchedEffect(entry.tripId, entry.blablaTripId, entry.blablaProfileUuid, externalObservationKey) {
        if (entry.canonicalBackendAuthoritative0494 || externalObservationKey.isBlank()) return@LaunchedEffect
        val observed = withContext(Dispatchers.IO) {
            var anyObserved = false
            rawRows.filter { BookingSource.BLABLACAR in it.sources }.forEach { row ->
                val profile = passengerStore.observeExternalPassenger(
                    displayName = row.name,
                    whatsapp = row.phone,
                    externalPassengerId = row.externalPassengerId,
                    reservationKey = row.externalReservationKey,
                    externalTripId = entry.blablaTripId,
                    driverProfileUuid = entry.blablaProfileUuid,
                )
                if (profile != null) anyObserved = true
            }
            anyObserved
        }
        if (observed) identityRevision++
    }
    @Suppress("UNUSED_VARIABLE")
    val identityRefresh = identityRevision
    @Suppress("UNUSED_VARIABLE")
    val completionRefresh = completionRevision
    if (showTripActions0549) {
        TripBlaBlaTripActionRow(entry, onAddManualPassenger)
    }
    if (renderSnapshot == null) return
    if (rawRows.isEmpty() && !embedChronologicalStops0667 && segmentLoads0671.isEmpty()) return

    val progress = trip?.let { TripPassengerRouteOrder.progress(it, currentCoordinate) }
    // Keep trusted route/GPS ordering internally, but do not expose a
    // "next action" status in the card. The pickup/dropoff emojis are the
    // explicit GPS actions while the place labels keep their existing editor action.
    val rows = passengerTimelineOperationalOrder(rawRows, progress)
        .sortedBy { row -> if (row.localBookingId == focusedBookingId) 0 else 1 }
    val chronologicalStops0667 = if ((embedChronologicalStops0667 || segmentLoads0671.isNotEmpty()) && trip != null) {
        tripChronologicalStops0667(
            trip = trip,
            departureAtMillis = entry.departureAtMillis,
            arrivalAtMillis = entry.arrivalAtMillis,
        )
    } else {
        emptyList()
    }

    var profileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var blockProfile by remember { mutableStateOf<PassengerProfile?>(null) }
    var historyRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var editManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var cancelManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var createProfileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var fareEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var phoneEditRow0671 by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var communicationShortcutRow0672 by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var statusShortcutRow0673 by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var boardingAddressEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var dropoffAddressEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var boardingAddressSaving0714 by remember { mutableStateOf(false) }
    var dropoffAddressSaving0714 by remember { mutableStateOf(false) }
    var quickMessageRow0656 by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var privateRefreshAttempted0656 by remember(trip?.id) { mutableStateOf(false) }
    var privateRefreshCompleted0656 by remember(trip?.id) { mutableStateOf(false) }
    var privateRefreshStartedAt0656 by remember(trip?.id) { mutableStateOf(0L) }
    val targetedRefreshCommit0656 by TargetedTripRefreshEvents0645.commit.collectAsState()
    val messageVehicle0714 = resolvePassengerMessageVehicle0714(
        context = context,
        entry = entry,
        store = store,
        trip = trip,
    ) // live read: no stale remember cache for car-of-day/message vehicle
    val privateMetadataIncomplete0656 = rows.any(::passengerPrivateMetadataIncomplete0656)
    val privateMetadataFingerprint0656 = rows.joinToString("|") { row ->
        listOf(
            passengerTimelineRowKey0394(row),
            row.phone.orEmpty(),
            row.fareMinorUnits?.toString().orEmpty(),
            row.boardingAddress,
            row.dropoffAddress,
        ).joinToString("~")
    }

    fun applyPassengerOperationalStatus0673(
        passenger0673: EnhancedPassengerCardRow,
        selectionRaw0673: String,
    ) {
        val selection0673 = selectionRaw0673.trim().uppercase()
        statusShortcutRow0673 = null
        val rowKey0673 = passengerTimelineRowKey0394(passenger0673)
        val currentBooking0673 = passenger0673.localBookingId?.let(renderSnapshot.bookingsById::get)
        val completed0673 =
            rowKey0673 in renderSnapshot.completedRowKeys ||
                passenger0673.operationalStatus == PassengerOperationalStatus.COMPLETED
        if (completed0673 && selection0673 !in setOf("COMPLETED", "PAID")) {
            onChanged("Esta ocorrência já foi concluída. A conclusão é permanente; apenas o pagamento ainda pode ser confirmado.")
            return
        }
        if (passenger0673.operationalStatus == PassengerOperationalStatus.CANCELLED && selection0673 != "CANCELLED") {
            onChanged("Esta reserva já foi cancelada. Uma nova participação precisa nascer como nova reserva/ocorrência.")
            return
        }
        if (selection0673 == "CANCELLED") {
            if (
                currentBooking0673 != null ||
                (BookingSource.BLABLACAR in passenger0673.sources && !passenger0673.externalReservationKey.isNullOrBlank())
            ) {
                cancelManualRow = passenger0673
            } else {
                onChanged("Não foi possível identificar a reserva/ocorrência exata para cancelar com segurança.")
            }
            return
        }
        if (currentBooking0673?.status == BookingStatus.REQUESTED) {
            onChanged("Use Aprovar ou Recusar para resolver esta solicitação antes de alterar o status operacional.")
            return
        }
        val selectedTrip0673 = trip
        if (selectedTrip0673 == null || currentBooking0673 == null) {
            if (
                BookingSource.BLABLACAR in passenger0673.sources &&
                !passenger0673.externalReservationKey.isNullOrBlank()
            ) {
                val savedExternal0673 = saveExternalPassengerOperationalStatus0714(
                    row = passenger0673,
                    selectionRaw = selection0673,
                    passengerStore = passengerStore,
                )
                if (savedExternal0673 != null) {
                    identityRevision++
                    UnifiedDebugEventStore.recordAlways(
                        "PASSENGER_EXTERNAL_STATUS_READBACK_OK_0714",
                        context.packageName,
                        "rowKey=" + passengerCancellationHash(rowKey0673) +
                            " selection=" + selection0673 + " authority=EXTERNAL_PASSENGER_METADATA",
                    )
                    if (selection0673 == "COMPLETED") {
                        completionService.confirm(entry, passenger0673)?.let {
                            completionRevision++
                            identityRevision++
                        }
                        scope.launch(Dispatchers.IO) {
                            runCatching { liveTrackingManager0668.closePassengerShare(rowKey0673) }
                        }
                    }
                    onChanged(passengerOperationalSaveMessage0714(selection0673))
                } else {
                    onChanged("Nada foi alterado: não foi possível confirmar a gravação do status.")
                }
            } else {
                onChanged("A ocorrência exata não está disponível para alterar o status.")
            }
            return
        }
        scope.launch {
            runCatching {
                persistCanonicalPassengerMutation0582(
                    context = context,
                    trip = selectedTrip0673,
                    updated = passengerOperationalMutation0582(currentBooking0673, selection0673),
                    store = store,
                    mutationCoordinator = mutationCoordinator,
                    mutationType = "PASSENGER_STATUS_" + selection0673,
                )
            }.onSuccess {
                if (selection0673 == "COMPLETED") {
                    completionService.confirm(entry, passenger0673)?.let {
                        completionRevision++
                        identityRevision++
                    }
                    scope.launch(Dispatchers.IO) {
                        runCatching { liveTrackingManager0668.closePassengerShare(rowKey0673) }
                    }
                }
                onChanged(passengerOperationalSaveMessage0714(selection0673))
            }.onFailure { error0673 ->
                onChanged("Nada foi alterado: " + (error0673.message ?: "falha ao gravar o status"))
            }
        }
    }

    LaunchedEffect(
        targetedRefreshCommit0656?.revision,
        trip?.id,
        privateRefreshStartedAt0656,
    ) {
        val selectedTrip0656 = trip ?: return@LaunchedEffect
        val commit0656 = targetedRefreshCommit0656 ?: return@LaunchedEffect
        if (
            privateRefreshAttempted0656 &&
            privateRefreshStartedAt0656 > 0L &&
            commit0656.canonicalTripId == selectedTrip0656.id &&
            commit0656.committedAtMillis >= privateRefreshStartedAt0656
        ) {
            privateRefreshCompleted0656 = true
            UnifiedDebugEventStore.recordAlways(
                "PASSENGER_PRIVATE_REFRESH_COMMITTED_0656",
                context.packageName,
                "canonicalTripId=" + passengerCancellationHash(selectedTrip0656.id) +
                    " changed=${commit0656.changed} canonicalRevision=${commit0656.canonicalRevision}",
            )
        }
    }

    LaunchedEffect(
        compactEmbeddedControls0593,
        trip?.id,
        privateMetadataIncomplete0656,
        privateMetadataFingerprint0656,
    ) {
        val selectedTrip0656 = trip ?: return@LaunchedEffect
        if (!compactEmbeddedControls0593 || !privateMetadataIncomplete0656 || privateRefreshAttempted0656) {
            return@LaunchedEffect
        }
        if (selectedTrip0656.blablaProfileUuid.isNullOrBlank() || selectedTrip0656.blablaTripId.isNullOrBlank()) {
            return@LaunchedEffect
        }
        privateRefreshAttempted0656 = true
        privateRefreshCompleted0656 = false
        privateRefreshStartedAt0656 = System.currentTimeMillis()
        val queued0656 = withContext(Dispatchers.IO) {
            CentralDayCommandBridge0552.refreshTrip(context, selectedTrip0656)
        }
        UnifiedDebugEventStore.recordAlways(
            "PASSENGER_PRIVATE_REFRESH_REQUEST_0656",
            context.packageName,
            "canonicalTripId=" + passengerCancellationHash(selectedTrip0656.id) +
                " trigger=COMPACT_SHORTCUTS_OPEN incomplete=true queued=$queued0656 exactTripOnly=true",
        )
        onChanged(
            if (queued0656) {
                "Atualizando telefone, valor e endereços desta viagem pelo HTML…"
            } else {
                "Os dados privados desta viagem já estão sendo atualizados ou a conta precisa ser reconectada."
            },
        )
    }

    fun requestPrivateRefreshBeforeManual0656(reason: String): Boolean {
        val selectedTrip0656 = trip ?: return false
        if (selectedTrip0656.blablaProfileUuid.isNullOrBlank() || selectedTrip0656.blablaTripId.isNullOrBlank()) return false

        if (privateRefreshAttempted0656 && !privateRefreshCompleted0656) {
            val elapsed0656 = System.currentTimeMillis() - privateRefreshStartedAt0656
            if (elapsed0656 in 0L until 20_000L) {
                onChanged("A atualização HTML desta viagem ainda está em andamento. Tente novamente em alguns segundos.")
                return true
            }
            privateRefreshCompleted0656 = true
        }
        if (privateRefreshAttempted0656 && privateRefreshCompleted0656) return false

        privateRefreshAttempted0656 = true
        privateRefreshCompleted0656 = false
        privateRefreshStartedAt0656 = System.currentTimeMillis()
        scope.launch {
            val queued0656 = withContext(Dispatchers.IO) {
                CentralDayCommandBridge0552.refreshTrip(context, selectedTrip0656)
            }
            UnifiedDebugEventStore.recordAlways(
                "PASSENGER_PRIVATE_REFRESH_REQUEST_0656",
                context.packageName,
                "canonicalTripId=" + passengerCancellationHash(selectedTrip0656.id) +
                    " trigger=$reason incomplete=true queued=$queued0656 exactTripOnly=true",
            )
            onChanged(
                if (queued0656) {
                    "Buscando no HTML os dados que faltam desta viagem…"
                } else {
                    "A atualização direcionada já está em andamento ou a sessão da conta precisa ser validada."
                },
            )
        }
        return true
    }

    val selectedHistoryRow = historyRow
    if (selectedHistoryRow != null) {
        val profile = selectedHistoryRow.passengerId?.let(passengerStore::profile)
            ?: passengerStore.profileByExternalPassengerId(selectedHistoryRow.externalPassengerId)
        PassengerHistoryPanel(
            history = profile?.let { passengerStore.persistentHistory(it.id) },
            onBack = { historyRow = null },
            onArchiveToggle = { selectedProfile ->
                passengerStore.setArchived(selectedProfile.id, !selectedProfile.archived)
                identityRevision++
                historyRow = null
                onChanged(
                    if (selectedProfile.archived) "Passageiro restaurado na lista; histórico preservado."
                    else "Passageiro arquivado da lista; histórico, UUIDs, bloqueios e viagens foram preservados.",
                )
            },
        )
        return
    }

    val compactSegmentMode0673 = segmentLoads0671.isNotEmpty() && trip != null
    if (compactSegmentMode0673) {
        Text("Vagas por trecho", style = MaterialTheme.typography.titleSmall)
        segmentLoads0671.forEachIndexed { segmentIndex0673, load0673 ->
            SegmentVacancyLine0671(
                entry = entry,
                trip = requireNotNull(trip),
                load0671 = load0673,
                segmentIndex0671 = segmentIndex0673,
                rows0671 = rows,
                onPassengerClick0672 = { communicationShortcutRow0672 = it },
                onTracking0674 = { sharePassengerTrackingShortcut0674(it) },
                isTrackingActive0676 = { passengerTrackingActive0676(it) },
                onTrackingStop0676 = { stopPassengerTracking0676(it) },
                onWhatsApp0673 = { row0673 ->
                    val phone0673 = row0673.phone
                    if (phone0673.isNullOrBlank()) phoneEditRow0671 = row0673
                    else openPassengerWhatsApp(context, phone0673)
                },
                onQuickMessage0673 = { quickMessageRow0656 = it },
                onPickup0673 = { row0673 -> openPassengerPickupShortcut0731(row0673) },
                onDropoff0673 = { row0673 -> openPassengerDropoffShortcut0731(row0673) },
                onStatus0673 = { statusShortcutRow0673 = it },
            )
        }
        val withoutBoarding0673 = passengerRowsWithoutBoardingStop0673(rows)
        if (withoutBoarding0673.isNotEmpty()) {
            Text(
                "Embarque não associado ao itinerário",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            withoutBoarding0673.forEach { row0673 ->
                PassengerQuickActionLine0673(
                    passenger0673 = row0673,
                    onNameClick0673 = { communicationShortcutRow0672 = it },
                    onWhatsApp0673 = { selected0673 ->
                        val phone0673 = selected0673.phone
                        if (phone0673.isNullOrBlank()) phoneEditRow0671 = selected0673
                        else openPassengerWhatsApp(context, phone0673)
                    },
                    onTracking0674 = { sharePassengerTrackingShortcut0674(it) },
                    trackingActive0676 = passengerTrackingActive0676(row0673),
                    onTrackingStop0676 = { stopPassengerTracking0676(it) },
                    onQuickMessage0673 = { quickMessageRow0656 = it },
                    onPickup0673 = { selected0673 -> openPassengerPickupShortcut0731(selected0673) },
                    onDropoff0673 = { selected0673 -> openPassengerDropoffShortcut0731(selected0673) },
                    onStatus0673 = { statusShortcutRow0673 = it },
                )
            }
        }
    } else {
        if (segmentLoads0671.isNotEmpty()) {
            Text("Vagas por trecho", style = MaterialTheme.typography.titleSmall)
        }
        var lastRenderedStopIndex0667 = -1
    var lastRenderedSegmentIndex0671 = -1
    rows.forEachIndexed { index, passenger ->
        if (segmentLoads0671.isNotEmpty() && trip != null) {
            val targetStopIndex0671 = passenger.boardingStopIndex
            if (targetStopIndex0671 != null) {
                val safeTarget0671 = targetStopIndex0671.coerceIn(0, segmentLoads0671.lastIndex)
                if (safeTarget0671 > lastRenderedSegmentIndex0671) {
                    for (segmentIndex0671 in (lastRenderedSegmentIndex0671 + 1)..safeTarget0671) {
                        SegmentVacancyLine0671(
                            entry = entry,
                            trip = trip,
                            load0671 = segmentLoads0671[segmentIndex0671],
                            segmentIndex0671 = segmentIndex0671,
                            rows0671 = rows,
                            onPassengerClick0672 = { communicationShortcutRow0672 = it },
                            onTracking0674 = { sharePassengerTrackingShortcut0674(it) },
                            isTrackingActive0676 = { passengerTrackingActive0676(it) },
                            onTrackingStop0676 = { stopPassengerTracking0676(it) },
                        )
                    }
                    lastRenderedSegmentIndex0671 = safeTarget0671
                }
            } else if (lastRenderedSegmentIndex0671 < segmentLoads0671.lastIndex) {
                for (segmentIndex0671 in (lastRenderedSegmentIndex0671 + 1)..segmentLoads0671.lastIndex) {
                    SegmentVacancyLine0671(
                            entry = entry,
                            trip = trip,
                            load0671 = segmentLoads0671[segmentIndex0671],
                            segmentIndex0671 = segmentIndex0671,
                            rows0671 = rows,
                            onPassengerClick0672 = { communicationShortcutRow0672 = it },
                            onTracking0674 = { sharePassengerTrackingShortcut0674(it) },
                            isTrackingActive0676 = { passengerTrackingActive0676(it) },
                            onTrackingStop0676 = { stopPassengerTracking0676(it) },
                        )
                }
                lastRenderedSegmentIndex0671 = segmentLoads0671.lastIndex
                Text(
                    text = "Embarque não associado ao itinerário",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (embedChronologicalStops0667 && chronologicalStops0667.isNotEmpty()) {
            val targetStopIndex0667 = passenger.boardingStopIndex
            if (targetStopIndex0667 != null) {
                val safeTarget0667 = targetStopIndex0667.coerceIn(0, chronologicalStops0667.lastIndex)
                for (stopIndex0667 in (lastRenderedStopIndex0667 + 1)..safeTarget0667) {
                    ChronologicalTripStopLine0667(
                        item0667 = chronologicalStops0667[stopIndex0667],
                        isLast0667 = stopIndex0667 == chronologicalStops0667.lastIndex,
                    )
                }
                lastRenderedStopIndex0667 = maxOf(lastRenderedStopIndex0667, safeTarget0667)
            } else if (lastRenderedStopIndex0667 < chronologicalStops0667.lastIndex) {
                for (stopIndex0667 in (lastRenderedStopIndex0667 + 1)..chronologicalStops0667.lastIndex) {
                    ChronologicalTripStopLine0667(
                        item0667 = chronologicalStops0667[stopIndex0667],
                        isLast0667 = stopIndex0667 == chronologicalStops0667.lastIndex,
                    )
                }
                lastRenderedStopIndex0667 = chronologicalStops0667.lastIndex
                Text(
                    text = "Embarque não associado ao itinerário",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (index > 0 && (
                (segmentLoads0671.isEmpty() && !embedChronologicalStops0667) ||
                    passenger.boardingStopIndex == rows[index - 1].boardingStopIndex
            )
        ) {
            HorizontalDivider()
        }
        val rowKey0394 = passengerTimelineRowKey0394(passenger)
        val rowProfile = renderSnapshot.profilesByRowKey[rowKey0394]
        val currentBooking = passenger.localBookingId?.let(renderSnapshot.bookingsById::get)
        var statusMenuOpen by remember(passenger.localBookingId, passenger.externalPassengerId) {
            mutableStateOf(false)
        }
        var decisionRunning by remember(passenger.localBookingId) { mutableStateOf<String?>(null) }
        var rejectConfirmOpen by remember(passenger.localBookingId) { mutableStateOf(false) }
        var rejectReason by remember(passenger.localBookingId) { mutableStateOf("") }
        val pendingApproval = currentBooking?.status == BookingStatus.REQUESTED &&
            currentBooking.source == BookingSource.ROTA_CERTA
        val rejected = currentBooking?.status == BookingStatus.REJECTED
        val completed = rowKey0394 in renderSnapshot.completedRowKeys
        val statusLabel = when {
            rejected -> "Recusado"
            pendingApproval -> "Aguardando aprovação"
            completed || passenger.operationalStatus == PassengerOperationalStatus.COMPLETED -> "Concluído"
            passenger.lastDriverSelection == "PAID" -> "Pago"
            passenger.operationalStatus == PassengerOperationalStatus.AT_LOCATION -> "No local"
            passenger.operationalStatus == PassengerOperationalStatus.IN_CAR -> "No carro"
            passenger.operationalStatus == PassengerOperationalStatus.CANCELLED -> "Cancelado"
            passenger.operationalStatus == PassengerOperationalStatus.PENDING -> "Pendente"
            else -> "Confirmado"
        }
        val selectOperationalStatus: (String) -> Unit = select@{ selection ->
            statusMenuOpen = false
            if (compactEmbeddedControls0593) {
                UnifiedDebugEventStore.recordAlways(
                    "CENTRAL_DAY_PASSENGER_STATUS_REQUEST_0594",
                    context.packageName,
                    "selection=$selection current=${passenger.operationalStatus.name} bookingPresent=${currentBooking != null}",
                    diagnosticContext = DiagnosticEventContext0507(
                        parentModule = DiagnosticModule0507.CENTRAL_DAY,
                        originModule = DiagnosticModule0507.CENTRAL_DAY,
                        executorModule = DiagnosticModule0507.CENTRAL_DAY,
                        submodule = "PASSENGER_CONTROLS",
                        component = "EnhancedPassengerTimelineSection",
                        operation = "CENTRAL_DAY_PASSENGER_STATUS",
                        entityType = "booking",
                        entityId = passengerCancellationHash(currentBooking?.id ?: passenger.localBookingId.orEmpty()),
                        result = "REQUESTED",
                    ),
                )
            }
            val occurrenceCompleted =
                completed || passenger.operationalStatus == PassengerOperationalStatus.COMPLETED
            if (occurrenceCompleted && selection !in setOf("COMPLETED", "PAID")) {
                onChanged("Esta ocorrência já foi concluída. A conclusão é permanente; apenas o pagamento ainda pode ser confirmado.")
                return@select
            }
            if (passenger.operationalStatus == PassengerOperationalStatus.CANCELLED && selection != "CANCELLED") {
                onChanged("Esta reserva já foi cancelada. Uma nova participação precisa nascer como nova reserva/ocorrência.")
                return@select
            }
            if (selection == "CANCELLED") {
                val trace = passengerCancellationDebugContext(entry, passenger, currentBooking)
                val inCarBefore = passenger.operationalStatus == PassengerOperationalStatus.IN_CAR
                UnifiedDebugEventStore.record(
                    "PASSENGER_STATUS_CHANGE_REQUESTED",
                    context.packageName,
                    "$trace requested=CANCELLED",
                )
                UnifiedDebugEventStore.record(
                    "PASSENGER_STATUS_PREVIOUS",
                    context.packageName,
                    "$trace bookingStatus=${passenger.bookingStatus?.name ?: "EXTERNAL_ONLY"} operationalStatus=${passenger.operationalStatus.name} inCar=$inCarBefore seats=${passenger.seats}",
                )
                UnifiedDebugEventStore.record(
                    "PASSENGER_STATUS_CANCEL_SELECTED",
                    context.packageName,
                    "$trace source=TIMELINE internalOnly=true",
                )
                if (currentBooking != null ||
                    (BookingSource.BLABLACAR in passenger.sources && !passenger.externalReservationKey.isNullOrBlank())
                ) {
                    cancelManualRow = passenger
                } else {
                    onChanged("Não foi possível identificar a reserva/ocorrência exata para cancelar com segurança.")
                }
                return@select
            }

            if (pendingApproval) {
                onChanged("Use Aprovar ou Recusar para resolver esta solicitação. O seletor operacional não aprova reservas pendentes.")
                return@select
            }
            scope.launch {
                val selectedTrip = trip
                val booking = currentBooking
                if (selectedTrip == null || booking == null) {
                    onChanged("A ocorrência canônica não está disponível para alterar o status.")
                    return@launch
                }

                runCatching {
                    val updated = passengerOperationalMutation0582(booking, selection)
                    persistCanonicalPassengerMutation0582(
                        context = context,
                        trip = selectedTrip,
                        updated = updated,
                        store = store,
                        mutationCoordinator = mutationCoordinator,
                        mutationType = "PASSENGER_STATUS_" + selection,
                    )
                }.onSuccess {
                    if (selection == "COMPLETED") {
                        completionService.confirm(entry, passenger)?.let {
                            completionRevision++
                            identityRevision++
                        }
                        scope.launch(Dispatchers.IO) {
                            runCatching { liveTrackingManager0668.closePassengerShare(rowKey0394) }
                        }
                    }
                    UnifiedDebugEventStore.record(
                        "TIMELINE_CANONICAL_PASSENGER_MUTATION_0494",
                        context.packageName,
                        "canonicalTripId=${seatSyncDiagnosticKey(selectedTrip.id)} bookingIdPresent=true selection=$selection authority=LOCAL_CANONICAL_OUTBOX localBusinessWrite=true directHttp=false",
                    )
                    val message = when (selection) {
                        "CONFIRMED" -> "Passageiro confirmado no estado canônico."
                        "AT_LOCATION" -> "Status No local salvo no estado canônico."
                        "IN_CAR" -> "Status No carro salvo no estado canônico."
                        "PAID" -> "Pagamento confirmado no estado canônico."
                        "COMPLETED" -> "Passageiro concluído no estado canônico."
                        else -> "Status atualizado no estado canônico."
                    }
                    if (compactEmbeddedControls0593) {
                        UnifiedDebugEventStore.recordAlways(
                            "CENTRAL_DAY_PASSENGER_STATUS_RESULT_0594",
                            context.packageName,
                            "selection=$selection result=COMMITTED",
                            diagnosticContext = DiagnosticEventContext0507(
                                parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                originModule = DiagnosticModule0507.CENTRAL_DAY,
                                executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                submodule = "PASSENGER_CONTROLS",
                                component = "EnhancedPassengerTimelineSection",
                                operation = "CENTRAL_DAY_PASSENGER_STATUS",
                                entityType = "booking",
                                entityId = passengerCancellationHash(booking.id),
                                result = "COMMITTED",
                            ),
                        )
                    }
                    onChanged(message)
                }.onFailure { error ->
                    if (compactEmbeddedControls0593) {
                        UnifiedDebugEventStore.recordAlways(
                            "CENTRAL_DAY_PASSENGER_STATUS_RESULT_0594",
                            context.packageName,
                            "selection=$selection result=FAILED reason=${error.message ?: error.javaClass.simpleName}",
                            diagnosticContext = DiagnosticEventContext0507(
                                parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                originModule = DiagnosticModule0507.CENTRAL_DAY,
                                executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                submodule = "PASSENGER_CONTROLS",
                                component = "EnhancedPassengerTimelineSection",
                                operation = "CENTRAL_DAY_PASSENGER_STATUS",
                                entityType = "booking",
                                entityId = passengerCancellationHash(booking.id),
                                result = "FAILED",
                                reason = error.message.orEmpty(),
                            ),
                        )
                    }
                    onChanged("Nada foi alterado: ${error.message ?: "falha ao gravar no estado canônico"}")
                }
            }
        }

        if (compactEmbeddedControls0593) {
            val phone0593 = passenger.phone
            val pickupTarget0593 = passengerPickupMapTarget(passenger)
            val dropoffTarget0593 = passengerDropoffMapTarget(passenger)
            val passengerTarget0593 = externalPassengerTarget(passenger)

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = { communicationShortcutRow0672 = passenger },
                        modifier = Modifier.weight(1f),
                        contentPadding = COMPACT_NAME_PADDING,
                    ) {
                        Text(
                            (if (rowProfile?.blocked == true) "🚫 " else "") +
                                passenger.name.ifBlank { "Passageiro" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (pendingApproval || rejected || currentBooking?.status == BookingStatus.CANCELLED) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            contentPadding = COMPACT_ACTION_PADDING,
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Text(statusLabel, maxLines = 1)
                        }
                    } else {
                        Column {
                            OutlinedButton(
                                onClick = { statusMenuOpen = true },
                                contentPadding = COMPACT_ACTION_PADDING,
                                modifier = Modifier.heightIn(min = 36.dp),
                            ) {
                                Text(statusLabel + " ▼", maxLines = 1)
                            }
                            DropdownMenu(
                                expanded = statusMenuOpen,
                                onDismissRequest = { statusMenuOpen = false },
                            ) {
                                DropdownMenuItem(text = { Text("Pendente") }, onClick = { selectOperationalStatus("PENDING") })
                                DropdownMenuItem(text = { Text("Confirmado") }, onClick = { selectOperationalStatus("CONFIRMED") })
                                DropdownMenuItem(text = { Text("No local") }, onClick = { selectOperationalStatus("AT_LOCATION") })
                                DropdownMenuItem(text = { Text("No carro") }, onClick = { selectOperationalStatus("IN_CAR") })
                                DropdownMenuItem(text = { Text("Pago") }, onClick = { selectOperationalStatus("PAID") })
                                DropdownMenuItem(text = { Text("Concluído") }, onClick = { selectOperationalStatus("COMPLETED") })
                                if (!completed && passenger.operationalStatus != PassengerOperationalStatus.CANCELLED) {
                                    DropdownMenuItem(text = { Text("Cancelar") }, onClick = { selectOperationalStatus("CANCELLED") })
                                }
                            }
                        }
                    }
                }

                // 0.1.731 — Viagens/Central share one navigation contract:
                // both the pin and the visible place label open Android's GPS-app chooser.
                // Manual passengers use the itinerary stop label when no private address exists.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextButton(
                        onClick = {
                            if (pickupTarget0593 != null) {
                                UnifiedDebugEventStore.recordAlways(
                                    "CENTRAL_DAY_PASSENGER_SHORTCUT_0594",
                                    context.packageName,
                                    "shortcut=PICKUP_PIN navigationTarget=true chooser=true",
                                    diagnosticContext = DiagnosticEventContext0507(
                                        parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                        originModule = DiagnosticModule0507.CENTRAL_DAY,
                                        executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                        submodule = "PASSENGER_CONTROLS",
                                        component = "EnhancedPassengerTimelineSection",
                                        operation = "CENTRAL_DAY_PASSENGER_SHORTCUT",
                                        entityType = "booking",
                                        entityId = passengerCancellationHash(currentBooking?.id ?: passenger.localBookingId.orEmpty()),
                                        result = "PICKUP_PIN",
                                    ),
                                )
                                openPassengerPickupMap(context, pickupTarget0593)
                            } else {
                                onChanged("Local de embarque indisponível para navegação.")
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        contentPadding = ADDRESS_ICON_PADDING,
                    ) { Text("📍", maxLines = 1) }
                    TextButton(
                        onClick = { openPassengerPickupShortcut0731(passenger) },
                        modifier = Modifier.weight(1f),
                        contentPadding = ADDRESS_PLACE_PADDING,
                    ) {
                        Text(
                            passengerOperationalAddressLabel0656(passenger, boarding = true),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextButton(
                        onClick = {
                            if (dropoffTarget0593 != null) {
                                UnifiedDebugEventStore.recordAlways(
                                    "CENTRAL_DAY_PASSENGER_SHORTCUT_0594",
                                    context.packageName,
                                    "shortcut=DROPOFF_PIN navigationTarget=true chooser=true",
                                    diagnosticContext = DiagnosticEventContext0507(
                                        parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                        originModule = DiagnosticModule0507.CENTRAL_DAY,
                                        executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                        submodule = "PASSENGER_CONTROLS",
                                        component = "EnhancedPassengerTimelineSection",
                                        operation = "CENTRAL_DAY_PASSENGER_SHORTCUT",
                                        entityType = "booking",
                                        entityId = passengerCancellationHash(currentBooking?.id ?: passenger.localBookingId.orEmpty()),
                                        result = "DROPOFF_PIN",
                                    ),
                                )
                                openPassengerDropoffMap(context, dropoffTarget0593)
                            } else {
                                onChanged("Local de desembarque indisponível para navegação.")
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        contentPadding = ADDRESS_ICON_PADDING,
                    ) { Text("🏁", maxLines = 1) }
                    TextButton(
                        onClick = { openPassengerDropoffShortcut0731(passenger) },
                        modifier = Modifier.weight(1f),
                        contentPadding = ADDRESS_PLACE_PADDING,
                    ) {
                        Text(
                            passengerOperationalAddressLabel0656(passenger, boarding = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    PassengerTrackingShortcut0676(
                        active0676 = passengerTrackingActive0676(passenger),
                        onTap0676 = {
                            val selectedTrip0668 = trip
                            val destinationLatitude0668 = passenger.dropoffLatitude
                            val destinationLongitude0668 = passenger.dropoffLongitude
                            when {
                                selectedTrip0668 == null -> {
                                    onChanged("Viagem canônica indisponível para criar o acompanhamento.")
                                }
                                destinationLatitude0668 == null || destinationLongitude0668 == null -> {
                                    onChanged("Atualize esta viagem pelo HTML para obter o ponto exato de desembarque antes de compartilhar o acompanhamento.")
                                }
                                else -> {
                                    UnifiedDebugEventStore.recordAlways(
                                        "CENTRAL_DAY_PASSENGER_SHORTCUT_0594",
                                        context.packageName,
                                        "shortcut=LIVE_TRACKING_0668 passengerScoped=true preShareHistory=false",
                                        diagnosticContext = DiagnosticEventContext0507(
                                            parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                            originModule = DiagnosticModule0507.CENTRAL_DAY,
                                            executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                            submodule = "PASSENGER_CONTROLS",
                                            component = "EnhancedPassengerTimelineSection",
                                            operation = "CENTRAL_DAY_PASSENGER_SHORTCUT",
                                            entityType = "booking",
                                            entityId = passengerCancellationHash(currentBooking?.id ?: passenger.localBookingId.orEmpty()),
                                            result = "LIVE_TRACKING_0668",
                                        ),
                                    )
                                    requestPassengerTracking0668(
                                        PassengerTrackingLinkRequest0668(
                                            tripId = selectedTrip0668.id,
                                            passengerKey = rowKey0394,
                                            passengerName = passenger.name.ifBlank { "Passageiro" },
                                            passengerPhone = passenger.phone.orEmpty(),
                                            destinationLatitude = destinationLatitude0668,
                                            destinationLongitude = destinationLongitude0668,
                                            destinationLabel = passenger.dropoffAddress.ifBlank { passenger.dropoff.orEmpty() },
                                            expiresAtMillis = passengerTrackingExpiry0668(entry.arrivalAtMillis),
                                        ),
                                    )
                                }
                            }
                        },
                        onLongPress0676 = { stopPassengerTracking0676(passenger) },
                    )

                    IconButton(
                        onClick = {
                            if (!phone0593.isNullOrBlank()) {
                                UnifiedDebugEventStore.recordAlways(
                                    "CENTRAL_DAY_PASSENGER_SHORTCUT_0594",
                                    context.packageName,
                                    "shortcut=WHATSAPP phonePresent=true",
                                    diagnosticContext = DiagnosticEventContext0507(
                                        parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                        originModule = DiagnosticModule0507.CENTRAL_DAY,
                                        executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                        submodule = "PASSENGER_CONTROLS",
                                        component = "EnhancedPassengerTimelineSection",
                                        operation = "CENTRAL_DAY_PASSENGER_SHORTCUT",
                                        entityType = "booking",
                                        entityId = passengerCancellationHash(currentBooking?.id ?: passenger.localBookingId.orEmpty()),
                                        result = "WHATSAPP",
                                    ),
                                )
                                openPassengerWhatsApp(context, phone0593)
                            }
                        },
                        enabled = !phone0593.isNullOrBlank(),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp_action),
                            contentDescription = "WhatsApp do passageiro",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    TextButton(
                        onClick = { phoneEditRow0671 = passenger },
                        modifier = Modifier.size(36.dp),
                        contentPadding = ADDRESS_ICON_PADDING,
                    ) { Text("✎", maxLines = 1) }

                    TextButton(
                        onClick = {
                            UnifiedDebugEventStore.recordAlways(
                                "CENTRAL_DAY_PASSENGER_SHORTCUT_0594",
                                context.packageName,
                                "shortcut=FARE farePresent=${passenger.fareMinorUnits != null}",
                                diagnosticContext = DiagnosticEventContext0507(
                                    parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                    originModule = DiagnosticModule0507.CENTRAL_DAY,
                                    executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                    submodule = "PASSENGER_CONTROLS",
                                    component = "EnhancedPassengerTimelineSection",
                                    operation = "CENTRAL_DAY_PASSENGER_SHORTCUT",
                                    entityType = "booking",
                                    entityId = passengerCancellationHash(currentBooking?.id ?: passenger.localBookingId.orEmpty()),
                                    result = "FARE",
                                ),
                            )
                            if (passenger.fareMinorUnits != null) {
                                copyPassengerFareValue(context, passenger)
                            } else if (!requestPrivateRefreshBeforeManual0656("FARE_SHORTCUT")) {
                                fareEditRow = passenger
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        contentPadding = ADDRESS_ICON_PADDING,
                    ) { Text("💰", maxLines = 1) }

                    TextButton(
                        onClick = {
                            UnifiedDebugEventStore.recordAlways(
                                "CENTRAL_DAY_PASSENGER_SHORTCUT_0594",
                                context.packageName,
                                "shortcut=READY_MESSAGE_SELECTOR",
                                diagnosticContext = DiagnosticEventContext0507(
                                    parentModule = DiagnosticModule0507.CENTRAL_DAY,
                                    originModule = DiagnosticModule0507.CENTRAL_DAY,
                                    executorModule = DiagnosticModule0507.CENTRAL_DAY,
                                    submodule = "PASSENGER_CONTROLS",
                                    component = "EnhancedPassengerTimelineSection",
                                    operation = "CENTRAL_DAY_PASSENGER_SHORTCUT",
                                    entityType = "booking",
                                    entityId = passengerCancellationHash(currentBooking?.id ?: passenger.localBookingId.orEmpty()),
                                    result = "READY_MESSAGE_SELECTOR",
                                ),
                            )
                            quickMessageRow0656 = passenger
                        },
                        modifier = Modifier.size(36.dp),
                        contentPadding = ADDRESS_ICON_PADDING,
                    ) { Text("💬", maxLines = 1) }

                    if (passengerTarget0593 != null) {
                        IconButton(
                            onClick = {
                                if (!openExternalPassengerBlaBla(context, passenger)) {
                                    Toast.makeText(
                                        context,
                                        "Conta BlaBlaCar deste passageiro não está conectada.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_blablacar_action),
                                contentDescription = "Abrir passageiro no BlaBlaCar",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                if (pendingApproval) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            enabled = decisionRunning == null,
                            onClick = {
                                val selectedTrip = trip
                                val booking = currentBooking
                                if (selectedTrip == null || booking == null) {
                                    onChanged("Não foi possível localizar a viagem/reserva canônica para aprovar.")
                                } else {
                                    decisionRunning = "APPROVE"
                                    scope.launch {
                                        runCatching {
                                            persistCanonicalPassengerMutation0582(
                                                context = context,
                                                trip = selectedTrip,
                                                updated = passengerDecisionMutation0582(booking, "APPROVE"),
                                                store = store,
                                                mutationCoordinator = mutationCoordinator,
                                                mutationType = "RESERVATION_APPROVED",
                                            )
                                        }.onSuccess {
                                            onChanged("Reserva aprovada no estado canônico ✅")
                                        }.onFailure {
                                            onChanged("Nada foi alterado: falha ao aprovar a reserva.")
                                        }
                                        decisionRunning = null
                                    }
                                }
                            },
                        ) { Text(if (decisionRunning == "APPROVE") "Aprovando…" else "Aprovar") }
                        TextButton(
                            enabled = decisionRunning == null,
                            onClick = { rejectConfirmOpen = true },
                        ) { Text("Recusar") }
                    }
                }

                if (rejectConfirmOpen) {
                    AlertDialog(
                        onDismissRequest = { if (decisionRunning == null) rejectConfirmOpen = false },
                        title = { Text("Recusar solicitação?") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("A mesma reserva será marcada como Recusada e as vagas deste trecho serão liberadas.")
                                OutlinedTextField(
                                    value = rejectReason,
                                    onValueChange = { rejectReason = it.take(240) },
                                    label = { Text("Motivo opcional") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = decisionRunning == null,
                                onClick = {
                                    val selectedTrip = trip
                                    val booking = currentBooking
                                    if (selectedTrip == null || booking == null) {
                                        rejectConfirmOpen = false
                                        onChanged("Não foi possível localizar a viagem/reserva canônica para recusar.")
                                    } else {
                                        decisionRunning = "REJECT"
                                        scope.launch {
                                            runCatching {
                                                persistCanonicalPassengerMutation0582(
                                                    context = context,
                                                    trip = selectedTrip,
                                                    updated = passengerDecisionMutation0582(booking, "REJECT"),
                                                    store = store,
                                                    mutationCoordinator = mutationCoordinator,
                                                    mutationType = "RESERVATION_REJECTED",
                                                )
                                            }.onSuccess {
                                                rejectConfirmOpen = false
                                                rejectReason = ""
                                                onChanged("Solicitação recusada no estado canônico")
                                            }.onFailure { error ->
                                                onChanged("Nada foi alterado: ${error.message ?: error.javaClass.simpleName}")
                                            }
                                            decisionRunning = null
                                        }
                                    }
                                },
                            ) { Text(if (decisionRunning == "REJECT") "Recusando…" else "Recusar") }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = decisionRunning == null,
                                onClick = { rejectConfirmOpen = false },
                            ) { Text("Voltar") }
                        },
                    )
                }
            }
        }

        if (!compactEmbeddedControls0593) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                val phone = passenger.phone
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
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
                        contentDescription = "Abrir WhatsApp do passageiro",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp),
                    )
                }
                TextButton(
                    onClick = { phoneEditRow0671 = passenger },
                    modifier = Modifier.size(36.dp),
                    contentPadding = ADDRESS_ICON_PADDING,
                ) { Text("✎", maxLines = 1) }

                val passengerTarget = externalPassengerTarget(passenger)
                if (passengerTarget != null) {
                    IconButton(
                        onClick = {
                            if (!openExternalPassengerBlaBla(context, passenger)) {
                                Toast.makeText(
                                    context,
                                    "Conta BlaBlaCar deste passageiro não está conectada.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_blablacar_action),
                            contentDescription = "Abrir passageiro no BlaBlaCar",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (passenger.fareMinorUnits != null) {
                            copyPassengerFareValue(context, passenger)
                        } else if (!requestPrivateRefreshBeforeManual0656("FARE_TIMELINE")) {
                            fareEditRow = passenger
                        }
                    },
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("💰", maxLines = 1)
                }

                OutlinedButton(
                    onClick = { quickMessageRow0656 = passenger },
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("💬", maxLines = 1)
                }
            }


            if (pendingApproval) {
                Text(
                    "🟠 Aguardando aprovação",
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        enabled = decisionRunning == null,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val selectedTrip = trip
                            val booking = currentBooking
                            if (selectedTrip == null || booking == null) {
                                onChanged("Não foi possível localizar a viagem/reserva canônica para aprovar.")
                            } else {
                                decisionRunning = "APPROVE"
                                scope.launch {
                                    runCatching {
                                        persistCanonicalPassengerMutation0582(
                                            context = context,
                                            trip = selectedTrip,
                                            updated = passengerDecisionMutation0582(booking, "APPROVE"),
                                            store = store,
                                            mutationCoordinator = mutationCoordinator,
                                            mutationType = "RESERVATION_APPROVED",
                                        )
                                    }.onSuccess {
                                        UnifiedDebugEventStore.record(
                                            "TIMELINE_CANONICAL_BOOKING_DECISION_0494",
                                            context.packageName,
                                            "action=APPROVE authority=LOCAL_CANONICAL_OUTBOX localBusinessWrite=true directHttp=false",
                                        )
                                        onChanged("Reserva aprovada no estado canônico ✅")
                                    }.onFailure { error ->
                                        onChanged("Nada foi alterado: ${error.message ?: error.javaClass.simpleName}")
                                    }
                                    decisionRunning = null
                                }
                            }
                        },
                    ) { Text(if (decisionRunning == "APPROVE") "Aprovando…" else "Aprovar") }
                    OutlinedButton(
                        enabled = decisionRunning == null,
                        modifier = Modifier.weight(1f),
                        onClick = { rejectConfirmOpen = true },
                    ) { Text("Recusar") }
                }
            }

            if (rejectConfirmOpen) {
                AlertDialog(
                    onDismissRequest = { if (decisionRunning == null) rejectConfirmOpen = false },
                    title = { Text("Recusar solicitação?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("A mesma reserva será marcada como Recusada e as vagas deste trecho serão liberadas.")
                            OutlinedTextField(
                                value = rejectReason,
                                onValueChange = { rejectReason = it.take(240) },
                                label = { Text("Motivo opcional") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = decisionRunning == null,
                            onClick = {
                                val selectedTrip = trip
                                val booking = currentBooking
                                if (selectedTrip == null || booking == null) {
                                    rejectConfirmOpen = false
                                    onChanged("Não foi possível localizar a viagem/reserva canônica para recusar.")
                                } else {
                                    decisionRunning = "REJECT"
                                    scope.launch {
                                        runCatching {
                                            persistCanonicalPassengerMutation0582(
                                                context = context,
                                                trip = selectedTrip,
                                                updated = passengerDecisionMutation0582(booking, "REJECT"),
                                                store = store,
                                                mutationCoordinator = mutationCoordinator,
                                                mutationType = "RESERVATION_REJECTED",
                                            )
                                        }.onSuccess {
                                            UnifiedDebugEventStore.record(
                                                "TIMELINE_CANONICAL_BOOKING_DECISION_0494",
                                                context.packageName,
                                                "action=REJECT authority=LOCAL_CANONICAL_OUTBOX localBusinessWrite=true directHttp=false reasonProvided=${rejectReason.isNotBlank()}",
                                            )
                                            rejectConfirmOpen = false
                                            rejectReason = ""
                                            onChanged("Solicitação recusada no estado canônico")
                                        }.onFailure { error ->
                                            onChanged("Nada foi alterado: ${error.message ?: error.javaClass.simpleName}")
                                        }
                                        decisionRunning = null
                                    }
                                }
                            },
                        ) { Text(if (decisionRunning == "REJECT") "Recusando…" else "Recusar") }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = decisionRunning == null,
                            onClick = { rejectConfirmOpen = false },
                        ) { Text("Voltar") }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(
                    onClick = {
                        historyRow = passenger.copy(passengerId = rowProfile?.id ?: passenger.passengerId)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = COMPACT_NAME_PADDING,
                ) {
                    Text(
                        (if (rowProfile?.blocked == true) "🚫 " else "") + passenger.name.ifBlank { "Passageiro" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (pendingApproval || rejected || currentBooking?.status == BookingStatus.CANCELLED) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = COMPACT_ACTION_PADDING,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text(statusLabel, maxLines = 1)
                    }
                } else {
                    Column {
                        OutlinedButton(
                            onClick = { statusMenuOpen = true },
                            contentPadding = COMPACT_ACTION_PADDING,
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Text(statusLabel + " ▼", maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = statusMenuOpen,
                            onDismissRequest = { statusMenuOpen = false },
                        ) {
                            DropdownMenuItem(text = { Text("Pendente") }, onClick = { selectOperationalStatus("PENDING") })
                                DropdownMenuItem(text = { Text("Confirmado") }, onClick = { selectOperationalStatus("CONFIRMED") })
                            DropdownMenuItem(text = { Text("No local") }, onClick = { selectOperationalStatus("AT_LOCATION") })
                            DropdownMenuItem(text = { Text("No carro") }, onClick = { selectOperationalStatus("IN_CAR") })
                            DropdownMenuItem(text = { Text("Pago") }, onClick = { selectOperationalStatus("PAID") })
                            DropdownMenuItem(text = { Text("Concluído") }, onClick = { selectOperationalStatus("COMPLETED") })
                        }
                    }
                    if (!completed && passenger.operationalStatus != PassengerOperationalStatus.CANCELLED) {
                        TextButton(
                            onClick = {
                                if (currentBooking != null ||
                                    (BookingSource.BLABLACAR in passenger.sources && !passenger.externalReservationKey.isNullOrBlank())
                                ) {
                                    cancelManualRow = passenger
                                } else {
                                    onChanged("Não foi possível identificar a reserva/ocorrência exata para cancelar com segurança.")
                                }
                            },
                        ) { Text("Cancelar") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val pickupTarget = passengerPickupMapTarget(passenger)
                TextButton(
                    onClick = { openPassengerPickupShortcut0731(passenger) },
                    enabled = pickupTarget != null,
                    modifier = Modifier.size(36.dp),
                    contentPadding = ADDRESS_ICON_PADDING,
                ) {
                    Text("📍", maxLines = 1)
                }
                TextButton(
                    onClick = { openPassengerPickupShortcut0731(passenger) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ADDRESS_PLACE_PADDING,
                ) {
                    Text(
                        passengerOperationalAddressLabel0656(passenger, boarding = true),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("→")
                val dropoffTarget = passengerDropoffMapTarget(passenger)
                TextButton(
                    onClick = { openPassengerDropoffShortcut0731(passenger) },
                    enabled = dropoffTarget != null,
                    modifier = Modifier.size(36.dp),
                    contentPadding = ADDRESS_ICON_PADDING,
                ) {
                    Text("🏁", maxLines = 1)
                }
                TextButton(
                    onClick = { openPassengerDropoffShortcut0731(passenger) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ADDRESS_PLACE_PADDING,
                ) {
                    Text(
                        passengerOperationalAddressLabel0656(passenger, boarding = false),
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
            val canonicalProfile = rowProfile
            val persistentHistory = canonicalProfile?.id?.let(renderSnapshot.historiesByProfileId::get)
            val identityLabel = when {
                canonicalProfile?.blocked == true -> "⛔ NÃO ACEITO NO MEU CARRO • ${persistentHistory?.totalRides ?: 0} concluída(s) • ${persistentHistory?.totalOccurrences ?: 0} ocorrência(s)"
                persistentHistory != null -> "${persistentHistory.totalRides} concluída(s) • ${persistentHistory.totalOccurrences} ocorrência(s)/reserva(s)"
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
    }


    if (segmentLoads0671.isNotEmpty() && trip != null &&
        lastRenderedSegmentIndex0671 < segmentLoads0671.lastIndex
    ) {
        for (segmentIndex0671 in (lastRenderedSegmentIndex0671 + 1)..segmentLoads0671.lastIndex) {
            SegmentVacancyLine0671(
                            entry = entry,
                            trip = trip,
                            load0671 = segmentLoads0671[segmentIndex0671],
                            segmentIndex0671 = segmentIndex0671,
                            rows0671 = rows,
                            onPassengerClick0672 = { communicationShortcutRow0672 = it },
                            onTracking0674 = { sharePassengerTrackingShortcut0674(it) },
                            isTrackingActive0676 = { passengerTrackingActive0676(it) },
                            onTrackingStop0676 = { stopPassengerTracking0676(it) },
                        )
        }
    } else if (embedChronologicalStops0667 && chronologicalStops0667.isNotEmpty() &&
        lastRenderedStopIndex0667 < chronologicalStops0667.lastIndex
    ) {
        for (stopIndex0667 in (lastRenderedStopIndex0667 + 1)..chronologicalStops0667.lastIndex) {
            ChronologicalTripStopLine0667(
                item0667 = chronologicalStops0667[stopIndex0667],
                isLast0667 = stopIndex0667 == chronologicalStops0667.lastIndex,
            )
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
                        booking.source in setOf(BookingSource.ROTA_CERTA, BookingSource.PRIVATE, BookingSource.OTHER) &&
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
                    history?.let { Text("${it.totalRides} viagem(ns) concluída(s)", style = MaterialTheme.typography.bodySmall) }
                    if (profile?.blocked == true) Text("⛔ NÃO ACEITO NO MEU CARRO", color = MaterialTheme.colorScheme.error)
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
                    blockProfile = profile
                    profileRow = null
                }) { Text(if (profile?.blocked == true) "✅" else "🚫") }
            },
            dismissButton = { TextButton(onClick = { profileRow = null }) { Text("Fechar") } },
        )
    }

    blockProfile?.let { profile ->
        val blocking = !profile.blocked
        AlertDialog(
            onDismissRequest = { blockProfile = null },
            title = { Text(if (blocking) "Não aceito no meu carro?" else "Remover bloqueio?") },
            text = {
                Text(
                    if (blocking) {
                        "O bloqueio fica ligado ao passengerId/identidade forte, nega a Agenda de Viagens e cancela reservas Rota Certa ativas, liberando as vagas."
                    } else {
                        "O desbloqueio é explícito. PassengerId, cadastro, histórico e viagens permanecem preservados."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    val saved = passengerStore.setBlocked(
                        profile.id,
                        blocking,
                        if (blocking) "Não aceito no meu carro" else "",
                    ) ?: profile
                    identityRevision++
                    blockProfile = null
                    onChanged(
                        if (blocking) "⛔ Não aceito no meu carro. Bloqueio salvo no passengerId."
                        else "Passageiro desbloqueado explicitamente.",
                    )
                    val settings = store.onlineSettings()
                    val accessContact = saved.agendaAccessContact()
                    if (settings.configured && passengerContactKey(accessContact).isNotBlank()) {
                        scope.launch {
                            runCatching {
                                TripRemoteApi(settings).setPassengerAccessBlocked(
                                    passengerContact = accessContact,
                                    blocked = blocking,
                                    passengerId = saved.id,
                                )
                            }.onSuccess { response ->
                                if (blocking) {
                                    AgendaBackgroundSync0392.enqueueImmediate(context, "passenger_block_changed")
                                }
                                onChanged(
                                    if (blocking) {
                                        "⛔ Bloqueio sincronizado. ${response.cancelledBookings} reserva(s) ativa(s) cancelada(s); vagas recalculadas."
                                    } else {
                                        "Desbloqueio sincronizado. Acesso automático à Agenda de Viagens restaurado."
                                    },
                                )
                            }.onFailure { error ->
                                onChanged(
                                    "Bloqueio local preservado; sincronização online pendente: ${error.message ?: "erro de conexão"}",
                                )
                            }
                        }
                    }
                }) { Text(if (blocking) "Confirmar ⛔" else "Desbloquear") }
            },
            dismissButton = { TextButton(onClick = { blockProfile = null }) { Text("Cancelar") } },
        )
    }

    editManualRow?.let { row ->
        val currentTrip = trip
        val booking = currentTrip?.let { selectedTrip ->
            row.localBookingId?.let { bookingId ->
                renderSnapshot.bookingsById[bookingId]?.takeIf { candidate ->
                    candidate.tripId == selectedTrip.id &&
                        candidate.source in setOf(BookingSource.ROTA_CERTA, BookingSource.PRIVATE, BookingSource.OTHER) &&
                        candidate.capacityClaimType == CapacityClaimType.PASSENGER &&
                        candidate.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)
                }
            }
        }
        if (currentTrip != null && booking != null) {
            ManualPassengerOccupancyEditorDialog(
                trip = currentTrip,
                booking = booking,
                existingBookings = renderSnapshot.bookingsById.values.filter { it.tripId == currentTrip.id },
                onDismiss = { editManualRow = null },
                onSave = { updated ->
                    scope.launch {
                        runCatching {
                            persistCanonicalPassengerMutation0582(
                                context = context,
                                trip = currentTrip,
                                updated = updated,
                                store = store,
                                mutationCoordinator = mutationCoordinator,
                                mutationType = "BOOKING_CHANGED_BY_DRIVER",
                            )
                            UnifiedDebugEventStore.record(
                                "TIMELINE_CANONICAL_BOOKING_EDIT_0494",
                                context.packageName,
                                "canonicalTripId=${seatSyncDiagnosticKey(currentTrip.id)} authority=LOCAL_CANONICAL_OUTBOX localBusinessWrite=true directHttp=false",
                            )
                        }.onSuccess {
                            editManualRow = null
                            onChanged("Passageiro atualizado. Vagas por trecho recalculadas no estado canônico.")
                        }.onFailure { error ->
                            onChanged("Não foi possível persistir a alteração: ${error.message ?: error.javaClass.simpleName}")
                        }
                    }
                },
                onError = onChanged,
            )
        } else {
            editManualRow = null
        }
    }

    cancelManualRow?.let { row ->
        val currentTrip = trip
        val booking = row.localBookingId?.let(renderSnapshot.bookingsById::get)
        AlertDialog(
            onDismissRequest = { cancelManualRow = null },
            title = { Text("Cancelar no Rota Certa e liberar a(s) vaga(s)?") },
            text = {
                Text(
                    if (BookingSource.BLABLACAR in row.sources) {
                        "O backend canônico do Rota Certa marcará esta ocorrência como cancelada e recalculará as vagas. A publicação BlaBlaCar não será cancelada automaticamente."
                    } else {
                        "A reserva será cancelada no backend canônico e as vagas serão recalculadas nos trechos correspondentes."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled =
                        (currentTrip?.remoteId?.isNotBlank() == true && booking != null) ||
                            (BookingSource.BLABLACAR in row.sources && !row.externalReservationKey.isNullOrBlank()),
                    onClick = {
                        val selectedTrip = currentTrip
                        val selectedBooking = booking
                        if (selectedTrip != null && selectedBooking != null) {
                            scope.launch {
                                runCatching {
                                    persistCanonicalPassengerMutation0582(
                                        context = context,
                                        trip = selectedTrip,
                                        updated = passengerOperationalMutation0582(selectedBooking, "CANCELLED"),
                                        store = store,
                                        mutationCoordinator = mutationCoordinator,
                                        mutationType = "BOOKING_CANCELLED_BY_DRIVER",
                                    )
                                }.onSuccess {
                                    cancelManualRow = null
                                    identityRevision++
                                    UnifiedDebugEventStore.record(
                                        "TIMELINE_CANONICAL_BOOKING_CANCEL_0494",
                                        context.packageName,
                                        "authority=LOCAL_CANONICAL_OUTBOX localBusinessWrite=true directHttp=false blablaPlatformChanged=false",
                                    )
                                    onChanged(
                                        if (BookingSource.BLABLACAR in row.sources) {
                                            "Reserva cancelada no Rota Certa e vagas canônicas recalculadas. A BlaBlaCar não foi alterada."
                                        } else {
                                            "Reserva cancelada no estado canônico e vagas recalculadas."
                                        },
                                    )
                                }.onFailure { error ->
                                    onChanged("Nada foi alterado: ${error.message ?: error.javaClass.simpleName}")
                                }
                            }
                        } else if (
                            BookingSource.BLABLACAR in row.sources &&
                            !row.externalReservationKey.isNullOrBlank()
                        ) {
                            val saved0714 = saveExternalPassengerOperationalStatus0714(
                                row = row,
                                selectionRaw = "CANCELLED",
                                passengerStore = passengerStore,
                            )
                            if (saved0714 != null) {
                                cancelManualRow = null
                                identityRevision++
                                UnifiedDebugEventStore.recordAlways(
                                    "PASSENGER_EXTERNAL_STATUS_READBACK_OK_0714",
                                    context.packageName,
                                    "rowKey=" + passengerCancellationHash(passengerTimelineRowKey0394(row)) +
                                        " selection=CANCELLED authority=EXTERNAL_PASSENGER_METADATA",
                                )
                                onChanged("Ocorrência cancelada no Rota Certa. A BlaBlaCar não foi alterada.")
                            } else {
                                onChanged("Nada foi alterado: não foi possível confirmar o cancelamento.")
                            }
                        } else {
                            cancelManualRow = null
                            onChanged("Reserva canônica não localizada. Nada foi alterado.")
                        }
                    },
                ) { Text("Cancelar reserva") }
            },
            dismissButton = { TextButton(onClick = { cancelManualRow = null }) { Text("Voltar") } },
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
                    val canonicalBooking0494 = row.localBookingId?.let(renderSnapshot.bookingsById::get)
                    val canonicalTrip0494 = trip
                    if (canonicalBooking0494 != null && canonicalTrip0494 != null) {
                        scope.launch {
                            runCatching {
                                val updated0494 = canonicalBooking0494.copy(
                                    passengerId = profile.id,
                                    localMetadataTouched = true,
                                )
                                persistCanonicalPassengerMutation0582(
                                    context = context,
                                    trip = canonicalTrip0494,
                                    updated = updated0494,
                                    store = store,
                                    mutationCoordinator = mutationCoordinator,
                                    mutationType = "PASSENGER_ID_LINKED",
                                )
                            }.onSuccess {
                                row.externalPassengerId?.let { externalId ->
                                    passengerStore.linkExternalPassengerId(profile.id, externalId)
                                }
                                UnifiedDebugEventStore.record(
                                    "TIMELINE_CANONICAL_PASSENGER_ID_LINK_0494",
                                    context.packageName,
                                    "canonicalTripId=${seatSyncDiagnosticKey(canonicalTrip0494.id)} passengerIdHash=${passengerDebugIdentityHash(profile.id)} authority=LOCAL_CANONICAL_OUTBOX directHttp=false",
                                )
                                onChanged("Cadastro do passageiro vinculado ao booking canônico.")
                            }.onFailure { error ->
                                onChanged("Vínculo não aplicado: ${error.message ?: error.javaClass.simpleName}")
                            }
                        }
                    } else if (linkPassengerProfileLegacyMetadata0494(row, profile.id, passengerStore)) {
                        row.externalPassengerId?.let { externalId ->
                            passengerStore.linkExternalPassengerId(profile.id, externalId)
                        }
                        onChanged("Cadastro vinculado à ocorrência legada.")
                    } else {
                        Toast.makeText(
                            context,
                            "Reserva sem identidade canônica estável; vínculo não aplicado.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    createProfileRow = null
                }) { Text(if (exact != null) "Vincular cadastro" else "Criar cadastro") }
            },
            dismissButton = { TextButton(onClick = { createProfileRow = null }) { Text("Cancelar") } },
        )
    }

    statusShortcutRow0673?.let { row0673 ->
        val currentStatus0673 = passengerOperationalStatusLabel0673(
            row0673.operationalStatus,
            row0673.lastDriverSelection,
        )
        AlertDialog(
            onDismissRequest = { statusShortcutRow0673 = null },
            title = { Text("Alterar status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row0673.name.ifBlank { "Passageiro" })
                    Text("Atual: $currentStatus0673", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { applyPassengerOperationalStatus0673(row0673, "PENDING") },
                    ) { Text("Pendente") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { applyPassengerOperationalStatus0673(row0673, "CONFIRMED") },
                    ) { Text("Confirmado") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { applyPassengerOperationalStatus0673(row0673, "AT_LOCATION") },
                    ) { Text("No local") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { applyPassengerOperationalStatus0673(row0673, "IN_CAR") },
                    ) { Text("No carro") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { applyPassengerOperationalStatus0673(row0673, "PAID") },
                    ) { Text("Pago") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { applyPassengerOperationalStatus0673(row0673, "COMPLETED") },
                    ) { Text("Concluído") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { applyPassengerOperationalStatus0673(row0673, "CANCELLED") },
                    ) { Text("Cancelar") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { statusShortcutRow0673 = null }) { Text("Fechar") }
            },
        )
    }

    communicationShortcutRow0672?.let { row0672 ->
        val phone0672 = row0672.phone
        val profile0672 = renderSnapshot.profilesByRowKey[passengerTimelineRowKey0394(row0672)]
        val pickup0673 = passengerPickupMapTarget(row0672)
        val dropoff0673 = passengerDropoffMapTarget(row0672)
        val boardingTime0673 = trip?.let { passengerBoardingTimeMillis0672(entry, it, row0672) }
        val timeLabel0673 = boardingTime0673?.let {
            tripChronologicalStopTimeLabel0667(it, passengerScheduleZone0672(trip))
        } ?: "não disponível"
        val statusLabel0673 = passengerOperationalStatusLabel0673(
            row0672.operationalStatus,
            row0672.lastDriverSelection,
        )
        AlertDialog(
            onDismissRequest = { communicationShortcutRow0672 = null },
            title = { Text(row0672.name.ifBlank { "Passageiro" }) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("Status: $statusLabel0673 • embarque previsto: $timeLabel0673", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            statusShortcutRow0673 = row0672
                        },
                    ) { Text("👆 Alterar status") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            if (phone0672.isNullOrBlank()) phoneEditRow0671 = row0672
                            else openPassengerWhatsApp(context, phone0672)
                        },
                    ) { Text("WhatsApp") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            quickMessageRow0656 = row0672
                        },
                    ) { Text("💬 Mensagens prontas") }

                    Text(
                        "Embarque: " + passengerOperationalAddressLabel0656(row0672, boarding = true),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            if (pickup0673 != null) openPassengerPickupMap(context, pickup0673)
                            else onChanged("Local de embarque indisponível para navegação.")
                        },
                    ) { Text("📍 GPS embarque") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            boardingAddressEditRow = row0672
                        },
                    ) { Text("Editar endereço de embarque") }

                    Text(
                        "Desembarque: " + passengerOperationalAddressLabel0656(row0672, boarding = false),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            if (dropoff0673 != null) openPassengerDropoffMap(context, dropoff0673)
                            else onChanged("Local de desembarque indisponível para navegação.")
                        },
                    ) { Text("🏁 GPS desembarque") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            dropoffAddressEditRow = row0672
                        },
                    ) { Text("Editar endereço de desembarque") }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            phoneEditRow0671 = row0672
                        },
                    ) { Text("Editar telefone") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            if (row0672.fareMinorUnits != null) copyPassengerFareValue(context, row0672)
                            else if (!requestPrivateRefreshBeforeManual0656("FARE_CENTER_0673")) fareEditRow = row0672
                        },
                    ) { Text("💰 Valor da reserva") }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = trip != null && row0672.dropoffLatitude != null && row0672.dropoffLongitude != null,
                        onClick = {
                            val selectedTrip0673 = trip ?: return@OutlinedButton
                            val lat0673 = row0672.dropoffLatitude ?: return@OutlinedButton
                            val lon0673 = row0672.dropoffLongitude ?: return@OutlinedButton
                            communicationShortcutRow0672 = null
                            requestPassengerTracking0668(
                                PassengerTrackingLinkRequest0668(
                                    tripId = selectedTrip0673.id,
                                    passengerKey = passengerTimelineRowKey0394(row0672),
                                    passengerName = row0672.name.ifBlank { "Passageiro" },
                                    passengerPhone = row0672.phone.orEmpty(),
                                    destinationLatitude = lat0673,
                                    destinationLongitude = lon0673,
                                    destinationLabel = row0672.dropoffAddress.ifBlank { row0672.dropoff.orEmpty() },
                                    expiresAtMillis = passengerTrackingExpiry0668(entry.arrivalAtMillis),
                                ),
                            )
                        },
                    ) { Text("🛰️ Compartilhar acompanhamento") }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            communicationShortcutRow0672 = null
                            historyRow = row0672.copy(passengerId = profile0672?.id ?: row0672.passengerId)
                        },
                    ) { Text("Histórico") }
                    if (externalPassengerTarget(row0672) != null) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                communicationShortcutRow0672 = null
                                if (!openExternalPassengerBlaBla(context, row0672)) {
                                    Toast.makeText(
                                        context,
                                        "Conta BlaBlaCar deste passageiro não está conectada.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                        ) { Text("Abrir no BlaBlaCar") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { communicationShortcutRow0672 = null }) { Text("Fechar") }
            },
        )
    }

    quickMessageRow0656?.let { row ->
        AlertDialog(
            onDismissRequest = { quickMessageRow0656 = null },
            title = { Text("Mensagem para " + row.name.ifBlank { "passageiro" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    passengerQuickMessageChoices0656.forEach { choice ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (choice.type == PassengerQuickMessageType0656.FARE && row.fareMinorUnits == null) {
                                    quickMessageRow0656 = null
                                    if (!requestPrivateRefreshBeforeManual0656("FARE_MESSAGE")) {
                                        fareEditRow = row
                                    }
                                } else {
                                    val message0656 = passengerQuickMessageText0656(
                                        entry = entry,
                                        trip = trip,
                                        row = row,
                                        type = choice.type,
                                        localeTag = PassengerMoney.spec(context).localeTag,
                                        vehicleMakeModel = messageVehicle0714.makeModel,
                                        vehicleColor = messageVehicle0714.color,
                                        vehiclePlate = messageVehicle0714.plate,
                                    )
                                    quickMessageRow0656 = null
                                    deliverPassengerQuickMessage0656(
                                        context = context,
                                        row = row,
                                        type = choice.type,
                                        message = message0656,
                                    )
                                }
                            },
                        ) { Text(choice.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { quickMessageRow0656 = null }) { Text("Fechar") }
            },
        )
    }

    phoneEditRow0671?.let { row0671 ->
        PassengerPhoneEditorDialog0671(
            row = row0671,
            onDismiss = { phoneEditRow0671 = null },
            onSave = { phone0671 ->
                phoneEditRow0671 = null
                if (savePassengerPhoneOverride0671(row0671, phone0671, passengerStore)) {
                    identityRevision++
                    UnifiedDebugEventStore.record(
                        "PASSENGER_PHONE_EDITED_0671",
                        context.packageName,
                        "bookingPresent=${!row0671.localBookingId.isNullOrBlank()} reservationPresent=${!row0671.externalReservationKey.isNullOrBlank()} phoneValueLogged=false",
                    )
                    onChanged("Telefone do passageiro salvo. O WhatsApp já usa o número atualizado.")
                } else {
                    Toast.makeText(context, "Passageiro sem identidade estável; telefone não foi alterado.", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    fareEditRow?.let { row ->
        PassengerFareEditorDialog(
            row = row,
            onDismiss = { fareEditRow = null },
            onSave = { amount, currency ->
                val canonicalBooking0513 = row.localBookingId?.let(renderSnapshot.bookingsById::get)
                val canonicalTrip0513 = trip?.takeIf { entry.canonicalBackendAuthoritative0494 }
                fareEditRow = null
                if (canonicalBooking0513 != null && canonicalTrip0513 != null) {
                    scope.launch {
                        val updated0513 = canonicalBooking0513.copy(
                            fareMinorUnits = amount,
                            fareCurrencyCode = currency,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                        runCatching {
                            persistCanonicalPassengerPrivateMetadata0513(
                                context = context,
                                trip = canonicalTrip0513,
                                previous = canonicalBooking0513,
                                updated = updated0513,
                                store = store,
                                mutationCoordinator = mutationCoordinator,
                            )
                        }.onSuccess {
                            copyPassengerFareValue(
                                context,
                                row.copy(fareMinorUnits = amount, fareCurrencyCode = currency),
                            )
                            onChanged("Valor salvo no estado canônico e copiado.")
                        }.onFailure { error ->
                            onChanged("Valor não salvo: " + (error.message ?: error.javaClass.simpleName))
                        }
                    }
                } else if (savePassengerFareLegacy0494(row, amount, currency, passengerStore)) {
                    copyPassengerFareValue(context, row.copy(fareMinorUnits = amount, fareCurrencyCode = currency))
                    onChanged("Valor salvo na ocorrência legada e copiado.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; valor não foi salvo.", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    boardingAddressEditRow?.let { row ->
        PassengerAddressEditorDialog(
            title = "Endereço completo de embarque",
            initialValue = passengerAddressEditorInitialValue(row.boardingAddress, row.boarding),
            saving0714 = boardingAddressSaving0714,
            onDismiss = { if (!boardingAddressSaving0714) boardingAddressEditRow = null },
            onSave = { address ->
                val canonicalBooking0513 = row.localBookingId?.let(renderSnapshot.bookingsById::get)
                val canonicalTrip0513 = trip?.takeIf { entry.canonicalBackendAuthoritative0494 }
                UnifiedDebugEventStore.recordAlways(
                    "PASSENGER_ADDRESS_SAVE_REQUESTED_0714",
                    context.packageName,
                    "rowKey=" + passengerCancellationHash(passengerTimelineRowKey0394(row)) +
                        " kind=BOARDING coordinatesInvalidated=true privateValueLogged=false",
                )
                if (canonicalBooking0513 != null && canonicalTrip0513 != null) {
                    boardingAddressSaving0714 = true
                    scope.launch {
                        val updated0513 = canonicalBooking0513.copy(
                            boardingAddress = address,
                            boardingLatitude = null,
                            boardingLongitude = null,
                            localMetadataTouched = true,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                        runCatching {
                            persistCanonicalPassengerPrivateMetadata0513(
                                context = context,
                                trip = canonicalTrip0513,
                                previous = canonicalBooking0513,
                                updated = updated0513,
                                store = store,
                                mutationCoordinator = mutationCoordinator,
                            )
                        }.onSuccess { saved0513 ->
                            val readback0513 = store.bookingsFor(canonicalTrip0513.id)
                                .firstOrNull { it.id == saved0513.id }
                            val verified0714 =
                                saved0513.boardingAddress.trim() == address.trim() &&
                                    saved0513.boardingLatitude == null &&
                                    saved0513.boardingLongitude == null &&
                                    readback0513 != null &&
                                    readback0513.boardingAddress.trim() == address.trim() &&
                                    readback0513.boardingLatitude == null &&
                                    readback0513.boardingLongitude == null
                            if (verified0714) {
                                identityRevision++
                                boardingAddressEditRow = null
                                UnifiedDebugEventStore.recordAlways(
                                    "PASSENGER_ADDRESS_READBACK_OK_0714",
                                    context.packageName,
                                    "rowKey=" + passengerCancellationHash(saved0513.id) +
                                        " kind=BOARDING coordinatesInvalidated=true privateValueLogged=false",
                                )
                                onChanged("Endereço de embarque salvo.")
                            } else {
                                UnifiedDebugEventStore.recordAlways(
                                    "PASSENGER_ADDRESS_READBACK_MISMATCH_0714",
                                    context.packageName,
                                    "rowKey=" + passengerCancellationHash(saved0513.id) +
                                        " kind=BOARDING privateValueLogged=false",
                                )
                                onChanged("Endereço não confirmado após salvar. Nada será considerado concluído.")
                            }
                        }.onFailure { error ->
                            onChanged("Endereço de embarque não salvo: " + (error.message ?: error.javaClass.simpleName))
                        }
                        boardingAddressSaving0714 = false
                    }
                } else if (savePassengerAddressLegacy0494(row, address, true, passengerStore)) {
                    identityRevision++
                    boardingAddressEditRow = null
                    onChanged("Endereço de embarque salvo.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; endereço não foi salvo.", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    dropoffAddressEditRow?.let { row ->
        PassengerAddressEditorDialog(
            title = "Endereço completo de destino",
            initialValue = passengerAddressEditorInitialValue(row.dropoffAddress, row.dropoff),
            saving0714 = dropoffAddressSaving0714,
            onDismiss = { if (!dropoffAddressSaving0714) dropoffAddressEditRow = null },
            onSave = { address ->
                val canonicalBooking0513 = row.localBookingId?.let(renderSnapshot.bookingsById::get)
                val canonicalTrip0513 = trip?.takeIf { entry.canonicalBackendAuthoritative0494 }
                UnifiedDebugEventStore.recordAlways(
                    "PASSENGER_ADDRESS_SAVE_REQUESTED_0714",
                    context.packageName,
                    "rowKey=" + passengerCancellationHash(passengerTimelineRowKey0394(row)) +
                        " kind=DROPOFF coordinatesInvalidated=true privateValueLogged=false",
                )
                if (canonicalBooking0513 != null && canonicalTrip0513 != null) {
                    dropoffAddressSaving0714 = true
                    scope.launch {
                        val updated0513 = canonicalBooking0513.copy(
                            dropoffAddress = address,
                            dropoffLatitude = null,
                            dropoffLongitude = null,
                            localMetadataTouched = true,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                        runCatching {
                            persistCanonicalPassengerPrivateMetadata0513(
                                context = context,
                                trip = canonicalTrip0513,
                                previous = canonicalBooking0513,
                                updated = updated0513,
                                store = store,
                                mutationCoordinator = mutationCoordinator,
                            )
                        }.onSuccess { saved0513 ->
                            val readback0513 = store.bookingsFor(canonicalTrip0513.id)
                                .firstOrNull { it.id == saved0513.id }
                            val verified0714 =
                                saved0513.dropoffAddress.trim() == address.trim() &&
                                    saved0513.dropoffLatitude == null &&
                                    saved0513.dropoffLongitude == null &&
                                    readback0513 != null &&
                                    readback0513.dropoffAddress.trim() == address.trim() &&
                                    readback0513.dropoffLatitude == null &&
                                    readback0513.dropoffLongitude == null
                            if (verified0714) {
                                identityRevision++
                                dropoffAddressEditRow = null
                                UnifiedDebugEventStore.recordAlways(
                                    "PASSENGER_ADDRESS_READBACK_OK_0714",
                                    context.packageName,
                                    "rowKey=" + passengerCancellationHash(saved0513.id) +
                                        " kind=DROPOFF coordinatesInvalidated=true privateValueLogged=false",
                                )
                                onChanged("Endereço de destino salvo.")
                            } else {
                                UnifiedDebugEventStore.recordAlways(
                                    "PASSENGER_ADDRESS_READBACK_MISMATCH_0714",
                                    context.packageName,
                                    "rowKey=" + passengerCancellationHash(saved0513.id) +
                                        " kind=DROPOFF privateValueLogged=false",
                                )
                                onChanged("Endereço não confirmado após salvar. Nada será considerado concluído.")
                            }
                        }.onFailure { error ->
                            onChanged("Endereço de destino não salvo: " + (error.message ?: error.javaClass.simpleName))
                        }
                        dropoffAddressSaving0714 = false
                    }
                } else if (savePassengerAddressLegacy0494(row, address, false, passengerStore)) {
                    identityRevision++
                    dropoffAddressEditRow = null
                    onChanged("Endereço de destino salvo.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; endereço não foi salvo.", Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

private fun passengerCancellationDebugContext(
    entry: TripTimelineEntry,
    row: EnhancedPassengerCardRow,
    booking: Booking?,
): String = listOf(
    "tripKey=" + passengerCancellationHash(entry.localTripId ?: entry.blablaTripId ?: entry.tripId),
    "bookingKey=" + passengerCancellationHash(booking?.id ?: row.externalReservationKey),
    "passengerKey=" + passengerCancellationHash(row.passengerId ?: row.externalPassengerId),
).joinToString(" ")

internal fun passengerCancellationHash(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return "none"
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal fun enhancedPassengerRows(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
    externalMetadataSnapshot0394: Map<String, ExternalPassengerMetadata>? = null,
    localBookingsSnapshot0394: List<Booking>? = null,
): List<EnhancedPassengerCardRow> {
    val legacyPassengers = if (entry.canonicalBackendAuthoritative0494) emptyList() else entry.blablaPassengers
    val rows = legacyPassengers.map { passenger ->
        val metadataKey = externalPassengerReservationKey(entry.blablaProfileUuid, passenger.booking_href)
        val metadata = if (externalMetadataSnapshot0394 != null) {
            externalMetadataSnapshot0394[metadataKey]
        } else {
            passengerStore.externalMetadata(metadataKey)
        }
        val hrefExternalId = stableExternalPassengerId(BlaBlaCollectorUrlModule.passengerIdentityKey(passenger.booking_href))
        val externalId = metadata?.externalPassengerId?.takeIf(String::isNotBlank) ?: hrefExternalId
        val boarding = passengerTimelinePlaceLabel(passenger.name, passenger.boarding)
        val dropoff = passengerTimelinePlaceLabel(passenger.name, passenger.dropoff)
        EnhancedPassengerCardRow(
            name = passenger.name.trim(),
            phone = metadata?.passengerContact?.trim()?.takeIf(String::isNotEmpty)
                ?: passenger.phone?.trim()?.takeIf(String::isNotEmpty),
            seats = passenger.seats.coerceAtLeast(1),
            boarding = boarding,
            dropoff = dropoff,
            sources = setOf(BookingSource.BLABLACAR),
            passengerId = metadata?.passengerId?.takeIf(String::isNotBlank),
            externalReservationKey = metadataKey,
            externalBookingHref = passenger.booking_href?.trim()?.takeIf(String::isNotEmpty),
            externalProfileUuid = entry.blablaProfileUuid?.trim()?.takeIf(String::isNotEmpty),
            operationalStatus = effectivePassengerOperationalStatus0673(
                metadata?.operationalStatus ?: PassengerOperationalStatus.PENDING,
                metadata?.lastDriverSelection.orEmpty(),
            ),
            paymentStatus = metadata?.paymentStatus ?: PassengerPaymentStatus.UNPAID,
            lastDriverSelection = metadata?.lastDriverSelection.orEmpty(),
            fareMinorUnits = metadata?.fareMinorUnits,
            fareCurrencyCode = metadata?.fareCurrencyCode.orEmpty(),
            boardingAddress = metadata?.boardingAddress.orEmpty(),
            externalPassengerId = externalId,
            dropoffAddress = metadata?.dropoffAddress.orEmpty(),
            boardingLatitude = metadata?.boardingLatitude,
            boardingLongitude = metadata?.boardingLongitude,
            dropoffLatitude = metadata?.dropoffLatitude,
            dropoffLongitude = metadata?.dropoffLongitude,
            boardingStopIndex = trip?.let { TripPassengerRouteOrder.stopIndexForLabel(it, boarding) },
            dropoffStopIndex = trip?.let { TripPassengerRouteOrder.stopIndexForLabel(it, dropoff) },
        )
    }.toMutableList()

    if (trip != null) {
        val stops = trip.stops.associateBy(TripStop::id)
        val local = (localBookingsSnapshot0394 ?: store.bookingsFor(trip.id))
            .filter { it.tripId == trip.id }
            .filter {
                it.capacityClaimType == CapacityClaimType.PASSENGER ||
                    it.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY
            }
            .filter { it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD }
            .filter { it.seats > 0 }

        local.forEach { booking ->
            val privateMetadataKey0494 = canonicalBookingPrivateMetadataKey0494(booking.id)
            val privateMetadata0494 = if (externalMetadataSnapshot0394 != null) {
                externalMetadataSnapshot0394[privateMetadataKey0494]
            } else {
                passengerStore.externalMetadata(privateMetadataKey0494)
            }
            val phone = privateMetadata0494?.passengerContact?.trim()?.takeIf(String::isNotEmpty)
                ?: booking.passengerContact.trim().takeIf(String::isNotEmpty)
            val boardingStop = stops[booking.boardingStopId]
            val dropoffStop = stops[booking.dropoffStopId]
            val boarding = boardingStop?.name
            val dropoff = dropoffStop?.name
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
            val dropoffStopIndex = TripPassengerRouteOrder.stopIndexForId(trip, booking.dropoffStopId)
            if (index >= 0) {
                val current = rows[index]
                rows[index] = current.copy(
                    name = current.name.ifBlank { booking.passengerName.trim() },
                    phone = current.phone?.takeIf(String::isNotBlank) ?: phone,
                    seats = maxOf(current.seats, booking.seats),
                    boarding = current.boarding ?: boarding,
                    dropoff = current.dropoff ?: dropoff,
                    sources = current.sources + booking.source,
                    passengerId = if (candidateIndex >= 0) {
                        booking.passengerId.takeIf(String::isNotBlank) ?: current.passengerId
                    } else {
                        // Probable name/route similarity is visual evidence only; never canonical identity.
                        current.passengerId
                    },
                    localBookingId = booking.id,
                    bookingStatus = booking.status,
                    operationalStatus = effectivePassengerOperationalStatus0673(
                        booking.operationalStatus,
                        booking.lastDriverSelection,
                    ),
                    paymentStatus = booking.paymentStatus,
                    lastDriverSelection = booking.lastDriverSelection,
                    fareMinorUnits = booking.fareMinorUnits ?: privateMetadata0494?.fareMinorUnits ?: current.fareMinorUnits,
                    fareCurrencyCode = booking.fareCurrencyCode.takeIf(String::isNotBlank)
                        ?: privateMetadata0494?.fareCurrencyCode?.takeIf(String::isNotBlank)
                        ?: current.fareCurrencyCode,
                    boardingAddress = booking.boardingAddress.takeIf(String::isNotBlank)
                        ?: privateMetadata0494?.boardingAddress?.takeIf(String::isNotBlank)
                        ?: current.boardingAddress,
                    dropoffAddress = booking.dropoffAddress.takeIf(String::isNotBlank)
                        ?: privateMetadata0494?.dropoffAddress?.takeIf(String::isNotBlank)
                        ?: current.dropoffAddress,
                    boardingLatitude = if (booking.localMetadataTouched && booking.boardingAddress.isNotBlank()) {
                        booking.boardingLatitude
                    } else {
                        booking.boardingLatitude
                            ?: privateMetadata0494?.boardingLatitude
                            ?: boardingStop?.latitude
                            ?: current.boardingLatitude
                    },
                    boardingLongitude = if (booking.localMetadataTouched && booking.boardingAddress.isNotBlank()) {
                        booking.boardingLongitude
                    } else {
                        booking.boardingLongitude
                            ?: privateMetadata0494?.boardingLongitude
                            ?: boardingStop?.longitude
                            ?: current.boardingLongitude
                    },
                    dropoffLatitude = if (booking.localMetadataTouched && booking.dropoffAddress.isNotBlank()) {
                        booking.dropoffLatitude
                    } else {
                        booking.dropoffLatitude
                            ?: privateMetadata0494?.dropoffLatitude
                            ?: dropoffStop?.latitude
                            ?: current.dropoffLatitude
                    },
                    dropoffLongitude = if (booking.localMetadataTouched && booking.dropoffAddress.isNotBlank()) {
                        booking.dropoffLongitude
                    } else {
                        booking.dropoffLongitude
                            ?: privateMetadata0494?.dropoffLongitude
                            ?: dropoffStop?.longitude
                            ?: current.dropoffLongitude
                    },
                    boardingStopIndex = stopIndex ?: current.boardingStopIndex,
                    dropoffStopIndex = dropoffStopIndex ?: current.dropoffStopIndex,
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
                    operationalStatus = effectivePassengerOperationalStatus0673(
                        booking.operationalStatus,
                        booking.lastDriverSelection,
                    ),
                    paymentStatus = booking.paymentStatus,
                    lastDriverSelection = booking.lastDriverSelection,
                    fareMinorUnits = booking.fareMinorUnits ?: privateMetadata0494?.fareMinorUnits,
                    fareCurrencyCode = booking.fareCurrencyCode.takeIf(String::isNotBlank)
                        ?: privateMetadata0494?.fareCurrencyCode.orEmpty(),
                    boardingAddress = booking.boardingAddress.takeIf(String::isNotBlank)
                        ?: privateMetadata0494?.boardingAddress.orEmpty(),
                    dropoffAddress = booking.dropoffAddress.takeIf(String::isNotBlank)
                        ?: privateMetadata0494?.dropoffAddress.orEmpty(),
                    boardingLatitude = if (booking.localMetadataTouched && booking.boardingAddress.isNotBlank()) {
                        booking.boardingLatitude
                    } else {
                        booking.boardingLatitude ?: privateMetadata0494?.boardingLatitude ?: boardingStop?.latitude
                    },
                    boardingLongitude = if (booking.localMetadataTouched && booking.boardingAddress.isNotBlank()) {
                        booking.boardingLongitude
                    } else {
                        booking.boardingLongitude ?: privateMetadata0494?.boardingLongitude ?: boardingStop?.longitude
                    },
                    dropoffLatitude = if (booking.localMetadataTouched && booking.dropoffAddress.isNotBlank()) {
                        booking.dropoffLatitude
                    } else {
                        booking.dropoffLatitude ?: privateMetadata0494?.dropoffLatitude ?: dropoffStop?.latitude
                    },
                    dropoffLongitude = if (booking.localMetadataTouched && booking.dropoffAddress.isNotBlank()) {
                        booking.dropoffLongitude
                    } else {
                        booking.dropoffLongitude ?: privateMetadata0494?.dropoffLongitude ?: dropoffStop?.longitude
                    },
                    boardingStopIndex = stopIndex,
                    dropoffStopIndex = dropoffStopIndex,
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
    saving0714: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    val normalized = value.trim()
    AlertDialog(
        onDismissRequest = { if (!saving0714) onDismiss() },
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
                enabled = normalized.isNotBlank() && !saving0714,
                onClick = { onSave(normalized) },
            ) { Text(if (saving0714) "Salvando…" else "Salvar") }
        },
        dismissButton = {
            TextButton(enabled = !saving0714, onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun ManualPassengerOccupancyEditorDialog(
    trip: Trip,
    booking: Booking,
    @Suppress("UNUSED_PARAMETER") existingBookings: List<Booking>,
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
                    if (valid) {
                        "A capacidade deste trecho será validada pelo backend canônico ao salvar."
                    } else {
                        "Selecione embarque e destino."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && seats in 1..999,
                onClick = {
                    runCatching {
                        booking.copy(
                            boardingStopId = fromId,
                            dropoffStopId = toId,
                            seats = seats,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    }.onSuccess(onSave).onFailure { onError(it.message ?: "Não foi possível preparar a alteração.") }
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

internal enum class PassengerMutationTransport0632 {
    PROTECTED_DECISION_APPROVE,
    PROTECTED_DECISION_REJECT,
    PROTECTED_OPERATIONAL,
    PROTECTED_CANCEL,
    PROTECTED_UPDATE,
    DRIVER_UPSERT,
}

internal fun passengerMutationTransport0632(
    booking: Booking,
    mutationTypeRaw: String,
): PassengerMutationTransport0632 {
    val mutationType = mutationTypeRaw.trim().uppercase()
    if (booking.source == BookingSource.ROTA_CERTA) {
        return when {
            mutationType == "RESERVATION_APPROVED" -> PassengerMutationTransport0632.PROTECTED_DECISION_APPROVE
            mutationType == "RESERVATION_REJECTED" -> PassengerMutationTransport0632.PROTECTED_DECISION_REJECT
            mutationType == "BOOKING_CANCELLED_BY_DRIVER" ||
                booking.status == BookingStatus.CANCELLED ||
                booking.operationalStatus == PassengerOperationalStatus.CANCELLED ->
                PassengerMutationTransport0632.PROTECTED_CANCEL
            mutationType.startsWith("PASSENGER_STATUS_") -> PassengerMutationTransport0632.PROTECTED_OPERATIONAL
            else -> PassengerMutationTransport0632.PROTECTED_UPDATE
        }
    }
    return if (
        mutationType.startsWith("PASSENGER_STATUS_") ||
        mutationType == "BOOKING_CANCELLED_BY_DRIVER" ||
        booking.status == BookingStatus.CANCELLED ||
        booking.operationalStatus == PassengerOperationalStatus.CANCELLED
    ) {
        PassengerMutationTransport0632.PROTECTED_OPERATIONAL
    } else {
        PassengerMutationTransport0632.DRIVER_UPSERT
    }
}

internal suspend fun publishPassengerMutationRemote0632(
    api: TripRemoteApi,
    remoteTripId: String,
    updated: Booking,
    mutationType: String,
): DriverBookingUpsertResponse {
    return when (passengerMutationTransport0632(updated, mutationType)) {
        PassengerMutationTransport0632.PROTECTED_DECISION_APPROVE ->
            api.decideDriverBooking(remoteTripId, updated.id, "APPROVE")
        PassengerMutationTransport0632.PROTECTED_DECISION_REJECT ->
            api.decideDriverBooking(remoteTripId, updated.id, "REJECT")
        PassengerMutationTransport0632.PROTECTED_CANCEL ->
            api.cancelProtectedDriverBooking(remoteTripId, updated.id)
        PassengerMutationTransport0632.PROTECTED_OPERATIONAL -> {
            val selection = when {
                mutationType.startsWith("PASSENGER_STATUS_") ->
                    mutationType.removePrefix("PASSENGER_STATUS_").trim().uppercase()
                updated.operationalStatus == PassengerOperationalStatus.CANCELLED ||
                    updated.status == BookingStatus.CANCELLED -> "CANCELLED"
                updated.paymentStatus == PassengerPaymentStatus.PAID -> "PAID"
                else -> updated.operationalStatus.name
            }
            api.updateDriverPassengerOperationalStatus(remoteTripId, updated.id, selection)
        }
        PassengerMutationTransport0632.PROTECTED_UPDATE ->
            api.updateProtectedDriverBooking(remoteTripId, updated)
        PassengerMutationTransport0632.DRIVER_UPSERT ->
            api.upsertDriverBooking(remoteTripId, updated)
    }
}

internal suspend fun persistCanonicalPassengerMutation0582(
    context: Context,
    trip: Trip,
    updated: Booking,
    store: TripStore,
    mutationCoordinator: TripMutationCoordinator0387,
    mutationType: String,
    mutationSource: String = "TIMELINE_PASSENGER_UI",
): Booking {
    require(updated.tripId == trip.id) { "BOOKING_TRIP_ID_MISMATCH" }

    val settings = store.onlineSettings()
    val remoteTripId = trip.remoteId?.trim()?.takeIf(String::isNotEmpty)
    val origin = resolvedTripRecordOrigin(trip)

    if (settings.configured && remoteTripId != null) {
        // 0.1.632: published trip mutations are remote-first. The backend transaction
        // persists the booking + segment loads + public projection in one revision,
        // and rejects overbooking before Android commits the local copy.
        val ack = publishPassengerMutationRemote0632(
            api = TripRemoteApi(settings),
            remoteTripId = remoteTripId,
            updated = updated,
            mutationType = mutationType,
        )
        val saved = store.saveBooking(
            ack.booking.toLocalBooking(
                localTripId = trip.id,
                existingLocal = updated.copy(localMetadataTouched = updated.localMetadataTouched),
            ),
        )
        if (origin == TripRecordOrigin.LOCAL && ack.entityRevision > 0L) {
            mutationCoordinator.recordRemoteAppliedLocal(
                canonicalTripId = trip.id,
                revision = ack.entityRevision,
                mutationType = mutationType,
                source = mutationSource,
                reconcileBookingInventory = false,
            )
        }
        BookingRealtimeEvents0356.notifyChanged()
        UnifiedDebugEventStore.record(
            "TIMELINE_AGENDA_ATOMIC_MUTATION_0632",
            context.packageName,
            "canonicalTripId=" + seatSyncDiagnosticKey(trip.id) +
                " bookingId=" + passengerCancellationHash(saved.id) +
                " mutationType=" + mutationType.take(64) +
                " mutationSource=" + mutationSource.take(64) +
                " transport=" + passengerMutationTransport0632(updated, mutationType).name +
                " entityRevision=" + ack.entityRevision +
                " remoteAck=true projectionAtomic=true localCommittedAfterAck=true blablaPlatformChanged=false",
        )
        return saved
    }

    if (settings.configured && origin == TripRecordOrigin.EXTERNAL_BACKING) {
        // An external backing without a backend trip id cannot safely publish a booking
        // mutation. Fail closed instead of letting Timeline and public Agenda diverge.
        UnifiedDebugEventStore.record(
            "TIMELINE_AGENDA_ATOMIC_MUTATION_BLOCKED_0632",
            context.packageName,
            "canonicalTripId=" + seatSyncDiagnosticKey(trip.id) +
                " bookingId=" + passengerCancellationHash(updated.id) +
                " mutationType=" + mutationType.take(64) +
                " reason=REMOTE_TRIP_ID_MISSING externalBacking=true localWrite=false",
        )
        error("Identidade da viagem pública indisponível. Nada foi alterado para evitar divergência de vagas.")
    }

    val saved = store.saveBooking(
        updated.copy(updatedAtMillis = System.currentTimeMillis()),
    )
    val queued = if (origin == TripRecordOrigin.LOCAL) {
        mutationCoordinator.recordLocalMutation(
            canonicalTripId = trip.id,
            mutationType = mutationType,
            source = mutationSource,
            reconcileBookingInventory = true,
        )
    } else {
        null
    }
    if (queued != null) {
        AgendaBackgroundSync0392.enqueueImmediate(context, "passenger_local_mutation_0632")
    }
    BookingRealtimeEvents0356.notifyChanged()
    UnifiedDebugEventStore.record(
        "TIMELINE_CANONICAL_PASSENGER_LOCAL_FALLBACK_0632",
        context.packageName,
        "canonicalTripId=" + seatSyncDiagnosticKey(trip.id) +
            " bookingId=" + passengerCancellationHash(saved.id) +
            " mutationType=" + mutationType.take(64) +
            " mutationSource=" + mutationSource.take(64) +
            " onlineConfigured=" + settings.configured +
            " outboxQueued=" + (queued != null) +
            " projectionAtomic=false directHttp=false blablaPlatformChanged=false",
    )
    return saved
}

internal fun passengerOperationalSaveMessage0714(selectionRaw: String): String = when (selectionRaw.trim().uppercase()) {
    "PENDING" -> "Status Pendente salvo."
    "CONFIRMED" -> "Passageiro confirmado."
    "AT_LOCATION" -> "Status No local salvo."
    "IN_CAR" -> "Status No carro salvo."
    "PAID" -> "Pagamento confirmado."
    "COMPLETED" -> "Passageiro concluído."
    else -> "Status atualizado."
}

private fun saveExternalPassengerOperationalStatus0714(
    row: EnhancedPassengerCardRow,
    selectionRaw: String,
    passengerStore: PassengerIdentityStore,
): ExternalPassengerMetadata? {
    val selection = selectionRaw.trim().uppercase()
    if (selection !in setOf("PENDING", "CONFIRMED", "AT_LOCATION", "IN_CAR", "PAID", "COMPLETED", "CANCELLED")) return null
    val key = row.externalReservationKey?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(
        reservationKey = key,
        operationalStatus = row.operationalStatus,
        paymentStatus = row.paymentStatus,
        lastDriverSelection = row.lastDriverSelection,
    )
    if (
        current.operationalStatus == PassengerOperationalStatus.COMPLETED &&
        selection !in setOf("COMPLETED", "PAID")
    ) return null
    if (current.operationalStatus == PassengerOperationalStatus.IN_CAR && selection == "CANCELLED") return null
    val operational = when (selection) {
        "PENDING" -> PassengerOperationalStatus.PENDING
        "CONFIRMED" -> PassengerOperationalStatus.CONFIRMED
        "AT_LOCATION" -> PassengerOperationalStatus.AT_LOCATION
        "IN_CAR" -> PassengerOperationalStatus.IN_CAR
        "COMPLETED" -> PassengerOperationalStatus.COMPLETED
        "CANCELLED" -> PassengerOperationalStatus.CANCELLED
        "PAID" -> current.operationalStatus
        else -> current.operationalStatus
    }
    passengerStore.saveExternalMetadata(
        current.copy(
            operationalStatus = operational,
            paymentStatus = if (selection == "PAID") PassengerPaymentStatus.PAID else current.paymentStatus,
            lastDriverSelection = selection,
        ),
    )
    val readback = passengerStore.externalMetadata(key) ?: return null
    val statusMatches = if (selection == "PAID") {
        readback.paymentStatus == PassengerPaymentStatus.PAID &&
            readback.lastDriverSelection == "PAID"
    } else {
        readback.operationalStatus == operational &&
            readback.lastDriverSelection == selection
    }
    return readback.takeIf { statusMatches }
}

internal fun passengerOperationalMutation0582(
    previous: Booking,
    selectionRaw: String,
): Booking {
    val selection = selectionRaw.trim().uppercase()
    require(selection in setOf("PENDING", "CONFIRMED", "AT_LOCATION", "IN_CAR", "PAID", "COMPLETED", "CANCELLED")) {
        "INVALID_OPERATIONAL_SELECTION"
    }
    require(previous.status !in setOf(BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
        "BOOKING_INACTIVE"
    }
    require(previous.status != BookingStatus.REQUESTED) { "RESERVATION_DECISION_REQUIRED" }
    require(previous.status != BookingStatus.REJECTED) { "BOOKING_REJECTED" }
    require(
        previous.operationalStatus != PassengerOperationalStatus.COMPLETED ||
            selection in setOf("COMPLETED", "PAID"),
    ) { "PASSENGER_OPERATIONAL_COMPLETED" }
    require(
        previous.operationalStatus != PassengerOperationalStatus.IN_CAR ||
            selection != "CANCELLED",
    ) { "PASSENGER_IN_CAR_NOT_CANCELABLE" }

    val operational = when (selection) {
        "PAID" -> previous.operationalStatus
        "PENDING" -> PassengerOperationalStatus.PENDING
        "CONFIRMED" -> PassengerOperationalStatus.CONFIRMED
        "AT_LOCATION" -> PassengerOperationalStatus.AT_LOCATION
        "IN_CAR" -> PassengerOperationalStatus.IN_CAR
        "COMPLETED" -> PassengerOperationalStatus.COMPLETED
        "CANCELLED" -> PassengerOperationalStatus.CANCELLED
        else -> previous.operationalStatus
    }
    return previous.copy(
        status = when {
            selection == "CANCELLED" -> BookingStatus.CANCELLED
            selection == "CONFIRMED" && previous.status == BookingStatus.HELD -> BookingStatus.CONFIRMED
            else -> previous.status
        },
        operationalStatus = operational,
        paymentStatus = if (selection == "PAID") PassengerPaymentStatus.PAID else previous.paymentStatus,
        lastDriverSelection = selection,
    )
}

internal fun passengerDecisionMutation0582(
    previous: Booking,
    actionRaw: String,
): Booking {
    val action = actionRaw.trim().uppercase()
    require(action in setOf("APPROVE", "REJECT")) { "INVALID_BOOKING_DECISION" }
    require(previous.status == BookingStatus.REQUESTED) { "BOOKING_ALREADY_RESOLVED" }
    require(previous.source == BookingSource.ROTA_CERTA) { "INVALID_PENDING_CAPACITY_CLAIM" }
    require(previous.capacityClaimType == CapacityClaimType.PASSENGER) { "INVALID_PENDING_CAPACITY_CLAIM" }
    require(!previous.occupancyGroupId.isNullOrBlank()) { "MISSING_PENDING_OCCUPANCY_GROUP" }
    return previous.copy(
        status = if (action == "APPROVE") BookingStatus.CONFIRMED else BookingStatus.REJECTED,
        operationalStatus = if (action == "APPROVE") {
            PassengerOperationalStatus.PENDING
        } else {
            PassengerOperationalStatus.PENDING
        },
        lastDriverSelection = action,
    )
}

private fun canonicalBookingPrivateMetadataKey0494(bookingId: String): String =
    "canonical-booking-private:" + bookingId.trim()

/** Legacy-only cache for rows that genuinely have no canonical booking identity. */
private fun savePassengerAddressLegacy0494(
    row: EnhancedPassengerCardRow,
    addressRaw: String,
    boarding: Boolean,
    passengerStore: PassengerIdentityStore,
): Boolean {
    val address = addressRaw.trim().takeIf(String::isNotEmpty) ?: return false
    val key = row.externalReservationKey ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(
        if (boarding) {
            current.copy(
                boardingAddress = address,
                boardingLatitude = null,
                boardingLongitude = null,
                boardingAccuracyMeters = null,
                boardingLocationSource = "",
                boardingLocationCollectedAtMillis = null,
            )
        } else {
            current.copy(
                dropoffAddress = address,
                dropoffLatitude = null,
                dropoffLongitude = null,
            )
        },
    )
    val readback = passengerStore.externalMetadata(key) ?: return false
    return if (boarding) {
        readback.boardingAddress.trim() == address &&
            readback.boardingLatitude == null &&
            readback.boardingLongitude == null
    } else {
        readback.dropoffAddress.trim() == address &&
            readback.dropoffLatitude == null &&
            readback.dropoffLongitude == null
    }
}

/** Legacy-only fare cache; canonical rows persist through the backend booking mutation. */
private fun savePassengerFareLegacy0494(
    row: EnhancedPassengerCardRow,
    amount: Long,
    currency: String,
    passengerStore: PassengerIdentityStore,
): Boolean {
    val key = row.externalReservationKey ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(current.copy(fareMinorUnits = amount, fareCurrencyCode = currency))
    return true
}

private fun savePassengerPhoneOverride0671(
    row: EnhancedPassengerCardRow,
    phone: String,
    passengerStore: PassengerIdentityStore,
): Boolean {
    val key0671 = row.localBookingId?.trim()?.takeIf(String::isNotEmpty)
        ?.let(::canonicalBookingPrivateMetadataKey0494)
        ?: row.externalReservationKey?.trim()?.takeIf(String::isNotEmpty)
        ?: return false
    val current0671 = passengerStore.externalMetadata(key0671)
        ?: ExternalPassengerMetadata(reservationKey = key0671)
    passengerStore.saveExternalMetadata(current0671.copy(passengerContact = phone))
    return true
}

private suspend fun persistCanonicalPassengerPrivateMetadata0513(
    context: Context,
    trip: Trip,
    previous: Booking,
    updated: Booking,
    store: TripStore,
    mutationCoordinator: TripMutationCoordinator0387,
): Booking {
    val saved = persistCanonicalPassengerMutation0582(
        context = context,
        trip = trip,
        updated = updated.copy(localMetadataTouched = true),
        store = store,
        mutationCoordinator = mutationCoordinator,
        mutationType = "PASSENGER_PRIVATE_METADATA_CHANGED",
    )
    UnifiedDebugEventStore.record(
        "TIMELINE_CANONICAL_PASSENGER_PRIVATE_MUTATION_0513",
        context.packageName,
        "canonicalTripId=" + seatSyncDiagnosticKey(trip.id) +
            " bookingId=" + passengerCancellationHash(previous.id) +
            " fareChanged=" + (previous.fareMinorUnits != updated.fareMinorUnits ||
                previous.fareCurrencyCode != updated.fareCurrencyCode) +
            " boardingAddressChanged=" + (previous.boardingAddress != updated.boardingAddress) +
            " dropoffAddressChanged=" + (previous.dropoffAddress != updated.dropoffAddress) +
            " source=CANONICAL_BACKEND_REMOTE_FIRST privateValuesLogged=false projectionAtomic=true",
    )
    return saved
}

/** Legacy-only metadata link for rows that genuinely have no canonical booking id. */
private fun linkPassengerProfileLegacyMetadata0494(
    row: EnhancedPassengerCardRow,
    passengerId: String,
    passengerStore: PassengerIdentityStore,
): Boolean {
    val key = row.externalReservationKey ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(current.copy(passengerId = passengerId))
    return true
}

internal fun passengerBoardingTimeMillis0672(
    entry: TripTimelineEntry,
    trip: Trip,
    row: EnhancedPassengerCardRow,
): Long? {
    val boardingIndex0672 = row.boardingStopIndex ?: return null
    val stops0672 = tripChronologicalStops0667(
        trip = trip,
        departureAtMillis = entry.departureAtMillis,
        arrivalAtMillis = entry.arrivalAtMillis,
    )
    val boardingStop0672 = stops0672.getOrNull(boardingIndex0672) ?: return null
    return if (boardingIndex0672 == 0) {
        boardingStop0672.timeMillis ?: entry.departureAtMillis
    } else {
        boardingStop0672.timeMillis
    }
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

internal fun passengerPrivateMetadataIncomplete0656(row: EnhancedPassengerCardRow): Boolean {
    if (BookingSource.BLABLACAR !in row.sources) return false
    // Coordinates can navigate, but the operator still requires visible/editable address text.
    return row.phone.isNullOrBlank() ||
        row.fareMinorUnits == null ||
        row.boardingAddress.isBlank() ||
        row.dropoffAddress.isBlank()
}

internal fun passengerOperationalAddressLabel0656(
    row: EnhancedPassengerCardRow,
    boarding: Boolean,
): String {
    val exact = if (boarding) row.boardingAddress.trim() else row.dropoffAddress.trim()
    if (exact.isNotBlank()) return exact
    val collected = if (boarding) row.boarding?.trim().orEmpty() else row.dropoff?.trim().orEmpty()
    return if (collected.isNotBlank()) {
        "$collected • definir endereço"
    } else if (boarding) {
        "Definir local de embarque"
    } else {
        "Definir local de destino"
    }
}

internal fun shouldIncludePassengerVehicle0656(type: PassengerQuickMessageType0656): Boolean = when (type) {
    PassengerQuickMessageType0656.CONFIRM_ONE_HOUR,
    PassengerQuickMessageType0656.AT_LOCATION,
    -> true
    PassengerQuickMessageType0656.CONFIRM_NOW,
    PassengerQuickMessageType0656.CONFIRM_TOMORROW,
    PassengerQuickMessageType0656.FARE,
    -> false
} // CONTEXTUAL_PASSENGER_VEHICLE_0722

internal fun passengerQuickMessageText0656(
    entry: TripTimelineEntry,
    row: EnhancedPassengerCardRow,
    type: PassengerQuickMessageType0656,
    trip: Trip? = null,
    localeTag: String = "pt-BR",
    vehicleMakeModel: String = "",
    vehicleColor: String = "",
    vehiclePlate: String = "",
): String {
    val locale = java.util.Locale.forLanguageTag(localeTag.ifBlank { "pt-BR" })
    val zone0672 = passengerScheduleZone0672(trip)
    val boardingMillis0672 = if (trip != null) {
        passengerBoardingTimeMillis0672(entry, trip, row)
    } else {
        // Compatibility for non-canonical callers only. Operational UI always supplies trip.
        entry.departureAtMillis
    }
    val boarding0672 = boardingMillis0672?.let { millis0672 ->
        java.time.Instant.ofEpochMilli(millis0672).atZone(zone0672)
    }
    val dateTime0672 = boarding0672?.let { date0672 ->
        java.time.format.DateTimeFormatter
            .ofPattern("EEEE, d 'de' MMMM, 'às' HH'h'mm", locale)
            .format(date0672)
            .replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString() }
    }
    val clock0672 = boarding0672?.let { date0672 ->
        java.time.format.DateTimeFormatter.ofPattern("HH'h'mm", locale).format(date0672)
    }
    val name = passengerFirstName0714(row.name)
    val origin = row.boarding?.trim()?.takeIf(String::isNotEmpty) ?: entry.origin.trim()
    val destination = row.dropoff?.trim()?.takeIf(String::isNotEmpty) ?: entry.destination.trim()
    val seatsText = if (row.seats == 1) "1 lugar" else "${row.seats} lugares"
    val vehicleDescriptor0717 = listOf(vehicleMakeModel.trim(), vehicleColor.trim().lowercase(locale))
        .filter(String::isNotBlank)
        .joinToString(" • ")
    val normalizedPlate0717 = vehiclePlate.trim().uppercase(locale).replace(Regex("\\s+"), "")
    val vehicleBlockLines0717 = buildList {
        if (vehicleDescriptor0717.isNotBlank()) add("🚗 Carro: $vehicleDescriptor0717")
        if (normalizedPlate0717.isNotBlank()) add("Placa: $normalizedPlate0717")
    }
    val vehicleBlock = if (
        !shouldIncludePassengerVehicle0656(type) || vehicleBlockLines0717.isEmpty()
    ) {
        ""
    } else {
        vehicleBlockLines0717.joinToString(separator = "\n", prefix = "\n\n")
    }

    return when (type) {
        PassengerQuickMessageType0656.CONFIRM_NOW -> buildString {
            append("Oi, ").append(name).append("! Confirmando nossa viagem de ")
                .append(origin).append(" para ").append(destination)
            if (dateTime0672 != null) {
                append(", ").append(dateTime0672.replaceFirstChar { ch -> ch.toString().lowercase(locale) })
            }
            append(". 👍\n\nEstá tudo certo para você?")
            append(vehicleBlock)
        }
        PassengerQuickMessageType0656.CONFIRM_TOMORROW ->
            "Oi, $name! Confirmando nossa viagem de amanhã" +
                (clock0672?.let { " às $it" } ?: "") +
                ". 👍\n\nPerto do horário envio minha localização em tempo real. 🚗" +
                vehicleBlock
        PassengerQuickMessageType0656.CONFIRM_ONE_HOUR ->
            "Oi, $name! Nossa viagem está prevista para daqui a cerca de 1 hora" +
                (clock0672?.let { ", às $it" } ?: "") +
                ".\n\nEstá tudo certo por aí?" +
                vehicleBlock
        PassengerQuickMessageType0656.AT_LOCATION ->
            "Oi, $name! Já cheguei ao local combinado para o embarque. 📍" +
                vehicleBlock
        PassengerQuickMessageType0656.FARE -> {
            val fare = row.fareMinorUnits?.let {
                passengerTimelineFareClipboardText(it, row.fareCurrencyCode, localeTag)
            } ?: "valor ainda não disponível"
            "Oi, $name! O valor da sua reserva para $seatsText é $fare." +
                vehicleBlock
        }
    }
}

private fun deliverPassengerQuickMessage0656(
    context: Context,
    row: EnhancedPassengerCardRow,
    type: PassengerQuickMessageType0656,
    message: String,
) {
    val digits = row.phone?.let(::passengerWhatsAppDigits0515)
    val opened = digits?.let { phone ->
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/$phone?text=" + Uri.encode(message)),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    } == true
    if (!opened) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Mensagem do passageiro", message))
        Toast.makeText(
            context,
            if (digits == null) "Mensagem copiada; WhatsApp do passageiro não está disponível." else "Mensagem copiada.",
            Toast.LENGTH_SHORT,
        ).show()
    }
    UnifiedDebugEventStore.record(
        "PASSENGER_QUICK_MESSAGE_0656",
        context.packageName,
        "template=${type.name} whatsappOpened=$opened phonePresent=${digits != null} payloadLogged=false",
    )
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
        !entry.blablaPublicHref.isNullOrBlank() ||
        !entry.blablaProfileUuid.isNullOrBlank()

internal const val TIMELINE_MANUAL_PASSENGER_SHORTCUT_0728 = "+👤"
internal const val TIMELINE_PUBLIC_BLABLACAR_SHORTCUT_0728 = "🌐"

@Composable
internal fun TripBlaBlaTripActionRow(
    entry: TripTimelineEntry,
    onAddManualPassenger: (() -> Unit)?,
    leadingActions0549: (@Composable () -> Unit)? = null,
    trailingActions0549: (@Composable () -> Unit)? = null,
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
        leadingActions0549?.invoke()
        if (onAddManualPassenger != null) {
            TextButton(
                modifier = Modifier.semantics { contentDescription = "Adicionar passageiro por fora" },
                onClick = {
                    UnifiedDebugEventStore.record(
                        "AGENDA_CARD_MANUAL_PASSENGER_OPEN",
                        context.packageName,
                        "timeline=true externalPublication=true",
                    )
                    onAddManualPassenger()
                },
                contentPadding = COMPACT_ACTION_PADDING,
            ) { Text(TIMELINE_MANUAL_PASSENGER_SHORTCUT_0728) }
        }
        val canonicalPublicHref0490 = canonicalTimelineBlaBlaPublicHref0490(entry)
        if (canonicalPublicHref0490 != null) {
            TextButton(
                modifier = Modifier.semantics { contentDescription = "Ver anúncio público na BlaBlaCar" },
                onClick = {
                    if (!openPublicTripBlaBla(context, canonicalPublicHref0490)) {
                        Toast.makeText(
                            context,
                            "A URL pública canônica desta viagem ainda não está disponível.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                contentPadding = COMPACT_ACTION_PADDING,
            ) { Text(TIMELINE_PUBLIC_BLABLACAR_SHORTCUT_0728) }
        }
        if (hasExternalTripActionEvidence(entry)) {
            IconButton(
                onClick = {
                    if (!openExternalTripBlaBla(context, entry.blablaProfileUuid, entry.blablaTripHref)) {
                        Toast.makeText(
                            context,
                            "Link direto da viagem indisponível. A referência será recuperada pela atualização automática quando houver evidência suficiente.",
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
        trailingActions0549?.invoke()
        }
    }
}

internal data class PassengerPickupMapTarget(
    val query: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

private fun trustedPassengerCoordinate0513(latitude: Double?, longitude: Double?): Boolean =
    latitude != null && longitude != null &&
        latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0

internal fun passengerPickupMapTarget(row: EnhancedPassengerCardRow): PassengerPickupMapTarget? {
    val exact = row.boardingAddress.trim().takeIf(String::isNotEmpty)
    val selectedStop = row.boarding?.trim()?.takeIf(String::isNotEmpty)
    val trusted = trustedPassengerCoordinate0513(row.boardingLatitude, row.boardingLongitude)
    val query = exact ?: selectedStop ?: if (trusted) "${row.boardingLatitude},${row.boardingLongitude}" else null
        ?: return null
    return PassengerPickupMapTarget(query, row.boardingLatitude.takeIf { trusted }, row.boardingLongitude.takeIf { trusted })
}

internal fun passengerDropoffMapTarget(row: EnhancedPassengerCardRow): PassengerPickupMapTarget? {
    val exact = row.dropoffAddress.trim().takeIf(String::isNotEmpty)
    val selectedStop = row.dropoff?.trim()?.takeIf(String::isNotEmpty)
    val trusted = trustedPassengerCoordinate0513(row.dropoffLatitude, row.dropoffLongitude)
    val query = exact ?: selectedStop ?: if (trusted) "${row.dropoffLatitude},${row.dropoffLongitude}" else null
        ?: return null
    return PassengerPickupMapTarget(query, row.dropoffLatitude.takeIf { trusted }, row.dropoffLongitude.takeIf { trusted })
}

private fun passengerMapUri0513(target: PassengerPickupMapTarget): Uri {
    val latitude = target.latitude
    val longitude = target.longitude
    return if (trustedPassengerCoordinate0513(latitude, longitude)) {
        val coordinateQuery = "$latitude,$longitude (" + target.query + ")"
        Uri.parse("geo:$latitude,$longitude?q=" + Uri.encode(coordinateQuery))
    } else {
        Uri.parse("geo:0,0?q=" + Uri.encode(target.query))
    }
}

internal data class ExternalPassengerTarget(val profileUuid: String, val href: String)

internal fun externalPassengerTarget(row: EnhancedPassengerCardRow): ExternalPassengerTarget? {
    val profileUuid = row.externalProfileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    val href = row.externalBookingHref?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!href.startsWith("https://www.blablacar.com.br/") || !href.contains("/rides/offer/passenger/")) return null
    return ExternalPassengerTarget(profileUuid = profileUuid, href = href)
}

internal fun openPublicTripBlaBla(context: Context, canonicalHref: String?): Boolean {
    val target = canonicalHref?.trim()?.takeIf(String::isNotBlank) ?: return false
    UnifiedDebugEventStore.record(
        "BLABLACAR_PUBLIC_TRIP_OPEN_EXPLICIT",
        context.packageName,
        "timeline=true canonical_public_href=true",
    )
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
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

private fun passengerNavigationChooser0731(
    target: PassengerPickupMapTarget,
    title: String,
): Intent {
    val navigationIntent0731 = Intent(Intent.ACTION_VIEW, passengerMapUri0513(target))
    return Intent.createChooser(navigationIntent0731, title)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun openPassengerPickupMap(context: Context, target: PassengerPickupMapTarget) {
    UnifiedDebugEventStore.record(
        "PASSENGER_PICKUP_MAP_OPEN",
        context.packageName,
        "timeline=true navigationChooser=true forcedPackage=false coordinate=" +
            trustedPassengerCoordinate0513(target.latitude, target.longitude),
    )
    runCatching {
        context.startActivity(passengerNavigationChooser0731(target, "Escolher app de navegação"))
    }.onFailure {
        Toast.makeText(context, "Não foi possível abrir o local de embarque.", Toast.LENGTH_LONG).show()
    }
}

private fun openPassengerDropoffMap(context: Context, target: PassengerPickupMapTarget) {
    UnifiedDebugEventStore.record(
        "PASSENGER_DROPOFF_MAP_OPEN",
        context.packageName,
        "timeline=true navigationChooser=true forcedPackage=false coordinate=" +
            trustedPassengerCoordinate0513(target.latitude, target.longitude),
    )
    runCatching {
        context.startActivity(passengerNavigationChooser0731(target, "Escolher app de navegação"))
    }.onFailure {
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


internal fun passengerPhoneForStorage0671(raw: String): String? =
    passengerWhatsAppDigits0515(raw)?.let { "+$it" }

@Composable
private fun PassengerPhoneEditorDialog0671(
    row: EnhancedPassengerCardRow,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value0671 by remember(passengerTimelineRowKey0394(row)) { mutableStateOf(row.phone.orEmpty()) }
    val normalized0671 = passengerPhoneForStorage0671(value0671)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar telefone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.name.ifBlank { "Passageiro" })
                OutlinedTextField(
                    value = value0671,
                    onValueChange = { value0671 = it.take(24) },
                    label = { Text("WhatsApp / telefone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value0671.isNotBlank() && normalized0671 == null) {
                    Text("Informe um número válido com DDD.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = normalized0671 != null, onClick = { normalized0671?.let(onSave) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

internal fun openPassengerTrackingWhatsApp0676(
    context: Context,
    rawPhone0676: String,
    message0676: String,
    url0676: String,
): Boolean {
    val digits0676 = passengerWhatsAppDigits0515(rawPhone0676) ?: return false
    val payload0676 = passengerTrackingPayload0714(message0676, url0676)
    return runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$digits0676?text=" + Uri.encode(payload0676)),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}

internal fun passengerWhatsAppDigits0515(raw: String): String? {
    val digits = raw.filter(Char::isDigit)
    if (digits.length !in 8..15) return null
    val normalized = if (!digits.startsWith("55") && digits.length in 10..11) "55$digits" else digits
    return normalized.takeIf { it.length in 8..15 }
}

internal fun openPassengerWhatsApp(context: Context, raw: String) {
    val digits = passengerWhatsAppDigits0515(raw) ?: return
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
