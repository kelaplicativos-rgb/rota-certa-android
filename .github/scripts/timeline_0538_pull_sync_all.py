from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
ACTIVITY = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
GESTURE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaPullRefreshGesture0388.kt"
GESTURE_TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/trips/AgendaPullRefreshGesture0388Test.kt"
GLOBAL_TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/trips/TimelineGlobalPullSync0538Test.kt"
PASSENGER_UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt"
BUILD = ROOT / "app/build.gradle.kts"


def fail(message: str) -> None:
    raise SystemExit(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")


def replace_once(content: str, old: str, new: str, label: str) -> str:
    count = content.count(old)
    if count != 1:
        fail(f"{label}: expected exactly one baseline anchor, found {count}")
    return content.replace(old, new, 1)


build = read(BUILD)
ui = read(UI)
activity = read(ACTIVITY)
gesture = read(GESTURE)
gesture_test = read(GESTURE_TEST)
passenger_ui = read(PASSENGER_UI)

if 'versionCode = 5829' not in build or 'versionName = "0.1.537"' not in build:
    fail("Unexpected version baseline; refusing to materialize 0.1.538")

required_ui = [
    'var expandedTripIds0536 by remember { mutableStateOf<Set<String>>(emptySet()) }',
    'onClickLabel = if (expanded) "Fechar viagem" else "Abrir viagem"',
    'AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(',
    'origin = BlaBlaCommandOrigin0407.CARD',
    'tripTargetsByCard0432',
]
for marker in required_ui:
    if marker not in ui:
        fail(f"Required Timeline invariant missing: {marker}")

for marker in [
    'val pickupTarget = passengerPickupMapTarget(passenger)',
    'openPassengerPickupMap(context, pickupTarget)',
    'val dropoffTarget = passengerDropoffMapTarget(passenger)',
    'openPassengerDropoffMap(context, dropoffTarget)',
]:
    if marker not in passenger_ui:
        fail(f"Passenger operational control missing before change: {marker}")
if "Carregando passageiros" in passenger_ui:
    fail("Visible passenger loading regression exists in baseline")

helper_anchor = '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTimelineScreen('''
helper = '''internal fun distinctTimelineGlobalPullTargets0538(
    targets: Iterable<BlaBlaTripTarget0407?>,
): List<BlaBlaTripTarget0407> = targets
    .filterNotNull()
    .distinctBy(BlaBlaTripTarget0407::strongIdentityKey)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTimelineScreen('''
ui = replace_once(ui, helper_anchor, helper, "global pull strong-target helper")

old_manual_effect = '''    LaunchedEffect(manualRefreshToken0499) {
        if (manualRefreshToken0499 > 0) {
            invalidateCanonicalTimeline0495("USER_PULL_REFRESH")
        }
    }

'''
ui = replace_once(ui, old_manual_effect, "", "remove old canonical-only pull effect")

target_block = '''    val tripTargetsByCard0432 = remember(entries, registeredAccounts0432) {
        entries.associate { entry ->
            timelineLazyItemKey0380(entry) to resolveBlaBlaTripTarget0407(
                context = context,
                entry = entry,
                accounts = registeredAccounts0432,
            )
        }
    }
'''
global_effect = target_block + '''    LaunchedEffect(manualRefreshToken0499) {
        if (manualRefreshToken0499 <= 0) return@LaunchedEffect
        val resolvedTargets0538 = tripTargetsByCard0432.values.filterNotNull()
        val targets0538 = distinctTimelineGlobalPullTargets0538(resolvedTargets0538)
        var acceptedTargets0538 = 0
        targets0538.forEach { target0538 ->
            val command0538 = BlaBlaCommand0407.forTarget(
                target = target0538,
                operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
                origin = BlaBlaCommandOrigin0407.SYSTEM_RECONCILIATION,
            )
            if (
                AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(
                    context = context,
                    target = target0538,
                    commandId = command0538.commandId,
                    requestedAtMillis = command0538.requestedAtMillis,
                )
            ) {
                acceptedTargets0538++
            }
        }
        UnifiedDebugEventStore.record(
            "TIMELINE_PULL_REFRESH_ALL_COLLECTOR_0538",
            context.packageName,
            "cards=${tripTargetsByCard0432.size} resolvedTargets=${resolvedTargets0538.size} " +
                "strongTargets=${targets0538.size} acceptedTargets=$acceptedTargets0538 " +
                "skippedIdentity=${tripTargetsByCard0432.size - resolvedTargets0538.size} " +
                "deduplicated=${resolvedTargets0538.size - targets0538.size} " +
                "collectorToAgenda=true directTimelineCollectorRead=false canonicalSecondaryRefresh=true",
        )
        invalidateCanonicalTimeline0495("USER_PULL_REFRESH")
    }
'''
ui = replace_once(ui, target_block, global_effect, "global pull collector batch")

old_request = '''    val requestTimelineCanonicalPullRefresh0499 = {
        if (!timelinePullRefreshing0499) {
            timelinePullRefreshing0499 = true
            timelinePullRefreshToken0499 += 1
            message = "Atualizando Timeline pelo domínio permitido do Rota Certa..."
            UnifiedDebugEventStore.record(
                "AGENDA_TIMELINE_CANONICAL_PULL_REFRESH_0499",
                activity.packageName,
                "networkSync=true source=CANONICAL_NATIVE_FIREWALL collectorRead=false collectorFallback=false collectorDerivedData=false",
            )
        }
    }
'''
new_request = '''    val requestTimelineCanonicalPullRefresh0499 = {
        if (!timelinePullRefreshing0499) {
            timelinePullRefreshing0499 = true
            timelinePullRefreshToken0499 += 1
            message = "Atualizando todas as viagens: BlaBlaCar → Agenda → Timeline..."
            UnifiedDebugEventStore.record(
                "AGENDA_TIMELINE_GLOBAL_PULL_REFRESH_0538",
                activity.packageName,
                "collectorBatch=true collectorToAgenda=true directTimelineCollectorRead=false canonicalSecondaryRefresh=true collectorFallback=false",
            )
        }
    }
'''
activity = replace_once(activity, old_request, new_request, "global pull request semantics")

old_success = '''                                message = "Timeline atualizada pelo domínio permitido do Rota Certa. O coletor BlaBlaCar não participa deste fluxo."
                            } else {
                                message = "Não foi possível atualizar a Timeline pelo domínio permitido: $error0499"
'''
new_success = '''                                message = "Atualização global solicitada: BlaBlaCar → Agenda → Timeline. O backend canônico permanece secundário."
                            } else {
                                message = "Atualização BlaBlaCar → Agenda continua em segundo plano; sincronização canônica secundária indisponível: $error0499"
'''
activity = replace_once(activity, old_success, new_success, "global pull completion semantics")

old_gate_ctor = '''internal class AgendaPullRefreshGestureGate0388(
    private val touchSlopPx: Float,
    private val verticalDominanceRatio: Float = 1.15f,
) {'''
new_gate_ctor = '''internal class AgendaPullRefreshGestureGate0388(
    private val touchSlopPx: Float,
    private val verticalDominanceRatio: Float = 1.15f,
    private val awayFromTopTriggerMultiplier: Float = 4f,
) {'''
gesture = replace_once(gesture, old_gate_ctor, new_gate_ctor, "away-from-top gesture threshold")

old_gate_logic = '''        val distanceSquared = dx * dx + dy * dy
        val slop = touchSlopPx.coerceAtLeast(0f)
        if (distanceSquared <= slop * slop) return null

        val verticalDown = dy > 0f && abs(dy) > abs(dx) * verticalDominanceRatio
        val outcome = when {
            !verticalDown -> AgendaPullRefreshOutcome0388.REJECTED_DIRECTION
            refreshingAtStart -> AgendaPullRefreshOutcome0388.BLOCKED_REFRESH_RUNNING
            !eligibleAtStart -> AgendaPullRefreshOutcome0388.BLOCKED_NOT_AT_TOP
            else -> AgendaPullRefreshOutcome0388.ACCEPTED
        }
        resolved = true
'''
new_gate_logic = '''        val distanceSquared = dx * dx + dy * dy
        val slop = touchSlopPx.coerceAtLeast(0f)
        val verticalDown = dy > 0f && abs(dy) > abs(dx) * verticalDominanceRatio
        val outcome = when {
            !verticalDown -> {
                if (distanceSquared <= slop * slop) return null
                AgendaPullRefreshOutcome0388.REJECTED_DIRECTION
            }
            refreshingAtStart -> {
                if (distanceSquared <= slop * slop) return null
                AgendaPullRefreshOutcome0388.BLOCKED_REFRESH_RUNNING
            }
            else -> {
                val triggerPx = if (eligibleAtStart) {
                    slop
                } else {
                    slop * awayFromTopTriggerMultiplier.coerceAtLeast(1f)
                }
                if (dy <= triggerPx) return null
                AgendaPullRefreshOutcome0388.ACCEPTED
            }
        }
        resolved = true
'''
gesture = replace_once(gesture, old_gate_logic, new_gate_logic, "gesture anywhere decision")

old_comment = ''' * It deliberately snapshots list eligibility and refresh state on DOWN. A gesture that starts
 * while the list is scrolled away from the top remains a normal list scroll even if that same
 * drag eventually reaches the top. Likewise, a gesture that starts while a full refresh is
 * running can never start a second cycle.
'''
new_comment = ''' * It snapshots list position and refresh state on DOWN. At the top, the existing touch-slop
 * threshold remains immediate. Away from the top, a larger deliberate downward pull is required
 * so ordinary short reverse scrolling still works. A gesture that starts while a full refresh is
 * running can never start a second cycle.
'''
gesture = replace_once(gesture, old_comment, new_comment, "gesture contract comment")

old_test = '''    @Test
    fun downwardDragStartedAwayFromTopRemainsNormalScrollForWholeSequence() {
        val gate = gate()
        gate.onDown(Offset.Zero, canRefreshAtStart = false, refreshRunningAtStart = false)

        val decision = gate.onMove(Offset(0f, 40f))

        assertEquals(AgendaPullRefreshOutcome0388.BLOCKED_NOT_AT_TOP, decision?.outcome)
        assertNull(gate.onMove(Offset(0f, 100f)), "reaching top later in the same drag must not become a refresh")
    }
'''
new_test = '''    @Test
    fun downwardDragStartedAwayFromTopPreservesShortScrollThenAcceptsDeliberatePull() {
        val gate = gate()
        gate.onDown(Offset.Zero, canRefreshAtStart = false, refreshRunningAtStart = false)

        assertNull(gate.onMove(Offset(0f, 30f)), "short reverse scroll away from top must remain available")
        val decision = gate.onMove(Offset(0f, 50f))

        assertEquals(AgendaPullRefreshOutcome0388.ACCEPTED, decision?.outcome)
        assertTrue(decision?.accepted == true)
        assertFalse(decision?.eligibleAtStart == true)
    }
'''
gesture_test = replace_once(gesture_test, old_test, new_test, "away-from-top gesture regression test")

write(UI, ui)
write(ACTIVITY, activity)
write(GESTURE, gesture)
write(GESTURE_TEST, gesture_test)

GLOBAL_TEST.write_text('''package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineGlobalPullSync0538Test {
    private fun target(
        tenant: String = "tenant",
        account: String = "account",
        profile: String,
        trip: String,
        href: String = "https://www.blablacar.com.br/ride-plan/trip-edit/$trip",
    ) = BlaBlaTripTarget0407(
        tenantId = tenant,
        accountId = account,
        profileUuid = profile,
        tripId = trip,
        tripHref = href,
    )

    @Test
    fun deduplicatesByStrongIdentityAcrossTimelineCards() {
        val first = target(profile = "PROFILE-A", trip = "trip-001")
        val duplicate = target(profile = "profile-a", trip = "trip-001", href = "https://www.blablacar.com.br/ride-plan/trip-edit/trip-001?source=duplicate")
        val second = target(profile = "profile-b", trip = "trip-002")

        val result = distinctTimelineGlobalPullTargets0538(listOf(first, null, duplicate, second))

        assertEquals(2, result.size)
        assertEquals(listOf(first.strongIdentityKey, second.strongIdentityKey), result.map { it.strongIdentityKey })
    }

    @Test
    fun keepsSameTripIdSeparatedAcrossProfiles() {
        val a = target(profile = "profile-a", trip = "same-trip")
        val b = target(profile = "profile-b", trip = "same-trip")

        val result = distinctTimelineGlobalPullTargets0538(listOf(a, b))

        assertEquals(2, result.size)
    }
}
''', encoding="utf-8")

build = replace_once(build, 'versionCode = 5829', 'versionCode = 5830', "versionCode")
build = replace_once(build, 'versionName = "0.1.537"', 'versionName = "0.1.538"', "versionName")
write(BUILD, build)

final_ui = read(UI)
final_activity = read(ACTIVITY)
final_gesture = read(GESTURE)
final_passenger = read(PASSENGER_UI)
if 'TIMELINE_PULL_REFRESH_ALL_COLLECTOR_0538' not in final_ui:
    fail("Global collector batch was not materialized")
if 'origin = BlaBlaCommandOrigin0407.SYSTEM_RECONCILIATION' not in final_ui:
    fail("Global pull does not use system reconciliation origin")
if 'origin = BlaBlaCommandOrigin0407.CARD' not in final_ui:
    fail("Per-card collector origin regressed")
if final_ui.count('AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(') < 2:
    fail("Expected both global and per-card collector enqueue paths")
if 'invalidateCanonicalTimeline0495("USER_PULL_REFRESH")' not in final_ui:
    fail("Secondary canonical refresh after collector batch missing")
if 'AGENDA_TIMELINE_GLOBAL_PULL_REFRESH_0538' not in final_activity:
    fail("Global pull activity event missing")
if 'slop * awayFromTopTriggerMultiplier.coerceAtLeast(1f)' not in final_gesture:
    fail("Away-from-top deliberate threshold missing")
if "Carregando passageiros" in final_passenger:
    fail("Passenger loading text regression detected")

print("Timeline 0.1.538 global pull collector sync materialized successfully")
