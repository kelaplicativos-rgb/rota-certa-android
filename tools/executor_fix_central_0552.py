from pathlib import Path

path = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt')
text = path.read_text()
old = '        val timelineIds = timelineProjection.entries.flatMap { entry -> listOf(entry.tripId, entry.localTripId) }.filter(String::isNotBlank).toSet()'
new = '        val timelineIds = timelineProjection.entries.flatMap { entry -> listOfNotNull(entry.tripId, entry.localTripId) }.filter { it.isNotBlank() }.toSet()'
count = text.count(old)
if count != 1:
    raise SystemExit(f'Central timeline identity patch expected 1 match, got {count}')
path.write_text(text.replace(old, new, 1))
