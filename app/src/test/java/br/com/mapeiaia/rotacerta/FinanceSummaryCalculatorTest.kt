package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceSummaryCalculatorTest {
    private fun entry(
        id: String,
        type: FinanceEntryType,
        amount: Long,
        status: FinanceEntryStatus,
        payment: FinancePaymentMethod,
    ) = FinanceEntry(
        id = id,
        type = type,
        description = id,
        amountCents = amount,
        status = status,
        paymentMethod = payment,
        category = "Teste",
        createdAtMillis = 1L,
        occurredAtMillis = 1L,
        source = FinanceEntrySource.MANUAL,
    )

    @Test
    fun separatesExpectedReceivedExpensesAndCashOnHand() {
        val summary = FinanceSummaryCalculator.calculate(
            listOf(
                entry("cash", FinanceEntryType.REVENUE, 20_400, FinanceEntryStatus.CONFIRMED, FinancePaymentMethod.CASH),
                entry("pix", FinanceEntryType.REVENUE, 10_000, FinanceEntryStatus.CONFIRMED, FinancePaymentMethod.PIX),
                entry("pending", FinanceEntryType.REVENUE, 8_000, FinanceEntryStatus.PENDING, FinancePaymentMethod.UNDEFINED),
                entry("fuel", FinanceEntryType.EXPENSE, 10_000, FinanceEntryStatus.CONFIRMED, FinancePaymentMethod.CASH),
                entry("cancelled", FinanceEntryType.REVENUE, 99_000, FinanceEntryStatus.CANCELLED, FinancePaymentMethod.CASH),
            ),
        )
        assertEquals(38_400L, summary.expectedRevenueCents)
        assertEquals(30_400L, summary.receivedRevenueCents)
        assertEquals(8_000L, summary.pendingRevenueCents)
        assertEquals(10_000L, summary.expensesCents)
        assertEquals(20_400L, summary.netResultCents)
        assertEquals(10_400L, summary.cashOnHandCents)
        assertEquals(1, summary.pendingCount)
    }

    @Test
    fun parsesBrazilianCurrencyWithoutFloatingPoint() {
        assertEquals(20_400L, FinancialRepository.parseCurrencyToCents("R$ 204,00"))
        assertEquals(125_000L, FinancialRepository.parseCurrencyToCents("1.250,00"))
        assertEquals(9_000L, FinancialRepository.parseCurrencyToCents("90"))
    }
}
