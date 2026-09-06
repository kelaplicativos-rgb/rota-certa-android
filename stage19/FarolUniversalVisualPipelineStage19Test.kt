package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolUniversalVisualPipelineStage19Test {
    private fun block(
        pkg: String? = "unknown.package",
        text: String = "Rua A, 10\nRua B, 20",
        id: String = "w/0/card",
        windowId: Int = 7,
        layer: Int = 10,
        depth: Int = 3,
        top: Int = 100,
        bottom: Int = 500,
        source: FarolUniversalVisualPipelineStage19.Source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
    ) = FarolUniversalVisualPipelineStage19.VisualBlock(
        id = id,
        parentId = "w/0",
        metadataPackageName = pkg,
        windowId = windowId,
        windowLayer = layer,
        depth = depth,
        text = text,
        source = source,
        left = 40,
        top = top,
        right = 1040,
        bottom = bottom,
    )

    private fun evaluate(pkg: String?) = FarolUniversalVisualPipelineStage19.evaluate(listOf(block(pkg = pkg)))

    @Test fun twoAddressesOverLauncherAnalyzes() = assertNotNull(evaluate("com.sec.android.app.launcher"))
    @Test fun twoAddressesOverWhatsAppAnalyzes() = assertNotNull(evaluate("com.whatsapp"))
    @Test fun twoAddressesOverChatGptAnalyzes() = assertNotNull(evaluate("com.openai.chatgpt"))
    @Test fun twoAddressesOverYouTubeAnalyzes() = assertNotNull(evaluate("com.google.android.youtube"))
    @Test fun twoAddressesOverBrowserAnalyzes() = assertNotNull(evaluate("com.android.chrome"))
    @Test fun twoAddressesOverMapsAnalyzes() = assertNotNull(evaluate("com.google.android.apps.maps"))
    @Test fun twoAddressesOverWazeAnalyzes() = assertNotNull(evaluate("com.waze"))
    @Test fun twoAddressesOverUberAnalyzes() = assertNotNull(evaluate("com.ubercab.driver"))
    @Test fun twoAddressesOver99Analyzes() = assertNotNull(evaluate("com.app99.driver"))
    @Test fun twoAddressesOverInDriveAnalyzes() = assertNotNull(evaluate("sinet.startup.indriver"))
    @Test fun twoAddressesOverUnknownPackageAnalyzes() = assertNotNull(evaluate("totally.unknown.package"))

    @Test fun accessibilityEmptyAndValidOcrAnalyzes() {
        val r = FarolUniversalVisualPipelineStage19.evaluate(listOf(block(pkg = null, source = FarolUniversalVisualPipelineStage19.Source.Ocr)))
        assertNotNull(r)
        assertEquals(FarolUniversalVisualPipelineStage19.Source.Ocr, r!!.source)
    }

    @Test fun rootPackageDifferentFromBackgroundDoesNotChangeVisualAuthority() {
        val a = evaluate("com.whatsapp")!!
        val b = evaluate("com.ubercab.driver")!!
        assertEquals(a.addressSignature, b.addressSignature)
        assertEquals(a.screenHash, b.screenHash)
    }

    @Test fun eventPackageFromAnotherAppCannotEraseSameVisual() {
        val before = evaluate("com.google.android.youtube")!!
        val after = evaluate("com.android.systemui")!!
        assertEquals(before.screenHash, after.screenHash)
    }

    @Test fun transientSystemUiDoesNotChangeSameVisualIdentity() {
        val before = evaluate("com.ubercab.driver")!!
        val after = evaluate("com.android.systemui")!!
        assertEquals(before.addressSignature, after.addressSignature)
        assertEquals(before.screenHash, after.screenHash)
    }

    @Test fun realAddressChangeInvalidatesPreviousVisualIdentity() {
        val before = FarolUniversalVisualPipelineStage19.evaluate(listOf(block(text = "Rua A, 10\nRua B, 20")))!!
        val after = FarolUniversalVisualPipelineStage19.evaluate(listOf(block(text = "Rua A, 10\nRua C, 30")))!!
        assertNotEquals(before.addressSignature, after.addressSignature)
        assertNotEquals(before.screenHash, after.screenHash)
    }

    @Test fun oldOcrBindingIsRejected() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 4, 4, 10, "visual|rua b 20", false))
    }

    @Test fun oldRouteBindingIsRejected() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 3, 5, 10, "visual|rua b 20", false))
    }

    @Test fun cacheFromOtherDestinationCannotMatchCurrentBinding() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 3, 4, 11, "visual|rua c 30", false))
    }

    @Test fun pendingVisualVerificationBlocksOldRoutePainting() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 3, 4, 10, "visual|rua b 20", true))
    }

    @Test fun twoDifferentAddressBlocksAreNotMixed() {
        val result = FarolUniversalVisualPipelineStage19.evaluate(
            listOf(
                block(text = "Rua A, 10", id = "upper", top = 100, bottom = 300),
                block(text = "Rua B, 20", id = "lower", top = 700, bottom = 900),
            ),
        )
        assertNull(result)
    }

    @Test fun onlyOneAddressDoesNotDecide() = assertNull(FarolUniversalVisualPipelineStage19.evaluate(listOf(block(text = "Rua A, 10"))))
    @Test fun noAddressDoesNotDecide() = assertNull(FarolUniversalVisualPipelineStage19.evaluate(listOf(block(text = "Aceitar R$ 18,00"))))

    @Test fun twoCurrentAddressesUseLastAsDestination() {
        val result = FarolUniversalVisualPipelineStage19.evaluate(listOf(block(text = "Rua Origem, 10\nRua Destino, 99")))!!
        assertEquals("Rua Origem, 10", result.pickup)
        assertEquals("Rua Destino, 99", result.destination)
    }

    @Test fun threeCurrentAddressesStillUseLastAsDestination() {
        val result = FarolUniversalVisualPipelineStage19.evaluate(listOf(block(text = "Rua A, 10\nRua B, 20\nRua C, 30")))!!
        assertEquals("Rua C, 30", result.destination)
    }

    @Test fun higherVisualWindowWinsOverLowerWindow() {
        val result = FarolUniversalVisualPipelineStage19.evaluate(
            listOf(
                block(text = "Rua Baixa, 1\nRua Baixa, 2", windowId = 1, layer = 2, id = "low"),
                block(text = "Rua Alta, 10\nRua Alta, 20", windowId = 2, layer = 9, id = "high"),
            ),
        )!!
        assertEquals("Rua Alta, 20", result.destination)
    }

    @Test fun upperPartialCardBlocksLowerCompleteCardInSameWindow() {
        val result = FarolUniversalVisualPipelineStage19.evaluate(
            listOf(
                block(text = "Rua Superior, 10", id = "upper", top = 100, bottom = 300),
                block(text = "Rua Inferior, 20\nRua Inferior, 30", id = "lower", top = 700, bottom = 1000),
            ),
        )
        assertNull(result)
    }

    @Test fun exactSameBindingCanPaintWhenNoVerificationPending() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertTrue(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 3, 4, 10, "visual|rua b 20", false))
    }

    @Test fun contractExplicitlyDeclaresPackageMetadataOnly() {
        assertEquals("PACKAGE_IDENTITY_IS_NOT_VISUAL_AUTHORITY_STAGE19", FarolUniversalVisualPipelineStage19.PACKAGE_IS_METADATA_MARKER)
    }
}
