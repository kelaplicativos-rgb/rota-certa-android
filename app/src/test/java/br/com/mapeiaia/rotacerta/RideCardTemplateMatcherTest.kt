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
        assertTrue(match!!.score >= 0.75)
    }

    @Test
    fun keepsAdaptiveFeaturesForSavedTemplateButDoesNotUseThemAsLiveDecisionGate() {
        val text = """
            R$ 20
            5 min
            3,2 km
            Rua A, 10
            Avenida B, 200
            Aceitar
        """.trimIndent()
        val template = RideCardTemplateMatcher.createTemplate("sinet.startup.indriver", text)

        assertTrue(template.requiredFeatures.any { it.startsWith("adaptive.") })

        val adaptiveOnlyTemplate = template.copy(
            requiredFeatures = template.requiredFeatures.filter { it.startsWith("adaptive.") },
        )

        assertNull(RideCardTemplateMatcher.match(text, "sinet.startup.indriver", listOf(adaptiveOnlyTemplate)))
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

    @Test
    fun doesNotMatchNinetyNineCardByWeakStructuralFeaturesOnly() {
        val model = """
            Perfil Essencial
            R$ 12,40
            5min (1,9km)
            Rua Exemplo, 10
            Avenida Modelo, 200
            Selecionar
        """.trimIndent()
        val liveCard = """
            99
            R$0,00
            Av. Afons de Sampaio e so
            FAÇA UMA GRANA EXTRA
            Av. Aricanduva
            R$ 29,99
            R$2.03km
        """.trimIndent()

        val template = RideCardTemplateMatcher.createTemplate("com.app99.driver", model)
        assertNull(RideCardTemplateMatcher.match(liveCard, "com.app99.driver", listOf(template)))
    }
}
