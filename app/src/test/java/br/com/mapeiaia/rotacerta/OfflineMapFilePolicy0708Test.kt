package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineMapFilePolicy0708Test {
    @Test
    fun acceptsRegionalMwmFilesCaseInsensitively() {
        assertTrue(OfflineMapFilePolicy0708.isSupportedName("Brazil_Sao Paulo.mwm"))
        assertTrue(OfflineMapFilePolicy0708.isSupportedName("MINAS_GERAIS.MWM"))
    }

    @Test
    fun rejectsApkZipAndUnrelatedFiles() {
        assertFalse(OfflineMapFilePolicy0708.isSupportedName("Organic Maps.apk"))
        assertFalse(OfflineMapFilePolicy0708.isSupportedName("mapas.zip"))
        assertFalse(OfflineMapFilePolicy0708.isSupportedName("foto.jpg"))
        assertFalse(OfflineMapFilePolicy0708.isSupportedName("mapa.mwm.tmp"))
    }

    @Test
    fun sanitizesUnsafePathCharactersWithoutChangingMwmSuffix() {
        assertEquals(
            "Brazil_Sao_Paulo.mwm",
            OfflineMapFilePolicy0708.sanitizeFileName("Brazil/Sao:Paulo.mwm"),
        )
    }

    @Test
    fun contractKeepsFarolAuthorityUntouchedAtFoundationStage() {
        assertEquals("OFFLINE_MAP_IMPORT_0708", OfflineMapFilePolicy0708.CONTRACT_MARKER)
        assertEquals(
            "OFFLINE_MAPS_DO_NOT_CHANGE_FAROL_AUTHORITY_0708",
            OfflineMapFilePolicy0708.NO_FAROL_AUTHORITY_MARKER,
        )
    }
}
