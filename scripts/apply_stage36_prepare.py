#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
p = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
s = p.read_text()
old = '        universalActiveRidePackageName = null\n        universalActiveAddressSignature = null\n        lastSnapshotHash = null\n'
new = '        universalActiveRidePackageName = null\n        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.markExplicitOff("work_mode_disabled")\n        universalActiveAddressSignature = null\n        lastSnapshotHash = null\n'
if s.count(old) != 1:
    raise SystemExit('stage36 prepare anchor mismatch')
p.write_text(s.replace(old, new, 1))
print('stage36_prepare=PASS')
