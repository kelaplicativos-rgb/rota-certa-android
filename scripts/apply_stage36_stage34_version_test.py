#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
p=root/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage34Test.kt'
s=p.read_text()
old='assertTrue(s.contains("versionCode = 5492")); assertTrue(s.contains("versionName = \\"0.1.208\\""))'
new='assertTrue(s.contains("versionCode = 5493")); assertTrue(s.contains("versionName = \\"0.1.209\\""))'
if s.count(old)!=1:
    raise SystemExit(f'stage34 successor version anchor count={s.count(old)}')
p.write_text(s.replace(old,new,1))
print('stage36_stage34_version_test=PASS')
