package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCardSnapshotStabilizerTest {
    private val packageName = "sinet.startup.indriver"

    @Test
    fun exactTwoAddressesAlwaysEnterImmediately() {
        val stabilizer = RideCardSnapshotStabilizer()

        assertFalse(
            stabilizer.shouldIgnore(
                packageName = packageName,
                addressCount = 2,
                active = true,
                nowMillis = 1_000L,
            ),
        )
        assertFalse(
            stabilizer.shouldIgnore(
                packageName = packageName,
                addressCount = 2,
                active = true,
                nowMillis = 1_100L,
            ),
        )
    }

    @Test
    fun expandedTransitionSnapshotsAreIgnoredInsideWindow() {
        val stabilizer = RideCardSnapshotStabilizer()
        stabilizer.shouldIgnore(packageName, addressCount = 2, active = true, nowMillis = 1_000L)

        assertTrue(stabilizer.shouldIgnore(packageName, addressCount = 4, active = true, nowMillis = 2_100L))
        assertTrue(stabilizer.shouldIgnore(packageName, addressCount = 6, active = true, nowMillis = 2_800L))
    }

    @Test
    fun expandedSnapshotIsAcceptedAfterProtectionWindow() {
        val stabilizer = RideCardSnapshotStabilizer(expansionWindowMillis = 2_800L)
        stabilizer.shouldIgnore(packageName, addressCount = 2, active = true, nowMillis = 1_000L)

        assertFalse(stabilizer.shouldIgnore(packageName, addressCount = 4, active = true, nowMillis = 3_801L))
    }

    @Test
    fun otherApplicationsAreNeverRestrictedByInDriveRule() {
        val stabilizer = RideCardSnapshotStabilizer()
        stabilizer.shouldIgnore(packageName, addressCount = 2, active = true, nowMillis = 1_000L)

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
