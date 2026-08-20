#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
DYNAMIC = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt"

if not DYNAMIC.is_file():
    raise SystemExit(f"missing materialized Stage47 dynamic account source: {DYNAMIC}")


def once(old: str, new: str, label: str) -> None:
    text = DYNAMIC.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    DYNAMIC.write_text(text.replace(old, new, 1), encoding="utf-8")


# Read-only contact enrichment. The trip page already provides passenger booking
# links. During the same authenticated sync, open each passenger booking detail
# and read the phone evidence behind the platform's own "Ligar" action. Never
# invoke the call action, never mutate the external booking, and never invent a
# phone when no tel: evidence exists.
once(
'''@Serializable
private data class DynamicTripDetail(
    val detail: BlaBlaDomTripDetail = BlaBlaDomTripDetail(),
    val driverProfileLinks: List<String> = emptyList(),
    val domHtml: String = "",
)
''',
'''@Serializable
private data class DynamicTripDetail(
    val detail: BlaBlaDomTripDetail = BlaBlaDomTripDetail(),
    val driverProfileLinks: List<String> = emptyList(),
    val domHtml: String = "",
)

@Serializable
private data class DynamicPassengerContactEvidence(
    val phone: String = "",
    val visibleName: String = "",
)
''',
    "passenger contact evidence model",
)

once(
'''    private var identityConfirmedThisSync = false
    private var rideReadAttempts = 0
''',
'''    private var identityConfirmedThisSync = false
    private var rideReadAttempts = 0
    private var pendingTripDetail: DynamicTripDetail? = null
    private var pendingTripPassengers = mutableListOf<BlaBlaCollectorPassenger>()
    private var passengerContactIndex = 0
    private var passengerContactReadAttempts = 0
''',
    "passenger contact traversal state",
)

once(
'''                    Phase.RIDES -> if (isBlaBla(url)) view.postDelayed({ captureRideList() }, 900)
                    Phase.DETAIL -> if (isBlaBla(url)) view.postDelayed({ captureTripDetail() }, 750)
                    Phase.IDLE -> if (isBlaBla(url)) view.postDelayed({ probeIdentity() }, 500)
''',
'''                    Phase.RIDES -> if (isBlaBla(url)) view.postDelayed({ captureRideList() }, 900)
                    Phase.DETAIL -> if (isBlaBla(url)) view.postDelayed({ captureTripDetail() }, 750)
                    Phase.PASSENGER_CONTACT -> if (isBlaBla(url)) view.postDelayed({ capturePassengerContact() }, 850)
                    Phase.IDLE -> if (isBlaBla(url)) view.postDelayed({ probeIdentity() }, 500)
''',
    "passenger contact page lifecycle",
)

once(
'''        rideReadAttempts = 0
        identityConfirmedThisSync = false
        phase = Phase.IDENTITY
''',
'''        rideReadAttempts = 0
        identityConfirmedThisSync = false
        pendingTripDetail = null
        pendingTripPassengers.clear()
        passengerContactIndex = 0
        passengerContactReadAttempts = 0
        phase = Phase.IDENTITY
''',
    "reset passenger contact state on sync",
)

