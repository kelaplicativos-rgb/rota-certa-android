package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlaBlaDriverProfileNamePolicyTest {
    @Test
    fun acceptsRealDriverLikeNames() {
        assertEquals("Ezequiel", BlaBlaDriverProfileNamePolicy.normalize(" Ezequiel "))
        assertEquals("Barbosa", BlaBlaDriverProfileNamePolicy.normalize("Barbosa"))
        assertEquals("Maria Aparecida", BlaBlaDriverProfileNamePolicy.normalize("Maria   Aparecida"))
    }

    @Test
    fun rejectsTripTitlesAndDates() {
        assertNull(
            BlaBlaDriverProfileNamePolicy.normalize(
                "Domingo, 27 de junho Viagem de São Paulo para São Tomé das Letras em Domingo, 27 de junho",
            ),
        )
        assertNull(BlaBlaDriverProfileNamePolicy.normalize("Viagem de São Paulo para Três Corações"))
        assertNull(BlaBlaDriverProfileNamePolicy.normalize("27 de junho"))
        assertNull(BlaBlaDriverProfileNamePolicy.normalize("São Paulo → São Thomé das Letras"))
    }
}
