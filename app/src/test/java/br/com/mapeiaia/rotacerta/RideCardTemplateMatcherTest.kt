package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCardTemplateMatcherTest {
    @Test
    fun matchesSameAppCroppedRouteCardWithDifferentAddresses() {
        val sample = """
            9min (1,3km)
            Yogui Stilo e Sports, Avenida Mateo Bei, 2651 - Cidade Sao Mateus
            9min (2,9km)
            Condominio Parque Residencial Santa Barbara, Cidade Satelite
        """.trimIndent()
        val nextCard = """
            5 min (1.5 km)
            Avenida Ragueb Chohfi, Sao Mateus, Sao Paulo
            3 minutos (0.8 km)
            R. Ator Paulo Gustavo, 270, Cidade Sao Mateus, Sao Paulo
        """.trimIndent()

        val template = RideCardTemplateMatcher.createTemplate("sinet.startup.indriver", sample)
        val match = RideCardTemplateMatcher.match(nextCard, "sinet.startup.indriver", listOf(template))

        assertNotNull(match)
        assertTrue(match!!.score >= 0.72)
    }

    @Test
    fun acceptsMarkedAddressCropWithoutTimeOrKm() {
        val crop = """
            A
            Delboni | Lapa (Lapa)
            B
            Rua Bernardino Vergueiro (Penha de Franca, Sao Paulo - State of Sao Paulo)
        """.trimIndent()

        assertTrue(RideCardTemplateMatcher.looksLikeLearnableRideCard(crop))
        val packageName = RideCardTemplateMatcher.packageNameForLearning(null, crop)
        assertEquals(RideCardTemplateMatcher.UNIVERSAL_LEARNED_PACKAGE, packageName)
        val template = RideCardTemplateMatcher.createTemplate(packageName, crop)
        val match = RideCardTemplateMatcher.match(crop, null, listOf(template))

        assertNotNull(match)
        assertTrue(template.requiredFeatures.contains("card.route.marked_stops"))
    }

    @Test
    fun savedTemplateDoesNotContainAdaptiveFeatures() {
        val text = """
            5 min (1.5 km)
            Avenida Ragueb Chohfi, Sao Mateus, Sao Paulo
            3 minutos (0.8 km)
            R. Ator Paulo Gustavo, 270, Cidade Sao Mateus, Sao Paulo
            Aceitar
        """.trimIndent()
        val template = RideCardTemplateMatcher.createTemplate("sinet.startup.indriver", text)

        assertFalse(template.requiredFeatures.any { it.startsWith("adaptive.") })
    }

    @Test
    fun doesNotMatchNavigationMapWithoutRidePackageAndCropTemplate() {
        val sample = """
            9min (1,3km)
            Yogui Stilo e Sports, Avenida Mateo Bei, 2651 - Cidade Sao Mateus
            9min (2,9km)
            Condominio Parque Residencial Santa Barbara, Cidade Satelite
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
    fun doesNotMatchDifferentRideAppPackage() { // strict_same_package_open_all_test_guard_0_1_128
        val sample = """
            7 min (2.0 km)
            Rua A, 10
            12 min (5.1 km)
            Avenida B, 200
            Selecionar
        """.trimIndent()
        val template = RideCardTemplateMatcher.createTemplate("com.ubercab.driver", sample)

        assertNull(RideCardTemplateMatcher.match(sample, "com.app99.driver", listOf(template)))
    }

    @Test
    fun doesNotMatchNinetyNineCardByWeakStructuralFeaturesOnly() {
        val model = """
            5min (1,9km)
            Rua Exemplo, 10
            8min (3,4km)
            Avenida Modelo, 200
            Perfil Essencial
        """.trimIndent()
        val liveCard = """
            99
            R$0,00
            Av. Afons de Sampaio e so
            FACA UMA GRANA EXTRA
            Av. Aricanduva
            R$ 29,99
            R$2.03km
        """.trimIndent()

        val template = RideCardTemplateMatcher.createTemplate("com.app99.driver", model)
        assertNull(RideCardTemplateMatcher.match(liveCard, "com.app99.driver", listOf(template)))
    }
}
