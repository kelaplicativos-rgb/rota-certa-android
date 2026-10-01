package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TripIcs {
    private val utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun build(trip: Trip, booking: Booking? = null): String {
        val ordered = trip.stops.sortedBy(TripStop::order)
        val from = booking?.let { b -> ordered.firstOrNull { it.id == b.boardingStopId } } ?: ordered.firstOrNull()
        val to = booking?.let { b -> ordered.firstOrNull { it.id == b.dropoffStopId } } ?: ordered.lastOrNull()
        val startMillis = from?.plannedDepartureMillis ?: from?.plannedArrivalMillis ?: trip.departureAtMillis
        val endMillis = to?.plannedArrivalMillis ?: (startMillis + 60L * 60L * 1000L)
        val uid = if (booking == null) "${trip.id}@rotacerta" else "${booking.id}@rotacerta"
        val summary = if (booking == null) trip.title else "Carona — ${from?.name.orEmpty()} → ${to?.name.orEmpty()}"
        val description = buildString {
            append("Rota Certa")
            trip.publicUrl?.let { append("\\nViagem: ${escape(it)}") }
            if (trip.notes.isNotBlank()) append("\\n${escape(trip.notes)}")
            booking?.let { append("\\nReserva: ${escape(it.id)}") }
        }
        return buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//Rota Certa//Agenda de Viagens//PT-BR\r\n")
            append("CALSCALE:GREGORIAN\r\n")
            append("METHOD:PUBLISH\r\n")
            append("BEGIN:VEVENT\r\n")
            append("UID:${escape(uid)}\r\n")
            append("DTSTAMP:${utcFormatter.format(Instant.now())}\r\n")
            append("DTSTART:${utcFormatter.format(Instant.ofEpochMilli(startMillis))}\r\n")
            append("DTEND:${utcFormatter.format(Instant.ofEpochMilli(endMillis.coerceAtLeast(startMillis + 60_000L)))}\r\n")
            append("SUMMARY:${escape(summary)}\r\n")
            from?.let { append("LOCATION:${escape(it.address.ifBlank { it.name })}\r\n") }
            append("DESCRIPTION:${description}\r\n")
            trip.publicUrl?.let { append("URL:${escape(it)}\r\n") }
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
    }

    fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
}

class TripIcsFileProvider : FileProvider()

object TripCalendarBridge {
    fun addToDeviceCalendar(context: Context, trip: Trip, booking: Booking? = null) {
        val ordered = trip.stops.sortedBy(TripStop::order)
        val from = booking?.let { b -> ordered.firstOrNull { it.id == b.boardingStopId } } ?: ordered.firstOrNull()
        val to = booking?.let { b -> ordered.firstOrNull { it.id == b.dropoffStopId } } ?: ordered.lastOrNull()
        val begin = from?.plannedDepartureMillis ?: from?.plannedArrivalMillis ?: trip.departureAtMillis
        val end = to?.plannedArrivalMillis ?: begin + 60L * 60L * 1000L
        val title = if (booking == null) trip.title else "Carona — ${from?.name.orEmpty()} → ${to?.name.orEmpty()}"
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.coerceAtLeast(begin + 60_000L))
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.EVENT_LOCATION, from?.address?.ifBlank { from.name }.orEmpty())
            putExtra(CalendarContract.Events.DESCRIPTION, trip.publicUrl ?: trip.notes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun shareTrip(context: Context, trip: Trip) {
        val route = trip.stops.sortedBy(TripStop::order).joinToString(" → ") { it.name }
        val text = buildString {
            append("Rota Certa — ${trip.title}\n$route")
            trip.publicUrl?.let { append("\n\nReserve/consulte: $it") }
        }
        shareText(context, "Compartilhar viagem", text)
    }

    fun sharePublicAgenda(context: Context, settings: TripOnlineSettings): Boolean {
        val calendarUrl = settings.publicCalendarUrl ?: return false
        val publicBase = settings.publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/') ?: return false
        val publicToken = settings.publicCalendarToken.takeIf { it.length >= 16 } ?: return false
        val agendaUrl = "$publicBase/?agenda=$publicToken"
        shareText(
            context,
            "Compartilhar Agenda de Viagens",
            buildString {
                append("Rota Certa — Agenda pública de viagens\n")
                append("Ver e reservar próximas viagens:\n$agendaUrl\n\n")
                append("Assinar calendário de viagens (.ics):\n$calendarUrl\n\n")
                append("Nenhum compromisso pessoal ou dado de passageiro é publicado.")
            },
        )
        return true
    }

    fun shareIcs(context: Context, trip: Trip, booking: Booking? = null) {
        val dir = File(context.cacheDir, "trip_calendar").apply { mkdirs() }
        val file = File(dir, "rota-certa-${booking?.id ?: trip.id}.ics")
        file.writeText(TripIcs.build(trip, booking), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.tripfiles", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartilhar calendário").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun shareText(context: Context, title: String, text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
