package br.com.mapeiaia.rotacerta.trips

import kotlinx.serialization.Serializable

@Serializable
data class RideAppLearningRequest0700(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkSha256: String,
    val dossier: String,
)

@Serializable
data class RideAppLearningResponse0700(
    val status: String = "",
    val packageName: String = "",
    val profileVersion: Int = 1,
    val confidence: Double = 0.0,
    val pickupLabels: List<String> = emptyList(),
    val destinationLabels: List<String> = emptyList(),
    val fareLabels: List<String> = emptyList(),
    val distanceLabels: List<String> = emptyList(),
    val rideAnchors: List<String> = emptyList(),
    val actionLabels: List<String> = emptyList(),
    val resourceHints: List<String> = emptyList(),
    val ignoreLabels: List<String> = emptyList(),
    val reason: String = "",
    val provider: String = "",
    val model: String = "",
    val contract: String = "",
) {
    val learned: Boolean
        get() = status.equals("LEARNED", ignoreCase = true) &&
            confidence >= 0.60 &&
            (destinationLabels.isNotEmpty() || resourceHints.isNotEmpty())
}
