package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLaunchPolicy0186Test {
    @Test
    fun genericHomeNeverReusesPreviousModule() {
        assertNull(HomeLaunchPolicy0186.requestedModule(HomeLaunchPolicy0186.MODE_COLLAPSED, "route"))
    }

    @Test
    fun deliberateModuleLaunchOpensOnlyRequestedModule() {
        assertEquals("alerts", HomeLaunchPolicy0186.requestedModule(HomeLaunchPolicy0186.MODE_MODULE, "alerts"))
    }
}
