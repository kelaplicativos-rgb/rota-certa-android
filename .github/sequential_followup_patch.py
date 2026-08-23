from pathlib import Path

p = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt')
s = p.read_text()

if 'DIRECT_TRIP_EVIDENCE_PERSISTED' in s and 'PASSENGER_TEL_INTERCEPTED' in s:
    raise SystemExit(0)

def once(old: str, new: str, label: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    s = s.replace(old, new, 1)

once('import android.webkit.WebView\n', 'import android.webkit.WebResourceRequest\nimport android.webkit.WebView\n', 'import-web-resource')

once(
'''private data class DynamicTripDetail(
    val detail: BlaBlaDomTripDetail = BlaBlaDomTripDetail(),
    val driverProfileLinks: List<String> = emptyList(),
    val passengerHrefs: List<String> = emptyList(),
    val explicitEmptyRoster: Boolean = false,
    val domHtml: String = "",
)''',
'''private data class DynamicTripDetail(
    val detail: BlaBlaDomTripDetail = BlaBlaDomTripDetail(),
    val driverProfileLinks: List<String> = emptyList(),
    val passengerHrefs: List<String> = emptyList(),
    val explicitEmptyRoster: Boolean = false,
    val itineraryStops: List<String> = emptyList(),
    val views: Int? = null,
    val domHtml: String = "",
)''', 'trip-detail-evidence')

once(
'''private data class DynamicPassengerContactEvidence(
    val phone: String = "",
    val visibleName: String = "",
    val fareAmount: String = "",
    val fareCurrencyCode: String = "",''',
'''private data class DynamicPassengerContactEvidence(
    val phone: String = "",
    val visibleName: String = "",
    val fareAmount: String = "",
    val fareCurrencyCode: String = "",
    val callActionPresent: Boolean = false,''', 'contact-action-field')

once(
'''    private var passengerCardReadAttempts = 0
    private var syncGeneration = 0L''',
'''    private var passengerCardReadAttempts = 0
    private var passengerCallActionTriggered = false
    private var interceptedPassengerPhone: String? = null
    private var syncGeneration = 0L''', 'phone-state')

once(
'''        passengerCardReadAttempts = 0
        phase = Phase.IDENTITY''',
'''        passengerCardReadAttempts = 0
        passengerCallActionTriggered = false
        interceptedPassengerPhone = null
        phase = Phase.IDENTITY''', 'phone-reset-begin')

once(
'''        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {''',
'''        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString()
                return if (interceptPhoneNavigation(target)) true else super.shouldOverrideUrlLoading(view, request)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return if (interceptPhoneNavigation(url)) true else super.shouldOverrideUrlLoading(view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {''', 'web-client-phone')

marker = '    private fun beginSync() {'
insert = '''    private fun interceptPhoneNavigation(rawUrl: String?): Boolean {
        val url = rawUrl?.trim().orEmpty()
        if (!url.startsWith("tel:", ignoreCase = true)) return false
        val passenger = pendingTripPassengers.getOrNull(passengerContactIndex)
        val pageUrl = if (::webView.isInitialized) webView.url.orEmpty() else ""
        val phone = normalizeCapturedPhone(url.substringAfter(':').substringBefore('?'))
        if (
            phase == Phase.PASSENGER_CONTACT &&
            passenger != null &&
            passengerPageMatchesExpected(passenger.booking_href.orEmpty(), pageUrl)
        ) {
            interceptedPassengerPhone = phone
            UnifiedDebugEventStore.record(
                "PASSENGER_TEL_INTERCEPTED",
                packageName,
                "account=${account.displayLabel} tripId=${candidates.getOrNull(candidateIndex)?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }.orEmpty()} passengerIndex=${passengerContactIndex + 1}/${pendingTripPassengers.size} phonePresent=${phone != null} externalDialerOpened=false",
            )
        } else {
            recordStale("tel_intercept_without_current_passenger", syncGeneration, candidateIndex)
        }
        return true
    }

'''
if s.count(marker) != 1:
    raise SystemExit('beginSync marker missing')
s = s.replace(marker, insert + marker, 1)

once(
'''                BlaBlaDirectPassengerStep.RESERVATION_URL -> {
                    phase = Phase.PASSENGER_CONTACT
                    passengerContactReadAttempts = 0
                    passengerCaptureInFlight = false''',
'''                BlaBlaDirectPassengerStep.RESERVATION_URL -> {
                    phase = Phase.PASSENGER_CONTACT
                    passengerContactReadAttempts = 0
                    passengerCallActionTriggered = false
                    interceptedPassengerPhone = null
                    passengerCaptureInFlight = false''', 'phone-reset-url')

once(
'''                BlaBlaDirectPassengerStep.PASSENGER_CARD -> {
                    phase = Phase.PASSENGER_CARD
                    passengerContactReadAttempts = 0
                    passengerCardReadAttempts = 0''',
'''                BlaBlaDirectPassengerStep.PASSENGER_CARD -> {
                    phase = Phase.PASSENGER_CARD
                    passengerContactReadAttempts = 0
                    passengerCardReadAttempts = 0
                    passengerCallActionTriggered = false
                    interceptedPassengerPhone = null''', 'phone-reset-card')

old = '''            val effectivePhone = current.phone?.takeIf(String::isNotBlank) ?: normalizeCapturedPhone(evidence.phone)
            saveCapturedPassengerFare(current.booking_href, evidence)
            saveCapturedPassengerBoardingEvidence(current.booking_href, evidence)'''
new = '''            val effectivePhone = current.phone?.takeIf(String::isNotBlank)
                ?: normalizeCapturedPhone(evidence.phone)
                ?: interceptedPassengerPhone
            if (effectivePhone == null && evidence.callActionPresent && !passengerCallActionTriggered) {
                passengerCallActionTriggered = true
                UnifiedDebugEventStore.record(
                    "PASSENGER_CALL_ACTION_PRESENT",
                    packageName,
                    "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} actionPresent=true clickIntercepted=true",
                )
                webView.evaluateJavascript(CLICK_CALL_ACTION_JS) {
                    if (passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
                        webView.postDelayed({
                            capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)
                        }, PASSENGER_CALL_SETTLE_MS)
                    }
                }
                return@evaluate
            }
            saveCapturedPassengerFare(current.booking_href, evidence)
            saveCapturedPassengerBoardingEvidence(current.booking_href, evidence)'''
once(old, new, 'phone-click-flow')

once(
'''            passengerContactIndex = expectedPassenger + 1
            passengerContactReadAttempts = 0
            loadNextPassengerContact(expectedSync, expectedCandidate)''',
'''            passengerContactIndex = expectedPassenger + 1
            passengerContactReadAttempts = 0
            passengerCallActionTriggered = false
            interceptedPassengerPhone = null
            loadNextPassengerContact(expectedSync, expectedCandidate)''', 'phone-reset-after')

once(
'''        passengerCardReadAttempts = 0
        tripRosterReadAttempts = 0
        passengerCaptureInFlight = false''',
'''        passengerCardReadAttempts = 0
        passengerCallActionTriggered = false
        interceptedPassengerPhone = null
        tripRosterReadAttempts = 0
        passengerCaptureInFlight = false''', 'phone-reset-clear')

once(
'''        completedCardTraversalKeys += currentCardTraversalKey
        UnifiedDebugEventStore.record(''',
'''        persistDirectTripEvidence(tripId)
        completedCardTraversalKeys += currentCardTraversalKey
        UnifiedDebugEventStore.record(''', 'persist-before-complete')

marker = '    private fun blockCurrentCard(expectedSync: Long, expectedCandidate: Int, reason: String) {'
helper = '''    private fun persistDirectTripEvidence(tripId: String) {
        if (tripId.isBlank()) return
        val detail = pendingTripDetail ?: return
        val evidenceStore = BlaBlaHarvestEvidenceStore(this)
        val existing = evidenceStore.read(account.id)
        val prior = existing.firstOrNull { it.tripId == tripId }
        val evidence = BlaBlaHarvestTripEvidence(
            tripId = tripId,
            publishedSeats = prior?.publishedSeats,
            views = detail.views ?: prior?.views,
            itineraryStops = detail.itineraryStops.ifEmpty { prior?.itineraryStops.orEmpty() },
            passengers = pendingTripPassengers.toList(),
            passengerRosterComplete = detail.detail.passengerRosterComplete || detail.explicitEmptyRoster,
        )
        evidenceStore.replace(account.id, existing.filterNot { it.tripId == tripId } + evidence)
        UnifiedDebugEventStore.record(
            "DIRECT_TRIP_EVIDENCE_PERSISTED",
            packageName,
            "account=${account.displayLabel} tripId=$tripId stops=${evidence.itineraryStops.size} viewsPresent=${evidence.views != null} passengers=${evidence.passengers.size} rosterComplete=${evidence.passengerRosterComplete}",
        )
    }

'''
if s.count(marker) != 1:
    raise SystemExit('block marker missing')
s = s.replace(marker, helper + marker, 1)

once(
'''              const nodes = Array.from(document.querySelectorAll('[href^="tel:"], a, button, [role="button"]'));
              const candidates = [];''',
'''              const nodes = Array.from(document.querySelectorAll('[href^="tel:"], a, button, [role="button"], [role="link"]'));
              const callAction = nodes.find((node) => {
                const text = clean(node.innerText || node.textContent);
                const label = clean((node.getAttribute && (node.getAttribute('aria-label') || node.getAttribute('title'))) || '');
                const href = (node.getAttribute && node.getAttribute('href')) || '';
                return /^tel:/i.test(href) || /^(ligar|chamar|telefone|telefonar)$/i.test(text) || /\\b(ligar|telefone|telefonar)\\b/i.test(label);
              });
              const candidates = [];''', 'js-call-action')

once(
'''                fareAmount: fareAmount,
                fareCurrencyCode: fareCurrencyCode,
                boardingAddress: pickup.address,''',
'''                fareAmount: fareAmount,
                fareCurrencyCode: fareCurrencyCode,
                callActionPresent: !!callAction,
                boardingAddress: pickup.address,''', 'js-call-return')

marker = '        private fun passengerCardOpenJs(cardIndex: Int): String = """'
click_js = '''        private val CLICK_CALL_ACTION_JS = """
            (function() {
              const clean = (v) => (v || '').replace(/\\s+/g, ' ').trim();
              const nodes = Array.from(document.querySelectorAll('a[href], button, [role="button"], [role="link"]'));
              const action = nodes.find((node) => {
                const text = clean(node.innerText || node.textContent);
                const label = clean((node.getAttribute && (node.getAttribute('aria-label') || node.getAttribute('title'))) || '');
                const href = (node.getAttribute && node.getAttribute('href')) || '';
                return /^tel:/i.test(href) || /^(ligar|chamar|telefone|telefonar)$/i.test(text) || /\\b(ligar|telefone|telefonar)\\b/i.test(label);
              });
              if (!action || typeof action.click !== 'function') return JSON.stringify({ present: !!action, clicked: false });
              action.click();
              return JSON.stringify({ present: true, clicked: true });
            })();
        """.trimIndent()

'''
if s.count(marker) != 1:
    raise SystemExit('passengerCardOpen marker missing')
s = s.replace(marker, click_js + marker, 1)

once(
'''        private const val PASSENGER_NAVIGATION_SETTLE_MS = 1_200L
        private const val CARD_TARGET_PREFIX = "rotacerta-card:"''',
'''        private const val PASSENGER_NAVIGATION_SETTLE_MS = 1_200L
        private const val PASSENGER_CALL_SETTLE_MS = 650L
        private const val CARD_TARGET_PREFIX = "rotacerta-card:"''', 'call-settle')

# Trip-page intermediary stops and view evidence.
once(
'''              const passengerRosterComplete = explicitEmptyRoster || (passengers.length > 0 && rosterContainers.length > 0 && !hasMore);
              $SANITIZED_HTML_JS''',
'''              const passengerRosterComplete = explicitEmptyRoster || (passengers.length > 0 && rosterContainers.length > 0 && !hasMore);
              const itineraryStops = [];
              [
                '[data-testid*="itinerary-departure-station"]',
                '[data-testid*="itinerary-arrival-station"]',
                '[data-testid*="itinerary-stop"]',
                '[data-testid*="station"]'
              ].forEach((selector) => {
                Array.from(document.querySelectorAll(selector)).forEach((node) => {
                  const value = clean(node.innerText);
                  if (value && !itineraryStops.includes(value)) itineraryStops.push(value);
                });
              });
              const pageText = clean(document.body && document.body.innerText);
              const viewsMatch = pageText.match(/(\\d{1,9})\\s+visualiza(?:ç|c)[õo]es/i);
              const views = viewsMatch ? parseInt(viewsMatch[1], 10) : null;
              $SANITIZED_HTML_JS''', 'trip-extra-evidence')

once(
'''                passengerHrefs: Array.from(new Set(passengerTargets)),
                explicitEmptyRoster: explicitEmptyRoster,
                domHtml: html.slice(0, 350000)''',
'''                passengerHrefs: Array.from(new Set(passengerTargets)),
                explicitEmptyRoster: explicitEmptyRoster,
                itineraryStops: itineraryStops,
                views: Number.isFinite(views) ? views : null,
                domHtml: html.slice(0, 350000)''', 'trip-extra-return')

for required in ['PASSENGER_TEL_INTERCEPTED', 'PASSENGER_CALL_ACTION_PRESENT', 'DIRECT_TRIP_EVIDENCE_PERSISTED', 'itineraryStops: itineraryStops']:
    if required not in s:
        raise SystemExit(f'missing {required}')

p.write_text(s)
