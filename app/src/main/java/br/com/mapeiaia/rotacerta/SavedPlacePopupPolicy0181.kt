package br.com.mapeiaia.rotacerta

object SavedPlacePopupPolicy0181 {
    const val DEFAULT_NAME = "Local salvo"
    const val DEFAULT_ALERT_NAME = "Alerta de proximidade"

    fun savedName(input: String, type: SavedPlaceType): String = input.trim().ifBlank {
        if (type == SavedPlaceType.ProximityAlert) DEFAULT_ALERT_NAME else DEFAULT_NAME
    }

    fun displayAddress(address: String, fallback: String): String =
        address.trim().ifBlank { fallback.trim() }
}
