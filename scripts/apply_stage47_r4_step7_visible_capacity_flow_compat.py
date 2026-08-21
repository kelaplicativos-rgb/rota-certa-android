#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
original = Path(__file__).with_name("apply_stage47_r4_step7_visible_capacity_flow.py")
if not original.is_file():
    raise SystemExit(f"missing visible capacity materializer: {original}")

text = original.read_text(encoding="utf-8")
old = '''once(
    TIMELINE,
''' + "'''" + '''            Text("$date — ${entry.origin} → ${entry.destination} $status", style = MaterialTheme.typography.titleSmall)
''' + "'''" + ''',
''' + "'''" + '''            val baseDirection = timelineBaseDirection(trip, homeCoordinate, homeRadiusKm)
            val directionPrefix = baseDirection?.let { "$it " }.orEmpty()
            Text("$directionPrefix$date — ${entry.origin} → ${entry.destination} $status", style = MaterialTheme.typography.titleSmall)
''' + "'''" + ''',
    "visible saved-base direction",
)
'''
new = '''timeline_source = TIMELINE.read_text(encoding="utf-8")
route_lines = [
    line for line in timeline_source.splitlines(keepends=True)
    if 'Text("${entry.origin} → ${entry.destination}"' in line
]
if len(route_lines) != 1:
    raise SystemExit(f"visible saved-base direction: expected one route title line, got {len(route_lines)}")
route_line = route_lines[0]
indent = route_line[: len(route_line) - len(route_line.lstrip())]
if 'Text("$directionPrefix${entry.origin} → ${entry.destination}"' in route_line:
    raise SystemExit("visible saved-base direction already materialized")
new_route_line = route_line.replace(
    'Text("${entry.origin} → ${entry.destination}"',
    'Text("$directionPrefix${entry.origin} → ${entry.destination}"',
    1,
)
direction_block = (
    indent + 'val baseDirection = timelineBaseDirection(trip, homeCoordinate, homeRadiusKm)\\n'
    + indent + 'val directionPrefix = baseDirection?.let { "$it " }.orEmpty()\\n'
    + new_route_line
)
TIMELINE.write_text(timeline_source.replace(route_line, direction_block, 1), encoding="utf-8")
'''
if text.count(old) != 1:
    raise SystemExit(f"compat direction materializer block count={text.count(old)}")
patched = text.replace(old, new, 1)
namespace = {"__name__": "__main__", "__file__": str(original)}
previous_argv = sys.argv
try:
    sys.argv = [str(original), str(SOURCE)]
    exec(compile(patched, str(original), "exec"), namespace)
finally:
    sys.argv = previous_argv

print("stage47_r4_step7_visible_capacity_flow_compat=PASS title_anchor=semantic_route_line")
