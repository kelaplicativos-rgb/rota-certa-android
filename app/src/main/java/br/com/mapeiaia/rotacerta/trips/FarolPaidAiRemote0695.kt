package br.com.mapeiaia.rotacerta.trips

import kotlinx.serialization.Serializable

@Serializable
data class FarolPaidAddressRequest0695(
    val text: String,
    val packageName: String,
    val fingerprint: String,
)

@Serializable
data class FarolPaidAddressResponse0695(
    val status: String = "",
    val address: String = "",
    val confidence: Double = 0.0,
    val reason: String = "",
    val provider: String = "",
    val model: String = "",
    val contract: String = "",
) {
    val resolved: Boolean
        get() = status.equals("RESOLVED", ignoreCase = true) &&
            address.isNotBlank() &&
            confidence >= 0.80
}
