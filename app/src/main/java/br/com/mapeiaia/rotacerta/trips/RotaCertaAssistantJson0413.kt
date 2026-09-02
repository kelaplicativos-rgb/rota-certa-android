package br.com.mapeiaia.rotacerta.trips

import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal const val ROTA_CERTA_ASSISTANT_INPUT_MAX_CHARS_0413 = 65_536

internal class RotaCertaAssistantJsonException0413(
    message: String,
) : IllegalArgumentException(message)

internal object RotaCertaAssistantJson0413 {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun looksLikeJson(raw: String): Boolean {
        val value = stripMarkdownFence(raw)
        return value.startsWith("{") || value.startsWith("[")
    }

    fun parse(
        raw: String,
        allowedActions: Set<RotaCertaAction0410>,
    ): RotaCertaStructuredCommand0410 {
        if (raw.length > ROTA_CERTA_ASSISTANT_INPUT_MAX_CHARS_0413) {
            throw RotaCertaAssistantJsonException0413(
                "O JSON excede o limite de 64 KB.",
            )
        }

        val payload = stripMarkdownFence(raw)
        val root = try {
            json.parseToJsonElement(payload) as? JsonObject
                ?: throw RotaCertaAssistantJsonException0413(
                    "O script precisa ter um objeto JSON na raiz.",
                )
        } catch (known: RotaCertaAssistantJsonException0413) {
            throw known
        } catch (_: Throwable) {
            throw RotaCertaAssistantJsonException0413(
                "JSON inválido. Revise a sintaxe antes de enviar.",
            )
        }

        val schemaVersion = root.string0413("schemaVersion").ifBlank { "1.0" }
        if (schemaVersion != "1.0") {
            throw RotaCertaAssistantJsonException0413(
                "schemaVersion não suportada: $schemaVersion.",
            )
        }

        val rawAction = root.string0413("action")
            .trim()
            .uppercase(Locale.ROOT)
        if (rawAction.isBlank()) {
            throw RotaCertaAssistantJsonException0413(
                "O JSON precisa informar action.",
            )
        }

        val action = when (rawAction) {
            "CREATE_ROUND_TRIP", "CREATE_ROUND_TRIPS", "CREATE_TRIP" ->
                RotaCertaAction0410.CREATE_TRIPS
            else -> RotaCertaAction0410.values()
                .firstOrNull { it.name == rawAction }
                ?: throw RotaCertaAssistantJsonException0413(
                    "Action desconhecida: $rawAction.",
                )
        }
        if (action !in allowedActions) {
            throw RotaCertaAssistantJsonException0413(
                "A action " + action.name +
                    " não está habilitada no Command Registry atual.",
            )
        }

        val mode = root.string0413("mode")
            .trim()
            .uppercase(Locale.ROOT)
        val executionMode = when (mode) {
            "", "EXECUTE", "EXECUTION", "LIVE" -> "EXECUTE"
            "SIMULATION", "SIMULATE" -> "SIMULATION"
            else -> throw RotaCertaAssistantJsonException0413(
                "mode não suportado: $mode.",
            )
        }

        val validation = root.object0413("validation")
        validation?.boolean0413("validateCalendarDate")
            ?.let { enabled ->
                if (!enabled) {
                    throw RotaCertaAssistantJsonException0413(
                        "validateCalendarDate não pode ser desativado.",
                    )
                }
            }
        validation?.boolean0413("failClosed")
            ?.let { enabled ->
                if (!enabled) {
                    throw RotaCertaAssistantJsonException0413(
                        "failClosed não pode ser desativado.",
                    )
                }
            }
        root.nullableString0413("onInvalidDate")
            ?.uppercase(Locale.ROOT)
            ?.let { behavior ->
                if (behavior != "REJECT_AND_DO_NOT_PUBLISH") {
                    throw RotaCertaAssistantJsonException0413(
                        "onInvalidDate precisa ser REJECT_AND_DO_NOT_PUBLISH.",
                    )
                }
            }

        val route = root.object0413("route")
        val outbound = root.object0413("outbound")
            ?: route?.object0413("outbound")
        val inbound = root.object0413("return")
            ?: root.object0413("inbound")
            ?: route?.object0413("return")
            ?: route?.object0413("inbound")

        val origin = root.string0413("origin").ifBlank {
            outbound?.string0413("origin").orEmpty()
        }
        val destination = root.string0413("destination").ifBlank {
            outbound?.string0413("destination").orEmpty()
        }

        val aliasRoundTrip = rawAction in setOf(
            "CREATE_ROUND_TRIP",
            "CREATE_ROUND_TRIPS",
        )
        val roundTrip = aliasRoundTrip ||
            root.boolean0413("roundTrip") == true ||
            inbound != null

        val inboundOrigin = inbound?.string0413("origin").orEmpty()
        val inboundDestination = inbound?.string0413("destination").orEmpty()
        if (roundTrip &&
            origin.isNotBlank() &&
            destination.isNotBlank() &&
            inbound != null
        ) {
            if ((inboundOrigin.isNotBlank() &&
                    !samePlace0413(inboundOrigin, destination)) ||
                (inboundDestination.isNotBlank() &&
                    !samePlace0413(inboundDestination, origin))
            ) {
                throw RotaCertaAssistantJsonException0413(
                    "A rota de volta não é o inverso da ida; o executor atual não pode descartá-la silenciosamente.",
                )
            }
        }

        val temporal = root.object0413("temporal")
        val date = root.nullableString0413("date")
            ?: temporal?.nullableString0413("explicitDate")
        val dateTokens = root.stringList0413("dateTokens")
            .ifEmpty { root.stringList0413("dates") }

        if (date != null &&
            dateTokens.isNotEmpty() &&
            date !in dateTokens
        ) {
            throw RotaCertaAssistantJsonException0413(
                "date e dates apontam para valores diferentes.",
            )
        }
        if (dateTokens.size > 62) {
            throw RotaCertaAssistantJsonException0413(
                "O JSON contém mais de 62 datas.",
            )
        }

        val publicTargetNames = root.stringList0413("publicTargetNames")
        if (publicTargetNames.size > 8) {
            throw RotaCertaAssistantJsonException0413(
                "O JSON contém mais de 8 nomes para busca pública.",
            )
        }

        val outboundTime = temporal?.nullableString0413("time")
            ?: root.nullableString0413("departureTime")
            ?: outbound?.nullableString0413("departureTime")
            ?: outbound?.nullableString0413("time")
        val returnTime = root.nullableString0413("returnDepartureTime")
            ?: inbound?.nullableString0413("departureTime")
            ?: inbound?.nullableString0413("time")

        val profileSelection = root.object0413("profileSelection")
        val profileStrategy = profileSelection
            ?.string0413("strategy")
            .orEmpty()
            .uppercase(Locale.ROOT)
        if (profileStrategy.isNotBlank() &&
            profileStrategy !in setOf("AUTO_RECONCILE", "CURRENT_SELECTION")
        ) {
            throw RotaCertaAssistantJsonException0413(
                "profileSelection.strategy não suportada: $profileStrategy.",
            )
        }

        val preExecution = root.object0413("preExecution")
        return RotaCertaStructuredCommand0410(
            schemaVersion = "1.0",
            commandId = UUID.randomUUID().toString(),
            action = action,
            tripReference = root.string0413("tripReference"),
            passengerReference = root.string0413("passengerReference"),
            bookingReference = root.string0413("bookingReference"),
            temporal = RotaCertaTemporalReference0410(
                raw = temporal?.string0413("raw").orEmpty(),
                explicitDate = date.takeIf { dateTokens.isEmpty() },
                relative = temporal?.nullableString0413("relative"),
                weekday = temporal?.nullableString0413("weekday"),
                dayOfMonth = temporal?.int0413("dayOfMonth"),
                month = temporal?.int0413("month"),
                year = temporal?.int0413("year"),
                time = outboundTime,
            ),
            dateTokens = if (dateTokens.isNotEmpty()) dateTokens else emptyList(),
            roundTrip = roundTrip,
            origin = origin,
            destination = destination,
            returnDepartureTime = returnTime.orEmpty(),
            publicTargetNames = publicTargetNames,
            seats = root.int0413("seats"),
            priceText = root.string0413("priceText"),
            freeTextValue = root.string0413("freeTextValue"),
            requestedPolicy = "",
            executionMode = executionMode,
            profileSelectionStrategy = profileStrategy,
            checkAllDriverProfiles = profileSelection
                ?.boolean0413("checkAllDriverProfiles") == true,
            requirePublicCollectorPreExecution = preExecution
                ?.boolean0413("runPublicCollector") == true,
            requirePhysicalContinuityCheck = preExecution
                ?.boolean0413("checkPhysicalContinuity") == true,
            requireScheduleConflictCheck = preExecution
                ?.boolean0413("checkScheduleConflicts") == true,
            interpretationConfidence = 1.0,
            interpretationNotes = "local_json_0413",
            multipleActions = root.boolean0413("multipleActions") ?: false,
        )
    }

    private fun stripMarkdownFence(raw: String): String {
        val trimmed = raw
            .trim()
            .removePrefix("\uFEFF")
            .trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstLineEnd = trimmed.indexOf('\n')
        if (firstLineEnd < 0) {
            throw RotaCertaAssistantJsonException0413(
                "Bloco JSON incompleto.",
            )
        }
        val body = trimmed.substring(firstLineEnd + 1)
        val closing = body.lastIndexOf("```")
        if (closing < 0) {
            throw RotaCertaAssistantJsonException0413(
                "Bloco JSON sem fechamento.",
            )
        }
        return body.substring(0, closing).trim()
    }

    private fun samePlace0413(left: String, right: String): Boolean =
        BlaBlaPublicSearchPlanner.normalizePlace(left) ==
            BlaBlaPublicSearchPlanner.normalizePlace(right)

    private fun JsonObject.object0413(key: String): JsonObject? =
        this[key] as? JsonObject

    private fun JsonObject.string0413(key: String): String =
        nullableString0413(key).orEmpty()

    private fun JsonObject.nullableString0413(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun JsonObject.int0413(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean0413(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.stringList0413(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { element ->
                (element as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            }
            ?.distinct()
            .orEmpty()
}
