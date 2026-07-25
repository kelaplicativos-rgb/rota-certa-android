package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainBubbleTapMenuContractTest {
    @Test
    fun compiledSourceOpensSixteenIndependentAnchoredResourceModules() {
        fun sourceFile(name: String): File = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        ).firstOrNull(File::exists) ?: error("$name nao encontrado")

        val service = sourceFile("LiveRideAccessibilityService.kt").readText()
        val controller = sourceFile("BubbleShortcutOverlayController.kt").readText()
        val positionPolicy = sourceFile("BubbleShortcutPositionPolicy.kt").readText()
        val catalog = sourceFile("BubbleShortcutModule.kt").readText()
        val externalModules = listOf(
            "DestinationBubbleShortcutModule.kt",
            "WhatsAppBubbleShortcutModule.kt",
            "TripConfirmationBubbleShortcutModule.kt",
            "ManualRideCardCaptureBubbleShortcutModule.kt",
            "StopBubbleShortcutModule.kt",
        ).joinToString("\n") { sourceFile(it).readText() }
        val allShortcutSources = catalog + "\n" + externalModules

        val overlayStart = service.indexOf("    private fun showOverlay(")
        val overlayEnd = service.indexOf("\n    private fun removeOverlay()", overlayStart)
        assertTrue("showOverlay precisa existir", overlayStart >= 0 && overlayEnd > overlayStart)
        val overlayBlock = service.substring(overlayStart, overlayEnd)

        assertTrue(
            "Toque principal precisa abrir/fechar os atalhos",
            "newView.setOnClickListener { toggleResourceShortcuts() }" in overlayBlock,
        )
        assertFalse(
            "Listener principal nao pode abrir a Home diretamente",
            "openApp()" in overlayBlock || "onMainBubbleClick()" in overlayBlock,
        )

        assertTrue("Contrato 0.1.120 precisa estar aplicado", "popup_navigation_service_0_1_120" in service)
        assertTrue("Servico precisa despachar o modulo", "onShortcut = ::executeShortcutModule" in service)
        assertTrue("Alertas precisam abrir modulo proprio", "BubbleShortcutAction.OpenAlerts" in service)
        assertTrue("Locais precisam abrir modulo proprio", "BubbleShortcutAction.OpenSavedPlaces" in service)
        assertTrue("Radares precisam abrir modulo proprio", "BubbleShortcutAction.OpenRadars" in service)
        assertTrue("Cards precisam abrir modulo proprio", "BubbleShortcutAction.OpenCards" in service)
        assertTrue(
            "Leitura interna deve permanecer para compatibilidade, embora fora do popup",
            "BubbleShortcutAction.ToggleReading -> toggleLiveReadingFromBubble()" in service,
        )
        assertTrue("WhatsApp precisa capturar telefone da tela", "BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()" in service)
        assertTrue(
            "Confirmacao precisa copiar a viagem sob demanda",
            "BubbleShortcutAction.CopyTripConfirmation -> copyTripConfirmationFromBubbleChecklist8()" in service,
        )
        assertTrue(
            "Captura manual precisa tirar print e criar modelo",
            "BubbleShortcutAction.SaveRideCard -> captureAndRegisterRideCardManualChecklist12()" in service,
        )
        assertTrue("Coletor precisa abrir diretamente", "BubbleShortcutAction.OpenCollector -> openCollectorFromBubble()" in service)
        assertTrue("Limpar precisa reutilizar o limpador existente", "BubbleShortcutAction.ClearClipboard -> clearClipboardFromBubble()" in service)
        assertTrue("Depurar precisa exportar diretamente", "BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()" in service)
        assertTrue("Encerrar precisa desligar o servico", "BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()" in service)

        assertTrue("Popup precisa fechar somente quando o arraste comeca", "popup_close_only_on_drag_0_1_120" in service)
        assertFalse(
            "ACTION_DOWN nao pode fechar o popup antes do clique",
            "hideResourceShortcuts()\n                    bubbleGestureActive = true" in service,
        )
        assertFalse("Callbacks fixos nao podem voltar", "BubbleShortcutActions(" in service)

        BubbleShortcutCatalog.requireValid()
        assertTrue("Catalogo modular ausente", "object BubbleShortcutCatalog" in catalog)
        assertTrue("Controlador precisa percorrer o catalogo", "BubbleShortcutCatalog.modules.forEach" in controller)
        assertTrue("Controlador precisa devolver o modulo", "onShortcut(module.spec)" in controller)
        assertEquals("O popup precisa conter dezesseis modulos", 16, BubbleShortcutCatalog.modules.size)
        assertEquals("Cada modulo precisa ser diferente", 16, BubbleShortcutCatalog.modules.map { it::class }.distinct().size)

        listOf(
            "Rota",
            "Destino",
            "Alertas",
            "Locais",
            "Radares",
            "Aparencia",
            "Backup",
            "WhatsApp da tela",
            "Copiar confirmação da viagem",
            "Coletor",
            "Limpar area de transferencia",
            "Depurar",
            "Respostas rápidas",
            "Encerrar Rota Certa",
            "Capturar card agora",
            "Cards cadastrados",
        ).forEach { label ->
            assertTrue("Atalho ausente: $label", "label = \"$label\"" in allShortcutSources)
        }

        assertFalse("Permissao deve ficar em controles gerais", BubbleShortcutCatalog.modules.any { it.spec.id == "permissions" })
        assertFalse("Leitura deve ficar em controles gerais", BubbleShortcutCatalog.modules.any { it.spec.id == "reading" })
        assertFalse("Relatorios nao podem permanecer no popup", BubbleShortcutCatalog.modules.any { it.spec.id == "reports" })
        assertFalse("Alerta duplicado nao pode permanecer", BubbleShortcutCatalog.modules.any { it.spec.id == "alert" })
        assertFalse("Salvar local duplicado nao pode permanecer", BubbleShortcutCatalog.modules.any { it.spec.id == "saved_place" })
        assertFalse("Salvar card antigo nao pode permanecer", BubbleShortcutCatalog.modules.any { it.spec.id == "ride_card" })

        assertTrue("Controlador precisa usar a politica isolada", "BubbleShortcutPositionPolicy.place" in controller)
        assertTrue("Posicao precisa considerar largura real da bolinha", "anchorWidth" in controller)
        assertTrue("Grade precisa tentar direita da bolinha", "rightX + menuWidth" in positionPolicy)
        assertTrue("Grade precisa tentar esquerda da bolinha", "leftX >= safe" in positionPolicy)
        assertTrue("Tela estreita precisa usar abaixo/acima", "anchorY + validAnchorHeight + horizontalGap" in positionPolicy)
        assertTrue("Popup grande precisa usar duas colunas", "LARGE_SCALE_TWO_COLUMNS" in controller)
        assertTrue("Popup de alerta precisa rejeitar Local", "if (alert.type != SavedPlaceType.ProximityAlert)" in controller)
        assertTrue("Popup precisa permitir editar", "popupButton(\"Editar\", scale)" in controller)
        assertTrue("Popup precisa permitir excluir", "popupButton(\"Excluir\", scale)" in controller)
    }
}