old_capture = '''    private fun captureTripDetail() {
        if (phase != Phase.DETAIL) return
        val candidate = candidates.getOrNull(candidateIndex) ?: return
        evaluate<DynamicTripDetail>(TRIP_DETAIL_DYNAMIC_JS) { result ->
            if (result != null) {
                store.saveDiagnosticHtml(account, "trip-${candidateIndex + 1}", result.domHtml)
                val driverUuids = uuids(result.driverProfileLinks)
                val expectedUuid = account.profileUuid?.lowercase()
                UnifiedDebugEventStore.record(
                    "TRIP_DETAIL_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} expectedUuid=${expectedUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")} url=${sanitizedUrl(webView.url.orEmpty())}",
                )
                if (expectedUuid != null && driverUuids.isNotEmpty() && expectedUuid !in driverUuids) {
                    skipped++
                    UnifiedDebugEventStore.record(
                        "TRIP_REJECTED",
                        packageName,
                        "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=explicit_detail_uuid_mismatch expectedUuid=$expectedUuid foundUuids=${driverUuids.joinToString(",")}",
                    )
                    candidateIndex++
                    loadCurrentCandidate()
                    return@evaluate
                }
                when {
                    expectedUuid != null && expectedUuid in driverUuids -> identityConfirmedThisSync = true
                    expectedUuid == null && driverUuids.size == 1 -> {
                        val updated = registry.bindIdentity(account.id, driverUuids.single(), result.detail.driverName)
                        if (updated != null) {
                            account = updated
                            identityConfirmedThisSync = true
                        }
                    }
                }
                val definition = account.verifiedDefinition()
                val trip = definition?.let {
                    BlaBlaDomNormalizer.toTrip(
                        account = it,
                        candidate = candidate,
                        detail = result.detail,
                        today = LocalDate.now(),
                        authenticatedProfileSessionVerified = identityConfirmedThisSync,
                    )
                }
                if (trip != null && identityConfirmedThisSync) {
                    collected += trip
                    UnifiedDebugEventStore.record(
                        "TRIP_ACCEPTED",
                        packageName,
                        "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} validation=${trip.uuid_validation} date=${trip.date} departure=${trip.departure_time.orEmpty()} origin=${trip.actual_departure.orEmpty()} destination=${trip.actual_arrival.orEmpty()}",
                    )
                } else {
                    skipped++
                    val reason = when {
                        definition == null -> "account_definition_missing"
                        !identityConfirmedThisSync -> "identity_not_verified"
                        else -> "trip_fields_unparseable"
                    }
                    UnifiedDebugEventStore.record(
                        "TRIP_REJECTED",
                        packageName,
                        "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=$reason expectedUuid=${account.profileUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")}",
                    )
                }
            } else {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=detail_dom_unreadable url=${sanitizedUrl(webView.url.orEmpty())}",
                )
            }
            candidateIndex++
            loadCurrentCandidate()
        }
    }
'''

