#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(sys.argv[1]).resolve(); F=ROOT/'app/src/main/java/br/com/mapeiaia/rotacerta/FarolReadingActivationStage26.kt'
s=F.read_text(); a=s.index('    class PreCollectGate {'); b=s.index('    data class PaintState',a)
new=r'''    class PreCollectGate {
        private var lastWindowSignature: String? = null
        private var lastRelevantValue: String? = null
        private var generation = 0L
        @Synchronized fun admit(readingEnabled:Boolean, signal:CheapVisualSignal):Admission {
            Metrics.increment("eventsReceived")
            if(!readingEnabled){ Metrics.increment("eventsRejectedReadingOff"); Metrics.increment("heavyCollectionsAvoided"); return Admission(false,false,"reading_off",generation,null) }
            if(signal.ownOverlay){ Metrics.increment("ownOverlayEventsIgnored"); Metrics.increment("heavyCollectionsAvoided"); return Admission(false,false,"own_overlay",generation,null) }
            val previousWindow=lastWindowSignature
            val window=canonical(signal.windowSignature)
            val value=canonicalStage34Visual(signal.sourceText).take(1024)
            val windowChanged=previousWindow!=null && previousWindow!=window
            val previous=lastRelevantValue
            lastWindowSignature=window
            if(value.isBlank()){
                if(previous!=null && !windowChanged){
                    lastRelevantValue=null
                    generation+=1L
                    Metrics.increment("heavyCollectionsStarted")
                    Metrics.increment("stage34SameContextClearVerification")
                    return Admission(true,true,"stage34_same_context_content_cleared_verify",generation,stableHash64("clear:$window"))
                }
                Metrics.increment("preCollectDuplicateSkipped"); Metrics.increment("heavyCollectionsAvoided")
                Metrics.increment(if(windowChanged)"stage34BlankWindowChurnPreserved" else "stage34BlankEventPreserved")
                return Admission(false,false,"stage34_blank_or_window_provenance_only",generation,stableHash64("lease"))
            }
            if(previous==value){ Metrics.increment("preCollectDuplicateSkipped"); Metrics.increment("heavyCollectionsAvoided"); Metrics.increment("stage34SameAddressEvidencePreserved"); return Admission(false,false,"stage34_same_address_evidence",generation,stableHash64(value)) }
            lastRelevantValue=value; generation+=1L; Metrics.increment("heavyCollectionsStarted"); Metrics.increment("stage34RelevantAddressEvidenceChanged")
            return Admission(true,true,if(previous==null)"stage34_first_address_evidence" else "stage34_address_evidence_changed",generation,stableHash64(value))
        }
        @Synchronized fun invalidate(){ lastWindowSignature=null; lastRelevantValue=null; generation+=1L }
        @Synchronized fun currentGeneration():Long=generation
    }

'''
s=s[:a]+new+s[b:]
anchor='    private fun stableHash64(value: String): Long {'
helper=r'''    private fun canonicalStage34Visual(value:String):String {
        val stable=value
            .replace(Regex("(?iu)r\\$\\s*\\d+(?:[.,]\\d{1,2})?")," valor ")
            .replace(Regex("(?iu)\\b\\d{1,3}\\s*(?:s|seg|segs|segundos|min|mins|minutos)\\b")," tempo ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b")," horario ")
            .replace(Regex("\\b\\d{1,3}%\\b")," percentual ")
        return canonical(stable)
    }
'''
if s.count(anchor)!=1: raise SystemExit('stable hash anchor')
s=s.replace(anchor,helper+anchor,1); F.write_text(s); print('stage34_precollect=PASS')
