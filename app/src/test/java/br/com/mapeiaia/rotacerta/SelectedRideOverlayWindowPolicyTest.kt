package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedRideOverlayWindowPolicyTest {
    private val selected = setOf(RideCardTemplateMatcher.INDRIVE_PACKAGE)

    @Test
    fun directSelectedPackageAlwaysWins() {
        assertEquals(
            RideCardTemplateMatcher.INDRIVE_PACKAGE,
            SelectedRideOverlayWindowPolicy.resolve(
                rootPackageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                lastSelectedPackageName = null,
                lastSelectedAtMillis = 0L,
                selectedPackages = selected,
                nowMillis = 10_000L,
            ),
        )
    }

    @Test
    fun transientSystemWindowKeepsRecentInDriveCardSession() {
        assertEquals(
            RideCardTemplateMatcher.INDRIVE_PACKAGE,
            SelectedRideOverlayWindowPolicy.resolve(
                rootPackageName = "com.android.systemui",
                lastSelectedPackageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                lastSelectedAtMillis = 10_000L,
                selectedPackages = selected,
                nowMillis = 14_500L,
            ),
        )
    }

    @Test
    fun staleOrUnselectedPackageIsNeverBorrowed() {
        assertNull(
            SelectedRideOverlayWindowPolicy.resolve(
                rootPackageName = "com.android.systemui",
                lastSelectedPackageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                lastSelectedAtMillis = 10_000L,
                selectedPackages = selected,
                nowMillis = 17_000L,
            ),
        )
        assertNull(
            SelectedRideOverlayWindowPolicy.resolve(
                rootPackageName = "com.android.systemui",
                lastSelectedPackageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                lastSelectedAtMillis = 10_000L,
                selectedPackages = emptySet(),
                nowMillis = 11_000L,
            ),
        )
    }
}
