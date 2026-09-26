package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBuildInfoTest {
    @Test
    fun copyTextUsesInstalledBuildConfig() {
        assertEquals(BuildConfig.VERSION_NAME, AppBuildInfo.versionName)
        assertEquals(BuildConfig.VERSION_CODE, AppBuildInfo.versionCode)
        assertTrue(AppBuildInfo.copyText().contains("Rota Certa ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        assertTrue(AppBuildInfo.copyText().contains("commit ${AppBuildInfo.commitShort}"))
        assertTrue(AppBuildInfo.copyText().contains("branch ${AppBuildInfo.branch}"))
    }

    @Test
    fun automaticBuildMetadataIsPresent() {
        assertTrue(BuildConfig.BUILD_GIT_SHA.isNotBlank())
        assertTrue(BuildConfig.BUILD_GIT_BRANCH.isNotBlank())
        assertTrue(BuildConfig.BUILD_GENERATED_AT.isNotBlank())
    }
}
