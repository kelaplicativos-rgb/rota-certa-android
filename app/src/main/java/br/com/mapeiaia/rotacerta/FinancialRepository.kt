package br.com.mapeiaia.rotacerta

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class FinancialRepository(context: Context) {
    private val appContext = context.applicationContext
    private val entriesFile = File(appContext.filesDir, ENTRIES_FILE_NAME)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun readAll(): List<FinanceEntry> = synchronized(FILE_LOCK) { readAllLocked() }

    fun todayEntries(nowMillis: Long = System.currentTimeMillis()): List<FinanceEntry> {
        val (start, end) = dayBounds(nowMillis)
        return readAll().filter { it.occurredAtMillis in start until end }.sortedByDescending(FinanceEntry::createdAtMillis)
    }

    fun todaySummary(nowMillis: Long = System.currentTimeMillis()): FinanceSummary =
        FinanceSummaryCalculator.calculate(todayEntries(nowMillis))

    fun addManual(
        type: FinanceEntryType,
        description: String,
        amountCents: Long,
        status: FinanceEntryStatus,
        paymentMethod: FinancePaymentMethod,
        category: String,
        note: String,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ): FinanceEntry {
        require(description.isNotBlank())
        require(amountCents > 0L)
        val now = System.currentTimeMillis()
        val entry = FinanceEntry(
            id = UUID.randomUUID().toString(),
            type = type,
            description = description.trim(),
            amountCents = amountCents,
            status = status,
            paymentMethod = paymentMethod,
            category = category.trim().ifBlank { if (type == FinanceEntryType.REVENUE) "Receita" else "Despesa" },
            note = note.trim(),
            createdAtMillis = now,
            occurredAtMillis = occurredAtMillis,
            source = FinanceEntrySource.MANUAL,
        )
        append(entry)
        return entry
    }

    fun registerPassengerValue(
        data: PassengerValueData,
        sourcePackage: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): PassengerRevenueRegistration = synchronized(FILE_LOCK) {
        val entries = readAllLocked()
        val day = dayKey(nowMillis)
        val identity = "$day|${PassengerValueFormatter.normalizedIdentity(data)}"
        val dedupe = "$identity|${data.amountCents}"
        entries.firstOrNull {
            it.source == FinanceEntrySource.PASSENGER_VALUE && it.dedupeKey == dedupe && it.status != FinanceEntryStatus.CANCELLED
        }?.let { return@synchronized PassengerRevenueRegistration.AlreadyExists(it) }
        entries.firstOrNull {
            it.source == FinanceEntrySource.PASSENGER_VALUE && it.identityKey == identity && it.status != FinanceEntryStatus.CANCELLED
        }?.let { return@synchronized PassengerRevenueRegistration.AmountConflict(it) }

        val entry = FinanceEntry(
            id = UUID.randomUUID().toString(),
            type = FinanceEntryType.REVENUE,
            description = data.passengerName,
            amountCents = data.amountCents,
            status = FinanceEntryStatus.PENDING,
            paymentMethod = FinancePaymentMethod.UNDEFINED,
            category = "Passageiro",
            note = sourcePackage?.let { "Capturado em $it" }.orEmpty(),
            createdAtMillis = nowMillis,
            occurredAtMillis = nowMillis,
            source = FinanceEntrySource.PASSENGER_VALUE,
            passengerName = data.passengerName,
            origin = data.origin,
            destination = data.destination,
            seats = data.seats,
            dedupeKey = dedupe,
            identityKey = identity,
        )
        appendLocked(entry)
        PassengerRevenueRegistration.Added(entry)
    }

    fun update(entry: FinanceEntry): Boolean = synchronized(FILE_LOCK) {
        val entries = readAllLocked().toMutableList()
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index < 0) return@synchronized false
        entries[index] = entry.copy(description = entry.description.trim(), category = entry.category.trim(), note = entry.note.trim())
        rewriteLocked(entries)
        true
    }

    fun delete(id: String): Boolean = synchronized(FILE_LOCK) {
        val entries = readAllLocked()
        val filtered = entries.filterNot { it.id == id }
        if (filtered.size == entries.size) return@synchronized false
        rewriteLocked(filtered)
        true
    }

    fun markReceived(id: String, method: FinancePaymentMethod): Boolean {
        val entry = readAll().firstOrNull { it.id == id } ?: return false
        return update(entry.copy(status = FinanceEntryStatus.CONFIRMED, paymentMethod = method))
    }

    fun cancel(id: String): Boolean {
        val entry = readAll().firstOrNull { it.id == id } ?: return false
        return update(entry.copy(status = FinanceEntryStatus.CANCELLED))
    }

    fun isDayClosed(nowMillis: Long = System.currentTimeMillis()): Boolean =
        dayKey(nowMillis) in preferences.getStringSet(KEY_CLOSED_DAYS, emptySet()).orEmpty()

    fun setDayClosed(closed: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        val days = preferences.getStringSet(KEY_CLOSED_DAYS, emptySet()).orEmpty().toMutableSet()
        val key = dayKey(nowMillis)
        if (closed) days += key else days -= key
        preferences.edit().putStringSet(KEY_CLOSED_DAYS, days).apply()
    }

    private fun append(entry: FinanceEntry) = synchronized(FILE_LOCK) { appendLocked(entry) }

    private fun appendLocked(entry: FinanceEntry) {
        entriesFile.parentFile?.mkdirs()
        entriesFile.appendText(json.encodeToString(entry) + "\n")
    }

    private fun readAllLocked(): List<FinanceEntry> {
        if (!entriesFile.exists()) return emptyList()
        return entriesFile.useLines { lines ->
            lines.mapNotNull { raw ->
                raw.takeIf(String::isNotBlank)?.let { runCatching { json.decodeFromString<FinanceEntry>(it) }.getOrNull() }
            }.toList()
        }
    }

    private fun rewriteLocked(entries: List<FinanceEntry>) {
        entriesFile.parentFile?.mkdirs()
        val temporary = File(entriesFile.parentFile, "$ENTRIES_FILE_NAME.tmp")
        temporary.bufferedWriter().use { writer ->
            entries.forEach { writer.append(json.encodeToString(it)).append('\n') }
        }
        if (!temporary.renameTo(entriesFile)) {
            entriesFile.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun dayBounds(nowMillis: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return start to calendar.timeInMillis
    }

    private fun dayKey(nowMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))

    companion object {
        fun parseCurrencyToCents(raw: String): Long? {
            val normalized = raw.trim().replace("R$", "", ignoreCase = true).replace(" ", "")
            if (normalized.isBlank()) return null
            val decimalSeparator = normalized.lastIndexOf(',')
            val integerPart: String
            val centsPart: String
            if (decimalSeparator >= 0) {
                integerPart = normalized.substring(0, decimalSeparator).replace(".", "")
                centsPart = normalized.substring(decimalSeparator + 1).padEnd(2, '0').take(2)
            } else {
                integerPart = normalized.replace(".", "")
                centsPart = "00"
            }
            val integer = integerPart.filter(Char::isDigit).toLongOrNull() ?: return null
            val cents = centsPart.filter(Char::isDigit).toIntOrNull() ?: return null
            return (integer * 100L + cents).takeIf { it > 0L }
        }

        fun normalizeForKey(value: String): String = Normalizer
            .normalize(value.lowercase(Locale("pt", "BR")).trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

        private val FILE_LOCK = Any()
        private const val ENTRIES_FILE_NAME = "finance-entries.jsonl"
        private const val PREFERENCES_NAME = "rota_certa_finance"
        private const val KEY_CLOSED_DAYS = "closed_days"
    }
}
