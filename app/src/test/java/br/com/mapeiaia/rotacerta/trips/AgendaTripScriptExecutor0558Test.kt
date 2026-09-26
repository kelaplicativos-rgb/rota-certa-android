package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgendaTripScriptExecutor0558Test {
    private val profile = "11111111-2222-3333-4444-555555555555"

    private fun command(
        date: String = "2026-09-20",
        time: String = "11:30",
        seats: Int = 4,
        extra: String = "",
    ) = """{
      "schemaVersion":"1.0",
      "action":"CREATE_TRIPS",
      "profileUuid":"$profile",
      "date":"$date",
      "departureTime":"$time",
      "origin":"Cidade A",
      "destination":"Cidade B",
      "seats":$seats
      $extra
    }"""

    @Test fun valid_script_parses() {
        val parsed = AgendaTripScriptParser0558.parse(command())
        assertEquals(1, parsed.instructions.size)
        assertEquals(profile, parsed.instructions.single().profileUuid)
    }

    @Test fun invalid_json_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse("{") }
    }

    @Test fun unsupported_schema_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command().replace("1.0", "2.0")) }
    }

    @Test fun missing_profile_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command().replace("\"profileUuid\":\"$profile\",", "")) }
    }

    @Test fun one_trip_stays_one_instruction() {
        assertEquals(1, AgendaTripScriptParser0558.parse(command()).instructions.size)
    }

    @Test fun several_dates_expand_deterministically() {
        val raw = command().replace("\"date\":\"2026-09-20\"", "\"dates\":[\"2026-09-22\",\"2026-09-20\"]")
        val parsed = AgendaTripScriptParser0558.parse(raw)
        assertEquals(listOf("2026-09-20", "2026-09-22"), parsed.instructions.map { it.date })
    }

    @Test fun empty_script_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse("   ") }
    }

    @Test fun invalid_date_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(date = "2026-02-30")) }
    }

    @Test fun invalid_time_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(time = "25:00")) }
    }

    @Test fun invalid_capacity_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(seats = 0)) }
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(seats = 5)) }
    }

    @Test fun unknown_field_is_fail_closed() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(extra = ",\"shell\":\"rm -rf /\"")) }
    }

    @Test fun arbitrary_javascript_field_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(extra = ",\"executeJavascript\":\"alert(1)\"")) }
    }

    @Test fun same_script_has_same_hash() {
        assertEquals(AgendaTripScriptParser0558.parse(command()).scriptHash, AgendaTripScriptParser0558.parse(command()).scriptHash)
    }

    @Test fun json_key_order_does_not_change_script_hash() {
        val first = AgendaTripScriptParser0558.parse(command())
        val secondRaw = """{"seats":4,"destination":"Cidade B","origin":"Cidade A","departureTime":"11:30","date":"2026-09-20","profileUuid":"$profile","action":"CREATE_TRIPS","schemaVersion":"1.0"}"""
        val second = AgendaTripScriptParser0558.parse(secondRaw)
        assertEquals(first.scriptHash, second.scriptHash)
    }

    @Test fun fingerprint_is_stable_for_same_instruction() {
        val item = AgendaTripScriptParser0558.parse(command()).instructions.single()
        assertEquals(AgendaTripScriptParser0558.fingerprint("tenant", item), AgendaTripScriptParser0558.fingerprint("tenant", item))
    }

    @Test fun fingerprint_changes_across_tenant() {
        val item = AgendaTripScriptParser0558.parse(command()).instructions.single()
        assertNotEquals(AgendaTripScriptParser0558.fingerprint("tenant-a", item), AgendaTripScriptParser0558.fingerprint("tenant-b", item))
    }

    @Test fun fingerprint_changes_across_profile() {
        val a = AgendaTripScriptParser0558.parse(command()).instructions.single()
        val b = a.copy(profileUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        assertNotEquals(AgendaTripScriptParser0558.fingerprint("tenant", a), AgendaTripScriptParser0558.fingerprint("tenant", b))
    }

    @Test fun round_trip_expands_to_two_instructions() {
        val raw = command(extra = ",\"roundTrip\":true,\"returnDepartureTime\":\"19:00\"")
        val parsed = AgendaTripScriptParser0558.parse(raw)
        assertEquals(2, parsed.instructions.size)
        assertEquals(AgendaTripScriptDirection0558.VOLTA, parsed.instructions.last().direction)
        assertEquals("Cidade B", parsed.instructions.last().origin)
    }

    @Test fun round_trip_without_return_time_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(extra = ",\"roundTrip\":true")) }
    }

    @Test fun non_inverse_explicit_return_is_rejected() {
        val raw = command(extra = ",\"return\":{\"origin\":\"Cidade C\",\"destination\":\"Cidade A\",\"time\":\"19:00\"}")
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(raw) }
    }

    @Test fun markdown_json_fence_is_supported() {
        assertEquals(1, AgendaTripScriptParser0558.parse("```json\n${command()}\n```").instructions.size)
    }

    @Test fun batch_envelope_preserves_script_id() {
        val raw = """{"schemaVersion":"1.0","scriptId":"agenda-42","createdAt":"2026-09-14T12:00:00Z","commands":[${command()}]}"""
        val parsed = AgendaTripScriptParser0558.parse(raw)
        assertEquals("agenda-42", parsed.scriptId)
        assertEquals(1, parsed.instructions.size)
    }

    @Test fun batch_envelope_supports_multiple_commands() {
        val second = command(date = "2026-09-21", time = "19:00")
        val raw = """{"schemaVersion":"1.0","commands":[${command()},$second]}"""
        assertEquals(2, AgendaTripScriptParser0558.parse(raw).instructions.size)
    }

    @Test fun empty_batch_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse("{\"schemaVersion\":\"1.0\",\"commands\":[]}") }
    }

    @Test fun more_than_62_dates_is_rejected() {
        val dates = (1..63).joinToString(",") { "\"2026-10-${((it - 1) % 31 + 1).toString().padStart(2, '0')}\"" }
        val raw = command().replace("\"date\":\"2026-09-20\"", "\"dates\":[$dates]")
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(raw) }
    }

    @Test fun duplicate_dates_are_collapsed() {
        val raw = command().replace("\"date\":\"2026-09-20\"", "\"dates\":[\"2026-09-20\",\"2026-09-20\"]")
        assertEquals(1, AgendaTripScriptParser0558.parse(raw).instructions.size)
    }

    @Test fun same_origin_destination_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command().replace("Cidade B", "Cidade A")) }
    }

    @Test fun unsupported_mode_is_rejected() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(extra = ",\"mode\":\"MAGIC\"")) }
    }

    @Test fun fail_closed_validation_cannot_be_disabled() {
        assertFailsWith<AgendaTripScriptContractException0558> { AgendaTripScriptParser0558.parse(command(extra = ",\"validation\":{\"failClosed\":false}")) }
    }

    @Test fun publish_false_is_data_not_code_execution() {
        val parsed = AgendaTripScriptParser0558.parse(command(extra = ",\"publish\":false"))
        assertFalse(parsed.instructions.single().publish)
    }

    @Test fun schema_numeric_one_is_v1_compatible() {
        val raw = command().replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":1")
        assertEquals(AGENDA_TRIP_SCRIPT_SCHEMA_0558, AgendaTripScriptParser0558.parse(raw).schemaVersion)
    }

    @Test fun instruction_index_is_stable_after_expansion() {
        val raw = command().replace("\"date\":\"2026-09-20\"", "\"dates\":[\"2026-09-20\",\"2026-09-22\"]")
        assertEquals(listOf(1, 2), AgendaTripScriptParser0558.parse(raw).instructions.map { it.index })
    }

    @Test fun whitespace_in_place_does_not_change_fingerprint() {
        val a = AgendaTripScriptParser0558.parse(command()).instructions.single()
        val b = a.copy(origin = "  Cidade   A  ")
        assertEquals(AgendaTripScriptParser0558.fingerprint("tenant", a), AgendaTripScriptParser0558.fingerprint("tenant", b))
    }

    @Test fun changed_time_changes_fingerprint() {
        val a = AgendaTripScriptParser0558.parse(command()).instructions.single()
        assertNotEquals(AgendaTripScriptParser0558.fingerprint("tenant", a), AgendaTripScriptParser0558.fingerprint("tenant", a.copy(departureTime = "11:31")))
    }

    @Test fun state_model_contains_ambiguous_reconciliation_barrier() {
        assertTrue(AgendaTripScriptState0558.values().contains(AgendaTripScriptState0558.PUBLISHED_AMBIGUOUS))
        assertTrue(AgendaTripScriptState0558.values().contains(AgendaTripScriptState0558.RECONCILING))
        assertTrue(AgendaTripScriptState0558.values().contains(AgendaTripScriptState0558.SYNCED))
    }
}
