package br.com.mapeiaia.rotacerta.coletor.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class DriverAccount { EZEQUIEL_S, BARBOSA }
enum class TripStatus { DRAFT, ACTIVE, COMPLETED, CANCELLED, ARCHIVED }
enum class BookingStatus { CONFIRMED, PENDING, CANCELLED, NO_SHOW, COMPLETED }
enum class PaymentStatus { PENDING, PARTIAL, RECEIVED, REFUNDED, CANCELLED }
enum class PaymentMethod { BLABLACAR, PIX, CASH, CARD, TRANSFER, OTHER }
enum class EntryType { INCOME, EXPENSE }
enum class ExpenseCategory {
    FUEL, TOLL, PARKING, FOOD, LODGING, MAINTENANCE, CLEANING, PLATFORM_FEE, OTHER
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class TripStop(
    val name: String,
    val address: String,
    val scheduledAt: LocalDateTime? = null,
    val coordinates: GeoPoint? = null,
)

data class Passenger(
    val id: String = UUID.randomUUID().toString(),
    val externalProfileId: String? = null,
    val name: String,
    val phone: String? = null,
    val profileUrl: String? = null,
    val notes: String? = null,
)

data class Booking(
    val id: String = UUID.randomUUID().toString(),
    val externalBookingId: String? = null,
    val passenger: Passenger,
    val seats: Int = 1,
    val boarding: TripStop,
    val dropOff: TripStop,
    val grossAmount: BigDecimal,
    val netAmount: BigDecimal = grossAmount,
    val paymentMethod: PaymentMethod = PaymentMethod.BLABLACAR,
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val bookingStatus: BookingStatus = BookingStatus.CONFIRMED,
    val conversationUrl: String? = null,
    val manuallyLockedFields: Set<String> = emptySet(),
    val updatedAt: Instant = Instant.now(),
)

data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val externalTripId: String? = null,
    val account: DriverAccount,
    val date: LocalDate,
    val departureAt: LocalDateTime,
    val arrivalAt: LocalDateTime? = null,
    val origin: TripStop,
    val finalDestination: TripStop,
    val intermediateStops: List<TripStop> = emptyList(),
    val offeredSeats: Int = 0,
    val status: TripStatus = TripStatus.ACTIVE,
    val sourceUrl: String? = null,
    val bookings: List<Booking> = emptyList(),
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class CashEntry(
    val id: String = UUID.randomUUID().toString(),
    val tripId: String? = null,
    val bookingId: String? = null,
    val type: EntryType,
    val description: String,
    val amount: BigDecimal,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus,
    val expenseCategory: ExpenseCategory? = null,
    val competenceDate: LocalDate,
    val paidAt: LocalDateTime? = null,
    val receiptPath: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
)

data class TripFinancialSummary(
    val grossRevenue: BigDecimal,
    val receivedRevenue: BigDecimal,
    val pendingRevenue: BigDecimal,
    val totalExpenses: BigDecimal,
    val expectedProfit: BigDecimal,
    val realizedProfit: BigDecimal,
)

fun Trip.calculateFinancialSummary(entries: List<CashEntry>): TripFinancialSummary {
    val tripEntries = entries.filter { it.tripId == id }
    val grossRevenue = bookings
        .filter { it.bookingStatus != BookingStatus.CANCELLED }
        .fold(BigDecimal.ZERO) { total, booking -> total + booking.grossAmount }
    val receivedRevenue = tripEntries
        .filter { it.type == EntryType.INCOME && it.paymentStatus == PaymentStatus.RECEIVED }
        .fold(BigDecimal.ZERO) { total, entry -> total + entry.amount }
    val totalExpenses = tripEntries
        .filter { it.type == EntryType.EXPENSE && it.paymentStatus == PaymentStatus.RECEIVED }
        .fold(BigDecimal.ZERO) { total, entry -> total + entry.amount }
    val pendingRevenue = (grossRevenue - receivedRevenue).max(BigDecimal.ZERO)
    return TripFinancialSummary(
        grossRevenue = grossRevenue,
        receivedRevenue = receivedRevenue,
        pendingRevenue = pendingRevenue,
        totalExpenses = totalExpenses,
        expectedProfit = grossRevenue - totalExpenses,
        realizedProfit = receivedRevenue - totalExpenses,
    )
}
