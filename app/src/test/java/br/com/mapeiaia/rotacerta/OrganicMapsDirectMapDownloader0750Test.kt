package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganicMapsDirectMapDownloader0750Test {
    @Test fun brazilNodeMatchesPortugueseAndEnglishWithoutAccentNoise() {
        assertTrue(OrganicMapsDirectMapDownloader0750.matchesBrazilName("Brasil"))
        assertTrue(OrganicMapsDirectMapDownloader0750.matchesBrazilName("BRAZIL"))
        assertTrue(OrganicMapsDirectMapDownloader0750.matchesBrazilName("  Brasil  "))
        assertFalse(OrganicMapsDirectMapDownloader0750.matchesBrazilName("Brazil - São Paulo"))
    }

    @Test fun countryNameNormalizationIsStable() {
        assertEquals("sao paulo", OrganicMapsDirectMapDownloader0750.normalizeName("São Paulo"))
        assertEquals("brasil", OrganicMapsDirectMapDownloader0750.normalizeName("BRASIL"))
    }
}
