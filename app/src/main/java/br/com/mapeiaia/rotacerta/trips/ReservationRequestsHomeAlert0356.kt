package br.com.mapeiaia.rotacerta.trips

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReservationRequestsHomeAlert0356() {
    val context = LocalContext.current
    val store = remember(context) { TripStore(context) }
    var pending by remember { mutableStateOf(store.bookings().filter { it.status == BookingStatus.REQUESTED }) }

    fun refreshLocal() {
        pending = store.bookings()
            .filter { it.status == BookingStatus.REQUESTED }
            .sortedBy(Booking::createdAtMillis)
    }

    LaunchedEffect(Unit) {
        runCatching {
            BookingPushRegistration0304.ensureRegistered(context, store)
            PublicBookingRemoteSync0296.pullAndReconcile(context, store)
        }
        refreshLocal()
        BookingRealtimeEvents0356.changes.collect { refreshLocal() }
    }

    if (pending.isEmpty()) return

    val warning = Color(0xFFFF9800)
    val first = pending.first()
    val trip = store.getTrip(first.tripId)
    val stops = trip?.stops.orEmpty().associateBy(TripStop::id)
    val from = stops[first.boardingStopId]?.name.orEmpty()
    val to = stops[first.dropoffStopId]?.name.orEmpty()
    val whenText = trip?.departureAtMillis?.let { millis ->
        DateTimeFormatter.ofPattern("EEE, dd/MM • HH:mm", Locale.getDefault())
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }.orEmpty()
    val title = if (pending.size == 1) "Solicitação de reserva · 1" else "Solicitações de reserva · ${pending.size}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable {
                val intent = Intent(context, TripsActivity::class.java)
                    .setAction(TripActions.ACTION_OPEN_RESERVATION_REQUESTS)
                if (pending.size == 1) {
                    intent.putExtra(TripActions.EXTRA_TRIP_ID, first.tripId)
                    intent.putExtra(TripActions.EXTRA_BOOKING_ID, first.id)
                } else {
                    intent.putExtra(TripActions.EXTRA_PENDING_ONLY, true)
                }
                context.startActivity(intent)
            },
        border = BorderStroke(2.dp, warning),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = warning, style = MaterialTheme.typography.titleMedium)
            Text("🟠 ${first.passengerName.ifBlank { "Passageiro" }} solicitou ${first.seats} lugar(es)")
            if (from.isNotBlank() || to.isNotBlank()) {
                Text("${from.ifBlank { "Embarque" }} → ${to.ifBlank { "Destino" }}", style = MaterialTheme.typography.bodySmall)
            }
            if (whenText.isNotBlank()) Text(whenText, style = MaterialTheme.typography.bodySmall)
            Text(
                if (pending.size == 1) "Toque para abrir esta solicitação."
                else "Toque para ver somente as solicitações aguardando aprovação.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
