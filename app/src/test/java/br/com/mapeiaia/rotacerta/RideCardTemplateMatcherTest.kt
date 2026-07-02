package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCardTemplateMatcherTest {
    @Test
    fun matchesSameAppCardWithSameStableFeaturesAndDifferentAddresses() {
        val sample = """
            Pedido de viagem
            R$ 15
            R$ 2,2/km ~1,7 km
            Rua Gaspar Guterres 129
            Rua Rafael Fernandes, 63
            Aceitar por R$ 15
            Ofereça sua tarifa
        """.trimIndent()
        val nextCard = """
            Pedido de viagem
            R$ 22
            R$ 1,9/km ~3,4 km
            Rua A, 10
            Avenida B, 200
            Aceitar por R$ 22
            Ofereça sua tarifa
        """.trimIndent()

        val template = RideCardTemplateMatcher.createTemplate("sinet.startup.indriver", sample)
        val match = RideCardTemplateMatcher.match(nextCard, "sinet.startup.indriver", listOf(template))

        assertNotNull(match)
        assertTrue(match!!.score >= 0.72)
    }

    @Test
    fun doesNotMatchNavigationMapWithoutRideFeatures() {
        val sample = """
            Pedido de viagem
            R$ 15
            R$ 2,2/km ~1,7 km
            Rua Gaspar Guterres 129
            Rua Rafael Fernandes, 63
            Aceitar por R$ 15
        """.trimIndent()
        val navigation = """
            Google Maps
            Rotas
            Iniciar
            Rua Gaspar Guterres
            Avenida Itaquera
            5 min
            1,7 km
        """.trimIndent()

        val template = RideCardTemplateMatcher.createTemplate("sinet.startup.indriver", sample)

        assertNull(RideCardTemplateMatcher.match(navigation, "com.google.android.apps.maps", listOf(template)))
    }

    @Test
    fun doesNotMatchDifferentRideAppPackage() {
        val sample = """
            UberX
            R$ 13,48
            7 min 2.0 km
            Rua A, 10
            Avenida B, 200
            Selecionar
        """.trimIndent()
        val template = RideCardTemplateMatcher.createTemplate("com.ubercab.driver", sample)

        assertNull(RideCardTemplateMatcher.match(sample, "com.app99.driver", listOf(template)))
    }
}
