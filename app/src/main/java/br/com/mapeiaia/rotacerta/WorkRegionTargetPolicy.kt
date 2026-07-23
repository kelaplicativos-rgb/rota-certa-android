package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

object WorkRegionTargetPolicy {
    const val MAX_PINS = 30
    const val LEGACY_PIN_ID = "legacy-alternative-pin"

    fun editablePins(settings: AppSettings): List<WorkRegionPin> {
        val explicit = settings.workRegionPins
            .filter { it.address.isNotBlank() }
            .distinctBy { identity(it) }
            .toMutableList()

        val legacyAddress = settings.alternativeAddress.trim()
        val legacyCoordinate = settings.alternativeCoordinate
        val legacyExists = legacyAddress.isNotBlank() || legacyCoordinate != null
        if (legacyExists) {
            val legacy = WorkRegionPin(
                id = LEGACY_PIN_ID,
                address = legacyAddress.ifBlank { "Alfinete salvo" },
                coordinate = legacyCoordinate,
                enabled = true,
                createdAtMillis = 0L,
            )
            if (explicit.none { identity(it) == identity(legacy) }) explicit += legacy
        }

        return explicit
            .sortedWith(
                compareBy<WorkRegionPin> { normalize(it.address) }
                    .thenBy { it.createdAtMillis }
                    .thenBy { it.id },
            )
            .take(MAX_PINS)
    }

    fun activePins(settings: AppSettings): List<WorkRegionPin> {
        if (!settings.alternativeTargetEnabled) return emptyList()
        return editablePins(settings)
            .filter { it.enabled && it.coordinate != null && it.address.isNotBlank() }
            .take(MAX_PINS)
    }

    fun addOrUpdate(settings: AppSettings, pin: WorkRegionPin): AppSettings {
        val cleanPin = pin.copy(address = pin.address.trim())
        if (cleanPin.address.isBlank() || cleanPin.coordinate == null) return settings

        val current = editablePins(settings)
            .filterNot { it.id == cleanPin.id || identity(it) == identity(cleanPin) }
        val updated = (current + cleanPin)
            .filter { it.address.isNotBlank() && it.coordinate != null }
            .distinctBy(::identity)
            .take(MAX_PINS)

        return settings.copy(
            workRegionPins = updated,
            alternativeAddress = "",
            alternativeCoordinate = null,
        )
    }

    fun remove(settings: AppSettings, pinId: String): AppSettings = settings.copy(
        workRegionPins = editablePins(settings).filterNot { it.id == pinId }.take(MAX_PINS),
        alternativeAddress = "",
        alternativeCoordinate = null,
    )

    fun setEnabled(settings: AppSettings, pinId: String, enabled: Boolean): AppSettings {
        val migrated = editablePins(settings).map { pin ->
            if (pin.id == pinId) pin.copy(enabled = enabled) else pin
        }
        return settings.copy(
            workRegionPins = migrated.take(MAX_PINS),
            alternativeAddress = "",
            alternativeCoordinate = null,
        )
    }

    fun nearestPin(
        pins: List<WorkRegionPin>,
        distancesKm: List<Double?>,
    ): WorkRegionPinDistance? = pins.indices
        .mapNotNull { index ->
            val distance = distancesKm.getOrNull(index) ?: return@mapNotNull null
            WorkRegionPinDistance(pins[index], distance)
        }
        .minByOrNull(WorkRegionPinDistance::distanceKm)

    fun cacheSignature(settings: AppSettings): String = editablePins(settings)
        .joinToString(";") { pin ->
            val coordinate = pin.coordinate?.let { "%.5f,%.5f".format(Locale.US, it.latitude, it.longitude) }.orEmpty()
            listOf(pin.id, normalize(pin.address), coordinate, pin.enabled.toString()).joinToString("|")
        }

    fun containsEquivalent(settings: AppSettings, address: String, coordinate: Coordinate?): Boolean {
        val candidate = WorkRegionPin("candidate", address.trim(), coordinate)
        return editablePins(settings).any { identity(it) == identity(candidate) }
    }

    private fun identity(pin: WorkRegionPin): String {
        val coordinate = pin.coordinate?.let {
            "%.5f,%.5f".format(Locale.US, it.latitude, it.longitude)
        }.orEmpty()
        return normalize(pin.address) + "|" + coordinate
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.trim().lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
}

data class WorkRegionPinDistance(
    val pin: WorkRegionPin,
    val distanceKm: Double,
)

data class WorkRegionPinRoute(
    val pin: WorkRegionPin,
    val distanceKm: Double?,
)
