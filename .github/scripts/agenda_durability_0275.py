from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one old block, found {count}")
    p.write_text(text.replace(old, new, 1))


dynamic = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt"
replace_once(
    dynamic,
    r'''              const dateEvidence = (root) => {
                const structured = Array.from(root.querySelectorAll('time[datetime]'))
                  .map((node) => clean(node.getAttribute('datetime')))
                  .filter(Boolean);
                const visible = Array.from(root.querySelectorAll('[data-testid*="date"], time, h1, h2, h3'))
                  .map((node) => clean(node.innerText))
                  .filter(Boolean);
                return clean(structured.concat(visible).join(' | ')).slice(0, 1200);
              };''',
    r'''              const looksLikeCalendarDate = (value) => {
                const text = clean(value);
                if (!text) return false;
                return /\b20\d{2}-\d{1,2}-\d{1,2}\b/.test(text) ||
                  /\b\d{1,2}[\/.-]\d{1,2}(?:[\/.-]\d{2,4})?\b/.test(text) ||
                  /\b(?:hoje|amanh[ãa])\b/i.test(text) ||
                  /\b\d{1,2}\s*(?:de\s+)?(?:jan(?:eiro)?|fev(?:ereiro)?|mar(?:ço|co)?|abr(?:il)?|mai(?:o)?|jun(?:ho)?|jul(?:ho)?|ago(?:sto)?|set(?:embro)?|out(?:ubro)?|nov(?:embro)?|dez(?:embro)?)\b/i.test(text);
              };
              const nearestPrecedingDateEvidence = (root) => {
                const markers = Array.from(document.querySelectorAll('[data-testid*="date"], time[datetime], h1, h2, h3'));
                for (let index = markers.length - 1; index >= 0; index--) {
                  const node = markers[index];
                  if (!node || node === root || (root.contains && root.contains(node))) continue;
                  if (!(node.compareDocumentPosition(root) & Node.DOCUMENT_POSITION_FOLLOWING)) continue;
                  const structured = clean(node.getAttribute && node.getAttribute('datetime'));
                  const visible = clean(node.innerText || node.textContent);
                  if (looksLikeCalendarDate(structured)) return structured;
                  if (looksLikeCalendarDate(visible)) return visible;
                }
                return '';
              };
              const dateEvidence = (root) => {
                const structured = Array.from(root.querySelectorAll('time[datetime]'))
                  .map((node) => clean(node.getAttribute('datetime')))
                  .filter(Boolean);
                const visible = Array.from(root.querySelectorAll('[data-testid*="date"], time, h1, h2, h3'))
                  .map((node) => clean(node.innerText))
                  .filter(Boolean);
                const localEvidence = structured.concat(visible);
                if (localEvidence.some(looksLikeCalendarDate)) {
                  return clean(localEvidence.join(' | ')).slice(0, 1200);
                }
                const preceding = nearestPrecedingDateEvidence(root);
                return clean(localEvidence.concat(preceding ? [preceding] : []).join(' | ')).slice(0, 1200);
              };''',
)

direction = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineBaseDirection.kt"
replace_once(
    direction,
    '''internal fun timelineDirectionDisplayLabel(state: TimelineDirectionState): String? = when (state) {
    TimelineDirectionState.OUTBOUND -> "↑ INDO"
    TimelineDirectionState.INBOUND -> "↓ VOLTANDO"
    TimelineDirectionState.NEUTRAL -> "↔ NEUTRA"
    TimelineDirectionState.UNKNOWN -> null
}''',
    '''internal fun timelineDirectionDisplayLabel(state: TimelineDirectionState): String? = when (state) {
    TimelineDirectionState.OUTBOUND -> "↑ IDA"
    TimelineDirectionState.INBOUND -> "↓ VOLTA"
    TimelineDirectionState.NEUTRAL -> "↔ NEUTRA"
    TimelineDirectionState.UNKNOWN -> null
}''',
)

