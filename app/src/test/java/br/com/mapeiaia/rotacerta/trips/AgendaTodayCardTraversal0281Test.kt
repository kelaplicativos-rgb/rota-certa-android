package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgendaTodayCardTraversal0281Test {
    private val today = LocalDate.of(2026, 8, 25)

    private fun candidate(id: String, dateText: String, text: String = dateText) = BlaBlaDomRideCandidate(
        href = "https://www.blablacar.com.br/rides/offer?id=$id",
        text = text,
        dateText = dateText,
    )

    @Test
    fun todayScopeOpensOnlyHojeCardAndIgnoresLaterCards() {
        val todayCard = candidate("today", "Hoje")
        val fridayCard = candidate("friday", "Sex. 28 Ago.")
        val septemberCard = candidate("september", "Sex. 04 Set.")

        val selected = BlaBlaCollectorCardModule.candidatesOnDate(
            listOf(todayCard, fridayCard, septemberCard),
            targetDate = today,
            today = today,
        )

        assertEquals(listOf(todayCard.href), selected.map { it.href })
    }

    @Test
    fun absoluteTodayDateUsesSameNormalizerAuthority() {
        val card = candidate("absolute", "2026-08-25")
        assertEquals(today, BlaBlaCollectorCardModule.candidateDate(card, today))
    }

    @Test
    fun missingDateEvidenceNeverBecomesTodayByGuess() {
        val card = candidate("unknown", "", "Santo André São Tomé das Letras 11:00")
        assertNull(BlaBlaCollectorCardModule.candidateDate(card, today))
    }
}
