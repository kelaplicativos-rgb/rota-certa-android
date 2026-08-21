package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolVisualPriority0189Test {
    @Test
    fun separatedAddressLinesWithIntermediateContentStayInSameVisualCard() {
        val groups = FarolVisualPriority0189.cluster(
            prefix = "ocr:10",
            fragments = listOf(
                FarolSpatialFragment0189("a", "Rua Luís Giudice, 143", 70, 500, 960, 560),
                FarolSpatialFragment0189("fare", "R$ 18,50", 70, 600, 400, 650),
                FarolSpatialFragment0189("b", "Rua Bactória, 38", 70, 690, 960, 750),
            ),
        )
        assertEquals(1, groups.size)
        assertTrue(groups.single().text.contains("Rua Luís Giudice, 143"))
        assertTrue(groups.single().text.contains("Rua Bactória, 38"))
    }

    @Test
    fun largeGapSeparatesStackedCards() {
        val groups = FarolVisualPriority0189.cluster(
            prefix = "ocr:10",
            fragments = listOf(
                FarolSpatialFragment0189("a1", "Rua A, 10", 60, 300, 950, 350),
                FarolSpatialFragment0189("a2", "Rua B, 20", 60, 400, 950, 450),
                FarolSpatialFragment0189("b1", "Rua C, 30", 60, 900, 950, 950),
                FarolSpatialFragment0189("b2", "Rua D, 40", 60, 1000, 950, 1050),
            ),
        )
        assertEquals(2, groups.size)
    }
}
