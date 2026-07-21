package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCardSnapshotStabilizerTest {
    private val packageName = "sinet.startup.indriver"

    @Test
    fun exactTwoAddressesAlwaysEnterImmediately() {
        val stabilizer = RideCardSnapshotStabilizer()

        assertFalse(stabilizer.shouldIgnore(packageName, addressCount = 2, active = true, nowMillis = 1_000L))
    }

    @Test
    fun expandedInDriveSnapshotsAreAlwaysRejected() {
        val stabilizer = RideCardSnapshotStabilizer()

        assertTrue(stabilizer.shouldIgnore(packageName, addressCount = 4, active = true, nowMillis = 1_000L))
        assertTrue(stabilizer.shouldIgnore(packageName, addressCount = 6, active = true, nowMillis = 9_000L))
    }

    @Test
    fun inactiveSnapshotIsHandledByImmediateClearPipeline() {
        val stabilizer = RideCardSnapshotStabilizer()

        assertFalse(stabilizer.shouldIgnore(packageName, addressCount = 0, active = false, nowMillis = 1_000L))
    }

    @Test
    fun otherApplicationsAreNotRestrictedToTwoAddresses() {
        val stabilizer = RideCardSnapshotStabilizer()

        assertFalse(
            stabilizer.shouldIgnore(
                packageName = "com.ubercab.driver",
                addressCount = 5,
                active = true,
                nowMillis = 1_200L,
            ),
        )
    }
}
