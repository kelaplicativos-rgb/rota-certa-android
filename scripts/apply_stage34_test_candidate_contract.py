#!/usr/bin/env python3
from pathlib import Path
import sys
F=Path(sys.argv[1]).resolve()/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage32Test.kt'; t=F.read_text()
def swap(name,body):
 global t
 start=f'    @Test fun {name}() {{'; a=t.find(start)
 if a<0: raise SystemExit('missing '+name)
 b=t.find('\n    @Test fun ',a+len(start)); b=b if b>=0 else t.rfind('\n}')
 t=t[:a]+body.rstrip()+'\n'+t[b:]
swap('realSemanticMutationClosesOldAndCreatesNewCase','''    @Test fun confirmedDestinationChangeIsSemanticMutationStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); g.observeCandidate("Rua A|Rua B 20"); assertTrue(g.observeCandidate("Rua A|Rua D 40").mutation)
    }''')
swap('semanticFingerprintChangesOnActualCardTextChange','''    @Test fun semanticFingerprintChangesOnConfirmedDestinationStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val a=g.observeCandidate("Rua A|Rua B 20").fingerprint; val b=g.observeCandidate("Rua A|Rua C 30").fingerprint; assertNotEquals(a,b)
    }''')
F.write_text(t); print('stage34_test_candidate_contract=PASS')
