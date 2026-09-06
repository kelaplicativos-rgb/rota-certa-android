package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolRealDevice0188Test {
    private val selected = setOf("com.example.driver")

    private fun block(
        id: String,
        text: String,
        parentId: String? = null,
        depth: Int = 2,
        windowId: Int = 10,
        layer: Int = 1,
        source: FarolEvidenceSource0188 = FarolEvidenceSource0188.Accessibility,
        left: Int = 50,
        top: Int = 100,
        right: Int = 1000,
        bottom: Int = 600,
        syntheticRoot: Boolean = false,
    ) = FarolCardBlock0188(
        id = id,
        parentId = parentId,
        packageName = "com.example.driver",
        windowId = windowId,
        windowLayer = layer,
        depth = depth,
        text = text,
        source = source,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        syntheticRoot = syntheticRoot,
    )

    @Test
    fun facialRecognitionDoesNotAuthorizeRoute() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(block("w10/root", "Reconhecimento facial\nAbra a boca\nRua das Flores, 100\nAvenida Brasil, 200")),
        )
        assertFalse(decision.authorized)
    }

    @Test
    fun twoAddressesInSameCardAuthorizeLastDestination() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(block("w10/card", "Rua das Flores, 100\nAvenida Brasil, 200")),
        )
        assertTrue(decision.authorized)
        assertEquals("Avenida Brasil, 200", decision.authorization?.destination)
    }

    @Test
    fun addressesFromDifferentCardsNeverCombine() {
        val first = block("w10/a", "Rua das Flores, 100", top = 100, bottom = 350)
        val second = block("w10/b", "Avenida Brasil, 200", top = 500, bottom = 750)
        val decision = FarolRealDeviceGate0188.evaluate("com.example.driver", selected, listOf(first, second))
        assertFalse(decision.authorized)
    }

    @Test
    fun popupWindowCanBeAuthorizedWhenPackageAndWindowStayBound() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(block("w44/popup", "Rua Augusta, 100\nAvenida Paulista, 900", windowId = 44, layer = 9)),
        )
        assertTrue(decision.authorized)
        assertEquals(44, decision.authorization?.windowId)
    }

    @Test
    fun selectedPackageAloneNeverAuthorizes() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(block("w10/home", "Status perfeito! Você está pronto")),
        )
        assertFalse(decision.authorized)
    }

    @Test
    fun upperCardWinsWhenTwoCompleteCardsShareWindow() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(
                block("w10/a", "Rua Apeninos, 100\nAvenida Jabaquara, 900", top = 150, bottom = 550),
                block("w10/b", "Rua Vergueiro, 200\nAvenida Paulista, 1000", top = 800, bottom = 1200),
            ),
        )
        assertTrue(decision.authorized)
        assertEquals("Avenida Jabaquara, 900", decision.authorization?.destination)
    }

    @Test
    fun higherWindowLayerWinsEvenWhenItsCardIsLowerOnScreen() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(
                block("feed", "Rua Feed, 10\nAvenida Feed, 20", windowId = 10, layer = 1, top = 100),
                block("popup", "Rua Popup, 30\nAvenida Popup, 40", windowId = 11, layer = 8, top = 700),
            ),
        )
        assertTrue(decision.authorized)
        assertEquals(11, decision.authorization?.windowId)
        assertEquals("Avenida Popup, 40", decision.authorization?.destination)
    }

    @Test
    fun upperPartialCardBlocksLowerCompleteCard() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(
                block("upper", "Rua Parcial, 10", top = 100, bottom = 400),
                block("lower", "Rua Completa, 20\nAvenida Completa, 30", top = 700, bottom = 1100),
            ),
        )
        assertFalse(decision.authorized)
    }

    @Test
    fun threeAddressesInOneCardUseFinalAddress() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(block("w10/card", "Rua Apeninos, 100\nRua Vergueiro, 200\nAvenida Paulista, 1000")),
        )
        assertTrue(decision.authorized)
        assertEquals("Avenida Paulista, 1000", decision.authorization?.destination)
    }

    @Test
    fun syntheticRootNeverCombinesMultipleCards() {
        val decision = FarolRealDeviceGate0188.evaluate(
            "com.example.driver",
            selected,
            listOf(
                block(
                    "root",
                    "Rua A, 10\nAvenida A, 20\nRua B, 30\nAvenida B, 40",
                    depth = 0,
                    top = 0,
                    bottom = 2000,
                    syntheticRoot = true,
                ),
                block("root/a", "Rua A, 10", parentId = "root", top = 100, bottom = 300),
                block("root/b", "Rua B, 30", parentId = "root", top = 900, bottom = 1100),
            ),
        )
        assertFalse(decision.authorized)
    }

    @Test
    fun unknownSelectedPackageUsesSameUniversalCore() {
        val packageName = "org.example.future.ride"
        val decision = FarolRealDeviceGate0188.evaluate(
            packageName,
            setOf(packageName),
            listOf(
                FarolCardBlock0188(
                    id = "future/card",
                    packageName = packageName,
                    windowId = 7,
                    depth = 2,
                    text = "Rua Augusta, 10\nAvenida Paulista, 100",
                    source = FarolEvidenceSource0188.Ocr,
                    left = 50,
                    top = 100,
                    right = 1000,
                    bottom = 600,
                ),
            ),
        )
        assertTrue(decision.authorized)
    }
    @Test
    fun realInDrivePoiInsideOneCoherentBlockReachesUniversalGate0194() {
        val packageName = "sinet.startup.indriver"
        val decision = FarolRealDeviceGate0188.evaluate(
            packageName,
            setOf(packageName),
            listOf(
                FarolCardBlock0188(
                    id = "real-0194",
                    parentId = null,
                    packageName = packageName,
                    windowId = 6544,
                    windowLayer = 1,
                    depth = 2,
                    text = "R. Carlos Vivaldi, 197 (Cidade Sao Mateus, Sao Paulo - SP, 03965-030)\n" +
                        "Parque do Carmo (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)",
                    source = FarolEvidenceSource0188.Accessibility,
                    left = 80,
                    top = 360,
                    right = 1000,
                    bottom = 720,
                    syntheticRoot = false,
                ),
            ),
        )
        assertTrue(decision.authorized)
        assertEquals(
            DestinationAddressIdentityPolicy.cleanDisplayAddress(
                "Parque do Carmo (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)",
            ),
            decision.authorization?.destination,
        )
    }

}
