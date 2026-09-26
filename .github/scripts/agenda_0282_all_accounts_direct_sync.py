from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: {label} expected one anchor, found {count}")
    path.write_text(text.replace(old, new, 1))


# 0.1.282 — make the broad/all-accounts sync use the same authenticated
# direct collector that is already physically good in the today-only flow.
# Preserve the broad date/account scope; remove only the legacy second automatic
# full-account MHTML harvest after a direct account session has completed.
ui = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt")

old = '''                } else if (account != null) {
                    archiving = true
                    message = "${account.displayLabel}: leitura concluída • baixando MHTMLs necessários…"
                    onChanged(message.orEmpty())
                    archiveLauncher.launch(BlaBlaManualSeatAutomationIntents.harvest(context, account))
                } else {
'''
new = '''                } else if (account != null) {
                    archiving = false
                    message = "${account.displayLabel}: leitura direta concluída ✅"
                    onChanged(message.orEmpty())
                    UnifiedDebugEventStore.record(
                        "AGENDA_ALL_ACCOUNTS_DIRECT_SYNC_FINISHED",
                        context.packageName,
                        "accountPresent=true dateScope=all directCollector=true mhtmlFullAccountSkipped=true",
                    )
                    advanceSyncQueue()
                } else {
'''
replace_once(ui, old, new, "replace legacy automatic MHTML pass")

old = '''            publishCombined(
                if (syncDateScope != null) "Sincronização do card de hoje concluída" else "Sincronização + MHTML concluídos",
            )
'''
new = '''            publishCombined(
                if (syncDateScope != null) "Sincronização do card de hoje concluída" else "Sincronização direta concluída",
            )
'''
replace_once(ui, old, new, "direct completion label")

text = ui.read_text()
required = [
    "AGENDA_TODAY_CARD_SYNC_FINISHED",
    "mhtmlFullAccountSkipped=true noLaterCardsVisited=true",
    "BlaBlaDynamicSessionIntents.syncToday(context, account, dateScope)",
    "BlaBlaDynamicSessionIntents.sync(context, account)",
    "AGENDA_ALL_ACCOUNTS_DIRECT_SYNC_FINISHED",
    "dateScope=all directCollector=true mhtmlFullAccountSkipped=true",
]
for marker in required:
    if marker not in text:
        raise SystemExit(f"TripBlaBlaCollectorUi.kt: required marker missing after rewrite: {marker}")
if "archiveLauncher.launch(BlaBlaManualSeatAutomationIntents.harvest(context, account))" in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: automatic full-account MHTML pass still reachable")
ui.write_text(text)


# Keep the proven 0.1.281 implementation intact and make the APK uniquely
# identifiable for the Samsung physical regression test.
build = Path("app/build.gradle.kts")
replace_once(build, 'versionCode = 5574', 'versionCode = 5575', "versionCode 0.1.282")
replace_once(build, 'versionName = "0.1.281"', 'versionName = "0.1.282"', "versionName 0.1.282")

# Materializer-level invariants: broad sync is still broad (no date target),
# today-only remains date-scoped, and the direct collector remains the source
# that reads/coalesces passenger evidence.
dynamic = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").read_text()
for marker in [
    "fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)",
    "fun syncToday(context: Context, account: BlaBlaDynamicAccount, targetDate: LocalDate): Intent =",
    "passengers = BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(source.detail.passengers)",
    "pendingTripPassengers = (",
]:
    if marker not in dynamic:
        raise SystemExit(f"BlaBlaDynamicAccounts.kt: direct collector invariant missing: {marker}")
