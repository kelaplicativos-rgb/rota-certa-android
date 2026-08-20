#!/usr/bin/env python3
from pathlib import Path
import sys

source = Path(sys.argv[1]).resolve()
build = source / "app/build.gradle.kts"
dynamic = source / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt"


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


text = build.read_text(encoding="utf-8")
old_code = "versionCode = 5524"
old_name = 'versionName = "0.1.231"'
new_code = "versionCode = 5528"
new_name = 'versionName = "0.1.235"'

if text.count(old_code) != 1 or text.count(old_name) != 1:
    raise SystemExit("Step7 version source is not the validated Step6 0.1.231/5524 state")

build.write_text(text.replace(old_code, new_code, 1).replace(old_name, new_name, 1), encoding="utf-8")

if not dynamic.is_file():
    raise SystemExit(f"missing materialized Stage47 dynamic account source: {dynamic}")

# Physical-test UX: keep the real authenticated BlaBlaCar WebView visible while
# synchronization is running. The small Stage47 status line stays above it.
once(
    dynamic,
'''        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) {
            val browserHost = android.widget.FrameLayout(this)
            browserHost.addView(
                webView,
                android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            browserHost.addView(
                TextView(this).apply {
                    text = "Sincronizando ${account.displayLabel}\\nO navegador está processando as viagens em segundo plano."
                    gravity = android.view.Gravity.CENTER
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.rgb(18, 18, 18))
                    setPadding(40, 40, 40, 40)
                },
                android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            root.addView(browserHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
''',
'''        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
''',
    "visible sync WebView",
)

# The authenticated profile page may not repeat the driver's UUID inside a
# profile anchor. For an account that already has a canonical UUID, inspect the
# rendered authenticated DOM plus same-page resource/navigation URLs and only
# confirm when that exact expected UUID is present.
once(
    dynamic,
'''private data class DynamicIdentityEvidence(
    val profileLinks: List<String> = emptyList(),
    val visibleName: String = "",
    val domHtml: String = "",
)
''',
'''private data class DynamicIdentityEvidence(
    val profileLinks: List<String> = emptyList(),
    val observedUuids: List<String> = emptyList(),
    val visibleName: String = "",
    val domHtml: String = "",
)
''',
    "identity evidence observed UUIDs",
)

once(
    dynamic,
'''            evidence?.let {
                store.saveDiagnosticHtml(account, "profile", it.domHtml)
                bindIdentityFromLinks(it.profileLinks, it.visibleName)?.let { updated -> account = updated }
            }
            if (identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()) {
''',
'''            evidence?.let {
                store.saveDiagnosticHtml(account, "profile", it.domHtml)
                val expectedUuid = account.profileUuid?.lowercase()
                val observedUuids = it.observedUuids.map(String::lowercase).toSet()
                val expectedFoundInAuthenticatedPage = expectedUuid != null && expectedUuid in observedUuids
                if (expectedFoundInAuthenticatedPage) {
                    identityConfirmedThisSync = true
                } else {
                    bindIdentityFromLinks(it.profileLinks, it.visibleName)?.let { updated -> account = updated }
                }
                UnifiedDebugEventStore.record(
                    "IDENTITY_EVIDENCE",
                    packageName,
                    "account=${account.displayLabel} expectedUuid=${expectedUuid.orEmpty()} expectedFound=$expectedFoundInAuthenticatedPage observedCount=${observedUuids.size} profileLinkCount=${it.profileLinks.size} url=${sanitizedUrl(webView.url.orEmpty())}",
                )
            }
            if (identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()) {
''',
    "authenticated profile expected UUID evidence",
)

once(
    dynamic,
'''              const nameNode = document.querySelector('[data-testid*="profile-name"], [data-testid*="driver-name"], h1');
              $SANITIZED_HTML_JS
              return JSON.stringify({
                profileLinks: Array.from(new Set(links)),
                visibleName: clean(nameNode && nameNode.innerText),
                domHtml: html.slice(0, 350000)
              });
''',
'''              const nameNode = document.querySelector('[data-testid*="profile-name"], [data-testid*="driver-name"], h1');
              const resourceUrls = (performance && performance.getEntriesByType)
                ? performance.getEntriesByType('resource').map((entry) => entry.name || '')
                : [];
              const navigationUrls = (performance && performance.getEntriesByType)
                ? performance.getEntriesByType('navigation').map((entry) => entry.name || '')
                : [];
              const rawIdentityEvidence = [
                location.href || '',
                document.documentElement ? (document.documentElement.outerHTML || '') : '',
                ...resourceUrls,
                ...navigationUrls
              ].join('\\n');
              const observedUuids = Array.from(new Set(
                (rawIdentityEvidence.match(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/ig) || [])
                  .map((value) => value.toLowerCase())
              ));
              $SANITIZED_HTML_JS
              return JSON.stringify({
                profileLinks: Array.from(new Set(links)),
                observedUuids: observedUuids,
                visibleName: clean(nameNode && nameNode.innerText),
                domHtml: html.slice(0, 350000)
              });
''',
    "identity DOM and resource UUID evidence",
)

# Keep Step7 as the same stage. Every later patch is layered after the proven
# identity/session materialization; none touches the FAROL.
def run_sibling_patcher(filename: str) -> None:
    patcher = Path(__file__).with_name(filename)
    if not patcher.is_file():
        raise SystemExit(f"missing Step7 materializer: {patcher}")
    previous_argv = sys.argv
    try:
        sys.argv = [str(patcher), str(source)]
        namespace = {"__name__": "__main__", "__file__": str(patcher)}
        exec(compile(patcher.read_text(encoding="utf-8"), str(patcher), "exec"), namespace)
    finally:
        sys.argv = previous_argv


run_sibling_patcher("apply_stage47_r4_step7_card_navigation.py")
run_sibling_patcher("apply_stage47_r4_step7_card_local_manage.py")
run_sibling_patcher("apply_stage47_r4_step7_timeline_future_archive.py")
run_sibling_patcher("apply_stage47_r4_step7_clean_occupancy_v2.py")
run_sibling_patcher("apply_stage47_r4_step7_passenger_card_identity.py")

print(
    "stage47_r4_step7_version=PASS version=0.1.235/5528 "
    "visible_sync_browser=true authenticated_expected_uuid_dom_evidence=true "
    "exact_trip_card_click=true manage_button_removed=true clean_cards=true "
    "visible_blablacar_passengers=true booked_seats=true private_passenger_simple=true "
    "passenger_name_phone_card=true passenger_whatsapp_click=true phone_primary_identity=true "
    "geo_corridor_merge=true overbooking_urgent=true future_chronological_timeline=true "
    "archive_local_only=true"
)
