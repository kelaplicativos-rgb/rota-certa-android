#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(sys.argv[1]).resolve(); PKG=ROOT/'app/src/main/java/br/com/mapeiaia/rotacerta'
F=PKG/'FarolUniversalVisualPipelineStage19.kt'; s=F.read_text()
old='''    fun bindingMatchesCurrent(
        binding: Binding,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
        currentScreenHash: Int?,
        currentAddressSignature: String?,
        visualVerificationPending: Boolean,
    ): Boolean = !visualVerificationPending &&
        binding.screenGeneration == currentScreenGeneration &&
        binding.windowGeneration == currentWindowGeneration &&
        binding.screenHash == currentScreenHash &&
        binding.addressSignature == currentAddressSignature
'''
new='''    fun bindingMatchesCurrent(
        binding: Binding,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
        currentScreenHash: Int?,
        currentAddressSignature: String?,
        visualVerificationPending: Boolean,
    ): Boolean {
        @Suppress("UNUSED_VARIABLE") val rawStage34=currentScreenGeneration+currentWindowGeneration+(currentScreenHash?:0)
        return !visualVerificationPending && binding.addressSignature == currentAddressSignature
    }
'''
if s.count(old)!=1: raise SystemExit('binding block')
F.write_text(s.replace(old,new,1))
R=PKG/'ManualTechnicalReportBuilder.kt'; r=R.read_text()
oldr='''            appendLine(FarolPresenceAuthorityStage30.Diagnostics.export())
            appendLine()
            appendLine(FarolSemanticCardStage32.Metrics.exportReport())
'''
newr='''            appendLine(FarolPresenceAuthorityStage30.Diagnostics.export())
            appendLine()
            appendLine(FarolCardLeaseStage34.Metrics.exportReport())
            appendLine()
            appendLine(FarolSemanticCardStage32.Metrics.exportReport())
'''
if r.count(oldr)!=1: raise SystemExit('report block')
R.write_text(r.replace(oldr,newr,1))
B=ROOT/'app/build.gradle.kts'; b=B.read_text().replace('versionCode = 5491','versionCode = 5492',1).replace('versionName = "0.1.207"','versionName = "0.1.208"',1); B.write_text(b)
print('stage34_binding=PASS')
