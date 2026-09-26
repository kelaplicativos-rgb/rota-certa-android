package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMapsApiKeyPolicyTest {
    private fun policySource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsApiKeyPolicy.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsApiKeyPolicy.kt"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("GoogleMapsApiKeyPolicy.kt nao encontrado")

    @Test
    fun keyPriorityFollowsTheCurrentMigrationStage() {
        val buildHasPriority =
            "bundledValue.orEmpty().trim().ifBlank { userValue.orEmpty().trim() }" in policySource()
        val expected = if (buildHasPriority) "build-key" else "user-key"

        assertEquals(expected, GoogleMapsApiKeyPolicy.effective(" user-key ", "build-key"))
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
