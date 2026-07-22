package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionAccelerationUi128ContractTest {
    private fun sourceFile(name: String): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists)?.readText() ?: error("$name nao encontrado")

    @Test
    fun accelerationCardIsPlacedImmediatelyAfterRadiusControls() {
        val main = sourceFile("MainActivity.kt")
        val analysisStart = main.indexOf("private fun AnalysisScreen(")
        val analysisEnd = main.indexOf("private fun LiveReadingCard(", analysisStart)
        val region = main.substring(analysisStart, analysisEnd)
        val radiusCard = region.indexOf("RadiusQuickCard(")
        val accelerationCard = region.indexOf("RegionAccelerationCard128(")
        val advancedMaps = region.indexOf("MapsAndAdvancedCard(")

        assertTrue("Acelerador deve ficar depois do raio", radiusCard >= 0 && accelerationCard > radiusCard)
        assertTrue("Acelerador deve ficar antes das opcoes avancadas", advancedMaps > accelerationCard)
        assertTrue("Botao principal precisa estar visivel", "Preparar regiao para resposta rapida" in main)
        assertTrue("Status precisa acompanhar mudancas das configuracoes", "LaunchedEffect(settings)" in region)
    }

    @Test
    fun networkFailureAlwaysReleasesThePrepareButton() {
        val main = sourceFile("MainActivity.kt")
        val prepareStart = main.indexOf("fun prepareRegionAcceleration128()")
        val prepareEnd = main.indexOf("fun clearRegionAcceleration128()", prepareStart)
        val region = main.substring(prepareStart, prepareEnd)

        assertTrue("Execucao precisa usar try/finally", "finally" in region)
        assertTrue("Botao precisa ser liberado no finally", "regionAccelerationRunning128 = false" in region)
        assertTrue("Erro deve ser mostrado ao usuario", "Nao foi possivel preparar a regiao" in region)
    }

    @Test
    fun featureDoesNotPretendToDownloadGoogleMaps() {
        val main = sourceFile("MainActivity.kt")
        val acceleration = sourceFile("RegionAcceleration.kt")

        assertTrue("Interface precisa explicar que nao baixa mapa pesado", "Nao baixa um mapa pesado" in main)
        assertTrue("Perfil precisa ter validade curta", "PROFILE_TTL_DAYS = 14L" in acceleration)
        assertTrue("Preparo deve persistir coordenadas nos ajustes", "updatedSettings = settings.copy" in acceleration)
        assertFalse("Nao pode existir cache de tiles do Google", "GoogleMapTile" in acceleration)
        assertFalse("Nao pode prometer download de mapa do Google", "baixar mapa do Google" in acceleration.lowercase())
    }
}
