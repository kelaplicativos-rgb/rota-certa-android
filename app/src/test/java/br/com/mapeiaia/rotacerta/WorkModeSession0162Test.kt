package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkModeSession0162Test {
    @Test
    fun masterSwitchChangesReadingAndRuntimeTogether() {
        val off = WorkModePolicy0162.setEnabled(AppSettings(appEnabled = true, liveReadingEnabled = true), false)
        assertFalse(off.appEnabled)
        assertFalse(off.liveReadingEnabled)
        assertFalse(WorkModePolicy0162.isEnabled(off))
        assertTrue(WorkModePolicy0162.isEnabled(WorkModePolicy0162.setEnabled(off, true)))
    }

    @Test
    fun passiveAndAccidentalAppsAreRemovedButUnknownDriverCanRemain() {
        val sanitized = DriverAppPackagePolicy0162.sanitize(
            listOf(
                "com.ubercab.driver",
                "com.sec.android.app.launcher",
                "com.openai.chatgpt",
                "com.example.worldwide.driver",
            ),
            "br.com.mapeiaia.rotacerta",
        )
        assertEquals(setOf("com.example.worldwide.driver", "com.ubercab.driver"), sanitized)
    }

    @Test
    fun selectedRootWinsSystemOverlay() {
        assertEquals(
            "com.ubercab.driver",
            DriverCardEventResolver0162.resolve(
                eventPackageName = "com.android.systemui",
                rootPackageName = "com.ubercab.driver",
                selectedPackages = setOf("com.ubercab.driver"),
                ownPackageName = "br.com.mapeiaia.rotacerta",
            ),
        )
    }

    @Test
    fun staleUberEventCannotReadChatGptOrLauncherRoot() {
        val selected = setOf("com.ubercab.driver", "com.openai.chatgpt", "com.sec.android.app.launcher")
        assertNull(DriverCardEventResolver0162.resolve("com.ubercab.driver", "com.openai.chatgpt", selected, "br.com.mapeiaia.rotacerta"))
        assertNull(DriverCardEventResolver0162.resolve("com.ubercab.driver", "com.sec.android.app.launcher", selected, "br.com.mapeiaia.rotacerta"))
    }

    @Test
    fun sessionRejectsLateResultAfterWindowChange() {
        val gate = DriverCardSessionGate0162()
        val first = gate.begin("com.ubercab.driver", 10)
        assertEquals(first, gate.begin("com.ubercab.driver", 10))
        val second = gate.begin("com.ubercab.driver", 11)
        assertNotEquals(first.generation, second.generation)
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        gate.invalidate()
        assertFalse(gate.isCurrent(second))
    }

    @Test
    fun displayIdentityIgnoresAnimatedTextAndOnlyChangesForSessionOrDestination() {
        val first = DriverCardDisplayIdentity0162.fingerprint("com.ubercab.driver", 7, null)
        val same = DriverCardDisplayIdentity0162.fingerprint("com.ubercab.driver", 7, null)
        val destination = DriverCardDisplayIdentity0162.fingerprint("com.ubercab.driver", 7, "uber|rua a 10")
        assertEquals(first, same)
        assertNotEquals(first, destination)
    }

    @Test
    fun appSpecificSanitizerPreservesOriginalAndAddsMarkerAlias() {
        val prepared = DriverCardTextSanitizer0162.prepare(
            "com.ubercab.driver",
            "Ponto de encontro: Rua Alfa, 10\nLocal de destino: Rua Beta, 20",
        )
        assertTrue("Ponto de encontro: Rua Alfa, 10" in prepared)
        assertTrue("Embarque: Rua Alfa, 10" in prepared)
        assertTrue("Destino: Rua Beta, 20" in prepared)
    }
}
