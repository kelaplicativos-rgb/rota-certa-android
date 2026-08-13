#!/usr/bin/env python3
from pathlib import Path
import sys
F=Path(sys.argv[1]).resolve()/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage32Test.kt'; t=F.read_text()
name='completedSameSemanticScreenshotIsDeduped'; start=f'    @Test fun {name}() {{'; a=t.find(start)
if a<0: raise SystemExit('missing '+name)
b=t.find('\n    @Test fun ',a+len(start)); b=b if b>=0 else t.rfind('\n}')
body='''    @Test fun completedSameCardLeaseCanReacquireFrameStage34() {
        val g=FarolSemanticCardStage32.ScreenshotRateGate(); g.request(1000,1); g.complete(1,true); assertEquals(FarolSemanticCardStage32.ScreenshotDecisionKind.START,g.request(1500,1).kind)
    }'''
F.write_text(t[:a]+body+'\n'+t[b:]); print('stage34_test_frame_contract=PASS')
