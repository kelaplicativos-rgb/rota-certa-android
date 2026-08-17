package br.com.mapeiaia.rotacerta.trips

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import br.com.mapeiaia.rotacerta.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TripActions {
    const val ACTION_OPEN_TRIPS = "br.com.mapeiaia.rotacerta.action.OPEN_TRIPS"
    const val ACTION_NEW_TRIP = "br.com.mapeiaia.rotacerta.action.NEW_TRIP"
    const val EXTRA_TRIP_ID = "trip_id"
}

object TripShortcutInstaller {
    const val SHORTCUT_OPEN = "rota_certa_trips_open_stage47"
    const val SHORTCUT_NEW = "rota_certa_trips_new_stage47"

    fun installDynamic(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val open = ShortcutInfo.Builder(context, SHORTCUT_OPEN)
            .setShortLabel("Minhas viagens")
            .setLongLabel("Abrir Agenda de Viagens")
            .setIntent(Intent(context, TripsActivity::class.java).setAction(TripActions.ACTION_OPEN_TRIPS))
            .build()
        val create = ShortcutInfo.Builder(context, SHORTCUT_NEW)
            .setShortLabel("Nova viagem")
            .setLongLabel("Publicar nova viagem")
            .setIntent(Intent(context, TripsActivity::class.java).setAction(TripActions.ACTION_NEW_TRIP))
            .build()
        manager.dynamicShortcuts = listOf(create, open)
    }

    fun requestPinnedCreateShortcut(context: Context): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val info = ShortcutInfo.Builder(context, "${SHORTCUT_NEW}_pinned")
            .setShortLabel("Nova viagem")
            .setLongLabel("Rota Certa — Nova viagem")
            .setIntent(Intent(context, TripsActivity::class.java).setAction(TripActions.ACTION_NEW_TRIP))
            .build()
        return manager.requestPinShortcut(info, null)
    }
}

class TripQuickTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val next = TripStore(this).nextPublishedTrip()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = if (next == null) "Nova viagem" else "Viagem ${next.capacity - SeatAvailabilityEngine.remainingSeatsForWholeTrip(next, TripStore(this@TripQuickTileService).bookingsFor(next.id))}/${next.capacity}"
            subtitle = next?.title?.take(28) ?: "Rota Certa"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val action = if (TripStore(this).nextPublishedTrip() == null) TripActions.ACTION_NEW_TRIP else TripActions.ACTION_OPEN_TRIPS
        val intent = Intent(this, TripsActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                4701,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class TripWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, views(context)) }
    }

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, TripWidgetProvider::class.java)
            manager.updateAppWidget(component, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val store = TripStore(context)
            val next = store.nextPublishedTrip()
            val views = RemoteViews(context.packageName, R.layout.trip_widget_stage47)
            val title = next?.title ?: "Nenhuma viagem publicada"
            val detail = if (next == null) {
                "Toque para criar a próxima"
            } else {
                val available = SeatAvailabilityEngine.remainingSeatsForWholeTrip(next, store.bookingsFor(next.id))
                val whenText = formatter.format(Instant.ofEpochMilli(next.departureAtMillis).atZone(ZoneId.systemDefault()))
                "$whenText • $available/${next.capacity} vagas livres"
            }
            views.setTextViewText(R.id.trip_widget_title, title)
            views.setTextViewText(R.id.trip_widget_detail, detail)
            val openIntent = Intent(context, TripsActivity::class.java)
                .setAction(if (next == null) TripActions.ACTION_NEW_TRIP else TripActions.ACTION_OPEN_TRIPS)
            val openPending = PendingIntent.getActivity(
                context,
                4702,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.trip_widget_root, openPending)
            val newPending = PendingIntent.getActivity(
                context,
                4703,
                Intent(context, TripsActivity::class.java).setAction(TripActions.ACTION_NEW_TRIP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.trip_widget_new, newPending)
            return views
        }
    }
}
