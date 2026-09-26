#!/usr/bin/env python3
from pathlib import Path
import sys
R=Path(sys.argv[1]).resolve()
F=R/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage32Test.kt'; t=F.read_text()
name='completedSameSemanticScreenshotIsDeduped'; start=f'    @Test fun {name}() {{'; a=t.find(start)
if a<0: raise SystemExit('missing '+name)
b=t.find('\n    @Test fun ',a+len(start)); b=b if b>=0 else t.rfind('\n}')
body='''    @Test fun completedSameCardLeaseCanReacquireFrameStage34() {
        val g=FarolSemanticCardStage32.ScreenshotRateGate(); g.request(1000,1); g.complete(1,true); assertEquals(FarolSemanticCardStage32.ScreenshotDecisionKind.START,g.request(1500,1).kind)
    }'''
F.write_text(t[:a]+body+'\n'+t[b:])

F=R/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolUniversalVisualPipelineStage19Test.kt'; t=F.read_text()
t=t.replace('''    @Test fun oldOcrBindingIsRejected() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 4, 4, 10, "visual|rua b 20", false))
    }''','''    @Test fun rawGenerationChurnKeepsSameOcrCardLeaseStage34() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertTrue(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 4, 4, 10, "visual|rua b 20", false))
    }''')
t=t.replace('''    @Test fun oldRouteBindingIsRejected() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 3, 5, 10, "visual|rua b 20", false))
    }''','''    @Test fun rawWindowChurnKeepsSameRouteCardLeaseStage34() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|rua b 20")
        assertTrue(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 3, 5, 10, "visual|rua b 20", false))
    }''')
F.write_text(t)

F=R/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolCausalCorrectionStage21Test.kt'; t=F.read_text()
t=t.replace('''    @Test fun oldRouteBindingIsStillRejected() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|x")
        assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 4, 4, 10, "visual|x", false))
    }''','''    @Test fun rawGenerationChurnKeepsSameRouteCardLeaseStage34() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(3, 4, 10, "visual|x")
        assertTrue(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(binding, 4, 4, 10, "visual|x", false))
    }''')
F.write_text(t)
print('stage34_test_frame_and_legacy_binding_contract=PASS')
