package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMapsApiKeyPolicyTest {
    @Test
    fun userKeyWinsAndBundledKeyIsFallback() {
        assertEquals("user-key", GoogleMapsApiKeyPolicy.effective(" user-key ", "build-key"))
        assertEquals("build-key", GoogleMapsApiKeyPolicy.effective("", " build-key "))
        assertTrue(GoogleMapsApiKeyPolicy.isConfigured("", "build-key"))
        assertFalse(GoogleMapsApiKeyPolicy.isConfigured("", ""))
    }

    @Test
    fun restoringBackupWithoutKeyNeverDeletesWorkingKey() {
        assertEquals(
            "current-key",
            GoogleMapsApiKeyPolicy.valueAfterRestore("current-key", "", "build-key"),
        )
        assertEquals(
            "build-key",
            GoogleMapsApiKeyPolicy.valueAfterRestore("", "", "build-key"),
        )
        assertEquals(
            "backup-key",
            GoogleMapsApiKeyPolicy.valueAfterRestore("current-key", "backup-key", "build-key"),
        )
    }
}
