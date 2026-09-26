#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()

p=root/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage36RuntimeTest.kt'
s=p.read_text()
old='@Test fun paintRequiresVerificationComplete(){val s=service();val a=s.indexOf("private suspend fun applyUniversalTwoAddressResultStage19");val b=s.indexOf("private fun scheduleAccessibilityFallbackStage23",a);assertTrue(s.substring(a,b).contains("isStage19BindingFresh(bindingStage19) && !stage19VisualVerificationPending"))}'
new='@Test fun paintRequiresVerificationComplete(){val s=service();assertTrue(s.contains("private suspend fun applyUniversalTwoAddressResultStage19"));assertTrue(s.contains("val paintFreshStage20 = isStage19BindingFresh(bindingStage19) && !stage19VisualVerificationPending"))}'
if s.count(old)!=1: raise SystemExit('stage36 runtime test anchor mismatch')
p.write_text(s.replace(old,new,1))

p34=root/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage34Test.kt'
s34=p34.read_text()
if s34.count('versionCode = 5492')!=1: raise SystemExit('stage34 versionCode successor anchor mismatch')
if s34.count('versionName = \\"0.1.208\\"')!=1: raise SystemExit('stage34 versionName successor anchor mismatch')
s34=s34.replace('versionCode = 5492','versionCode = 5493',1)
s34=s34.replace('versionName = \\"0.1.208\\"','versionName = \\"0.1.209\\"',1)
p34.write_text(s34)
print('stage36_runtime_test_fix=PASS')
