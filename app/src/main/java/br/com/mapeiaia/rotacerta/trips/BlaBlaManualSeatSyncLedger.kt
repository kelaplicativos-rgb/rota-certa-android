package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BlaBlaManualSeatSyncLedgerEntry(
    val localBookingId: String,
    val profileUuid: String,
    val tripId: String,
    val externallyReducedSeats: Int,
    val confirmedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * Records only VERIFIED external decrements caused by manual/private bookings.
 * A later removal may give seats back only when this proof exists, preventing
 * an external +N after a previous decrement failed or was never attempted.
 */
class BlaBlaManualSeatSyncLedger(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun markVerifiedDecrease(request: BlaBlaManualSeatSyncRequest) {
        if (request.seatDelta >= 0) return
        val seats = -request.seatDelta
        val entry = BlaBlaManualSeatSyncLedgerEntry(
            localBookingId = request.localBookingId,
            profileUuid = request.profileUuid,
            tripId = request.tripId,
            externallyReducedSeats = seats,
        )
        val next = list().filterNot { it.localBookingId == request.localBookingId } + entry
        save(next)
    }

    fun canReverse(localBookingId: String, seats: Int): Boolean =
        list().any { it.localBookingId == localBookingId && it.externallyReducedSeats == seats && seats > 0 }

    fun clearAfterVerifiedReverse(localBookingId: String) {
        save(list().filterNot { it.localBookingId == localBookingId })
    }

    fun entry(localBookingId: String): BlaBlaManualSeatSyncLedgerEntry? =
        list().firstOrNull { it.localBookingId == localBookingId }

    private fun list(): List<BlaBlaManualSeatSyncLedgerEntry> = runCatching {
        json.decodeFromString<List<BlaBlaManualSeatSyncLedgerEntry>>(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun save(entries: List<BlaBlaManualSeatSyncLedgerEntry>) {
        prefs.edit().putString(KEY_ENTRIES, json.encodeToString(entries)).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_manual_seat_sync_ledger_v1"
        private const val KEY_ENTRIES = "entries"
    }
}
