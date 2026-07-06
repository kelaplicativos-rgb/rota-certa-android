package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class BlaBlaCarCollectorParserTest {
    @Test
    fun normalizesBrazilianPhoneNumbers() {
        assertEquals("5511987654321", BlaBlaCarCollectorParser.normalizePhone("(11) 98765-4321"))
        assertEquals("5521912345678", BlaBlaCarCollectorParser.normalizePhone("+55 21 91234-5678"))
    }

    @Test
    fun extractsPassengersFromCopiedText() {
        val raw = """
            Maria Silva
            Telefone: (11) 98765-4321
            Valor R$ 45,00
            Joao Souza
            +55 21 91234-5678 R$ 60,50
        """.trimIndent()

        val passengers = BlaBlaCarCollectorParser.parsePassengers(raw)

        assertEquals(2, passengers.size)
        assertEquals("Maria Silva", passengers[0].name)
        assertEquals("5511987654321", passengers[0].phone)
        assertEquals("R$ 45,00", passengers[0].fareText)
        assertEquals("Joao Souza", passengers[1].name)
        assertEquals("5521912345678", passengers[1].phone)
        assertEquals("R$ 60,50", passengers[1].fareText)
    }

    @Test
    fun calculatesRevenueExpensesProfitAndProfitPerKm() {
        val record = BlaBlaCarTripRecord(
            distanceKm = "200 km",
            passengers = listOf(
                BlaBlaCarPassenger(name = "Maria", seats = 2, fareText = "R$ 45,00"),
                BlaBlaCarPassenger(name = "Joao", seats = 1, fareText = "30.00"),
            ),
            expenses = listOf(
                BlaBlaCarExpense(label = "Pedagio", amountText = "R$ 25,00"),
                BlaBlaCarExpense(label = "Lanche", amountText = "15"),
            ),
        )

        assertEquals(120.0, BlaBlaCarCollectorCalculator.totalRevenue(record), 0.001)
        assertEquals(40.0, BlaBlaCarCollectorCalculator.totalExpenses(record), 0.001)
        assertEquals(80.0, BlaBlaCarCollectorCalculator.profit(record), 0.001)
        assertEquals(0.4, BlaBlaCarCollectorCalculator.profitPerKm(record) ?: 0.0, 0.001)
    }
}