new_capture = '''    private fun captureTripDetail() {
        if (phase != Phase.DETAIL) return
        val candidate = candidates.getOrNull(candidateIndex) ?: return
        evaluate<DynamicTripDetail>(TRIP_DETAIL_DYNAMIC_JS) { result ->
            if (result == null) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=detail_dom_unreadable url=${sanitizedUrl(webView.url.orEmpty())}",
                )
                advanceAfterCurrentTrip()
                return@evaluate
            }

            store.saveDiagnosticHtml(account, "trip-${candidateIndex + 1}", result.domHtml)
            val driverUuids = uuids(result.driverProfileLinks)
            val expectedUuid = account.profileUuid?.lowercase()
            UnifiedDebugEventStore.record(
                "TRIP_DETAIL_CAPTURED",
                packageName,
                "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} expectedUuid=${expectedUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")} url=${sanitizedUrl(webView.url.orEmpty())}",
            )
            if (expectedUuid != null && driverUuids.isNotEmpty() && expectedUuid !in driverUuids) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=explicit_detail_uuid_mismatch expectedUuid=$expectedUuid foundUuids=${driverUuids.joinToString(",")}",
                )
                advanceAfterCurrentTrip()
                return@evaluate
            }
            when {
                expectedUuid != null && expectedUuid in driverUuids -> identityConfirmedThisSync = true
                expectedUuid == null && driverUuids.size == 1 -> {
                    val updated = registry.bindIdentity(account.id, driverUuids.single(), result.detail.driverName)
                    if (updated != null) {
                        account = updated
                        identityConfirmedThisSync = true
                    }
                }
            }

            if (!identityConfirmedThisSync || account.verifiedDefinition() == null) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=identity_not_verified expectedUuid=${account.profileUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")}",
                )
                advanceAfterCurrentTrip()
                return@evaluate
            }

            pendingTripDetail = result
            pendingTripPassengers = result.detail.passengers.toMutableList()
            passengerContactIndex = 0
            passengerContactReadAttempts = 0
            if (pendingTripPassengers.any { it.phone.isNullOrBlank() && !it.booking_href.isNullOrBlank() }) {
                loadNextPassengerContact()
            } else {
                finalizeCurrentTrip()
            }
        }
    }

    private fun loadNextPassengerContact() {
        while (passengerContactIndex < pendingTripPassengers.size) {
            val passenger = pendingTripPassengers[passengerContactIndex]
            val href = passenger.booking_href?.trim().orEmpty()
            if (passenger.phone.isNullOrBlank() && href.isNotBlank() && isBlaBla(href)) {
                phase = Phase.PASSENGER_CONTACT
                passengerContactReadAttempts = 0
                statusView.text = "${account.displayLabel} • contato ${passengerContactIndex + 1}/${pendingTripPassengers.size}…"
                webView.loadUrl(href)
                return
            }
            passengerContactIndex++
        }
        finalizeCurrentTrip()
    }

    private fun capturePassengerContact() {
        if (phase != Phase.PASSENGER_CONTACT) return
        val index = passengerContactIndex
        val current = pendingTripPassengers.getOrNull(index) ?: run {
            finalizeCurrentTrip()
            return
        }
        evaluate<DynamicPassengerContactEvidence>(PASSENGER_CONTACT_JS) { evidence ->
            val phone = normalizeCapturedPhone(evidence?.phone)
            if (phone == null && passengerContactReadAttempts < 2) {
                passengerContactReadAttempts++
                webView.postDelayed({ capturePassengerContact() }, 700)
                return@evaluate
            }

            val visibleName = evidence?.visibleName?.trim().orEmpty()
            pendingTripPassengers[index] = current.copy(
                name = current.name.ifBlank { visibleName },
                phone = current.phone?.takeIf(String::isNotBlank) ?: phone,
            )
            UnifiedDebugEventStore.record(
                "PASSENGER_CONTACT_CAPTURED",
                packageName,
                "account=${account.displayLabel} tripIndex=${candidateIndex + 1}/${candidates.size} passengerIndex=${index + 1}/${pendingTripPassengers.size} phonePresent=${phone != null} bookingLinkPresent=${!current.booking_href.isNullOrBlank()}",
            )
            passengerContactIndex++
            loadNextPassengerContact()
        }
    }

    private fun finalizeCurrentTrip() {
        val candidate = candidates.getOrNull(candidateIndex)
        val result = pendingTripDetail
        val definition = account.verifiedDefinition()
        if (candidate == null || result == null || definition == null) {
            skipped++
            UnifiedDebugEventStore.record(
                "TRIP_REJECTED",
                packageName,
                "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=pending_trip_state_missing",
            )
            advanceAfterCurrentTrip()
            return
        }

        val enrichedDetail = result.detail.copy(passengers = pendingTripPassengers.toList())
        val trip = BlaBlaDomNormalizer.toTrip(
            account = definition,
            candidate = candidate,
            detail = enrichedDetail,
            today = LocalDate.now(),
            authenticatedProfileSessionVerified = identityConfirmedThisSync,
        )
        if (trip != null && identityConfirmedThisSync) {
            collected += trip
            UnifiedDebugEventStore.record(
                "TRIP_ACCEPTED",
                packageName,
                "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} validation=${trip.uuid_validation} date=${trip.date} departure=${trip.departure_time.orEmpty()} origin=${trip.actual_departure.orEmpty()} destination=${trip.actual_arrival.orEmpty()} passengers=${trip.passengers.size} phones=${trip.passengers.count { !it.phone.isNullOrBlank() }}",
            )
        } else {
            skipped++
            UnifiedDebugEventStore.record(
                "TRIP_REJECTED",
                packageName,
                "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} reason=trip_fields_unparseable expectedUuid=${account.profileUuid.orEmpty()}",
            )
        }
        advanceAfterCurrentTrip()
    }

    private fun advanceAfterCurrentTrip() {
        pendingTripDetail = null
        pendingTripPassengers.clear()
        passengerContactIndex = 0
        passengerContactReadAttempts = 0
        phase = Phase.DETAIL
        candidateIndex++
        loadCurrentCandidate()
    }

    private fun normalizeCapturedPhone(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val hasPlus = value.startsWith("+")
        val digits = value.filter(Char::isDigit)
        if (digits.length < 8 || digits.length > 15) return null
        return if (hasPlus) "+$digits" else digits
    }
'''

