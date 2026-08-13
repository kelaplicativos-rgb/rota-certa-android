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
print('stage36_runtime_test_fix=PASS')
