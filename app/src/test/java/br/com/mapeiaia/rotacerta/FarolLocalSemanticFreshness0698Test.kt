package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolLocalSemanticFreshness0698Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun liveSource(): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun stage36TokenSurvivesUnrelatedWindowChurnForSameDestination() {
        val authority = FarolRuntimeAuthorityStage36.Authority(sessionStartWallMillis = 0L)
        authority.setManualAuthority(true)
        val signature = "com.ubercab|Rua Bahamas, 123"
        authority.bindDestination(signature)
        val token = authority.captureDestinationToken(signature)
        assertTrue(authority.isFresh(token))

        authority.observeWindowBoundary("com.android.systemui")
        authority.observeWindowBoundary("com.whatsapp")

        assertTrue(authority.isFresh(token))
        assertTrue(
            FarolLocalSemanticFreshness0698.evaluate(
                runtimeTokenFresh = authority.isFresh(token),
                bindingAddressSignature = signature,
                activeAddressSignature = signature,
            ) == FarolLocalSemanticFreshness0698.Verdict.ACCEPTED_SAME_DESTINATION,
        )
    }

    @Test
    fun destinationTransitionInvalidatesOldLocalResult() {
        val authority = FarolRuntimeAuthorityStage36.Authority(sessionStartWallMillis = 0L)
        authority.setManualAuthority(true)
        val first = "com.ubercab|Rua Bahamas, 123"
        val second = "com.ubercab|Rua Peixes, 456"
        authority.bindDestination(first)
        val oldToken = authority.captureDestinationToken(first)
        assertTrue(authority.isFresh(oldToken))

        authority.bindDestination(second)

        assertFalse(authority.isFresh(oldToken))
        assertTrue(
            FarolLocalSemanticFreshness0698.evaluate(
                runtimeTokenFresh = authority.isFresh(oldToken),
                bindingAddressSignature = first,
                activeAddressSignature = second,
            ) != FarolLocalSemanticFreshness0698.Verdict.ACCEPTED_SAME_DESTINATION,
        )
    }

    @Test
    fun provenVisualLeaseClearInvalidatesOldLocalResult() {
        val authority = FarolRuntimeAuthorityStage36.Authority(sessionStartWallMillis = 0L)
        authority.setManualAuthority(true)
        val signature = "com.ubercab|Travessa Alonso Ribera, 27"
        authority.bindDestination(signature)
        val oldToken = authority.captureDestinationToken(signature)
        assertTrue(authority.isFresh(oldToken))

        authority.clearVisualLease("test_proven_card_disappearance")

        assertFalse(authority.isFresh(oldToken))
    }

    @Test
    fun criticalLocalFreshnessDoesNotConsultStage46SurfaceEpoch() {
        val live = liveSource()
        val start = live.indexOf("private fun stage19LocalSemanticVerdict0698(")
        val end = live.indexOf("private fun stage26BindingKey(", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)

        assertTrue(block.contains("stage36BindingWorkToken"))
        assertTrue(block.contains("stage36RuntimeAuthority.isFresh"))
        assertTrue(block.contains("FarolLocalSemanticFreshness0698.evaluate"))
        assertFalse(block.contains("stage46BindingSurfaceToken"))
        assertFalse(block.contains("stage46VisualEpoch"))
        assertFalse(block.contains("surfaceFresh"))
    }

    @Test
    fun rawPreSanitizationIdentityNoLongerOwnsRuntimeLease() {
        val live = liveSource()
        val rawStart = live.indexOf("val stableAddressSignatureStage635")
        val sanitizeStart = live.indexOf("val routeSanitization0684", rawStart)
        assertTrue(rawStart >= 0 && sanitizeStart > rawStart)
        val rawBlock = live.substring(rawStart, sanitizeStart)
        assertFalse(rawBlock.contains("stage36RuntimeAuthority.bindDestination"))
        assertFalse(rawBlock.contains("stage32SemanticGate.observeCandidate"))

        val sanitizedStart = live.indexOf("val stableSanitizedAddressSignature0697", sanitizeStart)
        val approvedLog = live.indexOf("S684_ROUTE_ADDRESS_APPROVED", sanitizedStart)
        assertTrue(sanitizedStart > sanitizeStart && approvedLog > sanitizedStart)
        val sanitizedBlock = live.substring(sanitizedStart, approvedLog)
        assertTrue(sanitizedBlock.contains("stage36RuntimeAuthority.bindDestination(stableSanitizedAddressSignature0697)"))
        assertTrue(sanitizedBlock.contains("stage32SemanticGate.observeCandidate(stableSanitizedAddressSignature0697)"))
    }

    @Test
    fun localPaintExplicitlyUsesSemanticAuthority() {
        val live = liveSource()
        val start = live.indexOf("private suspend fun applyUniversalPreliminaryColorStage637(")
        val end = live.indexOf("private suspend fun applyUniversalTwoAddressResultStage19(", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)

        assertTrue(block.contains("isStage19LocalSemanticFresh0698"))
        assertTrue(block.contains("PaintAuthority.LOCAL_SEMANTIC"))
        assertFalse(block.contains("stage19VisualVerificationPending) return"))
    }
}
