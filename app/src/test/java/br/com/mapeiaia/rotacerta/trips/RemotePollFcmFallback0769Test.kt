package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemotePollFcmFallback0769Test {
    @Test fun healthyPollingDoesNotEscalateOptionalFcmFailure() {
        assertFalse(shouldEscalateRemotePushFailure0769(true, true))
        assertFalse(shouldEscalateRemotePushFailure0769(false, false))
        assertTrue(shouldEscalateRemotePushFailure0769(true, false))
    }

    @Test fun authenticatedPollingClearsOnlyTransportFallbackAttention() {
        for (reason in listOf(
            "FCM_TOKEN_FAILED", "FCM_TOKEN_UNAVAILABLE",
            "PUSH_REGISTER_FAILED", "PUSH_REGISTER_REJECTED",
            "PUSH_REGISTRATION_PENDING", "REMOTE_POLL_LISTENER_INACTIVE"
        )) {
            assertTrue(shouldRecoverRemoteTransportAttention0769("ATTENTION", reason, true))
            assertFalse(shouldRecoverRemoteTransportAttention0769("ATTENTION", reason, false))
        }
    }

    @Test fun pendingConsentAndRealFaultMustRemainVisible() {
        for (reason in listOf(
            "REMOTE_REQUEST_RECEIVED",
            "REMOTE_ACCESS_CHECK_FAILED",
            "REMOTE_ACCESS_PROVISION_FAILED",
            "DRIVER_ONLINE_CONFIGURATION_MISSING",
            "REMOTE_DEVICE_COLLECTION_FAILED"
        )) {
            assertFalse(shouldRecoverRemoteTransportAttention0769("ATTENTION", reason, true))
        }
        assertFalse(shouldRecoverRemoteTransportAttention0769("REQUESTED", "FCM_TOKEN_FAILED", true))
        assertFalse(shouldRecoverRemoteTransportAttention0769("READY", "FCM_TOKEN_FAILED", true))
    }
}
