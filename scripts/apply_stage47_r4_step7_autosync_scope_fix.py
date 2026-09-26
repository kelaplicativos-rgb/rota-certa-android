#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
UI = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt"
if not UI.is_file():
    raise SystemExit(f"missing materialized collector UI: {UI}")

text = UI.read_text(encoding="utf-8")

effect = '''    LaunchedEffect(autoSyncToken, syncing, accounts.size) {
        if (autoSyncToken <= handledAutoSyncToken || syncing) return@LaunchedEffect
        if (accounts.isEmpty()) {
            message = "Passageiro salvo • conecte uma conta BlaBlaCar para sincronizar."
            onChanged(message.orEmpty())
            UnifiedDebugEventStore.record(
                "AUTO_SYNC_PENDING",
                context.packageName,
                "reason=occupancy_change accounts=0 token=$autoSyncToken",
            )
            return@LaunchedEffect
        }
        handledAutoSyncToken = autoSyncToken
        syncQueue = accounts.map { it.id }
        syncCursor = 0
        syncing = true
        message = "Sincronizando BlaBlaCar após alteração de passageiro…"
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "AUTO_SYNC_REQUESTED",
            context.packageName,
            "reason=occupancy_change accounts=${accounts.size} token=$autoSyncToken",
        )
    }

'''

if text.count(effect) != 1:
    raise SystemExit(f"autosync effect count={text.count(effect)}, expected exactly one")
text = text.replace(effect, "", 1)

anchor = '''    LaunchedEffect(syncing, syncCursor, syncQueue) {
'''
if text.count(anchor) != 1:
    raise SystemExit(f"collector launch anchor count={text.count(anchor)}, expected exactly one")
text = text.replace(anchor, effect + anchor, 1)
UI.write_text(text, encoding="utf-8")

# Structural guard: all state variables and account snapshot must appear before
# the automatic effect. This prevents Kotlin local-scope regressions.
final = UI.read_text(encoding="utf-8")
effect_pos = final.index("LaunchedEffect(autoSyncToken, syncing, accounts.size)")
for marker in (
    "var syncing by remember",
    "var syncQueue by remember",
    "var syncCursor by remember",
    "var handledAutoSyncToken by remember",
    "val accounts = registry.list()",
):
    pos = final.find(marker)
    if pos < 0 or pos > effect_pos:
        raise SystemExit(f"collector autosync scope guard failed for {marker!r}")

print("stage47_r4_step7_autosync_scope_fix=PASS effect_after_state=true farol_touched=false")
