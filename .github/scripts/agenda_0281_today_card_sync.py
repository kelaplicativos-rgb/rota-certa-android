from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: {label} expected one anchor, found {count}")
    path.write_text(text.replace(old, new, 1))


# 1) Keep the date decision inside the existing card module. The DOM collector
# already exposes dateText per card and BlaBlaDomNormalizer.parseDate already
# understands Hoje / Amanhã / absolute Portuguese dates. Do not create a second
# collector or duplicate date parsing.
card = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaCollectorCardModule.kt")
text = card.read_text()
if "import java.time.LocalDate" not in text:
    text = text.replace(
        "package br.com.mapeiaia.rotacerta.trips\n\n",
        "package br.com.mapeiaia.rotacerta.trips\n\nimport java.time.LocalDate\n\n",
        1,
    )
if "fun candidatesOnDate(" not in text:
    anchor = "    fun canAdvance(currentCardComplete: Boolean, currentCardQuarantined: Boolean): Boolean =\n"
    helper = '''    fun candidateDate(
        candidate: BlaBlaDomRideCandidate,
        today: LocalDate = LocalDate.now(),
    ): LocalDate? = BlaBlaDomNormalizer.parseDate(
        listOf(candidate.dateText, candidate.text).joinToString(" | "),
        today,
    )

    fun candidatesOnDate(
        candidates: List<BlaBlaDomRideCandidate>,
        targetDate: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): List<BlaBlaDomRideCandidate> = candidates.filter { candidate ->
        candidateDate(candidate, today) == targetDate
    }

'''
    if text.count(anchor) != 1:
        raise SystemExit("BlaBlaCollectorCardModule.kt: canAdvance anchor not unique")
    text = text.replace(anchor, helper + anchor, 1)
card.write_text(text)


# 2) Propagate the requested date into the existing authenticated session.
dynamic = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt")
text = dynamic.read_text()

old = '''    const val EXTRA_TARGET_URL = "blablacar_target_url"
    const val EXTRA_TARGET_TRIP_ID = "blablacar_target_trip_id"
'''
new = '''    const val EXTRA_TARGET_URL = "blablacar_target_url"
    const val EXTRA_TARGET_TRIP_ID = "blablacar_target_trip_id"
    const val EXTRA_TARGET_DATE = "blablacar_target_date"
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("BlaBlaDynamicAccounts.kt: target date constant anchor not found")

old = '''    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)
    fun syncExact(context: Context, account: BlaBlaDynamicAccount, tripId: String, tripHref: String): Intent =
'''
new = '''    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)
    fun syncToday(context: Context, account: BlaBlaDynamicAccount, targetDate: LocalDate): Intent =
        intent(context, account, MODE_SYNC).putExtra(EXTRA_TARGET_DATE, targetDate.toString())
    fun syncExact(context: Context, account: BlaBlaDynamicAccount, tripId: String, tripHref: String): Intent =
'''
if old in text:
    text = text.replace(old, new, 1)
elif "fun syncToday(" not in text:
    raise SystemExit("BlaBlaDynamicAccounts.kt: syncToday anchor not found")

old = '''    private var targetTripId = ""
    private var targetTripHref = ""
    private var phase = Phase.IDLE
'''
new = '''    private var targetTripId = ""
    private var targetTripHref = ""
    private var targetDate: LocalDate? = null
    private var phase = Phase.IDLE
'''
if old in text:
    text = text.replace(old, new, 1)
elif "private var targetDate: LocalDate?" not in text:
    raise SystemExit("BlaBlaDynamicAccounts.kt: targetDate state anchor not found")

old = '''        mode = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_MODE) ?: BlaBlaDynamicSessionIntents.MODE_LOGIN
        targetTripId = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_TRIP_ID)?.trim().orEmpty()
        targetTripHref = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL)?.trim().orEmpty()
        if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC || BlaBlaCollectorUrlModule.tripId(targetTripHref) != targetTripId) {
            targetTripId = ""
            targetTripHref = ""
        }
