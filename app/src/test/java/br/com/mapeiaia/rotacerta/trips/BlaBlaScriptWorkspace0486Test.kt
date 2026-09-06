package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlaBlaScriptWorkspace0486Test {
    @Test
    fun blankAndOversizedScriptsAreRejectedButNormalJavascriptIsAccepted() {
        assertEquals("O script não pode ficar vazio.", BlaBlaScriptWorkspacePolicy0486.validationError("   "))
        assertTrue(
            BlaBlaScriptWorkspacePolicy0486.validationError(
                "x".repeat(BlaBlaScriptWorkspacePolicy0486.MAX_SCRIPT_CHARS + 1),
            )?.contains("excede o limite") == true,
        )
        assertNull(
            BlaBlaScriptWorkspacePolicy0486.validationError(
                "(function(){return JSON.stringify({ok:true});})();",
            ),
        )
    }

    @Test
    fun canonicalCatalogStillContainsExactly32RegisteredRequests() {
        assertEquals(32, BlaBlaDateScopeScriptCatalog0449.all.size)
        assertEquals(BlaBlaBrowserRequest.values().toSet(), BlaBlaDateScopeScriptCatalog0449.all)
    }
}