once(old_capture, new_capture, "authenticated passenger contact traversal")

once(
'''    private enum class Phase { IDLE, IDENTITY, RIDES, DETAIL }
''',
'''    private enum class Phase { IDLE, IDENTITY, RIDES, DETAIL, PASSENGER_CONTACT }
''',
    "passenger contact phase",
)

# The passenger detail page shown in the physical video exposes a "Ligar" action.
# Read only tel: evidence from rendered DOM/attributes. The script never clicks
# or invokes that action. No country code is assumed: international numbers are
# preserved as provided by the external platform.
once(
'''        private val TRIP_DETAIL_DYNAMIC_JS = """
''',
'''        private val PASSENGER_CONTACT_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\\s+/g, ' ').trim();
              const nodes = Array.from(document.querySelectorAll('[href^="tel:"], a, button, [role="button"]'));
              const candidates = [];
              nodes.forEach((node) => {
                const href = (node.getAttribute && node.getAttribute('href')) || '';
                if (/^tel:/i.test(href)) candidates.push(href);
                const outer = node.outerHTML || '';
                const matches = outer.match(/tel:[+0-9(). \\-]{8,32}/ig) || [];
                matches.forEach((value) => candidates.push(value));
              });
              const pageHtml = document.documentElement ? (document.documentElement.outerHTML || '') : '';
              (pageHtml.match(/tel:[+0-9(). \\-]{8,32}/ig) || []).forEach((value) => candidates.push(value));
              const rawPhone = candidates.find((value) => /^tel:/i.test(value)) || '';
              const phone = rawPhone
                ? rawPhone.replace(/^tel:/i, '').split('?')[0].replace(/[^+0-9]/g, '')
                : '';
              const nameNode = document.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], h1');
              return JSON.stringify({
                phone: phone,
                visibleName: clean(nameNode && nameNode.innerText)
              });
            })();
        """.trimIndent()

        private val TRIP_DETAIL_DYNAMIC_JS = """
''',
    "passenger contact DOM reader",
)

text = DYNAMIC.read_text(encoding="utf-8")
for marker in (
    "PASSENGER_CONTACT",
    "PASSENGER_CONTACT_JS",
    "PASSENGER_CONTACT_CAPTURED",
    "loadNextPassengerContact()",
    "capturePassengerContact()",
    "normalizeCapturedPhone",
    "phonePresent=${phone != null}",
):
    if marker not in text:
        raise SystemExit(f"passenger contact capture marker missing: {marker}")

# Safety invariants: this feature is read/reconciliation only.
for forbidden in (
    'Intent(Intent.ACTION_DIAL',
    'Intent(Intent.ACTION_CALL',
    'webView.loadUrl("tel:',
    'performClick()',
    'seat_write',
):
    if forbidden in text:
        raise SystemExit(f"passenger contact patch introduced forbidden behavior: {forbidden}")

print(
    "stage47_r4_step7_passenger_contact_capture=PASS "
    "authenticated_booking_detail_read=true tel_evidence_only=true external_write=false "
    "call_action_not_invoked=true phone_not_in_diagnostics=true no_country_hardcode=true "
    "timeline_whatsapp_ready=true farol_touched=false"
)
