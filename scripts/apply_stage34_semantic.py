#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(sys.argv[1]).resolve()
PKG=ROOT/'app/src/main/java/br/com/mapeiaia/rotacerta'
SEM=PKG/'FarolSemanticCardStage32.kt'
HELPER=Path(__file__).resolve().parents[1]/'stage34/FarolCardLeaseStage34.kt'
if not HELPER.exists(): raise SystemExit('missing Stage34 helper')
(PKG/'FarolCardLeaseStage34.kt').write_text(HELPER.read_text())

def between(text,start,end,new):
 a=text.index(start); b=text.index(end,a); return text[:a]+new+text[b:]
def once(text,old,new,label):
 if text.count(old)!=1: raise SystemExit(label)
 return text.replace(old,new,1)

s=SEM.read_text()
new_gate=r'''    class SemanticGate {
        private val cardLease = FarolCardLeaseStage34.Authority()
        private var owner: String? = null
        private var windowId = Int.MIN_VALUE
        private var slot = ""
        private var lastText = ""
        private var offGeneration = 0L
        private var lastSnapshot = Snapshot(0L, stableHash64("stage34-empty"), null, Int.MIN_VALUE, "", "")

        @Synchronized fun observe(signal: Signal): Decision {
            owner = firstNonBlank(normalizePackage(signal.sourcePackage), normalizePackage(signal.triggerPackage), normalizePackage(signal.windowPackage), owner)
            if (signal.windowId >= 0) windowId = signal.windowId
            slot = canonical(signal.sourceSlot).ifBlank { "provenance" }
            val text = canonicalSemanticSource(signal.sourceText).take(1800)
            if (text.isNotBlank()) lastText = text
            val lease = cardLease.observeRawEvent()
            val generation = maxOf(lease.leaseId, offGeneration)
            val fingerprint = if (lease.candidateBound) lease.identityHash else stableHash64("stage34-acquiring:$generation")
            lastSnapshot = Snapshot(generation, fingerprint, owner, windowId, slot, text)
            Metrics.increment("rawEventsPreserved")
            Metrics.increment("stage34PackageWindowProvenanceOnly")
            return Decision(false, false, generation, fingerprint, "stage34_raw_event_provenance_only", lastSnapshot)
        }

        @Synchronized fun observeCandidate(addressSignature: String): Decision {
            if (addressSignature.isBlank()) return Decision(false,false,lastSnapshot.generation,lastSnapshot.fingerprint,"blank_candidate_signature",snapshotLocked())
            val decision=cardLease.bindCandidate(addressSignature)
            val generation=maxOf(decision.snapshot.leaseId,offGeneration)
            val fingerprint=decision.snapshot.identityHash
            lastSnapshot=Snapshot(generation,fingerprint,owner,windowId,"candidate",decision.snapshot.destinationKey.orEmpty())
            if (decision.leaseTransition) {
                Metrics.increment("candidateSemanticMutations"); Metrics.increment("stage34RealDestinationTransitions")
            } else Metrics.increment("stage34SameDestinationPreserved")
            return Decision(decision.leaseTransition,decision.leaseTransition,generation,fingerprint,decision.reason,lastSnapshot)
        }

        @Synchronized fun markReadingOff(): Snapshot {
            val old=cardLease.markReadingOff()
            offGeneration=maxOf(offGeneration+1L,(old?.leaseId?:0L)+1L)
            owner=null; windowId=Int.MIN_VALUE; slot=""; lastText=""
            lastSnapshot=Snapshot(offGeneration,stableHash64("stage34-off:$offGeneration"),null,Int.MIN_VALUE,"","")
            Metrics.increment("semanticResetReadingOff"); Metrics.increment("stage34ReadingOffLeaseInvalidated")
            return lastSnapshot
        }

        @Synchronized fun snapshot(): Snapshot = snapshotLocked()
        @Synchronized fun lease(): Lease = Lease(lastSnapshot.generation,lastSnapshot.fingerprint)
        @Synchronized fun isFresh(lease: Lease): Boolean { val c=snapshotLocked(); return lease.generation==c.generation && lease.fingerprint==c.fingerprint }
        private fun snapshotLocked(): Snapshot {
            cardLease.snapshot()?.let { x ->
                val g=maxOf(x.leaseId,offGeneration); val f=if(x.candidateBound)x.identityHash else stableHash64("stage34-acquiring:$g")
                lastSnapshot=lastSnapshot.copy(generation=g,fingerprint=f)
            }
            return lastSnapshot
        }
    }

'''
s=between(s,'    class SemanticGate {','    enum class ScreenshotDecisionKind',new_gate)
old='''            if (dedupeCompleted && completedGeneration == semanticGeneration && pendingGeneration == null) {
                Metrics.increment("screenshotDuplicateCompleted")
                return ScreenshotDecision(ScreenshotDecisionKind.DUPLICATE_COMPLETED, semanticGeneration, eligibleAt(), "same_semantic_generation_completed")
            }
'''
new='''            if (dedupeCompleted && completedGeneration == semanticGeneration && pendingGeneration == null) {
                Metrics.increment("stage34CompletedLeaseReacquireEligible")
            }
'''
s=once(s,old,new,'same-lease frame branch')
SEM.write_text(s)
print('stage34_semantic=PASS')
