package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaBatchPublisherRealFlow0559Test {
    @Test
    fun videoObservedScreensAreClassifiedExplicitly() {
        val cases = listOf(
            "De onde você vai sair?" to AgendaPublisherScreen0559.ORIGIN,
            "Para onde você vai?" to AgendaPublisherScreen0559.DESTINATION,
            "Qual é o seu trajeto?" to AgendaPublisherScreen0559.ROUTE,
            "Encontrar mais passageiros" to AgendaPublisherScreen0559.EXTRA_STOPS,
            "Estes são os melhores pontos de parada. Estão bons?" to AgendaPublisherScreen0559.STOP_CONFIRMATION,
            "Quando você vai?" to AgendaPublisherScreen0559.DATE,
            "A que horas você vai buscar seus passageiros?" to AgendaPublisherScreen0559.TIME,
            "Então, quantos passageiros BlaBlaCar você poderá levar no carro?" to AgendaPublisherScreen0559.SEATS,
            "Ative a Reserva Automática para seus passageiros" to AgendaPublisherScreen0559.RESERVATION,
            "Defina o valor por lugar" to AgendaPublisherScreen0559.PRICE,
            "Vai e volta? Ofereça já a sua carona para o retorno" to AgendaPublisherScreen0559.RETURN_OFFER,
            "Qual é o seu CPF? Data de nascimento" to AgendaPublisherScreen0559.USER_VERIFICATION,
        )
        cases.forEach { (text, expected) -> assertEquals(expected, AgendaPublisherScreenClassifier0559.classify(text), text) }
    }

    @Test
    fun suasViagensIsNeverPublicationConfirmation() {
        assertEquals(AgendaPublisherScreen0559.UNKNOWN, AgendaPublisherScreenClassifier0559.classify("Suas viagens"))
        assertEquals(
            AgendaPublisherScreen0559.UNKNOWN,
            AgendaPublisherScreenClassifier0559.classify("Suas viagens Publicar"),
        )
    }

    @Test
    fun onlyExplicitPublicationEvidenceIsConfirmation() {
        assertEquals(
            AgendaPublisherScreen0559.CONFIRMATION,
            AgendaPublisherScreenClassifier0559.classify("Sua carona foi publicada com sucesso"),
        )
        assertEquals(
            AgendaPublisherScreen0559.CONFIRMATION,
            AgendaPublisherScreenClassifier0559.classify("Caronas foram publicadas"),
        )
    }

    @Test
    fun cpfVerificationIsHumanOnlyAndSensitiveValuesAreNotLogged() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBatchPublisherActivity0559.kt").readText()
        assertTrue(source.contains("AGENDA_BATCH_PUBLISH_USER_VERIFICATION_REQUIRED_0559"))
        assertTrue(source.contains("valuesCaptured=false valuesLogged=false"))
        assertTrue(source.contains("O Rota Certa não lê, armazena nem registra CPF/data de nascimento"))
        assertFalse(source.contains("setValue(input, cpf"))
        assertFalse(source.contains("setValue(input, b.cpf"))
    }

    @Test
    fun unknownScreenIsFailClosedAndHasNoGenericPublishFallback() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaBatchPublisherActivity0559.kt").readText()
        assertTrue(source.contains("action=NONE"))
        assertTrue(source.contains("AgendaPublisherScreen0559.UNKNOWN"))
        assertFalse(source.contains("SUCCESS:confirmado"))
        assertFalse(source.contains("suas viagens/.test"))
        assertTrue(source.contains("screen === 'FINAL_PUBLISH'"))
        assertTrue(source.contains("PUBLISH_SUBMITTED:EXPLICIT_FINAL_SCREEN"))
    }

    @Test
    fun scriptExecutorUsesHardenedPublisherAndStillRequiresReconciliation() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTripScriptExecutorActivity0558.kt").readText()
        assertTrue(source.contains("AgendaBatchPublisherActivity0559::class.java"))
        assertTrue(source.contains("PUBLISHED_AMBIGUOUS"))
        assertTrue(source.contains("confirmed=false"))
        assertTrue(source.contains("O executor NÃO republicará esta viagem"))
    }
}
