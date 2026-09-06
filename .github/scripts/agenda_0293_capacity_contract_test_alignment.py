from pathlib import Path

path = Path("app/src/test/java/br/com/mapeiaia/rotacerta/trips/Stage47Reliability0251Test.kt")
text = path.read_text()
old = '''    @Test\n    fun configuredVehicleCapacityFillsOnlyMissingCapacity() {\n        val external = entry(id = "external", capacity = 0, rosterComplete = true)\n        val explicit = entry(id = "local", capacity = 6, rosterComplete = true)\n        val updated = applyConfiguredVehicleCapacity(listOf(external, explicit), 4)\n        assertEquals(4, updated[0].capacity)\n        assertEquals(6, updated[1].capacity)\n    }'''
new = '''    @Test\n    fun configuredVehicleCapacityIsPhysicalAuthorityForAllTimelineEntries() {\n        val external = entry(id = "external", capacity = 0, rosterComplete = true)\n        val staleOrExternal = entry(id = "local", capacity = 6, rosterComplete = true)\n        val updated = applyConfiguredVehicleCapacity(listOf(external, staleOrExternal), 4)\n        assertEquals(4, updated[0].capacity)\n        assertEquals(4, updated[1].capacity)\n    }'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"capacity contract anchor count={count}")
path.write_text(text.replace(old, new, 1))
