from pathlib import Path

ui = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt')
gradle = Path('app/build.gradle.kts')
test = Path('app/src/test/java/br/com/mapeiaia/rotacerta/trips/TripTimelineProfileColors0288Test.kt')

text = ui.read_text()

def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'expected exactly one UI match, got {count}: {old[:80]!r}')
    text = text.replace(old, new, 1)

replace_once(
    '    val visibleEntries = remember(entries, trips, bookings, searchQuery) {\n'
    '        filterTimelineEntries(entries, trips, bookings, searchQuery)\n'
    '    }\n'
    '    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }\n',
    '    val visibleEntries = remember(entries, trips, bookings, searchQuery) {\n'
    '        filterTimelineEntries(entries, trips, bookings, searchQuery)\n'
    '    }\n'
    '    val registeredProfileUuids = BlaBlaDynamicAccountRegistry(context).list().mapNotNull { it.profileUuid }\n'
    '    val profileColorSlots = remember(entries, registeredProfileUuids) {\n'
    '        timelineProfileColorSlots(\n'
    '            registeredProfileUuids = registeredProfileUuids,\n'
    '            observedProfileIdentities = entries.map(::timelineProfileIdentity),\n'
    '        )\n'
    '    }\n'
    '    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }\n',
)

replace_once(
    '            formatter = formatter,\n'
    '            archived = archived,\n',
    '            formatter = formatter,\n'
    '            profileColorSlot = profileColorSlots[timelineProfileIdentity(entry)] ?: 0,\n'
    '            archived = archived,\n',
)

replace_once(
    'internal fun timelineOccupancyReadState(entry: TripTimelineEntry): TimelineOccupancyReadState = when {\n'
    '    entry.capacity > 0 && hasExternalPublication(entry) && entry.blablaPassengerRosterComplete != true && entry.maximumOccupiedSeats <= 0 ->\n'
    '        TimelineOccupancyReadState.CAPACITY_CONFIGURED_ROSTER_PENDING\n'
    '    entry.capacity > 0 -> TimelineOccupancyReadState.CAPACITY_CONFIGURED\n'
    '    entry.maximumOccupiedSeats > 0 -> TimelineOccupancyReadState.RESERVED\n'
    '    entry.blablaPassengerRosterComplete == true -> TimelineOccupancyReadState.COMPLETE_EMPTY\n'
    '    else -> TimelineOccupancyReadState.PENDING\n'
    '}\n\n'
    '@Composable\n'
    'private fun TimelineEntryCard(\n',
    'internal fun timelineOccupancyReadState(entry: TripTimelineEntry): TimelineOccupancyReadState = when {\n'
    '    entry.capacity > 0 && hasExternalPublication(entry) && entry.blablaPassengerRosterComplete != true && entry.maximumOccupiedSeats <= 0 ->\n'
    '        TimelineOccupancyReadState.CAPACITY_CONFIGURED_ROSTER_PENDING\n'
    '    entry.capacity > 0 -> TimelineOccupancyReadState.CAPACITY_CONFIGURED\n'
    '    entry.maximumOccupiedSeats > 0 -> TimelineOccupancyReadState.RESERVED\n'
    '    entry.blablaPassengerRosterComplete == true -> TimelineOccupancyReadState.COMPLETE_EMPTY\n'
    '    else -> TimelineOccupancyReadState.PENDING\n'
    '}\n\n'
    'internal fun timelineProfileIdentity(entry: TripTimelineEntry): String =\n'
    '    entry.blablaProfileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)\n'
    '        ?: entry.profileId.trim().lowercase().takeIf(String::isNotEmpty)\n'
    '        ?: entry.profileLabel.trim().lowercase()\n\n'
    'internal fun timelineProfileColorSlots(\n'
    '    registeredProfileUuids: List<String>,\n'
    '    observedProfileIdentities: List<String>,\n'
    '): Map<String, Int> {\n'
    '    val ordered = linkedSetOf<String>()\n'
    '    registeredProfileUuids\n'
    '        .map { it.trim().lowercase() }\n'
    '        .filter(String::isNotEmpty)\n'
    '        .forEach { ordered += it }\n'
    '    observedProfileIdentities\n'
    '        .map { it.trim().lowercase() }\n'
    '        .filter(String::isNotEmpty)\n'
    '        .forEach { ordered += it }\n'
    '    return ordered.withIndex().associate { indexed -> indexed.value to indexed.index }\n'
    '}\n\n'
    'private data class TimelineProfileCardColors(\n'
    '    val background: Color,\n'
    '    val border: Color,\n'
    ')\n\n'
    'private fun timelineProfileCardColors(slot: Int, dark: Boolean): TimelineProfileCardColors = when (slot % 12) {\n'
    '    0 -> if (dark) TimelineProfileCardColors(Color(0xFF172A46), Color(0xFF6EA0E8)) else TimelineProfileCardColors(Color(0xFFE7F0FF), Color(0xFF4F7FC7))\n'
    '    1 -> if (dark) TimelineProfileCardColors(Color(0xFF183221), Color(0xFF6CAE7C)) else TimelineProfileCardColors(Color(0xFFE3F4E8), Color(0xFF4F8A62))\n'
    '    2 -> if (dark) TimelineProfileCardColors(Color(0xFF2D2140), Color(0xFFA886DD)) else TimelineProfileCardColors(Color(0xFFF0E8FF), Color(0xFF7A5DB4))\n'
    '    3 -> if (dark) TimelineProfileCardColors(Color(0xFF3B2A14), Color(0xFFD5A052)) else TimelineProfileCardColors(Color(0xFFFFF0D9), Color(0xFFB47728))\n'
    '    4 -> if (dark) TimelineProfileCardColors(Color(0xFF3A1F2A), Color(0xFFD47E9D)) else TimelineProfileCardColors(Color(0xFFFCE7EE), Color(0xFFAD5C7A))\n'
    '    5 -> if (dark) TimelineProfileCardColors(Color(0xFF163432), Color(0xFF65AAA4)) else TimelineProfileCardColors(Color(0xFFDFF4F2), Color(0xFF4B8B86))\n'
    '    6 -> if (dark) TimelineProfileCardColors(Color(0xFF222640), Color(0xFF858ED6)) else TimelineProfileCardColors(Color(0xFFE8E9FF), Color(0xFF626BB5))\n'
    '    7 -> if (dark) TimelineProfileCardColors(Color(0xFF2B3019), Color(0xFFA3B665)) else TimelineProfileCardColors(Color(0xFFEFF3DA), Color(0xFF7B8A44))\n'
    '    8 -> if (dark) TimelineProfileCardColors(Color(0xFF15313A), Color(0xFF63A9BE)) else TimelineProfileCardColors(Color(0xFFE0F3F8), Color(0xFF4E8798))\n'
    '    9 -> if (dark) TimelineProfileCardColors(Color(0xFF39251D), Color(0xFFC88E72)) else TimelineProfileCardColors(Color(0xFFF8EAE3), Color(0xFFA96D50))\n'
    '    10 -> if (dark) TimelineProfileCardColors(Color(0xFF30223A), Color(0xFFB184C9)) else TimelineProfileCardColors(Color(0xFFF3E8F8), Color(0xFF8D5EA5))\n'
    '    else -> if (dark) TimelineProfileCardColors(Color(0xFF29302F), Color(0xFF8FA7A3)) else TimelineProfileCardColors(Color(0xFFE9F0EF), Color(0xFF6E8984))\n'
    '}\n\n'
    '@Composable\n'
    'private fun TimelineEntryCard(\n',
)

