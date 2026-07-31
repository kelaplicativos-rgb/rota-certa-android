package br.com.mapeiaia.rotacerta

import kotlinx.serialization.Serializable

@Serializable
enum class FinanceEntryType { REVENUE, EXPENSE }

@Serializable
enum class FinanceEntryStatus { PENDING, CONFIRMED, CANCELLED }

@Serializable
enum class FinancePaymentMethod { UNDEFINED, CASH, PIX, CARD, PLATFORM, OTHER }

@Serializable
enum class FinanceEntrySource { PASSENGER_VALUE, MANUAL }

@Serializable
data class FinanceEntry(
    val id: String,
    val type: FinanceEntryType,
    val description: String,
    val amountCents: Long,
    val status: FinanceEntryStatus,
    val paymentMethod: FinancePaymentMethod,
    val category: String,
    val note: String = "",
    val createdAtMillis: Long,
    val occurredAtMillis: Long,
    val source: FinanceEntrySource,
    val passengerName: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val seats: Int? = null,
    val dedupeKey: String? = null,
    val identityKey: String? = null,
)

data class FinanceSummary(
    val expectedRevenueCents: Long,
    val receivedRevenueCents: Long,
    val pendingRevenueCents: Long,
    val expensesCents: Long,
    val netResultCents: Long,
    val cashReceivedCents: Long,
    val cashExpensesCents: Long,
    val cashOnHandCents: Long,
    val pixReceivedCents: Long,
    val pendingCount: Int,
)

object FinanceSummaryCalculator {
    fun calculate(entries: List<FinanceEntry>): FinanceSummary {
        val active = entries.filter { it.status != FinanceEntryStatus.CANCELLED }
        val revenue = active.filter { it.type == FinanceEntryType.REVENUE }
        val confirmedRevenue = revenue.filter { it.status == FinanceEntryStatus.CONFIRMED }
        val pendingRevenue = revenue.filter { it.status == FinanceEntryStatus.PENDING }
        val confirmedExpenses = active.filter {
            it.type == FinanceEntryType.EXPENSE && it.status == FinanceEntryStatus.CONFIRMED
        }
        val expected = revenue.sumOf(FinanceEntry::amountCents)
        val received = confirmedRevenue.sumOf(FinanceEntry::amountCents)
        val pending = pendingRevenue.sumOf(FinanceEntry::amountCents)
        val expenses = confirmedExpenses.sumOf(FinanceEntry::amountCents)
        val cashReceived = confirmedRevenue.filter { it.paymentMethod == FinancePaymentMethod.CASH }.sumOf(FinanceEntry::amountCents)
        val cashExpenses = confirmedExpenses.filter { it.paymentMethod == FinancePaymentMethod.CASH }.sumOf(FinanceEntry::amountCents)
        val pixReceived = confirmedRevenue.filter { it.paymentMethod == FinancePaymentMethod.PIX }.sumOf(FinanceEntry::amountCents)
        return FinanceSummary(
            expectedRevenueCents = expected,
            receivedRevenueCents = received,
            pendingRevenueCents = pending,
            expensesCents = expenses,
            netResultCents = received - expenses,
            cashReceivedCents = cashReceived,
            cashExpensesCents = cashExpenses,
            cashOnHandCents = cashReceived - cashExpenses,
            pixReceivedCents = pixReceived,
            pendingCount = pendingRevenue.size,
        )
    }
}

sealed interface PassengerRevenueRegistration {
    data class Added(val entry: FinanceEntry) : PassengerRevenueRegistration
    data class AlreadyExists(val entry: FinanceEntry) : PassengerRevenueRegistration
    data class AmountConflict(val existing: FinanceEntry) : PassengerRevenueRegistration
}