'''
new = '''        mode = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_MODE) ?: BlaBlaDynamicSessionIntents.MODE_LOGIN
        targetTripId = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_TRIP_ID)?.trim().orEmpty()
        targetTripHref = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL)?.trim().orEmpty()
        targetDate = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_DATE)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
            ?.takeIf { mode == BlaBlaDynamicSessionIntents.MODE_SYNC }
        if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC || BlaBlaCollectorUrlModule.tripId(targetTripHref) != targetTripId) {
            targetTripId = ""
            targetTripHref = ""
        } else {
            targetDate = null
        }
'''
if old in text:
    text = text.replace(old, new, 1)
elif "EXTRA_TARGET_DATE" not in text or "LocalDate.parse(raw)" not in text:
    raise SystemExit("BlaBlaDynamicAccounts.kt: onCreate target date anchor not found")

old = '''            val visible = result.candidates
                .filter { candidate ->
                    val href = candidate.href
                    BlaBlaCollectorUrlModule.isSpecificTrip(href)
                }
                .distinctBy { BlaBlaCollectorUrlModule.canonical(it.href) }
            UnifiedDebugEventStore.record(
                "RIDES_TRAVERSAL_SCAN",
                packageName,
                "account=${account.displayLabel} visible=${visible.size} resolved=${resolvedCardTraversalKeys.size} completed=${completedCardTraversalKeys.size} quarantined=${quarantinedCardTraversalKeys.size} scrollY=${result.scrollY} scrollHeight=${result.scrollHeight} viewport=${result.viewportHeight} atBottom=${result.atBottom} pastDateFilter=false fixedTripLimit=false",
            )
            if (visible.isEmpty() && rideReadAttempts < MAX_RIDES_EMPTY_READ_ATTEMPTS && !looksLoggedOut(result.bodyText)) {
'''
new = '''            val visibleAll = result.candidates
                .filter { candidate ->
                    val href = candidate.href
                    BlaBlaCollectorUrlModule.isSpecificTrip(href)
                }
                .distinctBy { BlaBlaCollectorUrlModule.canonical(it.href) }
            val requestedDate = targetDate
            val visible = requestedDate?.let { date ->
                BlaBlaCollectorCardModule.candidatesOnDate(visibleAll, date)
            } ?: visibleAll
            UnifiedDebugEventStore.record(
                "RIDES_TRAVERSAL_SCAN",
                packageName,
                "account=${account.displayLabel} visible=${visibleAll.size} eligible=${visible.size} resolved=${resolvedCardTraversalKeys.size} completed=${completedCardTraversalKeys.size} quarantined=${quarantinedCardTraversalKeys.size} scrollY=${result.scrollY} scrollHeight=${result.scrollHeight} viewport=${result.viewportHeight} atBottom=${result.atBottom} pastDateFilter=${requestedDate != null} fixedTripLimit=${requestedDate != null} targetDate=${requestedDate ?: "none"}",
            )
            if (visibleAll.isEmpty() && rideReadAttempts < MAX_RIDES_EMPTY_READ_ATTEMPTS && !looksLoggedOut(result.bodyText)) {
'''
if old in text:
    text = text.replace(old, new, 1)
elif "eligible=${visible.size}" not in text:
    raise SystemExit("BlaBlaDynamicAccounts.kt: traversal scan anchor not found")

text = text.replace(
    "            if (visible.isEmpty() && looksLoggedOut(result.bodyText)) {\n",
    "            if (visibleAll.isEmpty() && looksLoggedOut(result.bodyText)) {\n",
    1,
)
text = text.replace(
    "                visible.isEmpty() &&\n                !BlaBlaCollectorCardModule.emptyListIsAuthoritative(result.explicitEmptyList)\n",
    "                visibleAll.isEmpty() &&\n                !BlaBlaCollectorCardModule.emptyListIsAuthoritative(result.explicitEmptyList)\n",
    1,
)

old = '''                UnifiedDebugEventStore.record(
                    "CARD_TRAVERSAL_START",
                    packageName,
                    "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(next.href).orEmpty()} uiOrder=true dateIgnored=true",
                )
'''
new = '''                UnifiedDebugEventStore.record(
                    "CARD_TRAVERSAL_START",
                    packageName,
                    "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(next.href).orEmpty()} uiOrder=true dateIgnored=${requestedDate == null} dateScope=${if (requestedDate == null) "all" else "today"} targetDate=${requestedDate ?: "none"}",
                )
'''
if old in text:
    text = text.replace(old, new, 1)
elif "dateScope=${if (requestedDate == null)" not in text:
    raise SystemExit("BlaBlaDynamicAccounts.kt: traversal start anchor not found")

anchor = '''            if (visible.any { tripTraversalKey(it).isBlank() }) {
                blockSyncWithoutCurrentCard("visible_card_without_stable_identity")
                return@evaluate
            }
            if (!result.atBottom) {
'''
replacement = '''            if (visible.any { tripTraversalKey(it).isBlank() }) {
                blockSyncWithoutCurrentCard("visible_card_without_stable_identity")
                return@evaluate
            }
            if (requestedDate != null) {
                val firstVisibleDate = visibleAll.firstOrNull()?.let { candidate ->
                    BlaBlaCollectorCardModule.candidateDate(candidate)
                }
                if (collected.isEmpty() && visible.isEmpty() && visibleAll.isNotEmpty() && firstVisibleDate == null) {
                    blockSyncWithoutCurrentCard("today_card_date_unreadable")
                    return@evaluate
                }
                val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
                saveFinalSnapshotOnce(verified)
                if (verified) {
                    UnifiedDebugEventStore.record(
                        "RIDES_TRAVERSAL_COMPLETE",
                        packageName,
                        "account=${account.displayLabel} resolvedCards=${resolvedCardTraversalKeys.size} completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} pastDateFilter=true fixedTripLimit=true targetDate=$requestedDate noLaterCardsVisited=true",
                    )
                    completeSync(collected.size)
                } else {
                    blockSyncWithoutCurrentCard("identity_not_verified_after_today_card")
                }
                return@evaluate
            }
            if (!result.atBottom) {
'''
if anchor in text:
    text = text.replace(anchor, replacement, 1)
elif "noLaterCardsVisited=true" not in text:
    raise SystemExit("BlaBlaDynamicAccounts.kt: date-scoped completion anchor not found")

dynamic.write_text(text)


# 3) The UI must launch the date-scoped session, and after that session returns
# it must NOT start the old full-account MHTML harvester. The authenticated
# session already reads the selected card and its passengers; skipping the full
# harvester is what guarantees later cards are never visited in today mode.
ui = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollectorUi.kt")
text = ui.read_text()

old = '''                } else if (account != null) {
                    archiving = true
                    message = "${account.displayLabel}: leitura concluída • baixando MHTMLs necessários…"
                    onChanged(message.orEmpty())
                    archiveLauncher.launch(BlaBlaManualSeatAutomationIntents.harvest(context, account))
                } else {
'''
new = '''                } else if (account != null && syncDateScope != null) {
                    archiving = false
                    message = "${account.displayLabel}: card de hoje concluído ✅"
                    onChanged(message.orEmpty())
                    UnifiedDebugEventStore.record(
                        "AGENDA_TODAY_CARD_SYNC_FINISHED",
                        context.packageName,
                        "accountPresent=true targetDate=$syncDateScope mhtmlFullAccountSkipped=true noLaterCardsVisited=true",
                    )
                    advanceSyncQueue()
                } else if (account != null) {
                    archiving = true
                    message = "${account.displayLabel}: leitura concluída • baixando MHTMLs necessários…"
                    onChanged(message.orEmpty())
                    archiveLauncher.launch(BlaBlaManualSeatAutomationIntents.harvest(context, account))
                } else {
'''
if old in text:
    text = text.replace(old, new, 1)
elif "AGENDA_TODAY_CARD_SYNC_FINISHED" not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: session return today anchor not found")

old = '''        } else {
            val exactTripId = targetedSyncTripId
            if (exactTripId != null) {
'''
new = '''        } else {
            val exactTripId = targetedSyncTripId
            val dateScope = syncDateScope
            if (exactTripId != null) {
'''
if old in text:
    text = text.replace(old, new, 1)
elif "val dateScope = syncDateScope" not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: date scope launcher state anchor not found")

old = '''            } else {
                sessionLauncher.launch(BlaBlaDynamicSessionIntents.sync(context, account))
            }
'''
new = '''            } else if (dateScope != null) {
                sessionLauncher.launch(BlaBlaDynamicSessionIntents.syncToday(context, account, dateScope))
            } else {
                sessionLauncher.launch(BlaBlaDynamicSessionIntents.sync(context, account))
            }
'''
if old in text:
    text = text.replace(old, new, 1)
elif "BlaBlaDynamicSessionIntents.syncToday" not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: syncToday launcher anchor not found")

old = '''        } else {
            syncing = false
            publishCombined("Sincronização + MHTML concluídos")
'''
new = '''        } else {
            syncing = false
            publishCombined(
                if (syncDateScope != null) "Sincronização do card de hoje concluída" else "Sincronização + MHTML concluídos",
            )
'''
if old in text:
    text = text.replace(old, new, 1)
elif "Sincronização do card de hoje concluída" not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: queue completion label anchor not found")

old_message = 'message = "Sincronizando ${accounts.size} conta(s) • Timeline somente de ${today.format(DateTimeFormatter.ofPattern("dd/MM"))}…"'
new_message = 'message = "Sincronizando somente o card de hoje (${today.format(DateTimeFormatter.ofPattern("dd/MM"))})…"'
if old_message in text:
    text = text.replace(old_message, new_message, 1)
elif new_message not in text:
    raise SystemExit("TripBlaBlaCollectorUi.kt: today message anchor not found")

ui.write_text(text)


# 4) Focused pure regression test: the exact same date parser used by the normal
# trip normalizer now decides which card the traversal is allowed to open.
test = Path("app/src/test/java/br/com/mapeiaia/rotacerta/trips/AgendaTodayCardTraversal0281Test.kt")
test.write_text('''package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgendaTodayCardTraversal0281Test {
    private val today = LocalDate.of(2026, 8, 25)

    private fun candidate(id: String, dateText: String, text: String = dateText) = BlaBlaDomRideCandidate(
        href = "https://www.blablacar.com.br/rides/offer?id=$id",
        text = text,
        dateText = dateText,
    )

    @Test
    fun todayScopeOpensOnlyHojeCardAndIgnoresLaterCards() {
        val todayCard = candidate("today", "Hoje")
        val fridayCard = candidate("friday", "Sex. 28 Ago.")
        val septemberCard = candidate("september", "Sex. 04 Set.")

        val selected = BlaBlaCollectorCardModule.candidatesOnDate(
            listOf(todayCard, fridayCard, septemberCard),
            targetDate = today,
            today = today,
        )

        assertEquals(listOf(todayCard.href), selected.map { it.href })
    }

    @Test
    fun absoluteTodayDateUsesSameNormalizerAuthority() {
        val card = candidate("absolute", "2026-08-25")
        assertEquals(today, BlaBlaCollectorCardModule.candidateDate(card, today))
    }

    @Test
    fun missingDateEvidenceNeverBecomesTodayByGuess() {
        val card = candidate("unknown", "", "Santo André São Tomé das Letras 11:00")
        assertNull(BlaBlaCollectorCardModule.candidateDate(card, today))
    }
}
''')


# 5) Version bump.
build = Path("app/build.gradle.kts")
text = build.read_text()
old = '        versionCode = 5573\n        versionName = "0.1.280"'
new = '        versionCode = 5574\n        versionName = "0.1.281"'
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("app/build.gradle.kts: expected 0.1.280 baseline not found")
build.write_text(text)