replace_once(
    '    formatter: DateTimeFormatter,\n'
    '    archived: Boolean,\n',
    '    formatter: DateTimeFormatter,\n'
    '    profileColorSlot: Int,\n'
    '    archived: Boolean,\n',
)

replace_once(
    '    val dark = isSystemInDarkTheme()\n'
    '    val cardColor = when (direction) {\n'
    '        TimelineDirectionState.OUTBOUND -> if (dark) Color(0xFF17351F) else Color(0xFFDDF3E3)\n'
    '        TimelineDirectionState.INBOUND -> if (dark) Color(0xFF3B291F) else Color(0xFFFFE4D6)\n'
    '        TimelineDirectionState.NEUTRAL,\n'
    '        TimelineDirectionState.UNKNOWN,\n'
    '        -> MaterialTheme.colorScheme.surface\n'
    '    }\n\n'
    '    Card(\n',
    '    val dark = isSystemInDarkTheme()\n'
    '    val profileColors = timelineProfileCardColors(profileColorSlot, dark)\n\n'
    '    Card(\n',
)

replace_once(
    '        colors = CardDefaults.cardColors(containerColor = cardColor),\n'
    '        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),\n',
    '        colors = CardDefaults.cardColors(containerColor = profileColors.background),\n'
    '        border = BorderStroke(1.dp, profileColors.border),\n',
)

ui.write_text(text)

gradle_text = gradle.read_text()
old_version = '        versionCode = 5580\n        versionName = "0.1.287"'
new_version = '        versionCode = 5581\n        versionName = "0.1.288"'
if gradle_text.count(old_version) != 1:
    raise SystemExit('version baseline mismatch')
gradle.write_text(gradle_text.replace(old_version, new_version, 1))

test.write_text("""package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class TripTimelineProfileColors0288Test {
    @Test
    fun registeredProfilesKeepSequentialStableSlots() {
        val slots = timelineProfileColorSlots(
            registeredProfileUuids = listOf(" EZEQUIEL-UUID ", "BARBOSA-UUID"),
            observedProfileIdentities = listOf(
                "barbosa-uuid",
                "ezequiel-uuid",
                "terceiro-perfil",
                "ezequiel-uuid",
                "quarto-perfil",
            ),
        )

        assertEquals(0, slots["ezequiel-uuid"])
        assertEquals(1, slots["barbosa-uuid"])
        assertEquals(2, slots["terceiro-perfil"])
        assertEquals(3, slots["quarto-perfil"])
        assertEquals(4, slots.size)
    }

    @Test
    fun duplicateAndBlankIdentitiesDoNotConsumeExtraColors() {
        val slots = timelineProfileColorSlots(
            registeredProfileUuids = listOf("perfil-a", "", "PERFIL-A"),
            observedProfileIdentities = listOf("perfil-a", " perfil-b ", "", "PERFIL-B"),
        )

        assertEquals(mapOf("perfil-a" to 0, "perfil-b" to 1), slots)
    }
}
""")
