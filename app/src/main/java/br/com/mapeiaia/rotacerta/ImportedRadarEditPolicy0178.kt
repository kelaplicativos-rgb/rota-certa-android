package br.com.mapeiaia.rotacerta

object ImportedRadarEditPolicy0178 {
    const val MAX_NAME_LENGTH = 80
    const val MIN_SPEED_KMH = 1
    const val MAX_SPEED_KMH = 300

    fun sanitizeName(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)

    fun parseSpeed(value: String): Int? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        return normalized.toIntOrNull()?.takeIf { it in MIN_SPEED_KMH..MAX_SPEED_KMH }
    }

    fun apply(radar: ImportedRadar, name: String, speedText: String): ImportedRadar? {
        val normalizedSpeedText = speedText.trim()
        val speed = parseSpeed(normalizedSpeedText)
        if (normalizedSpeedText.isNotBlank() && speed == null) return null
        return radar.copy(
            name = sanitizeName(name),
            speedKmh = speed,
        )
    }
}
