#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
COLLECTOR = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripBlaBlaCollector.kt"

if not COLLECTOR.is_file():
    raise SystemExit(f"missing materialized collector: {COLLECTOR}")

text = COLLECTOR.read_text(encoding="utf-8")
anchor = "    private fun parseDateTime(date: String, time: String?, zoneId: ZoneId): Long? = runCatching {\n"
if text.count(anchor) != 1:
    raise SystemExit(f"collector parseDateTime anchor count={text.count(anchor)}")

# The post-0.1.236 identity replacement intentionally changed only
# strongExternalIdentity. Its original section also contained these established
# helpers; restore them instead of recreating any architecture/module.
helpers = r'''    private fun mergeSourceSeats(
        local: Map<BookingSource, Int>,
        external: Map<BookingSource, Int>,
    ): Map<BookingSource, Int> = (local.keys + external.keys).associateWith { source ->
        maxOf(local[source] ?: 0, external[source] ?: 0)
    }.filterValues { it > 0 }

    private fun canonicalManageHref(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (value.startsWith('/')) return null
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val path = uri.path.orEmpty()
        return value.takeIf { path.contains("/trip") || path.contains("/rides/offer") }
    }

    private fun samePlace(left: String, right: String): Boolean {
        val a = normalizeWholePlace(left)
        val b = normalizeWholePlace(right)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return shorter.length >= 5 && longer.contains(shorter)
    }

    private fun normalizeWholePlace(value: String): String = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

'''

for helper in ("mergeSourceSeats", "canonicalManageHref", "samePlace", "normalizeWholePlace"):
    if f"private fun {helper}" in text:
        raise SystemExit(f"collector helper already present before compile fix: {helper}")

text = text.replace(anchor, helpers + anchor, 1)
COLLECTOR.write_text(text, encoding="utf-8")

final = COLLECTOR.read_text(encoding="utf-8")
for helper in ("mergeSourceSeats", "canonicalManageHref", "samePlace", "normalizeWholePlace"):
    if final.count(f"private fun {helper}") != 1:
        raise SystemExit(f"collector helper restoration failed: {helper}")
if "https://www.blablacar.com.br" in helpers or "Locale(\"pt\", \"BR\")" in helpers or "Brasil" in helpers:
    raise SystemExit("country-specific helper logic reintroduced")

print(
    "stage47_r4_step7_post_0236_compile_fix=PASS "
    "merge_source_seats_restored=true canonical_manage_href_restored_country_neutral=true "
    "same_place_restored=true normalize_place_restored=true farol_touched=false base_touched=false"
)
