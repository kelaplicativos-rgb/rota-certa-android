#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(sys.argv[1]).resolve(); PKG=ROOT/'app/src/main/java/br/com/mapeiaia/rotacerta'; S=PKG/'LiveRideAccessibilityService.kt'; H=Path(__file__).resolve().parents[1]/'stage40/FarolVisualStateAuthorityStage40.kt'
if not H.exists(): raise SystemExit('missing Stage40 visual helper')
(PKG/'FarolVisualStateAuthorityStage40.kt').write_text(H.read_text())
def once(t,o,n,l):
 c=t.count(o)
 if c!=1: raise SystemExit(f'{l}: expected 1 occurrence, got {c}')
 return t.replace(o,n,1)
s=S.read_text(); orange=s.count('showOverlay(RadarColor.Orange')
if orange<1: raise SystemExit('expected legacy Orange callers')
s=s.replace('showOverlay(RadarColor.Orange','showOverlay(RadarColor.Default')
o='''    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {\n        if (!serviceReady) return\n        val manager = windowManager ?: return\n'''
n='''    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {\n        if (!serviceReady) return\n        val readingEnabledStage40 = ::stage36RuntimeAuthority.isInitialized &&\n            stage36RuntimeAuthority.snapshot().enabled && WorkModePolicy0162.isEnabled(currentSettings)\n        val decisionStage40 = FarolVisualStateAuthorityStage40.decide(readingEnabledStage40, color.name, distanceKm)\n        val effectiveColorStage40 = when (decisionStage40.state) {\n            FarolVisualStateAuthorityStage40.PublicState.GRAY -> RadarColor.Idle\n            FarolVisualStateAuthorityStage40.PublicState.YELLOW -> RadarColor.Default\n            FarolVisualStateAuthorityStage40.PublicState.GREEN -> RadarColor.Green\n            FarolVisualStateAuthorityStage40.PublicState.RED -> RadarColor.Red\n        }\n        FarolMaximumForensicsStage38.record(\n            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S40_VISUAL_AUTHORITY_DECISION", universalResolvedForegroundPackage(),\n            details = "requested=$color; requestedDistance=${distanceKm ?: -1.0}; effective=$effectiveColorStage40; effectiveDistance=${decisionStage40.distanceKm ?: -1.0}; reading=$readingEnabledStage40; reason=${decisionStage40.reason}",\n        )\n        renderOverlayStage40(effectiveColorStage40, decisionStage40.distanceKm)\n    }\n\n    private fun renderOverlayStage40(color: RadarColor, distanceKm: Double? = null) {\n        if (!serviceReady) return\n        val manager = windowManager ?: return\n'''
s=once(s,o,n,'final visual authority gate')
if 'showOverlay(RadarColor.Orange' in s: raise SystemExit('Orange public caller remains')
if s.count('FarolVisualStateAuthorityStage40.decide(')!=1: raise SystemExit('final authority count invalid')
S.write_text(s); print(f'stage40_visual=PASS; removed_orange_callers={orange}')
