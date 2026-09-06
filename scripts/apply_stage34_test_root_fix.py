#!/usr/bin/env python3
from pathlib import Path
import sys
F=Path(sys.argv[1]).resolve()/'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage34Test.kt'
t=F.read_text()
old='''class FarolStage34Test {\n    private fun serviceSource()=File(System.getProperty("user.dir"),"app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()\n    private fun src(name:String)=File(System.getProperty("user.dir"),"app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()\n'''
new='''class FarolStage34Test {\n    private fun projectRoot():File {\n        var cursor=File(System.getProperty("user.dir")).absoluteFile\n        repeat(8) {\n            if(File(cursor,"app/build.gradle.kts").isFile) return cursor\n            if(cursor.name=="app" && File(cursor,"build.gradle.kts").isFile) return cursor.parentFile\n            cursor=cursor.parentFile ?: return@repeat\n        }\n        error("Stage34 project root not found")\n    }\n    private fun serviceSource()=File(projectRoot(),"app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()\n    private fun src(name:String)=File(projectRoot(),"app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()\n'''
if t.count(old)!=1: raise SystemExit('stage34 root helper block')
t=t.replace(old,new,1)
t=t.replace('File(System.getProperty("user.dir"),"app/build.gradle.kts")','File(projectRoot(),"app/build.gradle.kts")')
F.write_text(t)
print('stage34_test_root_fix=PASS')
