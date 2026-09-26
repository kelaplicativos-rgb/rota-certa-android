#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
p=root/'app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt'
s=p.read_text()
old='FarolRuntimeAuthorityStage36.Metrics.exportReport(if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority else null)'
new='FarolRuntimeAuthorityStage36.Metrics.exportReport(null)'
if s.count(old)!=1: raise SystemExit('stage36 report compile anchor')
p.write_text(s.replace(old,new,1))
print('stage36_report_compile_fix=PASS')
