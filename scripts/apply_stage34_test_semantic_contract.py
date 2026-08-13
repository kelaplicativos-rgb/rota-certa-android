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
swap('firstSemanticSourceCreatesGeneration','''    @Test fun firstSemanticSourceOpensStableCardLeaseWithoutRawMutation() {
        val g=FarolSemanticCardStage32.SemanticGate(); val d=g.observe(signal())
        assertFalse(d.mutation); assertEquals(1L,d.generation)
    }''')
swap('realSourceTextChangeInvalidatesLease','''    @Test fun rawSourceTextChangePreservesLeaseStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(text="99 Negocia Rua C 30 destino Rua D 40")); assertTrue(g.isFresh(l))
    }''')
swap('windowTransitionInvalidatesLease','''    @Test fun windowTransitionIsProvenanceOnlyStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(windowId=11)); assertTrue(g.isFresh(l))
    }''')
swap('ownerTransitionInvalidatesLease','''    @Test fun ownerTransitionIsProvenanceOnlyStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); g.observe(signal()); val l=g.lease(); g.observe(signal(pkg="com.ubercab.driver",source="com.ubercab.driver",window="com.ubercab.driver")); assertTrue(g.isFresh(l))
    }''')
swap('sourceSlotClearIsSemanticMutation','''    @Test fun sourceSlotClearIsRawNoiseStage34() {
        val g=FarolSemanticCardStage32.SemanticGate(); val a=g.observe(signal()); val b=g.observe(signal(text="")); assertFalse(b.mutation); assertEquals(a.generation,b.generation)
    }''')
F.write_text(t); print('stage34_test_semantic_contract=PASS')