ui = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
replace_once(
    ui,
    "import androidx.compose.foundation.isSystemInDarkTheme\n",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.isSystemInDarkTheme\n",
)
replace_once(
    ui,
    "import androidx.compose.foundation.layout.padding\n",
    "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.shape.RoundedCornerShape\n",
)
replace_once(
    ui,
    '''            ResponsiveTripAction("Limpar Timeline") {
                val clearedResponse = collectorStore.saveResponse(''',
    '''            ResponsiveTripAction("Limpar Timeline") {
                archiveStore.clearExternal(physical)
                val clearedResponse = collectorStore.saveResponse(''',
)
replace_once(
    ui,
    '''                    "externalTripsRemoved=true localTripsPreserved=${trips.size} localBookingsPreserved=${bookings.size}",''',
    '''                    "externalTripsRemoved=true externalArchiveStateReset=true localTripsPreserved=${trips.size} localBookingsPreserved=${bookings.size}",''',
)
replace_once(
    ui,
    '''                onChanged("Timeline sincronizada limpa. Viagens locais, reservas e login foram preservados.")''',
    '''                onChanged("Timeline sincronizada limpa. Arquivamento externo zerado; viagens locais, reservas e login foram preservados.")''',
)
replace_once(
    ui,
    '''            timelineDirectionDisplayLabel(direction)?.let { Text(it, style = MaterialTheme.typography.labelLarge) }''',
    '''            timelineDirectionDisplayLabel(direction)?.let { label ->
                val chipColor = when (direction) {
                    TimelineDirectionState.OUTBOUND -> if (dark) Color(0xFF285A34) else Color(0xFFB8E6C4)
                    TimelineDirectionState.INBOUND -> if (dark) Color(0xFF6A3A23) else Color(0xFFFFD1B8)
                    TimelineDirectionState.NEUTRAL,
                    TimelineDirectionState.UNKNOWN,
                    -> MaterialTheme.colorScheme.surfaceVariant
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(chipColor, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }''',
)
replace_once(
    ui,
    '''    fun setArchived(entry: TripTimelineEntry, archived: Boolean) {
        val edit = prefs.edit()
        aliases(entry).forEach { edit.putBoolean(it, archived) }
        edit.apply()
    }

    private fun aliases(entry: TripTimelineEntry): Set<String> = setOfNotNull(''',
    '''    fun setArchived(entry: TripTimelineEntry, archived: Boolean) {
        val edit = prefs.edit()
        aliases(entry).forEach { edit.putBoolean(it, archived) }
        edit.apply()
    }

    fun clearExternal(entries: List<TripTimelineEntry>) {
        val edit = prefs.edit()
        entries
            .filter(::hasExternalPublication)
            .flatMap(::aliases)
            .distinct()
            .forEach(edit::remove)
        edit.apply()
    }

    private fun aliases(entry: TripTimelineEntry): Set<String> = setOfNotNull(''',
)

replace_once(
    "app/build.gradle.kts",
    'versionCode = 5567\n        versionName = "0.1.274"',
    'versionCode = 5568\n        versionName = "0.1.275"',
)

test = Path("app/src/test/java/br/com/mapeiaia/rotacerta/trips/AgendaDurability0275Test.kt")
if not test.exists():
    test.write_text('''package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaDurability0275Test {
    private val today = LocalDate.of(2026, 8, 24)

    @Test
    fun groupedDateHeaderResolvesTomorrowEvenWhenCardStartsWithTimeOnlyEvidence() {
        assertEquals(
            LocalDate.of(2026, 8, 25),
            BlaBlaDomNormalizer.parseDate("10:30 | 14:30 | Ter., 25 de agosto", today),
        )
    }

    @Test
    fun rideListCollectorUsesNearestPrecedingCalendarHeaderAsFallback() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(source.contains("looksLikeCalendarDate"))
        assertTrue(source.contains("nearestPrecedingDateEvidence"))
        assertTrue(source.contains("node.compareDocumentPosition(root) & Node.DOCUMENT_POSITION_FOLLOWING"))
    }

    @Test
    fun clearingSyncedTimelineAlsoResetsOnlyExternalArchiveAliases() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(source.contains("archiveStore.clearExternal(physical)"))
        assertTrue(source.contains("fun clearExternal(entries: List<TripTimelineEntry>)"))
        assertTrue(source.contains(".filter(::hasExternalPublication)"))
        assertTrue(source.contains("externalArchiveStateReset=true"))
    }

    @Test
    fun directionLabelsAreExplicitAndFailClosedWhenUnknown() {
        assertEquals("↑ IDA", timelineDirectionDisplayLabel(TimelineDirectionState.OUTBOUND))
        assertEquals("↓ VOLTA", timelineDirectionDisplayLabel(TimelineDirectionState.INBOUND))
        assertEquals("↔ NEUTRA", timelineDirectionDisplayLabel(TimelineDirectionState.NEUTRAL))
        assertEquals(null, timelineDirectionDisplayLabel(TimelineDirectionState.UNKNOWN))
    }

    @Test
    fun publicSearchRemainsAttachedToAgendaToolbar() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/ResponsiveTripActions.kt").readText()
        assertTrue(source.contains("Consulta pública"))
        assertTrue(source.contains("BlaBlaPublicSearchStore"))
        assertTrue(source.contains("BlaBlaPublicSearchPanel"))
    }
}
''')
