package br.com.mapeiaia.rotacerta.trips

import android.webkit.WebViewClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaMainFrameRecovery0418Test {
    @Test
    fun `address unreachable class retries only for allowed interactive main frame`() {
        assertTrue(
            shouldRetryBlaBlaMainFrameNavigation0418(
                errorCode = WebViewClient.ERROR_CONNECT,
                isMainFrame = true,
                interactiveMode = true,
                allowedUrl = true,
                completedAttempts = 0,
            ),
        )
        assertFalse(
            shouldRetryBlaBlaMainFrameNavigation0418(
                errorCode = WebViewClient.ERROR_CONNECT,
                isMainFrame = false,
                interactiveMode = true,
                allowedUrl = true,
                completedAttempts = 0,
            ),
        )
        assertFalse(
            shouldRetryBlaBlaMainFrameNavigation0418(
                errorCode = WebViewClient.ERROR_CONNECT,
                isMainFrame = true,
                interactiveMode = false,
                allowedUrl = true,
                completedAttempts = 0,
            ),
        )
        assertFalse(
            shouldRetryBlaBlaMainFrameNavigation0418(
                errorCode = WebViewClient.ERROR_CONNECT,
                isMainFrame = true,
                interactiveMode = true,
                allowedUrl = false,
                completedAttempts = 0,
            ),
        )
    }

    @Test
    fun `transient dns timeout and io errors share bounded recovery policy`() {
        listOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO,
        ).forEach { code ->
            assertTrue(
                shouldRetryBlaBlaMainFrameNavigation0418(
                    errorCode = code,
                    isMainFrame = true,
                    interactiveMode = true,
                    allowedUrl = true,
                    completedAttempts = 1,
                ),
            )
        }
    }

    @Test
    fun `recovery stops after three attempts and does not retry non transport errors`() {
        assertFalse(
            shouldRetryBlaBlaMainFrameNavigation0418(
                errorCode = WebViewClient.ERROR_CONNECT,
                isMainFrame = true,
                interactiveMode = true,
                allowedUrl = true,
                completedAttempts = BLABLA_MAIN_FRAME_RECOVERY_MAX_ATTEMPTS_0418,
            ),
        )
        assertFalse(
            shouldRetryBlaBlaMainFrameNavigation0418(
                errorCode = WebViewClient.ERROR_UNSAFE_RESOURCE,
                isMainFrame = true,
                interactiveMode = true,
                allowedUrl = true,
                completedAttempts = 0,
            ),
        )
    }
}
