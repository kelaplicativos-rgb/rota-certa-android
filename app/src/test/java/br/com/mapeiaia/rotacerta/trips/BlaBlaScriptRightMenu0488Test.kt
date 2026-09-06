package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaScriptRightMenu0488Test {
    private val activity =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
    private val workspace =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaScriptWorkspace0486.kt").readText()

    @Test
    fun scriptsRightMenuOffersNewAndMaintenanceActions() {
        assertTrue(activity.contains("TripScreen.SCRIPTS -> listOf("))
        assertTrue(activity.contains("AgendaHeaderAction0396(\"Novo script\")"))
        assertTrue(activity.contains("BlaBlaScriptsCommand0488.NEW_SCRIPT"))
        assertTrue(activity.contains("AgendaHeaderAction0396(\"Restaurar seleção padrão\")"))
        assertTrue(activity.contains("BlaBlaScriptsCommand0488.RESTORE_SELECTION_DEFAULTS"))
        assertTrue(activity.contains("uiCommand0488 = scriptsUiCommand0488"))
        assertTrue(activity.contains("uiCommandToken0488 = scriptsUiCommandToken0488"))
    }

    @Test
    fun newScriptCanBeLibraryOnlyOrBoundToExistingCollectorRequest() {
        assertTrue(workspace.contains("data class BlaBlaCustomScript0488"))
        assertTrue(workspace.contains("fun saveCustomScript("))
        assertTrue(workspace.contains("Text(if (customEditing == null) \"Novo script\" else \"Editar script\")"))
        assertTrue(workspace.contains("Text(\"Nome do script\")"))
        assertTrue(workspace.contains("Text(\"JavaScript\")"))
        assertTrue(workspace.contains("\"Sem vínculo com o coletor\""))
        assertTrue(workspace.contains("\"Sem vínculo • biblioteca\""))
        assertTrue(workspace.contains("BlaBlaDateScopeScriptCatalog0449.groups.forEach"))
        assertTrue(workspace.contains("saveOverride(targetRequest, code)"))
        assertTrue(workspace.contains("Vinculado: ao salvar, este código vira a versão efetiva dessa ação no coletor"))
        assertTrue(workspace.contains("REMOTE_WRITE continua exigindo a operação explícita do orquestrador"))
    }

    @Test
    fun customLibrarySupportsEditAndDeleteWithoutSilentlyErasingCollectorOverride() {
        assertTrue(workspace.contains("Text(\"Meus scripts\""))
        assertTrue(workspace.contains("Text(\"Editar\")"))
        assertTrue(workspace.contains("Text(\"Excluir da biblioteca\")"))
        assertTrue(workspace.contains("fun deleteCustomScript(id: String)"))
        assertTrue(workspace.contains("Um override já aplicado ao coletor não é apagado automaticamente"))
        val deleteBody = workspace
            .substringAfter("fun deleteCustomScript(id: String): Boolean {")
            .substringBefore("private fun scriptKey")
        assertFalse(deleteBody.contains("restoreOriginal("))
        assertFalse(deleteBody.contains("remove(scriptKey"))
    }
}
